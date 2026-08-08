package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ChatTemplateSource
import io.github.daniele21.localllm.contracts.ConfigurationErrorCode
import io.github.daniele21.localllm.contracts.ContextPolicy
import io.github.daniele21.localllm.contracts.EffectiveGenerationMetadata
import io.github.daniele21.localllm.contracts.GenerationEvent
import io.github.daniele21.localllm.contracts.GenerationHandle
import io.github.daniele21.localllm.contracts.GenerationInput
import io.github.daniele21.localllm.contracts.GenerationListener
import io.github.daniele21.localllm.contracts.GenerationMetrics
import io.github.daniele21.localllm.contracts.GenerationRequest
import io.github.daniele21.localllm.contracts.LocalLlmClient
import io.github.daniele21.localllm.contracts.LocalLlmError
import io.github.daniele21.localllm.contracts.ModelLoadKind
import io.github.daniele21.localllm.contracts.OutputConstraint
import io.github.daniele21.localllm.contracts.PrepareResult
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.RuntimeSnapshot
import io.github.daniele21.localllm.contracts.RuntimeState
import io.github.daniele21.localllm.contracts.SeedPolicy
import io.github.daniele21.localllm.contracts.SeedPolicyType
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.SessionOptions
import io.github.daniele21.localllm.contracts.ThinkingMode
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.models.ContextPreference
import io.github.daniele21.localllm.models.GenerationDefaults
import io.github.daniele21.localllm.models.InferencePreset
import io.github.daniele21.localllm.models.ModelProfileRegistry
import io.github.daniele21.localllm.models.OutputMode
import io.github.daniele21.localllm.models.ResolvedUseCase
import io.github.daniele21.localllm.observability.NoOpTelemetryRepository
import io.github.daniele21.localllm.observability.TelemetryRepository
import io.github.daniele21.localllm.store.ModelStore
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

fun interface MonotonicClock {
    fun nowNanos(): Long
}

fun interface SeedSource {
    fun nextSeed(): Long
}

@Suppress("TooManyFunctions", "LongParameterList", "LargeClass", "LongMethod")
class RuntimeOrchestrator(
    private val registry: ModelProfileRegistry,
    private val modelStore: ModelStore,
    private val backend: InferenceBackend,
    private val scheduler: SingleDecodeScheduler = SingleDecodeScheduler(),
    private val integrityCache: ModelIntegrityCache = ModelIntegrityCache(),
    private val clock: MonotonicClock = MonotonicClock(System::nanoTime),
    private val priorityResolver: (GenerationRequest) -> DecodePriority = { DecodePriority.USER_INTERACTIVE },
    private val memoryPolicy: RuntimeMemoryPolicy = RuntimeMemoryPolicy(),
    private val seedSource: SeedSource = SeedSource { ThreadLocalRandom.current().nextLong(MAX_SEED_EXCLUSIVE) },
    telemetryRepository: TelemetryRepository = NoOpTelemetryRepository,
    epochClock: EpochClock = EpochClock { System.currentTimeMillis() },
) : LocalLlmClient,
    AutoCloseable {
    private val resourceLock = Any()
    private val state = AtomicReference(RuntimeState.IDLE)
    private val sessions = ConcurrentHashMap<SessionId, SessionDescriptor>()
    private val closed = AtomicBoolean(false)
    private val deferredModelUnload = AtomicBoolean(false)
    private val runtimeTelemetry = RuntimeTelemetry(telemetryRepository, epochClock)

    @Volatile
    private var backendInitialized = false

    @Volatile
    private var loadedModel: LoadedModelDescriptor? = null

    override fun runtimeSnapshot(): RuntimeSnapshot {
        val schedulerSnapshot = scheduler.snapshot()
        return RuntimeSnapshot(
            state = state.get(),
            loadedModel = loadedModel?.handle?.digest,
            activeSessions = sessions.size,
            queuedRequests = schedulerSnapshot.queuedRequests,
        )
    }

    override fun prepare(applicationId: ApplicationId, useCaseId: UseCaseId): PrepareResult {
        if (closed.get()) {
            return PrepareResult(false, null, "Runtime is closed")
        }
        state.set(RuntimeState.PREPARING)
        return try {
            val resolved = registry.resolve(applicationId, useCaseId)
            val model = synchronized(resourceLock) { ensureModelLoaded(resolved) }
            state.set(RuntimeState.READY)
            PrepareResult(
                ready = true,
                modelDigest = model.handle.digest,
                detail = "Model verified and loaded with ${backend.id}",
            )
        } catch (error: Throwable) {
            state.set(RuntimeState.FAILED)
            PrepareResult(
                ready = false,
                modelDigest = null,
                detail = error.message ?: "Unable to prepare local model",
            )
        }
    }

    override fun createSession(applicationId: ApplicationId, useCaseId: UseCaseId): SessionId =
        createSession(applicationId, useCaseId, SessionOptions())

    override fun createSession(applicationId: ApplicationId, useCaseId: UseCaseId, options: SessionOptions): SessionId {
        check(!closed.get()) { "Runtime is closed" }
        val resolved = registry.resolve(applicationId, useCaseId)
        return synchronized(resourceLock) {
            val requestedDigest = resolved.model.artifact.digest
            val warm = loadedModel?.let { current ->
                current.handle.digest == requestedDigest && current.profileId == resolved.model.id
            } == true
            val model = ensureModelLoaded(resolved)
            val sessionId = SessionId(UUID.randomUUID().toString())
            sessions[sessionId] = SessionDescriptor(
                id = sessionId,
                applicationId = applicationId,
                useCaseId = useCaseId,
                resolved = resolved,
                model = model.handle,
                options = options,
                modelLoadDurationMs = if (warm) null else model.handle.loadDurationMs,
                modelLoadKind = if (warm) ModelLoadKind.WARM else ModelLoadKind.COLD,
            )
            state.set(RuntimeState.READY)
            sessionId
        }
    }

    @Suppress("ReturnCount")
    override fun generate(request: GenerationRequest, listener: GenerationListener): GenerationHandle {
        if (closed.get()) {
            return failImmediately(request.requestId, listener, LocalLlmError.Configuration("Runtime is closed"))
        }
        val session = sessions[request.sessionId]
            ?: return failImmediately(
                request.requestId,
                listener,
                LocalLlmError.Configuration("Unknown session ${request.sessionId.value}"),
            )
        if (session.applicationId != request.applicationId || session.useCaseId != request.useCaseId) {
            return failImmediately(
                request.requestId,
                listener,
                LocalLlmError.Configuration("Generation request does not match its session binding"),
            )
        }
        if (!session.acquireRequest()) {
            return failImmediately(
                request.requestId,
                listener,
                LocalLlmError.Configuration("Session ${request.sessionId.value} is closing"),
            )
        }

        val lifecycle = RequestLifecycle(
            requestId = request.requestId,
            listener = listener,
            onTerminal = { releaseSessionRequest(session) },
        )
        val enqueuedAt = clock.nowNanos()
        runtimeTelemetry.queued(request, session.model.digest)

        val submission = try {
            scheduler.submit(
                requestId = request.requestId,
                priority = priorityResolver(request),
                task = { executeGeneration(request, session, lifecycle, enqueuedAt) },
                onQueuedCancellation = {
                    lifecycle.cancelRequested.set(true)
                    val cancellation = LocalLlmError.Cancelled("Generation cancelled before execution")
                    runtimeTelemetry.failed(request.requestId, cancellation)
                    lifecycle.finish(GenerationEvent.Failed(request.requestId, cancellation))
                },
                onRunningCancellation = {
                    lifecycle.cancelRequested.set(true)
                    runCatching { backend.cancel(request.requestId.value) }
                },
                onQueued = { position ->
                    runtimeTelemetry.queuedPosition(request.requestId, position)
                    lifecycle.emit(GenerationEvent.Queued(request.requestId, position))
                },
            )
        } catch (error: Throwable) {
            val failure = LocalLlmError.Configuration(error.message ?: "Unable to schedule generation")
            runtimeTelemetry.failed(request.requestId, failure)
            lifecycle.finish(GenerationEvent.Failed(request.requestId, failure))
            return NoOpGenerationHandle(request.requestId)
        }

        return RuntimeGenerationHandle(
            requestId = request.requestId,
            schedulerHandle = submission.handle,
            lifecycle = lifecycle,
        )
    }

    override fun closeSession(sessionId: SessionId) {
        val session = sessions[sessionId] ?: return
        session.closing.set(true)
        if (session.activeRequests.get() == 0) {
            releaseSession(session)
        }
    }

    fun memoryResourceSnapshot(): RuntimeMemoryResourceSnapshot {
        val schedulerSnapshot = scheduler.snapshot()
        return RuntimeMemoryResourceSnapshot(
            modelLoaded = loadedModel != null,
            activeSessions = sessions.size,
            activeGeneration = schedulerSnapshot.activeRequest != null,
            queuedGenerations = schedulerSnapshot.queuedRequests,
        )
    }

    fun handleMemoryPressure(pressure: RuntimeMemoryPressure): RuntimeMemoryResult {
        val action = memoryPolicy.decide(pressure, memoryResourceSnapshot())
        return when (action) {
            RuntimeMemoryAction.NONE -> RuntimeMemoryResult(action, 0, false, false)

            RuntimeMemoryAction.UNLOAD_IDLE_MODEL -> {
                val unloaded = unloadIdleModel()
                RuntimeMemoryResult(action, 0, unloaded, deferred = !unloaded)
            }

            RuntimeMemoryAction.CANCEL_AND_RELEASE_ALL -> releaseForCriticalMemory(action)
        }
    }

    fun unloadIdleModel(): Boolean = synchronized(resourceLock) {
        val schedulerSnapshot = scheduler.snapshot()
        if (sessions.isNotEmpty() || schedulerSnapshot.activeRequest != null || schedulerSnapshot.queuedRequests != 0) {
            return@synchronized false
        }
        val model = loadedModel ?: return@synchronized false
        backend.unloadModel(model.handle)
        loadedModel = null
        state.set(RuntimeState.IDLE)
        true
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        scheduler.snapshot().activeRequest?.let(scheduler::cancel)
        scheduler.close()
        sessions.values.forEach { session ->
            session.closing.set(true)
            if (session.activeRequests.get() == 0) {
                releaseSession(session)
            }
        }
        synchronized(resourceLock) {
            if (sessions.isEmpty()) {
                loadedModel?.let { backend.unloadModel(it.handle) }
                loadedModel = null
                if (backendInitialized) {
                    backend.shutdown()
                    backendInitialized = false
                }
            }
        }
        deferredModelUnload.set(false)
        integrityCache.clear()
        state.set(RuntimeState.IDLE)
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun executeGeneration(request: GenerationRequest, session: SessionDescriptor, lifecycle: RequestLifecycle, enqueuedAt: Long) {
        if (lifecycle.cancelRequested.get()) {
            val cancellation = LocalLlmError.Cancelled()
            runtimeTelemetry.failed(request.requestId, cancellation)
            lifecycle.finish(GenerationEvent.Failed(request.requestId, cancellation))
            return
        }

        try {
            val executionStartedAt = clock.nowNanos()
            val resolved = resolveGenerationConfiguration(request, session)
            lifecycle.ensureNotCancelled()
            val planningStartedAt = clock.nowNanos()
            val promptPlan = backend.planPrompt(
                session.model,
                BackendPromptPlanningRequest(
                    input = request.input,
                    systemPrompt = resolved.systemPrompt,
                    chatTemplatePolicy = session.resolved.model.chatTemplatePolicy,
                    thinkingMode = resolved.thinkingMode,
                ),
            )
            lifecycle.ensureNotCancelled()
            val promptPlanningMs = nanosToMillis(clock.nowNanos() - planningStartedAt)
            val capabilities = backend.modelCapabilities(session.model)
            lifecycle.ensureNotCancelled()
            validateOutputConstraint(request, resolved, capabilities)
            val requestedContextSize = resolveContextSize(
                session = session,
                promptTokenCount = promptPlan.tokenCount,
                maxOutputTokens = resolved.maxOutputTokens,
                capabilities = capabilities,
                preference = resolved.contextPreference,
            )
            lifecycle.ensureNotCancelled()
            val contextResult = materializeContext(session, requestedContextSize)
            if (lifecycle.cancelRequested.get()) {
                releaseCancelledMaterialization(session, contextResult)
                throw GenerationCancelledException()
            }
            val effective = EffectiveGenerationMetadata(
                preset = resolved.preset?.ref,
                temperature = resolved.temperature,
                topP = resolved.topP,
                topK = resolved.topK,
                minP = resolved.minP,
                presencePenalty = resolved.presencePenalty,
                repeatPenalty = resolved.repeatPenalty,
                repeatLastN = resolved.repeatLastN,
                requestedSeedPolicy = resolved.seedPolicy.toType(),
                effectiveSeed = resolved.effectiveSeed,
                maxOutputTokens = resolved.maxOutputTokens,
                contextSize = contextResult.context.contextSize,
                promptTokenCount = promptPlan.tokenCount,
                chatTemplateId = promptPlan.chatTemplateId,
                chatTemplateSource = promptPlan.chatTemplateSource,
                systemPromptVersion = resolved.systemPromptVersion,
                thinkingMode = resolved.thinkingMode,
            )
            runtimeTelemetry.prepared(
                requestId = request.requestId,
                configuration = effective,
                promptPlanningMs = promptPlanningMs,
                contextCreationMs = contextResult.creationMs,
            )
            lifecycle.emit(GenerationEvent.Prepared(request.requestId, session.model.digest, effective))

            val firstTokenAt = AtomicLong(0)
            val output = StringBuilder()
            state.set(RuntimeState.GENERATING)
            runtimeTelemetry.started(request.requestId)
            lifecycle.emit(GenerationEvent.Started(request.requestId, session.model.digest))
            val backendRequest = BackendGenerationRequest(
                requestId = request.requestId.value,
                prompt = promptPlan.prompt,
                maxOutputTokens = resolved.maxOutputTokens,
                temperature = resolved.temperature,
                topP = resolved.topP,
                topK = resolved.topK,
                minP = resolved.minP,
                presencePenalty = resolved.presencePenalty,
                repeatPenalty = resolved.repeatPenalty,
                repeatLastN = resolved.repeatLastN,
                seed = resolved.effectiveSeed,
                outputConstraint = request.outputConstraint,
                stopTokenIds = promptPlan.stopTokenIds,
                stopSequences = promptPlan.stopSequences,
            )
            lifecycle.ensureNotCancelled()
            val outcome = backend.generate(contextResult.context, backendRequest) { text, generatedTokens ->
                if (lifecycle.cancelRequested.get()) {
                    false
                } else {
                    firstTokenAt.compareAndSet(0, clock.nowNanos())
                    output.append(text)
                    lifecycle.emit(
                        GenerationEvent.TextDelta(
                            requestId = request.requestId,
                            text = text,
                            generatedTokens = generatedTokens,
                        ),
                    )
                    !lifecycle.cancelRequested.get()
                }
            }

            if (lifecycle.cancelRequested.get() || outcome is BackendGenerationOutcome.Cancelled) {
                val cancellation = LocalLlmError.Cancelled()
                runtimeTelemetry.failed(request.requestId, cancellation)
                lifecycle.finish(GenerationEvent.Failed(request.requestId, cancellation))
            } else {
                val backendMetrics = (outcome as BackendGenerationOutcome.Completed).metrics
                val publicMetrics = backendMetrics.toPublicMetrics(
                    queueMs = nanosToMillis(executionStartedAt - enqueuedAt),
                    modelLoadMs = session.modelLoadDurationMs,
                    modelLoadKind = session.modelLoadKind,
                    timeToFirstTokenMs = firstTokenAt.get().takeIf { it != 0L }
                        ?.let { nanosToMillis(it - executionStartedAt) },
                    totalMs = nanosToMillis(clock.nowNanos() - enqueuedAt),
                    promptPlanningMs = promptPlanningMs,
                    contextCreationMs = contextResult.creationMs,
                )
                runtimeTelemetry.completed(request.requestId, publicMetrics)
                lifecycle.finish(
                    GenerationEvent.Completed(
                        requestId = request.requestId,
                        output = output.toString(),
                        metrics = publicMetrics,
                    ),
                )
            }
        } catch (_: GenerationCancelledException) {
            val cancellation = LocalLlmError.Cancelled()
            runtimeTelemetry.failed(request.requestId, cancellation)
            lifecycle.finish(GenerationEvent.Failed(request.requestId, cancellation))
        } catch (error: GenerationPlanningException) {
            val failure = LocalLlmError.Configuration(error.message.orEmpty(), error.reason)
            runtimeTelemetry.failed(request.requestId, failure)
            lifecycle.finish(GenerationEvent.Failed(request.requestId, failure))
        } catch (error: BackendException) {
            val failure = error.toPublicError()
            runtimeTelemetry.failed(request.requestId, failure)
            lifecycle.finish(GenerationEvent.Failed(request.requestId, failure))
        } catch (error: Throwable) {
            val failure = LocalLlmError.NativeRuntime(error.message ?: "Unexpected local inference failure")
            runtimeTelemetry.failed(request.requestId, failure)
            lifecycle.finish(GenerationEvent.Failed(request.requestId, failure))
        } finally {
            val unloaded = attemptDeferredModelUnload(ignoreActiveGeneration = true)
            if (!closed.get() && !unloaded) {
                state.set(if (deferredModelUnload.get()) RuntimeState.DEGRADED else RuntimeState.READY)
            }
        }
    }

    @Suppress("CyclomaticComplexMethod", "ComplexCondition", "ThrowsCount")
    private fun resolveGenerationConfiguration(request: GenerationRequest, session: SessionDescriptor): ResolvedRequestConfiguration {
        val useCase = session.resolved.useCase
        val requestedPreset = request.overrides.preset ?: useCase.defaultPreset
        val preset = requestedPreset?.let { ref ->
            useCase.presets.firstOrNull { it.ref == ref }
                ?: throw GenerationPlanningException(
                    ConfigurationErrorCode.PRESET_NOT_FOUND,
                    "Preset ${ref.id.value} version ${ref.version} is not available",
                )
        }
        val defaults = preset?.generation ?: useCase.generationDefaults
        val maxOutputTokens = request.overrides.maxOutputTokens ?: defaults.maxOutputTokens
        val temperature = request.overrides.temperature ?: defaults.temperature
        val topP = request.overrides.topP ?: defaults.topP
        val topK = request.overrides.topK ?: defaults.topK
        val minP = request.overrides.minP ?: defaults.minP
        val presencePenalty = request.overrides.presencePenalty ?: defaults.presencePenalty
        val thinkingMode = request.overrides.thinkingMode ?: defaults.thinkingMode
        val repeatPenalty = request.overrides.repeatPenalty ?: defaults.repeatPenalty
        val repeatLastN = request.overrides.repeatLastN ?: defaults.repeatLastN
        validateGenerationValues(
            maxOutputTokens,
            temperature,
            topP,
            topK,
            minP,
            presencePenalty,
            repeatPenalty,
            repeatLastN,
        )

        val seedPolicy = request.overrides.requestedSeedPolicy() ?: defaults.seedPolicy
        val effectiveSeed = when (seedPolicy) {
            is SeedPolicy.Fixed -> seedPolicy.value
            SeedPolicy.Random -> seedSource.nextSeed()
        }
        if (effectiveSeed !in 0 until MAX_SEED_EXCLUSIVE) {
            throw GenerationPlanningException(
                ConfigurationErrorCode.INVALID_GENERATION_CONFIGURATION,
                "Seed source returned a value outside the unsigned 32-bit range",
            )
        }
        if (request.input is GenerationInput.RawCompletion && thinkingMode == ThinkingMode.ENABLED) {
            throw GenerationPlanningException(
                ConfigurationErrorCode.INVALID_GENERATION_CONFIGURATION,
                "Thinking mode requires chat-template rendering and cannot be used with raw completion",
            )
        }
        if (request.input is GenerationInput.RawCompletion && !session.resolved.model.chatTemplatePolicy.allowRawCompletion) {
            throw GenerationPlanningException(
                ConfigurationErrorCode.RAW_COMPLETION_NOT_ALLOWED,
                "Raw completion is not allowed for this model profile",
            )
        }
        return ResolvedRequestConfiguration(
            preset = preset,
            maxOutputTokens = maxOutputTokens,
            temperature = temperature,
            topP = topP,
            topK = topK,
            minP = minP,
            presencePenalty = presencePenalty,
            thinkingMode = thinkingMode,
            repeatPenalty = repeatPenalty,
            repeatLastN = repeatLastN,
            seedPolicy = seedPolicy,
            effectiveSeed = effectiveSeed,
            systemPromptVersion = preset?.systemPromptVersion ?: useCase.systemPromptVersion,
            systemPrompt = preset?.systemPrompt ?: useCase.systemPrompt,
            contextPreference = preset?.contextPreference ?: ContextPreference(),
        )
    }

    @Suppress("ComplexCondition")
    private fun validateGenerationValues(
        maxOutputTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        minP: Float,
        presencePenalty: Float,
        repeatPenalty: Float,
        repeatLastN: Int,
    ) {
        if (maxOutputTokens !in 1..MAX_OUTPUT_TOKENS ||
            !temperature.isFinite() || temperature !in 0f..2f ||
            !topP.isFinite() || topP <= 0f || topP > 1f ||
            topK !in 0..MAX_TOP_K ||
            !minP.isFinite() || minP !in 0f..1f ||
            !presencePenalty.isFinite() || presencePenalty !in 0f..2f ||
            !repeatPenalty.isFinite() || repeatPenalty !in MIN_REPEAT_PENALTY..MAX_REPEAT_PENALTY ||
            repeatLastN !in 0..MAX_REPEAT_LAST_N ||
            (repeatPenalty != MIN_REPEAT_PENALTY && repeatLastN == 0)
        ) {
            throw GenerationPlanningException(
                ConfigurationErrorCode.INVALID_GENERATION_CONFIGURATION,
                "Generation settings are outside the supported bounds",
            )
        }
    }

    private fun validateOutputConstraint(
        request: GenerationRequest,
        resolved: ResolvedRequestConfiguration,
        capabilities: BackendModelCapabilities,
    ) {
        val requestedMode = when (request.outputConstraint) {
            OutputConstraint.Text -> OutputMode.TEXT
            OutputConstraint.Json -> OutputMode.JSON
            is OutputConstraint.JsonSchema -> OutputMode.JSON_SCHEMA
        }
        val allowedModes = resolved.preset?.allowedOutputModes
            ?: setOf(sessionOutputMode(request.sessionId))
        if (requestedMode !in allowedModes ||
            (request.outputConstraint !is OutputConstraint.Text && !capabilities.supportsGrammar)
        ) {
            throw GenerationPlanningException(
                ConfigurationErrorCode.OUTPUT_CONSTRAINT_UNSUPPORTED,
                "The requested output constraint is not supported by this use case and backend",
            )
        }
    }

    private fun sessionOutputMode(sessionId: SessionId): OutputMode = sessions[sessionId]?.resolved?.useCase?.outputMode
        ?: throw GenerationPlanningException(ConfigurationErrorCode.CONFIGURATION, "Session is no longer available")

    @Suppress("ThrowsCount")
    private fun resolveContextSize(
        session: SessionDescriptor,
        promptTokenCount: Int,
        maxOutputTokens: Int,
        capabilities: BackendModelCapabilities,
        preference: ContextPreference,
    ): Int {
        if (promptTokenCount <= 0 || capabilities.maximumContextTokens <= 0) {
            throw GenerationPlanningException(
                ConfigurationErrorCode.PROMPT_TOKENIZATION_FAILED,
                "Prompt tokenization did not produce a valid token count",
            )
        }
        val required = runCatching {
            Math.addExact(Math.addExact(promptTokenCount, maxOutputTokens), CONTEXT_RESERVE_TOKENS)
        }.getOrElse {
            throw GenerationPlanningException(
                ConfigurationErrorCode.CONTEXT_CAPACITY_EXCEEDED,
                "Prompt and output budget exceed the supported context capacity",
            )
        }
        val maximum = minOf(capabilities.maximumContextTokens, preference.maximumTokens ?: Int.MAX_VALUE)
        if (required > maximum) {
            throw GenerationPlanningException(
                ConfigurationErrorCode.CONTEXT_CAPACITY_EXCEEDED,
                "Prompt and output require $required tokens but the maximum is $maximum",
            )
        }
        return when (val policy = session.options.contextPolicy) {
            is ContextPolicy.Manual -> {
                if (!ContextSizeSelector.supportsManual(policy.tokens, required, maximum)) {
                    throw GenerationPlanningException(
                        ConfigurationErrorCode.CONTEXT_CAPACITY_EXCEEDED,
                        "Prompt and output require $required tokens but the manual context is ${policy.tokens}",
                    )
                }
                policy.tokens
            }

            ContextPolicy.Auto -> ContextSizeSelector.selectAuto(
                required = required,
                maximum = maximum,
                preferredMinimum = preference.preferredTokens,
            ) ?: throw GenerationPlanningException(
                ConfigurationErrorCode.CONTEXT_CAPACITY_EXCEEDED,
                "No supported context size can contain the requested prompt and output",
            )
        }
    }

    private fun materializeContext(session: SessionDescriptor, requestedContextSize: Int): ContextMaterialization =
        synchronized(resourceLock) {
            val current = session.context
            if (current != null && current.contextSize >= requestedContextSize) {
                return@synchronized ContextMaterialization(current, null, created = false)
            }
            if (current != null && session.options.kind == SessionKind.CONVERSATIONAL) {
                throw GenerationPlanningException(
                    ConfigurationErrorCode.CONTEXT_RECONFIGURATION_REQUIRED,
                    "Conversational context growth requires an explicit new session",
                )
            }
            if (current != null) {
                backend.releaseContext(current)
                session.context = null
            }
            val startedAt = clock.nowNanos()
            val created = backend.createContext(
                model = session.model,
                profile = session.resolved.model,
                configuration = BackendContextConfiguration(requestedContextSize),
            )
            session.context = created
            ContextMaterialization(created, nanosToMillis(clock.nowNanos() - startedAt), created = true)
        }

    private fun releaseCancelledMaterialization(session: SessionDescriptor, materialization: ContextMaterialization) {
        if (!materialization.created) return
        synchronized(resourceLock) {
            if (session.context === materialization.context) {
                backend.releaseContext(materialization.context)
                session.context = null
            }
        }
    }

    private fun SeedPolicy.toType(): SeedPolicyType = when (this) {
        SeedPolicy.Random -> SeedPolicyType.RANDOM
        is SeedPolicy.Fixed -> SeedPolicyType.FIXED
    }

    private fun BackendException.toPublicError(): LocalLlmError {
        val reason = when (code) {
            "CHAT_TEMPLATE_UNAVAILABLE" -> ConfigurationErrorCode.CHAT_TEMPLATE_UNAVAILABLE
            "CHAT_TEMPLATE_UNSUPPORTED" -> ConfigurationErrorCode.CHAT_TEMPLATE_UNSUPPORTED
            "TOKENIZATION_FAILED" -> ConfigurationErrorCode.PROMPT_TOKENIZATION_FAILED
            "CONTEXT_OVERFLOW" -> ConfigurationErrorCode.CONTEXT_CAPACITY_EXCEEDED
            "OUTPUT_CONSTRAINT_UNSUPPORTED" -> ConfigurationErrorCode.OUTPUT_CONSTRAINT_UNSUPPORTED
            "INVALID_OUTPUT_CONSTRAINT" -> ConfigurationErrorCode.INVALID_OUTPUT_CONSTRAINT
            else -> null
        }
        return if (reason == null) {
            LocalLlmError.NativeRuntime("$code: ${message.orEmpty()}")
        } else {
            LocalLlmError.Configuration(message.orEmpty(), reason)
        }
    }

    private fun ensureModelLoaded(resolved: ResolvedUseCase): LoadedModelDescriptor {
        check(!closed.get()) { "Runtime is closed" }
        val requestedDigest = resolved.model.artifact.digest
        val current = loadedModel
        if (current != null && current.handle.digest == requestedDigest && current.profileId == resolved.model.id) {
            return current
        }

        val schedulerSnapshot = scheduler.snapshot()
        check(sessions.isEmpty() && schedulerSnapshot.activeRequest == null && schedulerSnapshot.queuedRequests == 0) {
            "Cannot switch model while sessions or generation requests are active"
        }

        val stored = modelStore.find(requestedDigest)
            ?: throw BackendException("MODEL_UNAVAILABLE", "Model ${requestedDigest.sha256} is not installed")
        val verification = integrityCache.verify(modelStore, stored)
        if (!verification.valid) {
            throw BackendException("MODEL_INTEGRITY", verification.detail)
        }

        if (!backendInitialized) {
            backend.initialize()
            backendInitialized = true
        }
        current?.let { backend.unloadModel(it.handle) }
        loadedModel = null

        val loaded = LoadedModelDescriptor(
            profileId = resolved.model.id,
            handle = backend.loadModel(stored, resolved.model),
        )
        loadedModel = loaded
        return loaded
    }

    private fun releaseSessionRequest(session: SessionDescriptor) {
        val remaining = session.activeRequests.decrementAndGet()
        check(remaining >= 0) { "Session request count became negative" }
        if (remaining == 0 && session.closing.get()) {
            releaseSession(session)
        }
    }

    private fun releaseSession(session: SessionDescriptor) {
        if (!session.released.compareAndSet(false, true)) {
            return
        }
        val release = runCatching {
            synchronized(resourceLock) {
                session.context?.let(backend::releaseContext)
                session.context = null
                sessions.remove(session.id, session)
            }
        }
        if (release.isFailure) {
            session.released.set(false)
            state.set(RuntimeState.DEGRADED)
            release.getOrThrow()
        }
        attemptDeferredModelUnload(ignoreActiveGeneration = false)
    }

    private fun releaseForCriticalMemory(action: RuntimeMemoryAction): RuntimeMemoryResult {
        deferredModelUnload.set(true)
        sessions.values.forEach { session -> session.closing.set(true) }
        val cancelled = scheduler.cancelAll()
        sessions.values.forEach { session ->
            if (session.activeRequests.get() == 0) {
                releaseSession(session)
            }
        }
        val unloaded = attemptDeferredModelUnload(ignoreActiveGeneration = false)
        if (!unloaded) {
            state.set(RuntimeState.DEGRADED)
        }
        return RuntimeMemoryResult(
            action = action,
            cancelledRequests = cancelled,
            modelUnloaded = unloaded,
            deferred = !unloaded,
        )
    }

    private fun attemptDeferredModelUnload(ignoreActiveGeneration: Boolean): Boolean {
        if (!deferredModelUnload.get()) return false
        return synchronized(resourceLock) {
            val schedulerSnapshot = scheduler.snapshot()
            val activeBlocksUnload = !ignoreActiveGeneration && schedulerSnapshot.activeRequest != null
            if (sessions.isNotEmpty() || schedulerSnapshot.queuedRequests != 0 || activeBlocksUnload) {
                return@synchronized false
            }

            loadedModel?.let { backend.unloadModel(it.handle) }
            loadedModel = null
            deferredModelUnload.set(false)
            state.set(RuntimeState.IDLE)
            true
        }
    }

    private fun failImmediately(requestId: RequestId, listener: GenerationListener, error: LocalLlmError): GenerationHandle {
        runtimeTelemetry.rejected(requestId, error)
        runCatching { listener.onEvent(GenerationEvent.Failed(requestId, error)) }
        return NoOpGenerationHandle(requestId)
    }

    private fun BackendGenerationMetrics.toPublicMetrics(
        queueMs: Long,
        modelLoadMs: Long?,
        modelLoadKind: ModelLoadKind,
        timeToFirstTokenMs: Long?,
        totalMs: Long,
        promptPlanningMs: Long,
        contextCreationMs: Long?,
    ): GenerationMetrics = GenerationMetrics(
        queueMs = queueMs,
        modelLoadMs = modelLoadMs,
        timeToFirstTokenMs = timeToFirstTokenMs,
        totalMs = totalMs,
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        decodeTokensPerSecond = if (generationDurationMs > 0) {
            outputTokens * 1_000.0 / generationDurationMs
        } else {
            null
        },
        prefillMs = promptDurationMs,
        decodeMs = generationDurationMs,
        modelLoadKind = modelLoadKind,
        stopReason = stopReason,
        promptPlanningMs = promptPlanningMs,
        contextCreationMs = contextCreationMs,
    )

    private fun nanosToMillis(nanos: Long): Long = nanos / 1_000_000

    private data class LoadedModelDescriptor(val profileId: String, val handle: BackendModelHandle)

    private data class SessionDescriptor(
        val id: SessionId,
        val applicationId: ApplicationId,
        val useCaseId: UseCaseId,
        val resolved: ResolvedUseCase,
        val model: BackendModelHandle,
        val options: SessionOptions,
        val modelLoadDurationMs: Long?,
        val modelLoadKind: ModelLoadKind,
        @Volatile var context: BackendContextHandle? = null,
        val activeRequests: AtomicInteger = AtomicInteger(0),
        val closing: AtomicBoolean = AtomicBoolean(false),
        val released: AtomicBoolean = AtomicBoolean(false),
    ) {
        fun acquireRequest(): Boolean {
            if (closing.get()) return false
            activeRequests.incrementAndGet()
            if (!closing.get()) return true
            activeRequests.decrementAndGet()
            return false
        }
    }

    private data class ResolvedRequestConfiguration(
        val preset: InferencePreset?,
        val maxOutputTokens: Int,
        val temperature: Float,
        val topP: Float,
        val topK: Int,
        val minP: Float,
        val presencePenalty: Float,
        val thinkingMode: ThinkingMode,
        val repeatPenalty: Float,
        val repeatLastN: Int,
        val seedPolicy: SeedPolicy,
        val effectiveSeed: Long,
        val systemPromptVersion: String,
        val systemPrompt: String?,
        val contextPreference: ContextPreference,
    )

    private data class ContextMaterialization(val context: BackendContextHandle, val creationMs: Long?, val created: Boolean)

    private class GenerationPlanningException(val reason: ConfigurationErrorCode, message: String) : IllegalArgumentException(message)

    private companion object {
        const val MAX_SEED_EXCLUSIVE = 0x1_0000_0000L
        const val MAX_OUTPUT_TOKENS = 32_768
        const val MAX_TOP_K = 1_000
        const val MIN_REPEAT_PENALTY = 1f
        const val MAX_REPEAT_PENALTY = 2f
        const val MAX_REPEAT_LAST_N = 4_096
        const val CONTEXT_RESERVE_TOKENS = 256
    }
}

private class GenerationCancelledException : RuntimeException()

private class RequestLifecycle(val requestId: RequestId, private val listener: GenerationListener, private val onTerminal: () -> Unit) {
    val cancelRequested = AtomicBoolean(false)
    private val terminal = AtomicBoolean(false)

    fun emit(event: GenerationEvent) {
        if (!terminal.get()) {
            runCatching { listener.onEvent(event) }
        }
    }

    fun ensureNotCancelled() {
        if (cancelRequested.get()) throw GenerationCancelledException()
    }

    fun finish(event: GenerationEvent) {
        if (!terminal.compareAndSet(false, true)) {
            return
        }
        try {
            runCatching { listener.onEvent(event) }
        } finally {
            onTerminal()
        }
    }
}

private class RuntimeGenerationHandle(
    override val requestId: RequestId,
    private val schedulerHandle: DecodeTaskHandle,
    private val lifecycle: RequestLifecycle,
) : GenerationHandle {
    override fun cancel() {
        lifecycle.cancelRequested.set(true)
        schedulerHandle.cancel()
    }
}

private class NoOpGenerationHandle(override val requestId: RequestId) : GenerationHandle {
    override fun cancel() = Unit
}
