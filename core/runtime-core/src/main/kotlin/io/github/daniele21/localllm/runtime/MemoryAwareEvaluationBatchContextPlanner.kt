package io.github.daniele21.localllm.runtime

data class MemoryAwareEvaluationBatchContextRequest(
    val modelProfileId: String,
    val requestedPerSequenceContextTokens: Int,
    val minimumPerSequenceContextTokens: Int,
    val approvedPerSequenceContextTiers: List<Int>,
    val sequenceCount: Int,
    val residency: RuntimeResidencySnapshot,
) {
    init {
        require(modelProfileId.isNotBlank()) { "Model profile ID must not be blank" }
        require(requestedPerSequenceContextTokens > 0) { "Requested per-sequence context must be positive" }
        require(minimumPerSequenceContextTokens > 0) { "Minimum per-sequence context must be positive" }
        require(minimumPerSequenceContextTokens <= requestedPerSequenceContextTokens) {
            "Minimum per-sequence context must not exceed the requested context"
        }
        require(approvedPerSequenceContextTiers.all { it > 0 }) { "Approved per-sequence context tiers must be positive" }
        require(sequenceCount in RuntimeEvaluationBatchRequest.MIN_RUNTIME_EVALUATION_BATCH_WIDTH..RuntimeEvaluationBatchRequest.MAX_RUNTIME_EVALUATION_BATCH_WIDTH) {
            "Evaluation batch sequence count is outside the supported runtime width"
        }
    }
}

sealed interface MemoryAwareEvaluationBatchContextDecision {
    data class Allow(
        val perSequenceContextTokens: Int,
        val aggregateContextTokens: Int,
        val downshifted: Boolean,
        val estimate: MemoryCostEstimate,
    ) : MemoryAwareEvaluationBatchContextDecision

    data class Reject(
        val reason: MemoryAwareContextRejectReason,
        val admissionReason: MemoryAdmissionRejectReason? = null,
    ) : MemoryAwareEvaluationBatchContextDecision
}

/**
 * Memory admission for a dedicated multi-sequence evaluation context.
 *
 * Cost estimation is performed against aggregate n_ctx, never the per-sequence value, so a width-4
 * context cannot be admitted using a width-1 memory estimate. Missing aggregate estimates fail
 * closed when this planner is configured.
 */
class MemoryAwareEvaluationBatchContextPlanner(
    private val observationSource: RuntimeMemoryObservationSource,
    private val costEstimator: ContextMemoryCostEstimator,
    private val admissionController: MemoryAdmissionController,
) {
    fun plan(request: MemoryAwareEvaluationBatchContextRequest): MemoryAwareEvaluationBatchContextDecision {
        val candidates = request.approvedPerSequenceContextTiers
            .asSequence()
            .distinct()
            .filter { it in request.minimumPerSequenceContextTokens..request.requestedPerSequenceContextTokens }
            .sortedDescending()
            .toList()
        if (candidates.isEmpty()) {
            return MemoryAwareEvaluationBatchContextDecision.Reject(MemoryAwareContextRejectReason.NO_ELIGIBLE_CONTEXT_TIER)
        }
        val observation = observationSource.observe()
            ?: return MemoryAwareEvaluationBatchContextDecision.Reject(
                MemoryAwareContextRejectReason.MEMORY_OBSERVATION_UNAVAILABLE,
            )

        var sawEstimate = false
        var lastAdmissionReject: MemoryAdmissionRejectReason? = null
        for (perSequenceTokens in candidates) {
            val aggregateTokens = try {
                Math.multiplyExact(perSequenceTokens, request.sequenceCount)
            } catch (_: ArithmeticException) {
                return MemoryAwareEvaluationBatchContextDecision.Reject(
                    reason = MemoryAwareContextRejectReason.MEMORY_BUDGET_REJECTED,
                    admissionReason = MemoryAdmissionRejectReason.BYTE_ARITHMETIC_OVERFLOW,
                )
            }
            val estimate = costEstimator.estimate(request.modelProfileId, aggregateTokens) ?: continue
            sawEstimate = true
            when (
                val admission = admissionController.decide(
                    observation = observation,
                    request = MemoryAdmissionRequest(
                        resource = MemoryAdmissionResource.CONTEXT,
                        estimate = estimate,
                        residency = request.residency,
                    ),
                )
            ) {
                MemoryAdmissionDecision.Allow -> return MemoryAwareEvaluationBatchContextDecision.Allow(
                    perSequenceContextTokens = perSequenceTokens,
                    aggregateContextTokens = aggregateTokens,
                    downshifted = perSequenceTokens != request.requestedPerSequenceContextTokens,
                    estimate = estimate,
                )

                is MemoryAdmissionDecision.Reject -> lastAdmissionReject = admission.reason
            }
        }

        return if (!sawEstimate) {
            MemoryAwareEvaluationBatchContextDecision.Reject(MemoryAwareContextRejectReason.MEMORY_COST_ESTIMATE_UNAVAILABLE)
        } else {
            MemoryAwareEvaluationBatchContextDecision.Reject(
                reason = MemoryAwareContextRejectReason.MEMORY_BUDGET_REJECTED,
                admissionReason = lastAdmissionReject,
            )
        }
    }
}
