package io.github.daniele21.localllm.console

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.UseCaseId

internal const val INFERENCE_SOURCE_ERROR = "Inference playground unavailable"
internal const val PREPARATION_ERROR = "Model preparation failed"
internal const val SESSION_CREATION_ERROR = "Inference session creation failed"
internal const val GENERATION_START_ERROR = "Generation could not be started"
internal const val CANCELLATION_ERROR = "Generation cancellation failed"
internal const val SESSION_CLEANUP_ERROR = "Inference session cleanup failed"
internal const val MAX_PROMPT_CHARACTERS = 32_768
internal const val MAX_OUTPUT_CHARACTERS = 131_072

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

data class ConsoleInferenceOperationOutcome(val success: Boolean, val state: ConsoleInferenceState, val sourceError: String? = null)

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

    override fun start(request: ConsoleInferenceRequest, listener: ConsoleInferenceListener): ConsoleInferenceOperationOutcome =
        unavailable()

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
