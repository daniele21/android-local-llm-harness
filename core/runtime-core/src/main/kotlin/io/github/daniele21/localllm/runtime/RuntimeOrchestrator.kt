package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.GenerationEvent
import io.github.daniele21.localllm.contracts.GenerationHandle
import io.github.daniele21.localllm.contracts.GenerationListener
import io.github.daniele21.localllm.contracts.GenerationMetrics
import io.github.daniele21.localllm.contracts.GenerationRequest
import io.github.daniele21.localllm.contracts.LocalLlmClient
import io.github.daniele21.localllm.contracts.LocalLlmError
import io.github.daniele21.localllm.contracts.PrepareResult
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.RuntimeSnapshot
import io.github.daniele21.localllm.contracts.RuntimeState
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.models.ModelProfileRegistry
import io.github.daniele21.localllm.models.ResolvedUseCase
import io.github.daniele21.localllm.store.ModelStore
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

fun interface MonotonicClock {
    fun nowNanos(): Long
}

class RuntimeOrchestrator(
    private val registry: ModelProfileRegistry,
    private val modelStore: ModelStore,
    private val backend: InferenceBackend,
    private val scheduler: SingleDecodeScheduler = SingleDecodeScheduler(),
    private val integrityCache: ModelIntegrityCache = ModelIntegrityCache(),
    private val clock: MonotonicClock = MonotonicClock(System::nanoTime),
    private val priorityResolver: (GenerationRequest) -> DecodePriority = { DecodePriority.USER_INTERACTIVE },
    private val memoryPolicy: RuntimeMemoryPolicy = RuntimeMemoryPolicy(),
) : LocalLlmClient, AutoCloseable {
    private val resourceLock = Any()
    private val state = AtomicReference(RuntimeState.IDLE)
    private val sessions = ConcurrentHashMap<SessionId, SessionDescriptor>()
    private val closed = AtomicBoolean(false)
    private val deferredModelUnload = AtomicBoolean(false)

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

    override fun createSession(applicationId: ApplicationId, useCaseId: UseCaseId): SessionId {
        check(!closed.get()) { "Runtime is closed" }
        val resolved = registry.resolve(applicationId, useCaseId)
        return synchronized(resourceLock) {
            val model = ensureModelLoaded(resolved)
            val context = backend.createContext(model.handle, resolved.model)
            val sessionId = SessionId(UUID.randomUUID().toString())
            sessions[sessionId] = SessionDescriptor(
                id = sessionId,
                applicationId = applicationId,
                useCaseId = useCaseId,
                resolved = resolved,
                context = context,
                modelLoadDurationMs = model.handle.loadDurationMs,
            )
            state.set(RuntimeState.READY)
            sessionId
        }
    }

    override fun generate(
        request: GenerationRequest,
        listener: GenerationListener,
    ): GenerationHandle {
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

        val submission = try {
            scheduler.submit(
                requestId = request.requestId,
                priority = priorityResolver(request),
                task = { executeGeneration(request, session, lifecycle, enqueuedAt) },
                onQueuedCancellation = {
                    lifecycle.cancelRequested.set(true)
                    lifecycle.finish(
                        GenerationEvent.Failed(
                            request.requestId,
                            LocalLlmError.Cancelled("Generation cancelled before execution"),
                        ),
                    )
                },
                onRunningCancellation = {
                    lifecycle.cancelRequested.set(true)
                    runCatching { backend.cancel(request.requestId.value) }
                },
                onQueued = { position ->
                    lifecycle.emit(GenerationEvent.Queued(request.requestId, position))
                },
            )
        } catch (error: Throwable) {
            lifecycle.finish(
                GenerationEvent.Failed(
                    request.requestId,
                    LocalLlmError.Configuration(error.message ?: "Unable to schedule generation"),
                ),
            )
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

    private fun executeGeneration(
        request: GenerationRequest,
        session: SessionDescriptor,
        lifecycle: RequestLifecycle,
        enqueuedAt: Long,
    ) {
        if (lifecycle.cancelRequested.get()) {
            lifecycle.finish(GenerationEvent.Failed(request.requestId, LocalLlmError.Cancelled()))
            return
        }

        val startedAt = clock.nowNanos()
        val firstTokenAt = AtomicLong(0)
        val output = StringBuilder()
        state.set(RuntimeState.GENERATING)
        lifecycle.emit(GenerationEvent.Started(request.requestId, session.context.model.digest))

        try {
            val defaults = session.resolved.useCase.generationDefaults
            val backendRequest = BackendGenerationRequest(
                requestId = request.requestId.value,
                prompt = request.input,
                maxOutputTokens = request.overrides.maxOutputTokens ?: defaults.maxOutputTokens,
                temperature = request.overrides.temperature ?: defaults.temperature,
                topP = defaults.topP,
                topK = defaults.topK,
                seed = request.overrides.seed ?: defaults.seed ?: 0L,
            )
            val outcome = backend.generate(session.context, backendRequest) { text, generatedTokens ->
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
                lifecycle.finish(GenerationEvent.Failed(request.requestId, LocalLlmError.Cancelled()))
            } else {
                val metrics = (outcome as BackendGenerationOutcome.Completed).metrics
                lifecycle.finish(
                    GenerationEvent.Completed(
                        requestId = request.requestId,
                        output = output.toString(),
                        metrics = metrics.toPublicMetrics(
                            queueMs = nanosToMillis(startedAt - enqueuedAt),
                            modelLoadMs = session.modelLoadDurationMs,
                            timeToFirstTokenMs = firstTokenAt.get().takeIf { it != 0L }
                                ?.let { nanosToMillis(it - startedAt) },
                            totalMs = nanosToMillis(clock.nowNanos() - enqueuedAt),
                        ),
                    ),
                )
            }
        } catch (error: BackendException) {
            lifecycle.finish(
                GenerationEvent.Failed(
                    request.requestId,
                    LocalLlmError.NativeRuntime("${error.code}: ${error.message}"),
                ),
            )
        } catch (error: Throwable) {
            lifecycle.finish(
                GenerationEvent.Failed(
                    request.requestId,
                    LocalLlmError.NativeRuntime(error.message ?: "Unexpected local inference failure"),
                ),
            )
        } finally {
            val unloaded = attemptDeferredModelUnload(ignoreActiveGeneration = true)
            if (!closed.get() && !unloaded) {
                state.set(if (deferredModelUnload.get()) RuntimeState.DEGRADED else RuntimeState.READY)
            }
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
                backend.releaseContext(session.context)
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

    private fun failImmediately(
        requestId: RequestId,
        listener: GenerationListener,
        error: LocalLlmError,
    ): GenerationHandle {
        runCatching { listener.onEvent(GenerationEvent.Failed(requestId, error)) }
        return NoOpGenerationHandle(requestId)
    }

    private fun BackendGenerationMetrics.toPublicMetrics(
        queueMs: Long,
        modelLoadMs: Long,
        timeToFirstTokenMs: Long?,
        totalMs: Long,
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
    )

    private fun nanosToMillis(nanos: Long): Long = nanos / 1_000_000

    private data class LoadedModelDescriptor(
        val profileId: String,
        val handle: BackendModelHandle,
    )

    private data class SessionDescriptor(
        val id: SessionId,
        val applicationId: ApplicationId,
        val useCaseId: UseCaseId,
        val resolved: ResolvedUseCase,
        val context: BackendContextHandle,
        val modelLoadDurationMs: Long,
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
}

private class RequestLifecycle(
    val requestId: RequestId,
    private val listener: GenerationListener,
    private val onTerminal: () -> Unit,
) {
    val cancelRequested = AtomicBoolean(false)
    private val terminal = AtomicBoolean(false)

    fun emit(event: GenerationEvent) {
        if (!terminal.get()) {
            runCatching { listener.onEvent(event) }
        }
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

private class NoOpGenerationHandle(
    override val requestId: RequestId,
) : GenerationHandle {
    override fun cancel() = Unit
}
