package io.github.daniele21.localllm.contracts

enum class ConsumerStopReason {
    END_OF_GENERATION,
    MAX_OUTPUT_TOKENS,
    STOP_SEQUENCE,
    GRAMMAR_COMPLETE,
    GENERATION_GUARD_REPETITION,
    GENERATION_GUARD_THINKING_BUDGET,
    UNKNOWN,
}

data class ConsumerInferenceMetrics(
    val outputTokens: Int?,
    val timeToFirstTokenMs: Long?,
    val totalMs: Long,
    val decodeTokensPerSecond: Double?,
    val inputTokens: Int?,
    val reasoningTokens: Int?,
    val answerTokens: Int?,
    val queueMs: Long,
    val stopReason: ConsumerStopReason,
) {
    init {
        require(totalMs >= 0) { "Total duration must not be negative" }
        require(queueMs >= 0) { "Queue duration must not be negative" }
        require(timeToFirstTokenMs == null || timeToFirstTokenMs >= 0) { "TTFT must not be negative" }
        require(outputTokens == null || outputTokens >= 0) { "Output token count must not be negative" }
        require(inputTokens == null || inputTokens >= 0) { "Input token count must not be negative" }
        require(reasoningTokens == null || reasoningTokens >= 0) { "Reasoning token count must not be negative" }
        require(answerTokens == null || answerTokens >= 0) { "Answer token count must not be negative" }
        require(decodeTokensPerSecond == null || (decodeTokensPerSecond.isFinite() && decodeTokensPerSecond >= 0.0)) {
            "Decode throughput must be finite and non-negative"
        }
    }
}

data class ConsumerExecutionIdentity(
    val useCaseId: UseCaseId,
    val capabilityRevision: String,
    val preset: InferencePresetRef?,
    val reasoningMode: EffectiveConsumerReasoningMode,
    val outputConstraint: ConsumerOutputConstraintKind,
    val sessionKind: SessionKind,
) {
    init {
        require(capabilityRevision.isNotBlank()) { "Capability revision must not be blank" }
    }
}

data class ConsumerInferenceResult(
    val answer: String,
    val surfacedReasoning: String?,
    val metrics: ConsumerInferenceMetrics,
    val execution: ConsumerExecutionIdentity,
)
