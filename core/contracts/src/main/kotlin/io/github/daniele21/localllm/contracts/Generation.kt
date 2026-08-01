package io.github.daniele21.localllm.contracts

data class GenerationRequest(
    val requestId: RequestId,
    val sessionId: SessionId,
    val applicationId: ApplicationId,
    val useCaseId: UseCaseId,
    val input: String,
    val overrides: GenerationOverrides = GenerationOverrides(),
)

data class GenerationOverrides(
    val maxOutputTokens: Int? = null,
    val temperature: Float? = null,
    val seed: Long? = null,
)

sealed interface GenerationEvent {
    val requestId: RequestId

    data class Queued(
        override val requestId: RequestId,
        val position: Int,
    ) : GenerationEvent

    data class Started(
        override val requestId: RequestId,
        val modelDigest: ModelDigest,
    ) : GenerationEvent

    data class TextDelta(
        override val requestId: RequestId,
        val text: String,
        val generatedTokens: Int,
    ) : GenerationEvent

    data class Completed(
        override val requestId: RequestId,
        val output: String,
        val metrics: GenerationMetrics,
    ) : GenerationEvent

    data class Failed(
        override val requestId: RequestId,
        val error: LocalLlmError,
    ) : GenerationEvent
}

data class GenerationMetrics(
    val queueMs: Long,
    val modelLoadMs: Long?,
    val timeToFirstTokenMs: Long?,
    val totalMs: Long,
    val inputTokens: Int?,
    val outputTokens: Int?,
    val decodeTokensPerSecond: Double?,
)

sealed interface LocalLlmError {
    val code: String
    val message: String

    data class Configuration(
        override val message: String,
    ) : LocalLlmError {
        override val code: String = "CONFIGURATION"
    }

    data class ModelUnavailable(
        override val message: String,
    ) : LocalLlmError {
        override val code: String = "MODEL_UNAVAILABLE"
    }

    data class NativeRuntime(
        override val message: String,
    ) : LocalLlmError {
        override val code: String = "NATIVE_RUNTIME"
    }

    data class Cancelled(
        override val message: String = "Generation cancelled",
    ) : LocalLlmError {
        override val code: String = "CANCELLED"
    }
}

fun interface GenerationListener {
    fun onEvent(event: GenerationEvent)
}

interface GenerationHandle {
    val requestId: RequestId
    fun cancel()
}
