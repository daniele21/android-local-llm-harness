package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ConfigurationErrorCode
import io.github.daniele21.localllm.contracts.EffectiveGenerationMetadata
import io.github.daniele21.localllm.contracts.GenerationContentType
import io.github.daniele21.localllm.contracts.GenerationEvent
import io.github.daniele21.localllm.contracts.GenerationHandle
import io.github.daniele21.localllm.contracts.GenerationListener
import io.github.daniele21.localllm.contracts.GenerationMetrics
import io.github.daniele21.localllm.contracts.GenerationRequest
import io.github.daniele21.localllm.contracts.LocalLlmClient
import io.github.daniele21.localllm.contracts.LocalLlmError
import io.github.daniele21.localllm.contracts.ModelLoadKind
import io.github.daniele21.localllm.contracts.PrepareResult
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.RuntimeSnapshot
import io.github.daniele21.localllm.contracts.RuntimeState
import io.github.daniele21.localllm.contracts.SeedPolicy
import io.github.daniele21.localllm.contracts.SeedPolicyType
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.SessionOptions
import io.github.daniele21.localllm.contracts.StopReason
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.models.ModelProfileRegistry
import io.github.daniele21.localllm.models.ResolvedUseCase
import io.github.daniele21.localllm.observability.NoOpTelemetryRepository
import io.github.daniele21.localllm.observability.TelemetryRepository
import io.github.daniele21.localllm.store.ModelStore
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicBoolean
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
    private val memoryAwareContextPlanner: MemoryAwareContextPlanner? = null,
    private val memoryAwareModelLoadPlanner: MemoryAwareModelLoadPlanner? = null,
    seedSource: SeedSource = SeedSource { ThreadLocalRandom.current().nextLong(MAX_SEED_EXCLUSIVE) },
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
    private val generationPlanning = GenerationPlanningPolicy(seedSource)
    private val modelResidency = ModelResidencyLifecycle()

    @Volatile
    private var backendInitialized = false

    override fun runtimeSnapshot(): RuntimeSnapshot {
        val schedulerSnapshot = scheduler.snapshot()
        return RuntimeSnapshot(
            state = state.get(),
            loadedModel = modelResidency.residentModelOrNull()?.handle?.digest,
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
            val warm = modelResidency.reusableModelOrNull()?.let { current ->
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
        if (!session.lifecycle.tryAcquireRequest()) {
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
                    lifecycle.requestCancellation()
                    val cancellation = LocalLlmError.Cancelled("Generation cancelled before execution")
                    runtimeTelemetry.failed(request.requestId, cancellation)
                    lifecycle.finish(GenerationEvent.Failed(request.requestId, cancellation))
                },
                onRunningCancellation = {
                    lifecycle.requestCancellation()
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
        session.lifecycle.beginClose()
        if (session.lifecycle.isReleaseReady()) {
            releaseSession(session)
        }
    }

    fun memoryResourceSnapshot(): RuntimeMemoryResourceSnapshot {
        val schedulerSnapshot = scheduler.snapshot()
        return RuntimeMemoryResourceSnapshot(
            modelLoaded = modelResidency.hasResidentModel(),
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
        if (!unloadResidentModel()) {
            return@synchronized false
        }
        state.set(RuntimeState.IDLE)
        true
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        deferredModelUnload.set(true)
        scheduler.snapshot().activeRequest?.let(scheduler::cancel)
        scheduler.close()
        sessions.values.forEach { session ->
            session.lifecycle.beginClose()
            if (session.lifecycle.isReleaseReady()) {
                releaseSession(session)
            }
        }
        val finalized = attemptDeferredModelUnload(ignoreActiveGeneration = false)
        integrityCache.clear()
        if (!finalized) {
            state.set(RuntimeState.DEGRADED)
        }
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun executeGeneration(request: GenerationRequest, session: SessionDescriptor, lifecycle: RequestLifecycle, enqueuedAt: Long) {
        if (lifecycle.isCancellationRequested()) {
            val cancellation = LocalLlmError.Cancelled()
            runtimeTelemetry.failed(request.requestId, cancellation)
            lifecycle.finish(GenerationEvent.Failed(request.requestId, cancellation))
            return
        }

        try {
            val executionStartedAt = clock.nowNanos()
            val resolved = generationPlanning.resolveConfiguration(request, session.resolved)
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
            generationPlanning.validateOutputConstraint(
                outputConstraint = request.outputConstraint,
                resolved = resolved,
                resolvedUseCase = session.resolved,
                capabilities = capabilities,
            )
            val contextPlan = generationPlanning.planContextSize(
                resolvedUseCase = session.resolved,
                options = session.options,
                promptTokenCount = promptPlan.tokenCount,
                maxOutputTokens = resolved.maxOutputTokens,
                capabilities = capabilities,
                preference = resolved.contextPreference,
            )
            lifecycle.ensureNotCancelled()
            val contextResult = materializeContext(session, contextPlan)
            if (lifecycle.isCancellationRequested()) {
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
            val firstAnswerAt = AtomicLong(0)
            val rawOutput = StringBuilder()
            val reasoningOutput = StringBuilder()
            val answerOutput = StringBuilder()
            val streamProtocol = (resolved.preset?.generation ?: session.resolved.useCase.generationDefaults).reasoningStreamProtocol
            val reasoningControl = generationPlanning.resolveReasoningControl(
                thinkingMode = resolved.thinkingMode,
                guardPolicy = resolved.guardPolicy,
                streamProtocol = streamProtocol,
                maxOutputTokens = resolved.maxOutputTokens,
                capabilities = capabilities,
            )
            val streamParser = ReasoningStreamParser(resolved.thinkingMode, streamProtocol)
            var lastGeneratedTokens = 0
            state.set(RuntimeState.GENERATING)
            runtimeTelemetry.started(request.requestId)
            lifecycle.emit(GenerationEvent.Started(request.requestId, session.model.digest))
            val generationGuard = GenerationGuard(
                thinkingMode = resolved.thinkingMode,
                policy = resolved.guardPolicy,
                thinkingCloseMarker = streamProtocol.closeMarker,
                enforceThinkingBudget = reasoningControl == null,
            )
            val guardStopReason = AtomicReference<StopReason?>(null)
            val emitParsedChunk: (ParsedGenerationChunk, Int) -> Unit = { parsed, generatedTokens ->
                when (parsed.contentType) {
                    GenerationContentType.REASONING -> reasoningOutput.append(parsed.text)

                    GenerationContentType.ANSWER -> {
                        answerOutput.append(parsed.text)
                        if (parsed.text.isNotBlank()) {
                            firstAnswerAt.compareAndSet(0, clock.nowNanos())
                        }
                    }
                }
                lifecycle.emit(
                    GenerationEvent.TextDelta(
                        requestId = request.requestId,
                        text = parsed.text,
                        generatedTokens = generatedTokens,
                        contentType = parsed.contentType,
                    ),
                )
            }
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
                reasoningControl = reasoningControl,
            )
            lifecycle.ensureNotCancelled()
            val outcome = backend.generate(contextResult.context, backendRequest) { text, generatedTokens ->
                if (lifecycle.isCancellationRequested()) {
                    false
                } else {
                    firstTokenAt.compareAndSet(0, clock.nowNanos())
                    lastGeneratedTokens = generatedTokens
                    rawOutput.append(text)
                    streamParser.accept(text).forEach { emitParsedChunk(it, generatedTokens) }
                    val guardReason = generationGuard.observe(text, generatedTokens)
                    if (guardReason != null) guardStopReason.compareAndSet(null, guardReason)
                    guardReason == null && !lifecycle.isCancellationRequested()
                }
            }
            if (!lifecycle.isCancellationRequested()) {
                streamParser.finish().forEach { emitParsedChunk(it, lastGeneratedTokens) }
            }

            val guardReason = guardStopReason.get()
            if (lifecycle.isCancellationRequested()) {
                val cancellation = LocalLlmError.Cancelled()
                runtimeTelemetry.failed(request.requestId, cancellation)
                lifecycle.finish(GenerationEvent.Failed(request.requestId, cancellation))
            } else if (guardReason != null) {
                completeGeneration(
                    request = request,
                    session = session,
                    lifecycle = lifecycle,
                    output = rawOutput.toString(),
                    reasoningOutput = reasoningOutput.toString(),
                    answerOutput = answerOutput.toString(),
                    backendMetrics = outcome.metrics().copy(stopReason = guardReason),
                    executionStartedAt = executionStartedAt,
                    enqueuedAt = enqueuedAt,
                    firstTokenAt = firstTokenAt,
                    firstAnswerAt = firstAnswerAt,
                    promptPlanningMs = promptPlanningMs,
                    contextCreationMs = contextResult.creationMs,
                )
            } else if (outcome is BackendGenerationOutcome.Cancelled) {
                val cancellation = LocalLlmError.Cancelled()
                runtimeTelemetry.failed(request.requestId, cancellation)
                lifecycle.finish(GenerationEvent.Failed(request.requestId, cancellation))
            } else {
                completeGeneration(
                    request = request,
                    session = session,
                    lifecycle = lifecycle,
                    output = rawOutput.toString(),
                    reasoningOutput = reasoningOutput.toString(),
                    answerOutput = answerOutput.toString(),
                    backendMetrics = (outcome as BackendGenerationOutcome.Completed).metrics,
                    executionStartedAt = executionStartedAt,
                    enqueuedAt = enqueuedAt,
                    firstTokenAt = firstTokenAt,
                    firstAnswerAt = firstAnswerAt,
                    promptPlanningMs = promptPlanningMs,
                    contextCreationMs = contextResult.creationMs,
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

    private fun materializeContext(session: SessionDescriptor, contextPlan: ContextPlanningResult): ContextMaterialization =
        synchronized(resourceLock) {
            val requestedContextSize = contextPlan.requestedContextTokens
            val current = session.context
            val capabilities = session.resolved.model.runtimeCapabilities
            val statelessReuse = session.resolved.useCase.cachePolicy.reuseStatelessContext &&
                capabilities.supportsStatelessContextReuse
            val liveReuseAllowed = session.options.kind == SessionKind.CONVERSATIONAL || statelessReuse
            if (current != null && current.contextSize >= requestedContextSize && liveReuseAllowed) {
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
            val admittedContextSize = resolveMemoryAwareContextSize(session, contextPlan)
            val startedAt = clock.nowNanos()
            val created = backend.createContext(
                model = session.model,
                profile = session.resolved.model,
                configuration = BackendContextConfiguration(admittedContextSize),
            )
            session.context = created
            ContextMaterialization(created, nanosToMillis(clock.nowNanos() - startedAt), created = true)
        }

    private fun resolveMemoryAwareContextSize(session: SessionDescriptor, contextPlan: ContextPlanningResult): Int {
        val planner = memoryAwareContextPlanner ?: return contextPlan.requestedContextTokens
        val schedulerSnapshot = scheduler.snapshot()
        val decision = planner.plan(
            MemoryAwareContextRequest(
                modelProfileId = session.resolved.model.id,
                requestedContextTokens = contextPlan.requestedContextTokens,
                minimumContextTokens = contextPlan.minimumRequiredTokens,
                approvedContextTiers = contextPlan.memoryEligibleContextTiers,
                residency = RuntimeResidencySnapshot(
                    modelLoaded = modelResidency.hasResidentModel(),
                    residentContexts = sessions.values.count { it.context != null },
                    activeGeneration = schedulerSnapshot.activeRequest != null,
                    queuedGenerations = schedulerSnapshot.queuedRequests,
                ),
            ),
        )
        return when (decision) {
            is MemoryAwareContextDecision.Allow -> decision.contextTokens

            is MemoryAwareContextDecision.Reject -> throw GenerationPlanningException(
                ConfigurationErrorCode.MEMORY_BUDGET_EXCEEDED,
                memoryAdmissionFailureMessage(contextPlan, decision),
            )
        }
    }

    private fun memoryAdmissionFailureMessage(contextPlan: ContextPlanningResult, decision: MemoryAwareContextDecision.Reject): String =
        buildString {
            append("Memory admission rejected context ")
            append(contextPlan.requestedContextTokens)
            append(" tokens: ")
            append(decision.reason.name)
            decision.admissionReason?.let { reason ->
                append(" (")
                append(reason.name)
                append(')')
            }
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

    private fun ensureModelLoaded(resolved: ResolvedUseCase): ResidentModel {
        check(!closed.get()) { "Runtime is closed" }
        validateRuntimeCapabilities(resolved)
        val requestedDigest = resolved.model.artifact.digest
        val current = modelResidency.reusableModelOrNull()
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

        if (current != null) {
            check(unloadResidentModel()) { "Unable to unload current model before switching" }
        }
        admitModelLoad(resolved, schedulerSnapshot)

        if (!backendInitialized) {
            backend.initialize()
            backendInitialized = true
        }

        modelResidency.beginLoad(resolved.model.id, requestedDigest)
        return try {
            val loaded = ResidentModel(
                profileId = resolved.model.id,
                handle = backend.loadModel(stored, resolved.model),
            )
            modelResidency.loadSucceeded(loaded)
            loaded
        } catch (error: Throwable) {
            modelResidency.loadFailed()
            throw error
        }
    }

    private fun unloadResidentModel(): Boolean {
        val resident = modelResidency.beginUnload() ?: return false
        return try {
            backend.unloadModel(resident.handle)
            modelResidency.unloadSucceeded()
            true
        } catch (error: Throwable) {
            modelResidency.unloadFailed()
            throw error
        }
    }

    private fun admitModelLoad(resolved: ResolvedUseCase, schedulerSnapshot: DecodeSchedulerSnapshot) {
        val planner = memoryAwareModelLoadPlanner ?: return
        val decision = planner.plan(
            MemoryAwareModelLoadRequest(
                modelProfileId = resolved.model.id,
                residency = RuntimeResidencySnapshot(
                    modelLoaded = false,
                    residentContexts = 0,
                    activeGeneration = schedulerSnapshot.activeRequest != null,
                    queuedGenerations = schedulerSnapshot.queuedRequests,
                ),
            ),
        )
        if (decision is MemoryAwareModelLoadDecision.Reject) {
            throw ModelLoadMemoryAdmissionException(resolved.model.id, decision)
        }
    }

    private fun validateRuntimeCapabilities(resolved: ResolvedUseCase) {
        val capabilities = resolved.model.runtimeCapabilities
        capabilities.requiredBackendId?.let { required ->
            check(backend.id == required) { "Runtime profile requires backend $required" }
        }
        capabilities.requiredBackendRevision?.let { required ->
            check(backend.revision == required) { "Runtime profile requires backend revision $required" }
        }
        check(!resolved.useCase.cachePolicy.enablePrefixSnapshot || capabilities.supportsPrefixSnapshot) {
            "Runtime profile does not allow prefix snapshots"
        }
        check(!resolved.useCase.cachePolicy.reuseStatelessContext || capabilities.supportsStatelessContextReuse) {
            "Runtime profile does not allow stateless context reuse"
        }
    }

    private fun BackendGenerationOutcome.metrics(): BackendGenerationMetrics = when (this) {
        is BackendGenerationOutcome.Completed -> metrics
        is BackendGenerationOutcome.Cancelled -> metrics
    }

    private fun completeGeneration(
        request: GenerationRequest,
        session: SessionDescriptor,
        lifecycle: RequestLifecycle,
        output: String,
        reasoningOutput: String,
        answerOutput: String,
        backendMetrics: BackendGenerationMetrics,
        executionStartedAt: Long,
        enqueuedAt: Long,
        firstTokenAt: AtomicLong,
        firstAnswerAt: AtomicLong,
        promptPlanningMs: Long,
        contextCreationMs: Long?,
    ) {
        val publicMetrics = backendMetrics.toPublicMetrics(
            queueMs = nanosToMillis(executionStartedAt - enqueuedAt),
            modelLoadMs = session.modelLoadDurationMs,
            modelLoadKind = session.modelLoadKind,
            timeToFirstTokenMs = firstTokenAt.get().takeIf { it != 0L }
                ?.let { nanosToMillis(it - executionStartedAt) },
            timeToFirstAnswerMs = firstAnswerAt.get().takeIf { it != 0L }
                ?.let { nanosToMillis(it - executionStartedAt) },
            totalMs = nanosToMillis(clock.nowNanos() - enqueuedAt),
            promptPlanningMs = promptPlanningMs,
            contextCreationMs = contextCreationMs,
        )
        runtimeTelemetry.completed(request.requestId, publicMetrics)
        lifecycle.finish(
            GenerationEvent.Completed(
                requestId = request.requestId,
                output = output,
                metrics = publicMetrics,
                reasoningOutput = reasoningOutput,
                answerOutput = answerOutput,
            ),
        )
    }

    private fun releaseSessionRequest(session: SessionDescriptor) {
        if (session.lifecycle.releaseRequest()) {
            releaseSession(session)
        }
    }

    private fun releaseSession(session: SessionDescriptor) {
        if (!session.lifecycle.tryBeginRelease()) {
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
            session.lifecycle.releaseFailed()
            state.set(RuntimeState.DEGRADED)
            release.getOrThrow()
        }
        session.lifecycle.releaseSucceeded()
        attemptDeferredModelUnload(ignoreActiveGeneration = false)
    }

    private fun releaseForCriticalMemory(action: RuntimeMemoryAction): RuntimeMemoryResult {
        deferredModelUnload.set(true)
        sessions.values.forEach { session -> session.lifecycle.beginClose() }
        val cancelled = scheduler.cancelAll()
        sessions.values.forEach { session ->
            if (session.lifecycle.isReleaseReady()) {
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
        if (!deferredModelUnload.get() && !closed.get()) return false
        return synchronized(resourceLock) {
            val schedulerSnapshot = scheduler.snapshot()
            val activeBlocksUnload = !ignoreActiveGeneration && schedulerSnapshot.activeRequest != null
            if (sessions.isNotEmpty() || schedulerSnapshot.queuedRequests != 0 || activeBlocksUnload) {
                return@synchronized false
            }

            unloadResidentModel()
            if (closed.get() && backendInitialized) {
                backend.shutdown()
                backendInitialized = false
            }
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
        timeToFirstAnswerMs: Long?,
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
        timeToFirstAnswerMs = timeToFirstAnswerMs,
        reasoningTokens = reasoningTokens,
        answerTokens = answerTokens,
    )

    private fun nanosToMillis(nanos: Long): Long = nanos / 1_000_000

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
        val lifecycle: SessionLifecycle = SessionLifecycle(),
    )

    private data class ContextMaterialization(val context: BackendContextHandle, val creationMs: Long?, val created: Boolean)
}

private class GenerationCancelledException : RuntimeException()

private class RequestLifecycle(val requestId: RequestId, private val listener: GenerationListener, private val onTerminal: () -> Unit) {
    private val generationLifecycle = GenerationLifecycle()

    fun requestCancellation(): Boolean = generationLifecycle.requestCancellation()

    fun isCancellationRequested(): Boolean = generationLifecycle.isCancellationRequested()

    fun emit(event: GenerationEvent) {
        if (!generationLifecycle.isTerminal()) {
            runCatching { listener.onEvent(event) }
        }
    }

    fun ensureNotCancelled() {
        if (generationLifecycle.isCancellationRequested()) throw GenerationCancelledException()
    }

    fun finish(event: GenerationEvent) {
        if (!generationLifecycle.tryFinish()) {
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
        if (lifecycle.requestCancellation()) {
            schedulerHandle.cancel()
        }
    }
}

private class NoOpGenerationHandle(override val requestId: RequestId) : GenerationHandle {
    override fun cancel() = Unit
}