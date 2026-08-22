package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.models.GgufModelProfile

/**
 * Backend-neutral context configuration for evaluation-only multi-case execution.
 *
 * This is intentionally separate from [BackendContextConfiguration]: production inference keeps
 * its one-sequence context and scheduler semantics. The current evaluation lane is bounded to a
 * small width so a backend cannot silently turn the capability into general concurrent serving.
 */
data class BackendEvaluationBatchContextConfiguration(val perSequenceContextSize: Int, val maxSequences: Int) {
    init {
        require(perSequenceContextSize > 0) { "Evaluation per-sequence context size must be positive" }
        require(maxSequences in MIN_EVALUATION_BATCH_WIDTH..MAX_EVALUATION_BATCH_WIDTH) {
            "Evaluation batch width must be in $MIN_EVALUATION_BATCH_WIDTH..$MAX_EVALUATION_BATCH_WIDTH"
        }
    }

    companion object {
        const val MIN_EVALUATION_BATCH_WIDTH = 2
        const val MAX_EVALUATION_BATCH_WIDTH = 4
    }
}

interface BackendEvaluationBatchContextHandle : BackendContextHandle {
    val perSequenceContextSize: Int
    val maxSequences: Int
}

data class BackendEvaluationBatchCaseResult(val requestId: String, val output: String, val outcome: BackendGenerationOutcome) {
    init {
        require(requestId.isNotBlank()) { "Evaluation batch request ID must not be blank" }
    }
}

data class BackendEvaluationBatchResult(val cases: List<BackendEvaluationBatchCaseResult>) {
    init {
        require(cases.isNotEmpty()) { "Evaluation batch result must contain at least one case" }
        require(cases.size <= BackendEvaluationBatchContextConfiguration.MAX_EVALUATION_BATCH_WIDTH) {
            "Evaluation batch result exceeds the supported width"
        }
        require(cases.map(BackendEvaluationBatchCaseResult::requestId).distinct().size == cases.size) {
            "Evaluation batch result must not contain duplicate request IDs"
        }
    }
}

/**
 * Optional backend capability for bounded evaluation throughput experiments.
 *
 * Implementations own only backend/native resources. Runtime/model lifecycle, admission, sample
 * identity, scoring, telemetry persistence and product scheduling stay with their existing owners.
 * A runtime may use this SPI only through an explicit evaluation path; [InferenceBackend.generate]
 * and the production SingleDecodeScheduler remain authoritative for ordinary requests.
 */
interface EvaluationBatchInferenceBackend {
    fun createEvaluationBatchContext(
        model: BackendModelHandle,
        profile: GgufModelProfile,
        configuration: BackendEvaluationBatchContextConfiguration,
    ): BackendEvaluationBatchContextHandle

    fun releaseEvaluationBatchContext(context: BackendEvaluationBatchContextHandle)

    /**
     * Executes the supplied requests as one bounded backend operation.
     *
     * Results must be returned in exactly the same order as [requests]. Each request retains its
     * own sampler/output constraints and request ID. Implementations must fail closed rather than
     * returning a reordered, missing or duplicated result set.
     */
    fun generateEvaluationBatch(
        context: BackendEvaluationBatchContextHandle,
        requests: List<BackendGenerationRequest>,
    ): BackendEvaluationBatchResult

    /** Cooperative cancellation for one case inside an active evaluation batch. */
    fun cancelEvaluationCase(requestId: String): Boolean
}
