package io.github.daniele21.localllm.runtime

fun interface ModelMemoryCostEstimator {
    fun estimate(modelProfileId: String): MemoryCostEstimate?
}

data class MemoryAwareModelLoadRequest(val modelProfileId: String, val residency: RuntimeResidencySnapshot) {
    init {
        require(modelProfileId.isNotBlank()) { "Model profile ID must not be blank" }
    }
}

enum class MemoryAwareModelLoadRejectReason {
    MEMORY_OBSERVATION_UNAVAILABLE,
    MEMORY_COST_ESTIMATE_UNAVAILABLE,
    MEMORY_BUDGET_REJECTED,
}

sealed interface MemoryAwareModelLoadDecision {
    data class Allow(val estimate: MemoryCostEstimate) : MemoryAwareModelLoadDecision

    data class Reject(val reason: MemoryAwareModelLoadRejectReason, val admissionReason: MemoryAdmissionRejectReason? = null) :
        MemoryAwareModelLoadDecision
}

class MemoryAwareModelLoadPlanner(
    private val observationSource: RuntimeMemoryObservationSource,
    private val costEstimator: ModelMemoryCostEstimator,
    private val admissionController: MemoryAdmissionController,
) {
    fun plan(request: MemoryAwareModelLoadRequest): MemoryAwareModelLoadDecision {
        val observation = observationSource.observe()
            ?: return MemoryAwareModelLoadDecision.Reject(MemoryAwareModelLoadRejectReason.MEMORY_OBSERVATION_UNAVAILABLE)
        val estimate = costEstimator.estimate(request.modelProfileId)
            ?: return MemoryAwareModelLoadDecision.Reject(MemoryAwareModelLoadRejectReason.MEMORY_COST_ESTIMATE_UNAVAILABLE)
        return when (
            val admission = admissionController.decide(
                observation = observation,
                request = MemoryAdmissionRequest(
                    resource = MemoryAdmissionResource.MODEL,
                    estimate = estimate,
                    residency = request.residency,
                ),
            )
        ) {
            MemoryAdmissionDecision.Allow -> MemoryAwareModelLoadDecision.Allow(estimate)

            is MemoryAdmissionDecision.Reject -> MemoryAwareModelLoadDecision.Reject(
                reason = MemoryAwareModelLoadRejectReason.MEMORY_BUDGET_REJECTED,
                admissionReason = admission.reason,
            )
        }
    }
}
