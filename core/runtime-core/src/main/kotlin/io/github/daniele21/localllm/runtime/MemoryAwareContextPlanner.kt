package io.github.daniele21.localllm.runtime

fun interface RuntimeMemoryObservationSource {
    fun observe(): RuntimeMemoryObservation?
}

fun interface ContextMemoryCostEstimator {
    fun estimate(modelProfileId: String, contextTokens: Int): MemoryCostEstimate?
}

data class MemoryAwareContextRequest(
    val modelProfileId: String,
    val requestedContextTokens: Int,
    val minimumContextTokens: Int,
    val approvedContextTiers: List<Int>,
    val residency: RuntimeResidencySnapshot,
) {
    init {
        require(modelProfileId.isNotBlank()) { "Model profile ID must not be blank" }
        require(requestedContextTokens > 0) { "Requested context tokens must be positive" }
        require(minimumContextTokens > 0) { "Minimum context tokens must be positive" }
        require(minimumContextTokens <= requestedContextTokens) {
            "Minimum context tokens must not exceed the requested context"
        }
        require(approvedContextTiers.all { it > 0 }) { "Approved context tiers must be positive" }
    }
}

enum class MemoryAwareContextRejectReason {
    NO_ELIGIBLE_CONTEXT_TIER,
    MEMORY_OBSERVATION_UNAVAILABLE,
    MEMORY_COST_ESTIMATE_UNAVAILABLE,
    MEMORY_BUDGET_REJECTED,
}

sealed interface MemoryAwareContextDecision {
    data class Allow(
        val contextTokens: Int,
        val downshifted: Boolean,
        val estimate: MemoryCostEstimate,
    ) : MemoryAwareContextDecision

    data class Reject(
        val reason: MemoryAwareContextRejectReason,
        val admissionReason: MemoryAdmissionRejectReason? = null,
    ) : MemoryAwareContextDecision
}

class MemoryAwareContextPlanner(
    private val observationSource: RuntimeMemoryObservationSource,
    private val costEstimator: ContextMemoryCostEstimator,
    private val admissionController: MemoryAdmissionController,
) {
    fun plan(request: MemoryAwareContextRequest): MemoryAwareContextDecision {
        val candidates = request.approvedContextTiers
            .asSequence()
            .distinct()
            .filter { it in request.minimumContextTokens..request.requestedContextTokens }
            .sortedDescending()
            .toList()
        if (candidates.isEmpty()) {
            return MemoryAwareContextDecision.Reject(MemoryAwareContextRejectReason.NO_ELIGIBLE_CONTEXT_TIER)
        }

        val observation = observationSource.observe()
            ?: return MemoryAwareContextDecision.Reject(MemoryAwareContextRejectReason.MEMORY_OBSERVATION_UNAVAILABLE)
        var sawEstimate = false
        var lastAdmissionReject: MemoryAdmissionRejectReason? = null

        candidates.forEach { contextTokens ->
            val estimate = costEstimator.estimate(request.modelProfileId, contextTokens) ?: return@forEach
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
                MemoryAdmissionDecision.Allow -> return MemoryAwareContextDecision.Allow(
                    contextTokens = contextTokens,
                    downshifted = contextTokens != request.requestedContextTokens,
                    estimate = estimate,
                )

                is MemoryAdmissionDecision.Reject -> lastAdmissionReject = admission.reason
            }
        }

        if (!sawEstimate) {
            return MemoryAwareContextDecision.Reject(MemoryAwareContextRejectReason.MEMORY_COST_ESTIMATE_UNAVAILABLE)
        }
        return MemoryAwareContextDecision.Reject(
            reason = MemoryAwareContextRejectReason.MEMORY_BUDGET_REJECTED,
            admissionReason = lastAdmissionReject,
        )
    }
}
