package io.github.daniele21.localllm.console

import io.github.daniele21.localllm.contracts.GenerationEvent
import io.github.daniele21.localllm.contracts.GenerationHandle
import io.github.daniele21.localllm.contracts.GenerationListener
import io.github.daniele21.localllm.contracts.GenerationMetrics
import io.github.daniele21.localllm.contracts.GenerationOverrides
import io.github.daniele21.localllm.contracts.GenerationRequest
import io.github.daniele21.localllm.contracts.LocalLlmClient
import io.github.daniele21.localllm.contracts.LocalLlmError
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.SessionId
import java.util.UUID

@Suppress("TooManyFunctions", "ReturnCount")
class LocalLlmConsoleInferenceControl(
    private val client: LocalLlmClient,
    targets: List<ConsoleInferenceTarget>,
    private val source: String = "In process",
    private val requestIdFactory: ConsoleInferenceRequestIdFactory = ConsoleInferenceRequestIdFactory {
        RequestId(UUID.randomUUID().toString())
    },
) : ConsoleInferenceControl {
    private val lock = Any()
    private val targetsById = targets.associateBy(ConsoleInferenceTarget::id)
    private val orderedTargets = targets.sortedBy(ConsoleInferenceTarget::id)

    private var state = idleState()
    private var activeSession: SessionId? = null
    private var activeRequestId: RequestId? = null
    private var activeHandle: GenerationHandle? = null
    private var activeListener: ConsoleInferenceListener? = null

    init {
        require(targetsById.size == targets.size) { "Inference target IDs must be unique" }
    }

    override fun snapshot(): ConsoleInferenceState = synchronized(lock) { state }

    override fun start(request: ConsoleInferenceRequest, listener: ConsoleInferenceListener): ConsoleInferenceOperationOutcome {
        val target = targetsById[request.targetId]
            ?: return failedOperation("Unknown inference target")
        val requestId = beginPreparation(target, listener)
            ?: return failedOperation("Generation is already active")

        val prepared = runCatching { client.prepare(target.applicationId, target.useCaseId) }
            .getOrElse { return terminalFailure(PREPARATION_ERROR) }
        if (!prepared.ready) return terminalFailure(PREPARATION_ERROR)

        val sessionId = runCatching { client.createSession(target.applicationId, target.useCaseId) }
            .getOrElse { return terminalFailure(SESSION_CREATION_ERROR) }
        registerSession(sessionId)

        val generationRequest = GenerationRequest(
            requestId = requestId,
            sessionId = sessionId,
            applicationId = target.applicationId,
            useCaseId = target.useCaseId,
            input = request.prompt,
            overrides = GenerationOverrides(
                maxOutputTokens = request.maxOutputTokens,
                temperature = request.temperature,
                seed = request.seed,
            ),
        )
        val handle = runCatching {
            client.generate(generationRequest, GenerationListener(::onGenerationEvent))
        }.getOrElse {
            return terminalFailure(GENERATION_START_ERROR, cleanupSession = true)
        }
        val current = registerHandle(requestId, handle)
        publish(current)
        return ConsoleInferenceOperationOutcome(success = true, state = current)
    }

    override fun cancel(): ConsoleInferenceOperationOutcome {
        val handle = synchronized(lock) { activeHandle }
            ?: return failedOperation("No cancellable generation is active")
        return runCatching {
            handle.cancel()
            val current = synchronized(lock) {
                if (state.executionActive) {
                    state = state.copy(
                        cancellationRequested = true,
                        cancellationAvailable = false,
                        detail = "Cancellation requested",
                    )
                }
                state
            }
            publish(current)
            ConsoleInferenceOperationOutcome(success = true, state = current)
        }.getOrElse {
            val current = synchronized(lock) {
                state = state.copy(sourceError = CANCELLATION_ERROR)
                state
            }
            publish(current)
            ConsoleInferenceOperationOutcome(false, current, CANCELLATION_ERROR)
        }
    }

    override fun clear(): ConsoleInferenceOperationOutcome {
        val current = synchronized(lock) {
            if (state.executionActive || activeSession != null) {
                return failedOperation("Active generation cannot be cleared")
            }
            activeRequestId = null
            activeHandle = null
            activeListener = null
            state = idleState()
            state
        }
        return ConsoleInferenceOperationOutcome(success = true, state = current)
    }

    override fun close() {
        val resources = synchronized(lock) {
            val result = ActiveResources(activeHandle, activeSession)
            activeHandle = null
            activeSession = null
            activeRequestId = null
            activeListener = null
            state = idleState()
            result
        }
        runCatching { resources.handle?.cancel() }
        runCatching { resources.session?.let(client::closeSession) }
    }

    private fun beginPreparation(target: ConsoleInferenceTarget, listener: ConsoleInferenceListener): RequestId? {
        val result = synchronized(lock) {
            if (state.executionActive || activeSession != null) return null
            val requestId = requestIdFactory.create()
            activeListener = listener
            activeRequestId = requestId
            state = state.copy(
                phase = ConsoleInferencePhase.PREPARING,
                activeTargetId = target.id,
                output = "",
                outputTruncated = false,
                generatedTokens = null,
                sessionActive = false,
                cancellationAvailable = false,
                cancellationRequested = false,
                metrics = null,
                errorCode = null,
                detail = "Preparing connected runtime",
                sourceError = null,
            )
            requestId to state
        }
        listener.onStateChanged(result.second)
        return result.first
    }

    private fun registerSession(sessionId: SessionId) {
        val current = synchronized(lock) {
            activeSession = sessionId
            state = state.copy(sessionActive = true, detail = "Inference session created")
            state
        }
        publish(current)
    }

    private fun registerHandle(requestId: RequestId, handle: GenerationHandle): ConsoleInferenceState = synchronized(lock) {
        if (activeRequestId == requestId && state.executionActive) {
            activeHandle = handle
            state = state.copy(cancellationAvailable = true)
        }
        state
    }

    private fun onGenerationEvent(event: GenerationEvent) {
        val terminal = event is GenerationEvent.Completed || event is GenerationEvent.Failed
        val current = synchronized(lock) {
            if (event.requestId != activeRequestId) return
            state = when (event) {
                is GenerationEvent.Queued -> state.copy(
                    phase = ConsoleInferencePhase.QUEUED,
                    detail = "Generation queued",
                )

                is GenerationEvent.Started -> state.copy(
                    phase = ConsoleInferencePhase.GENERATING,
                    detail = "Generation running",
                )

                is GenerationEvent.Prepared -> state.copy(
                    detail = "Prompt planned: ${event.configuration.promptTokenCount}/${event.configuration.contextSize} tokens",
                )

                is GenerationEvent.TextDelta -> appendOutput(event.text, event.generatedTokens)

                is GenerationEvent.Completed -> completedState(event.output, event.metrics)

                is GenerationEvent.Failed -> failedState(event.error)
            }
            if (terminal) activeHandle = null
            state
        }
        publish(current)
        if (terminal) cleanupSessionAfterTerminal()
    }

    private fun appendOutput(text: String, generatedTokens: Int): ConsoleInferenceState {
        val remaining = (MAX_OUTPUT_CHARACTERS - state.output.length).coerceAtLeast(0)
        val appended = text.take(remaining)
        return state.copy(
            phase = ConsoleInferencePhase.GENERATING,
            output = state.output + appended,
            outputTruncated = state.outputTruncated || appended.length < text.length,
            generatedTokens = generatedTokens,
            detail = "Generation running",
        )
    }

    private fun completedState(output: String, metrics: GenerationMetrics): ConsoleInferenceState = state.copy(
        phase = ConsoleInferencePhase.COMPLETED,
        output = output.take(MAX_OUTPUT_CHARACTERS),
        outputTruncated = output.length > MAX_OUTPUT_CHARACTERS,
        generatedTokens = metrics.outputTokens,
        cancellationAvailable = false,
        metrics = metrics.toConsoleMetrics(),
        errorCode = null,
        detail = "Generation completed",
        sourceError = null,
    )

    private fun failedState(error: LocalLlmError): ConsoleInferenceState = state.copy(
        phase = if (error is LocalLlmError.Cancelled) {
            ConsoleInferencePhase.CANCELLED
        } else {
            ConsoleInferencePhase.FAILED
        },
        cancellationAvailable = false,
        errorCode = error.code,
        detail = if (error is LocalLlmError.Cancelled) {
            "Generation cancelled"
        } else {
            "Generation failed"
        },
    )

    private fun cleanupSessionAfterTerminal() {
        val session = synchronized(lock) { activeSession } ?: return clearTerminalListener()
        val failure = runCatching { client.closeSession(session) }.exceptionOrNull()
        val result = synchronized(lock) {
            if (failure == null && activeSession == session) {
                activeSession = null
                activeRequestId = null
                state = state.copy(sessionActive = false)
            } else if (failure != null) {
                state = state.copy(
                    phase = ConsoleInferencePhase.FAILED,
                    sessionActive = true,
                    cancellationAvailable = false,
                    errorCode = "SESSION_CLEANUP_FAILED",
                    detail = SESSION_CLEANUP_ERROR,
                    sourceError = SESSION_CLEANUP_ERROR,
                )
            }
            val listener = activeListener
            activeListener = null
            listener to state
        }
        result.first?.onStateChanged(result.second)
    }

    private fun terminalFailure(detail: String, cleanupSession: Boolean = false): ConsoleInferenceOperationOutcome {
        val cleanupFailed = cleanupSession && closeSessionForStartFailure()
        val finalDetail = if (cleanupFailed) SESSION_CLEANUP_ERROR else detail
        val result = synchronized(lock) {
            val listener = activeListener
            activeHandle = null
            activeRequestId = null
            activeListener = null
            state = state.copy(
                phase = ConsoleInferencePhase.FAILED,
                sessionActive = cleanupFailed,
                cancellationAvailable = false,
                errorCode = if (cleanupFailed) "SESSION_CLEANUP_FAILED" else "INFERENCE_START_FAILED",
                detail = finalDetail,
                sourceError = finalDetail,
            )
            listener to state
        }
        result.first?.onStateChanged(result.second)
        return ConsoleInferenceOperationOutcome(false, result.second, finalDetail)
    }

    private fun closeSessionForStartFailure(): Boolean {
        val session = synchronized(lock) { activeSession } ?: return false
        val failed = runCatching { client.closeSession(session) }.isFailure
        if (!failed) {
            synchronized(lock) {
                if (activeSession == session) {
                    activeSession = null
                    state = state.copy(sessionActive = false)
                }
            }
        }
        return failed
    }

    private fun clearTerminalListener() {
        synchronized(lock) { activeListener = null }
    }

    private fun failedOperation(detail: String): ConsoleInferenceOperationOutcome {
        val current = snapshot().copy(sourceError = detail)
        return ConsoleInferenceOperationOutcome(success = false, state = current, sourceError = detail)
    }

    private fun publish(current: ConsoleInferenceState) {
        val listener = synchronized(lock) { activeListener }
        listener?.onStateChanged(current)
    }

    private fun idleState(): ConsoleInferenceState = ConsoleInferenceState(
        available = true,
        source = source,
        targets = orderedTargets,
        phase = ConsoleInferencePhase.IDLE,
    )

    private fun GenerationMetrics.toConsoleMetrics(): ConsoleInferenceMetrics = ConsoleInferenceMetrics(
        queueMs = queueMs,
        modelLoadMs = modelLoadMs,
        timeToFirstTokenMs = timeToFirstTokenMs,
        prefillMs = prefillMs,
        decodeMs = decodeMs,
        totalMs = totalMs,
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        decodeTokensPerSecond = decodeTokensPerSecond,
        modelLoadKind = modelLoadKind.name,
    )

    private data class ActiveResources(val handle: GenerationHandle?, val session: SessionId?)
}
