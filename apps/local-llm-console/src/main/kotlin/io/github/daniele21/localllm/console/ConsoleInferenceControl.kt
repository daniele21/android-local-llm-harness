package io.github.daniele21.localllm.console

import io.github.daniele21.localllm.contracts.ApplicationId
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
import io.github.daniele21.localllm.contracts.UseCaseId
import java.util.UUID

private const val INFERENCE_SOURCE_ERROR = "Inference playground unavailable"
private const val PREPARATION_ERROR = "Model preparation failed"
private const val SESSION_CREATION_ERROR = "Inference session creation failed"
private const val GENERATION_START_ERROR = "Generation could not be started"
private const val CANCELLATION_ERROR = "Generation cancellation failed"
private const val SESSION_CLEANUP_ERROR = "Inference session cleanup failed"
private const val MAX_PROMPT_CHARACTERS = 32_768
private const val MAX_OUTPUT_CHARACTERS = 131_072

data class ConsoleInferenceTarget(
    val applicationId: ApplicationId,
    val useCaseId: UseCaseId,
    val label: String = "${applicationId.value} / ${useCaseId.value}",
) {
    val id: String = "${applicationId.value}:${useCaseId.value}"

    init {
        require(label.isNotBlank()) { "Inference target label must not be blank" }
    }
}

data class ConsoleInferenceRequest(
    val targetId: String,
    val prompt: String,
    val maxOutputTokens: Int = 128,
    val temperature: Float = 0.2f,
    val seed: Long = 42L,
) {
    init {
        require(targetId.isNotBlank()) { "Inference target must not be blank" }
        require(prompt.isNotBlank()) { "Inference prompt must not be blank" }
        require(prompt.length <= MAX_PROMPT_CHARACTERS) {
            "Inference prompt exceeds $MAX_PROMPT_CHARACTERS characters"
        }
        require(maxOutputTokens > 0) { "Inference max output tokens must be positive" }
        require(temperature.isFinite() && temperature >= 0f) {
            "Inference temperature must be finite and non-negative"
        }
    }
}

enum class ConsoleInferencePhase {
    DISCONNECTED,
    IDLE,
    PREPARING,
    QUEUED,
    GENERATING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

data class ConsoleInferenceMetrics(
    val queueMs: Long?,
    val modelLoadMs: Long?,
    val timeToFirstTokenMs: Long?,
    val prefillMs: Long?,
    val decodeMs: Long?,
    val totalMs: Long?,
    val inputTokens: Int?,
    val outputTokens: Int?,
    val decodeTokensPerSecond: Double?,
    val modelLoadKind: String,
)

data class ConsoleInferenceState(
    val available: Boolean,
    val source: String,
    val targets: List<ConsoleInferenceTarget>,
    val phase: ConsoleInferencePhase,
    val activeTargetId: String? = null,
    val output: String = "",
    val outputTruncated: Boolean = false,
    val generatedTokens: Int? = null,
    val sessionActive: Boolean = false,
    val cancellationAvailable: Boolean = false,
    val cancellationRequested: Boolean = false,
    val metrics: ConsoleInferenceMetrics? = null,
    val errorCode: String? = null,
    val detail: String? = null,
    val sourceError: String? = null,
) {
    val executionActive: Boolean
        get() = phase in ACTIVE_PHASES

    private companion object {
        val ACTIVE_PHASES = setOf(
            ConsoleInferencePhase.PREPARING,
            ConsoleInferencePhase.QUEUED,
            ConsoleInferencePhase.GENERATING,
        )
    }
}

data class ConsoleInferenceOperationOutcome(
    val success: Boolean,
    val state: ConsoleInferenceState,
    val sourceError: String? = null,
)

fun interface ConsoleInferenceListener {
    fun onStateChanged(state: ConsoleInferenceState)
}

interface ConsoleInferenceControl : AutoCloseable {
    fun snapshot(): ConsoleInferenceState

    fun start(request: ConsoleInferenceRequest, listener: ConsoleInferenceListener): ConsoleInferenceOperationOutcome

    fun cancel(): ConsoleInferenceOperationOutcome

    fun clear(): ConsoleInferenceOperationOutcome
}

object DisconnectedConsoleInferenceControl : ConsoleInferenceControl {
    override fun snapshot(): ConsoleInferenceState = ConsoleInferenceState(
        available = false,
        source = "Not connected",
        targets = emptyList(),
        phase = ConsoleInferencePhase.DISCONNECTED,
    )

    override fun start(
        request: ConsoleInferenceRequest,
        listener: ConsoleInferenceListener,
    ): ConsoleInferenceOperationOutcome = unavailable()

    override fun cancel(): ConsoleInferenceOperationOutcome = unavailable()

    override fun clear(): ConsoleInferenceOperationOutcome = unavailable()

    override fun close() = Unit

    private fun unavailable(): ConsoleInferenceOperationOutcome = ConsoleInferenceOperationOutcome(
        success = false,
        state = snapshot().copy(sourceError = INFERENCE_SOURCE_ERROR),
        sourceError = INFERENCE_SOURCE_ERROR,
    )
}

fun interface ConsoleInferenceRequestIdFactory {
    fun create(): RequestId
}

@Suppress("TooManyFunctions")
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

    override fun start(
        request: ConsoleInferenceRequest,
        listener: ConsoleInferenceListener,
    ): ConsoleInferenceOperationOutcome {
        val target = targetsById[request.targetId]
            ?: return failedOperation("Unknown inference target")
        val preparing = synchronized(lock) {
            if (state.executionActive || activeSession != null) return failedOperation("Generation is already active")
            activeListener = listener
            activeRequestId = requestIdFactory.create()
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
            state
        }
        listener.onStateChanged(preparing)

        val prepared = runCatching { client.prepare(target.applicationId, target.useCaseId) }
            .getOrElse { return terminalFailure(PREPARATION_ERROR) }
        if (!prepared.ready) return terminalFailure(PREPARATION_ERROR)

        val sessionId = runCatching { client.createSession(target.applicationId, target.useCaseId) }
            .getOrElse { return terminalFailure(SESSION_CREATION_ERROR) }
        val requestId = synchronized(lock) {
            activeSession = sessionId
            state = state.copy(sessionActive = true, detail = "Inference session created")
            activeRequestId ?: error("Inference request ID is unavailable")
        }
        publishSnapshot()

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
        val current = synchronized(lock) {
            if (activeRequestId == requestId && state.executionActive) {
                activeHandle = handle
                state = state.copy(cancellationAvailable = true)
            }
            state
        }
        publish(current)
        return ConsoleInferenceOperationOutcome(success = true, state = current)
    }

    override fun cancel(): ConsoleInferenceOperationOutcome {
        val handle = synchronized(lock) { activeHandle }
            ?: return failedOperation("No cancellable generation is active")
        return runCatching {
            handle.cancel()
            val current = synchronized(lock) {
                state = state.copy(
                    cancellationRequested = true,
                    cancellationAvailable = false,
                    detail = "Cancellation requested",
                )
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
            if (state.executionActive || activeSession != null) return failedOperation("Active generation cannot be cleared")
            activeRequestId = null
            activeHandle = null
            activeListener = null
            state = idleState()
            state
        }
        return ConsoleInferenceOperationOutcome(success = true, state = current)
    }

    override fun close() {
        val handle: GenerationHandle?
        val session: SessionId?
        synchronized(lock) {
            handle = activeHandle
            session = activeSession
            activeHandle = null
            activeSession = null
            activeRequestId = null
            activeListener = null
            state = idleState()
        }
        runCatching { handle?.cancel() }
        runCatching { session?.let(client::closeSession) }
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
        val listener: ConsoleInferenceListener?
        val current = synchronized(lock) {
            if (activeSession == session && failure == null) {
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
            listener = activeListener
            activeListener = null
            state
        }
        listener?.onStateChanged(current)
    }

    private fun terminalFailure(
        detail: String,
        cleanupSession: Boolean = false,
    ): ConsoleInferenceOperationOutcome {
        val cleanupFailed = if (cleanupSession) closeSessionForStartFailure() else false
        val finalDetail = if (cleanupFailed) SESSION_CLEANUP_ERROR else detail
        val listener: ConsoleInferenceListener?
        val current = synchronized(lock) {
            listener = activeListener
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
            state
        }
        listener?.onStateChanged(current)
        return ConsoleInferenceOperationOutcome(false, current, finalDetail)
    }

    private fun closeSessionForStartFailure(): Boolean {
        val session = synchronized(lock) { activeSession } ?: return false
        val failure = runCatching { client.closeSession(session) }.isFailure
        synchronized(lock) {
            if (!failure && activeSession == session) {
                activeSession = null
                state = state.copy(sessionActive = false)
            }
        }
        return failure
    }

    private fun clearTerminalListener() {
        synchronized(lock) { activeListener = null }
    }

    private fun failedOperation(detail: String): ConsoleInferenceOperationOutcome {
        val current = snapshot().copy(sourceError = detail)
        return ConsoleInferenceOperationOutcome(success = false, state = current, sourceError = detail)
    }

    private fun publishSnapshot() = publish(snapshot())

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
}
