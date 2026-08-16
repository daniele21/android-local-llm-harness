package io.github.daniele21.localllm.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryAwareModelLoadPlannerTest {
    @Test
    fun `allows model load when observation and estimate fit budget`() {
        val planner = planner(
            observation = RuntimeMemoryObservation(availableMemoryBytes = 1_000L),
            estimate = MemoryCostEstimate(200L, 300L, MemoryCostSource.MEASURED, "model-300"),
        )

        val decision = planner.plan(request())

        assertTrue(decision is MemoryAwareModelLoadDecision.Allow)
        assertEquals("model-300", (decision as MemoryAwareModelLoadDecision.Allow).estimate.profileId)
    }

    @Test
    fun `fails closed when observation is unavailable`() {
        val planner = planner(observation = null, estimate = MemoryCostEstimate(200L, 300L, MemoryCostSource.MEASURED, "model-300"))

        val decision = planner.plan(request())

        assertEquals(
            MemoryAwareModelLoadDecision.Reject(MemoryAwareModelLoadRejectReason.MEMORY_OBSERVATION_UNAVAILABLE),
            decision,
        )
    }

    @Test
    fun `fails closed when model estimate is unavailable`() {
        val planner = planner(observation = RuntimeMemoryObservation(availableMemoryBytes = 1_000L), estimate = null)

        val decision = planner.plan(request())

        assertEquals(
            MemoryAwareModelLoadDecision.Reject(MemoryAwareModelLoadRejectReason.MEMORY_COST_ESTIMATE_UNAVAILABLE),
            decision,
        )
    }

    @Test
    fun `propagates typed admission rejection`() {
        val planner = planner(
            observation = RuntimeMemoryObservation(availableMemoryBytes = 350L),
            estimate = MemoryCostEstimate(200L, 300L, MemoryCostSource.MEASURED, "model-300"),
        )

        val decision = planner.plan(request())

        assertEquals(
            MemoryAwareModelLoadDecision.Reject(
                reason = MemoryAwareModelLoadRejectReason.MEMORY_BUDGET_REJECTED,
                admissionReason = MemoryAdmissionRejectReason.AVAILABLE_MEMORY_FLOOR,
            ),
            decision,
        )
    }

    @Test
    fun `platform low memory rejects before load`() {
        val planner = planner(
            observation = RuntimeMemoryObservation(availableMemoryBytes = 1_000L, lowMemory = true),
            estimate = MemoryCostEstimate(200L, 300L, MemoryCostSource.MEASURED, "model-300"),
        )

        val decision = planner.plan(request())

        assertEquals(
            MemoryAwareModelLoadDecision.Reject(
                reason = MemoryAwareModelLoadRejectReason.MEMORY_BUDGET_REJECTED,
                admissionReason = MemoryAdmissionRejectReason.PLATFORM_LOW_MEMORY,
            ),
            decision,
        )
    }

    private fun planner(observation: RuntimeMemoryObservation?, estimate: MemoryCostEstimate?): MemoryAwareModelLoadPlanner =
        MemoryAwareModelLoadPlanner(
            observationSource = RuntimeMemoryObservationSource { observation },
            costEstimator = ModelMemoryCostEstimator { modelProfileId -> estimate.takeIf { modelProfileId == MODEL_PROFILE_ID } },
            admissionController = MemoryAdmissionController(
                RuntimeMemoryBudget(
                    minimumAvailableBytes = 100L,
                    safetyReserveBytes = 100L,
                    maxProcessPssBytes = null,
                    maxResidentContexts = 2,
                ),
            ),
        )

    private fun request(): MemoryAwareModelLoadRequest = MemoryAwareModelLoadRequest(
        modelProfileId = MODEL_PROFILE_ID,
        residency = RuntimeResidencySnapshot(
            modelLoaded = false,
            residentContexts = 0,
            activeGeneration = false,
            queuedGenerations = 0,
        ),
    )

    private companion object {
        const val MODEL_PROFILE_ID = "model-profile"
    }
}
