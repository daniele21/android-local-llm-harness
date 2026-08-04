package io.github.daniele21.localllm.phonetest

import android.content.Context
import io.github.daniele21.localllm.contracts.GenerationEvent
import io.github.daniele21.localllm.contracts.GenerationHandle
import io.github.daniele21.localllm.contracts.GenerationListener
import io.github.daniele21.localllm.contracts.GenerationOverrides
import io.github.daniele21.localllm.contracts.GenerationRequest
import io.github.daniele21.localllm.contracts.LocalLlmError
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.runtime.LlamaCppInferenceBackend
import io.github.daniele21.localllm.runtime.RuntimeOrchestrator
import io.github.daniele21.localllm.store.FileSystemModelStore
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

@Suppress("TooManyFunctions", "ReturnCount", "CyclomaticComplexMethod", "NestedBlockDepth")
internal class PhonePlaygroundController(context: Context, private val listener: (PlaygroundState) -> Unit) : AutoCloseable {
    private val appContext = context.applicationContext
    private val modelStore = FileSystemModelStore(File(appContext.noBackupFilesDir, MODEL_STORE_DIRECTORY))
    private val executor = Executors.newSingleThreadExecutor()
    private val lock = Any()

    private var state = PlaygroundState()
    private var harness: PhoneHarness? = null
    private var harnessModelDigest: ModelDigest? = null
    private var activeSession: SessionId? = null
    private var activeRequestId: RequestId? = null
    private var activeHandle: GenerationHandle? = null

    val active: Boolean
        get() = synchronized(lock) { state.active || activeSession != null }

    fun snapshot(): PlaygroundState = synchronized(lock) { state }

    fun start(model: ImportedPhoneModel, prompt: String, options: PlaygroundRequestOptions): Boolean {
        val normalizedPrompt = prompt.trim()
        require(normalizedPrompt.isNotBlank()) { "Prompt must not be blank" }
        require(normalizedPrompt.length <= MAX_PROMPT_CHARACTERS) {
            "Prompt exceeds $MAX_PROMPT_CHARACTERS characters"
        }
        val requestId = synchronized(lock) {
            if (state.active || activeSession != null) return false
            RequestId(UUID.randomUUID().toString()).also {
                activeRequestId = it
                state = PlaygroundState(
                    phase = PlaygroundPhase.PREPARING,
                    detail = "Verifying model and preparing runtime",
                )
            }
        }
        publish(snapshot())
        executor.execute {
            startOnWorker(
                model = model,
                requestId = requestId,
                prompt = normalizedPrompt,
                options = options,
            )
        }
        return true
    }

    fun cancel(): Boolean {
        val handle = synchronized(lock) { activeHandle } ?: return false
        return runCatching {
            handle.cancel()
            val current = synchronized(lock) {
                if (state.active) {
                    state = state.copy(
                        cancellationAvailable = false,
                        cancellationRequested = true,
                        detail = "Cancellation requested",
                    )
                }
                state
            }
            publish(current)
            true
        }.getOrDefault(false)
    }

    fun releaseRuntime(onComplete: () -> Unit): Boolean {
        val resources = synchronized(lock) {
            if (state.active || activeSession != null) return false
            val result = RuntimeResources(harness?.runtime, activeSession, activeHandle)
            harness = null
            harnessModelDigest = null
            activeSession = null
            activeRequestId = null
            activeHandle = null
            state = PlaygroundState(
                phase = PlaygroundPhase.PREPARING,
                detail = "Releasing playground runtime",
            )
            result
        }
        publish(snapshot())
        executor.execute {
            closeResources(resources)
            val current = synchronized(lock) {
                state = PlaygroundState()
                state
            }
            publish(current)
            onComplete()
        }
        return true
    }

    override fun close() {
        val resources = synchronized(lock) {
            val result = RuntimeResources(harness?.runtime, activeSession, activeHandle)
            harness = null
            harnessModelDigest = null
            activeSession = null
            activeRequestId = null
            activeHandle = null
            state = PlaygroundState()
            result
        }
        closeResources(resources)
        executor.shutdownNow()
    }

    private fun startOnWorker(model: ImportedPhoneModel, requestId: RequestId, prompt: String, options: PlaygroundRequestOptions) {
        try {
            val verification = modelStore.verify(model.digest)
            check(verification.valid) { "Model integrity verification failed" }
            val currentHarness = ensureHarness(model)
            val prepared = currentHarness.runtime.prepare(
                currentHarness.applicationId,
                currentHarness.useCaseId,
            )
            check(prepared.ready) { "Model preparation failed" }
            val session = currentHarness.runtime.createSession(
                currentHarness.applicationId,
                currentHarness.useCaseId,
            )
            registerSession(requestId, session)
            val generationRequest = GenerationRequest(
                requestId = requestId,
                sessionId = session,
                applicationId = currentHarness.applicationId,
                useCaseId = currentHarness.useCaseId,
                input = prompt,
                overrides = GenerationOverrides(
                    maxOutputTokens = options.maxOutputTokens,
                    temperature = options.temperature,
                    seed = options.seed,
                ),
            )
            val handle = currentHarness.runtime.generate(
                generationRequest,
                GenerationListener(::onGenerationEvent),
            )
            registerHandle(requestId, handle)
        } catch (_: Throwable) {
            failStart(requestId)
        }
    }

    private fun ensureHarness(model: ImportedPhoneModel): PhoneHarness {
        synchronized(lock) {
            val current = harness
            if (current != null && harnessModelDigest == model.digest) return current
        }
        val previous = synchronized(lock) {
            val current = harness?.runtime
            harness = null
            harnessModelDigest = null
            current
        }
        runCatching { previous?.close() }
        val resolved = resolvedPhonePlaygroundUseCase(model)
        val nativeLibraryDirectory = File(appContext.applicationInfo.nativeLibraryDir)
        require(nativeLibraryDirectory.isDirectory) {
            "Native library directory is unavailable"
        }
        val created = PhoneHarness(
            runtime = RuntimeOrchestrator(
                registry = SinglePhoneBindingRegistry(resolved),
                modelStore = modelStore,
                backend = LlamaCppInferenceBackend(nativeLibraryDirectory),
            ),
            applicationId = resolved.binding.applicationId,
            useCaseId = resolved.binding.useCaseId,
        )
        synchronized(lock) {
            harness = created
            harnessModelDigest = model.digest
        }
        return created
    }

    private fun registerSession(requestId: RequestId, session: SessionId) {
        val current = synchronized(lock) {
            check(activeRequestId == requestId) { "Playground request changed while preparing" }
            activeSession = session
            state = state.copy(detail = "Inference session created")
            state
        }
        publish(current)
    }

    private fun registerHandle(requestId: RequestId, handle: GenerationHandle) {
        val current = synchronized(lock) {
            if (activeRequestId == requestId && state.active) {
                activeHandle = handle
                state = state.copy(cancellationAvailable = true)
            }
            state
        }
        publish(current)
    }

    private fun onGenerationEvent(event: GenerationEvent) {
        val terminal = event is GenerationEvent.Completed || event is GenerationEvent.Failed
        val current = synchronized(lock) {
            if (event.requestId != activeRequestId) return
            state = when (event) {
                is GenerationEvent.Queued -> state.copy(
                    phase = PlaygroundPhase.QUEUED,
                    detail = "Generation queued",
                )

                is GenerationEvent.Started -> state.copy(
                    phase = PlaygroundPhase.GENERATING,
                    detail = "Generating locally",
                )

                is GenerationEvent.TextDelta -> appendOutput(event.text, event.generatedTokens)

                is GenerationEvent.Completed -> completedState(event)

                is GenerationEvent.Failed -> failedState(event.error)
            }
            if (terminal) activeHandle = null
            state
        }
        publish(current)
        if (terminal) {
            runCatching { executor.execute(::cleanupAfterTerminal) }
        }
    }

    private fun appendOutput(text: String, generatedTokens: Int): PlaygroundState {
        val remaining = (MAX_OUTPUT_CHARACTERS - state.output.length).coerceAtLeast(0)
        val appended = text.take(remaining)
        return state.copy(
            phase = PlaygroundPhase.GENERATING,
            output = state.output + appended,
            outputTruncated = state.outputTruncated || appended.length < text.length,
            generatedTokens = generatedTokens,
            detail = "Generating locally",
        )
    }

    private fun completedState(event: GenerationEvent.Completed): PlaygroundState = state.copy(
        phase = PlaygroundPhase.COMPLETED,
        output = event.output.take(MAX_OUTPUT_CHARACTERS),
        outputTruncated = event.output.length > MAX_OUTPUT_CHARACTERS,
        generatedTokens = event.metrics.outputTokens,
        cancellationAvailable = false,
        metrics = PlaygroundMetrics.from(event.metrics),
        errorCode = null,
        detail = "Generation completed",
    )

    private fun failedState(error: LocalLlmError): PlaygroundState = state.copy(
        phase = if (error is LocalLlmError.Cancelled) PlaygroundPhase.CANCELLED else PlaygroundPhase.FAILED,
        cancellationAvailable = false,
        errorCode = error.code,
        detail = if (error is LocalLlmError.Cancelled) "Generation cancelled" else "Generation failed",
    )

    private fun cleanupAfterTerminal() {
        val session = synchronized(lock) { activeSession }
        val runtime = synchronized(lock) { harness?.runtime }
        val cleanupFailed = session != null && runCatching { runtime?.closeSession(session) }.isFailure
        val current = synchronized(lock) {
            if (cleanupFailed) {
                state = state.copy(
                    phase = PlaygroundPhase.FAILED,
                    errorCode = "SESSION_CLEANUP_FAILED",
                    detail = "Inference session cleanup failed",
                )
            } else {
                activeSession = null
                activeRequestId = null
            }
            activeHandle = null
            state
        }
        publish(current)
    }

    private fun failStart(requestId: RequestId) {
        val session = synchronized(lock) {
            if (activeRequestId != requestId) return
            activeSession
        }
        val runtime = synchronized(lock) { harness?.runtime }
        val cleanupFailed = session != null && runCatching { runtime?.closeSession(session) }.isFailure
        val current = synchronized(lock) {
            if (activeRequestId != requestId) return
            activeSession = if (cleanupFailed) session else null
            activeRequestId = null
            activeHandle = null
            state = state.copy(
                phase = PlaygroundPhase.FAILED,
                cancellationAvailable = false,
                errorCode = if (cleanupFailed) "SESSION_CLEANUP_FAILED" else "INFERENCE_START_FAILED",
                detail = if (cleanupFailed) {
                    "Inference session cleanup failed"
                } else {
                    "Local inference could not be started"
                },
            )
            state
        }
        publish(current)
    }

    private fun closeResources(resources: RuntimeResources) {
        runCatching { resources.handle?.cancel() }
        runCatching { resources.session?.let { resources.runtime?.closeSession(it) } }
        runCatching { resources.runtime?.close() }
    }

    private fun publish(current: PlaygroundState) {
        listener(current)
    }

    private data class RuntimeResources(val runtime: RuntimeOrchestrator?, val session: SessionId?, val handle: GenerationHandle?)

    private companion object {
        const val MODEL_STORE_DIRECTORY = "local-llm-phone-test"
        const val MAX_PROMPT_CHARACTERS = 32_768
        const val MAX_OUTPUT_CHARACTERS = 131_072
    }
}
