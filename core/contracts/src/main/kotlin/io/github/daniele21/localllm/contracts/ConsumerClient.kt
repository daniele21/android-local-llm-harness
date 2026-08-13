package io.github.daniele21.localllm.contracts

@JvmInline
value class ConsumerPreparedId(val value: String) {
    init {
        require(value.isNotBlank()) { "Prepared selection ID must not be blank" }
    }
}

data class ConsumerPrepareRequest(val useCaseId: UseCaseId, val selection: ConsumerSelectionRequest = ConsumerSelectionRequest())

data class ConsumerPreparedSelection(
    val preparedId: ConsumerPreparedId,
    val useCaseId: UseCaseId,
    val capabilityRevision: String,
    val preset: InferencePresetRef?,
    val reasoningMode: EffectiveConsumerReasoningMode,
    val outputConstraint: ConsumerOutputConstraintKind,
    val sessionKind: SessionKind,
)

enum class ConsumerErrorCode {
    USE_CASE_NOT_ALLOWED,
    STALE_CAPABILITY,
    MODEL_UNAVAILABLE,
    CAPABILITY_INCOMPATIBLE,
    PRESET_NOT_ALLOWED,
    REASONING_NOT_ALLOWED,
    REASONING_REQUIRED,
    OUTPUT_NOT_ALLOWED,
    SESSION_KIND_NOT_ALLOWED,
    INVALID_INPUT,
    PREPARE_FAILED,
    PREPARED_SELECTION_STALE,
    PREPARED_SELECTION_NOT_FOUND,
    SESSION_NOT_FOUND,
    CANCELLED,
    RUNTIME_FAILURE,
}

data class ConsumerFailure(val code: ConsumerErrorCode, val message: String) {
    init {
        require(message.isNotBlank()) { "Consumer failure message must not be blank" }
    }
}

sealed interface ConsumerPrepareResult {
    data class Prepared(val selection: ConsumerPreparedSelection) : ConsumerPrepareResult

    data class Rejected(val failure: ConsumerFailure) : ConsumerPrepareResult
}

sealed interface ConsumerSessionResult {
    data class Created(val sessionId: SessionId) : ConsumerSessionResult

    data class Rejected(val failure: ConsumerFailure) : ConsumerSessionResult
}

sealed interface ConsumerGenerationInput {
    data class Text(val value: String) : ConsumerGenerationInput {
        init {
            require(value.isNotBlank()) { "Consumer input must not be blank" }
            require('\u0000' !in value) { "Consumer input must not contain NUL" }
        }
    }

    data class Messages(val values: List<ConversationMessage>) : ConsumerGenerationInput {
        init {
            require(values.isNotEmpty()) { "Consumer messages must not be empty" }
        }
    }
}

sealed interface ConsumerOutputConstraint {
    data object Text : ConsumerOutputConstraint

    data object Json : ConsumerOutputConstraint

    data class JsonSchema(val schema: String) : ConsumerOutputConstraint {
        init {
            require(schema.isNotBlank()) { "JSON schema must not be blank" }
            require('\u0000' !in schema) { "JSON schema must not contain NUL" }
        }
    }
}

data class ConsumerGenerationRequest(
    val requestId: RequestId,
    val sessionId: SessionId,
    val input: ConsumerGenerationInput,
    val outputConstraint: ConsumerOutputConstraint,
)

enum class ConsumerContentType {
    REASONING,
    ANSWER,
}

sealed interface ConsumerGenerationEvent {
    val requestId: RequestId

    data class Queued(override val requestId: RequestId, val position: Int) : ConsumerGenerationEvent

    data class Prepared(override val requestId: RequestId, val execution: ConsumerExecutionIdentity) : ConsumerGenerationEvent

    data class Started(override val requestId: RequestId) : ConsumerGenerationEvent

    data class ContentDelta(override val requestId: RequestId, val text: String, val contentType: ConsumerContentType) :
        ConsumerGenerationEvent

    data class Completed(override val requestId: RequestId, val result: ConsumerInferenceResult) : ConsumerGenerationEvent {
        val answer: String
            get() = result.answer
        val surfacedReasoning: String?
            get() = result.surfacedReasoning
        val metrics: ConsumerInferenceMetrics
            get() = result.metrics
        val execution: ConsumerExecutionIdentity
            get() = result.execution
    }

    data class Failed(override val requestId: RequestId, val failure: ConsumerFailure) : ConsumerGenerationEvent
}

fun interface ConsumerGenerationListener {
    fun onEvent(event: ConsumerGenerationEvent)
}

interface ConsumerGenerationHandle {
    val requestId: RequestId

    fun cancel()
}

sealed interface ConsumerGenerationStartResult {
    data class Accepted(val handle: ConsumerGenerationHandle) : ConsumerGenerationStartResult

    data class Rejected(val failure: ConsumerFailure) : ConsumerGenerationStartResult
}

interface ConsumerLocalLlmClient {
    fun capabilities(useCaseId: UseCaseId): ConsumerCapabilityResult

    fun prepare(request: ConsumerPrepareRequest): ConsumerPrepareResult

    fun createSession(preparedId: ConsumerPreparedId): ConsumerSessionResult

    fun generate(request: ConsumerGenerationRequest, listener: ConsumerGenerationListener): ConsumerGenerationStartResult

    fun closeSession(sessionId: SessionId)
}
