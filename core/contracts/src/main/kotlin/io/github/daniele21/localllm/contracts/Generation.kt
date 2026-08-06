package io.github.daniele21.localllm.contracts

data class GenerationRequest(
    val requestId: RequestId,
    val sessionId: SessionId,
    val applicationId: ApplicationId,
    val useCaseId: UseCaseId,
    val input: GenerationInput,
    val overrides: GenerationOverrides = GenerationOverrides(),
    val outputConstraint: OutputConstraint = OutputConstraint.Text,
) {
    constructor(
        requestId: RequestId,
        sessionId: SessionId,
        applicationId: ApplicationId,
        useCaseId: UseCaseId,
        input: String,
        overrides: GenerationOverrides = GenerationOverrides(),
        outputConstraint: OutputConstraint = OutputConstraint.Text,
    ) : this(
        requestId = requestId,
        sessionId = sessionId,
        applicationId = applicationId,
        useCaseId = useCaseId,
        input = GenerationInput.Text(input),
        overrides = overrides,
        outputConstraint = outputConstraint,
    )
}

data class GenerationOverrides(
    val preset: InferencePresetRef? = null,
    val maxOutputTokens: Int? = null,
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val seedPolicy: SeedPolicy? = null,
    val seed: Long? = null,
) {
    init {
        require(seedPolicy == null || seed == null) { "Specify either seedPolicy or legacy seed, not both" }
        seed?.let { SeedPolicy.Fixed(it) }
    }

    fun requestedSeedPolicy(): SeedPolicy? = seedPolicy ?: seed?.let(SeedPolicy::Fixed)
}

sealed interface GenerationEvent {
    val requestId: RequestId

    data class Queued(override val requestId: RequestId, val position: Int) : GenerationEvent

    data class Started(override val requestId: RequestId, val modelDigest: ModelDigest) : GenerationEvent

    data class Prepared(override val requestId: RequestId, val modelDigest: ModelDigest, val configuration: EffectiveGenerationMetadata) :
        GenerationEvent

    data class TextDelta(override val requestId: RequestId, val text: String, val generatedTokens: Int) : GenerationEvent

    data class Completed(override val requestId: RequestId, val output: String, val metrics: GenerationMetrics) : GenerationEvent

    data class Failed(override val requestId: RequestId, val error: LocalLlmError) : GenerationEvent
}

data class GenerationMetrics(
    val queueMs: Long,
    val modelLoadMs: Long?,
    val timeToFirstTokenMs: Long?,
    val totalMs: Long,
    val inputTokens: Int?,
    val outputTokens: Int?,
    val decodeTokensPerSecond: Double?,
    val prefillMs: Long? = null,
    val decodeMs: Long? = null,
    val modelLoadKind: ModelLoadKind = ModelLoadKind.UNKNOWN,
    val stopReason: StopReason = StopReason.UNKNOWN,
    val promptPlanningMs: Long? = null,
    val contextCreationMs: Long? = null,
)

enum class ModelLoadKind {
    COLD,
    WARM,
    UNKNOWN,
}

sealed interface LocalLlmError {
    val code: String
    val message: String

    data class Configuration(override val message: String, val reason: ConfigurationErrorCode = ConfigurationErrorCode.CONFIGURATION) :
        LocalLlmError {
        override val code: String = reason.name
    }

    data class ModelUnavailable(override val message: String) : LocalLlmError {
        override val code: String = "MODEL_UNAVAILABLE"
    }

    data class NativeRuntime(override val message: String) : LocalLlmError {
        override val code: String = "NATIVE_RUNTIME"
    }

    data class Cancelled(override val message: String = "Generation cancelled") : LocalLlmError {
        override val code: String = "CANCELLED"
    }
}

enum class ConfigurationErrorCode {
    CONFIGURATION,
    PRESET_NOT_FOUND,
    PRESET_NOT_ALLOWED,
    INVALID_GENERATION_CONFIGURATION,
    RAW_COMPLETION_NOT_ALLOWED,
    CHAT_TEMPLATE_UNAVAILABLE,
    CHAT_TEMPLATE_UNSUPPORTED,
    PROMPT_TOKENIZATION_FAILED,
    CONTEXT_CAPACITY_EXCEEDED,
    CONTEXT_RECONFIGURATION_REQUIRED,
    OUTPUT_CONSTRAINT_UNSUPPORTED,
    INVALID_OUTPUT_CONSTRAINT,
}

fun interface GenerationListener {
    fun onEvent(event: GenerationEvent)
}

interface GenerationHandle {
    val requestId: RequestId

    fun cancel()
}
