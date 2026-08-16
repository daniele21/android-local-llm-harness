package io.github.daniele21.localllm.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryAwareContextPlannerTest {
    private val residency = RuntimeResidencySnapshot(
        modelLoaded = true,
        residentContexts = 0,
        activeGeneration = false,
        queuedGenerations = 0,
    )

    @Test
    fun `keeps requested tier when it fits memory budget`() {
        val planner = planner(
            availableBytes = 5_000,
            costs = mapOf(8_192 to 500L, 4_096 to 300L, 2_048 to 200L),
        )

        val decision = planner.plan(request())

        assertEquals(
            MemoryAwareContextDecision.Allow(
                contextTokens = 8_192,
                downshifted = false,
                estimate = estimate(500, "8192"),
            ),
            decision,
        )
    }

    @Test
    fun `downshifts from requested tier until one fits`() {
        val planner = planner(
            availableBytes = 1_000,
            costs = mapOf(8_192 to 900L, 4_096 to 700L, 2_048 to 300L),
        )

        val decision = planner.plan(request())

        assertEquals(
            MemoryAwareContextDecision.Allow(
                contextTokens = 2_048,
                downshifted = true,
                estimate = estimate(300, "2048"),
            ),
            decision,
        )
    }

    @Test
    fun `never downshifts below required context capacity`() {
        val planner = planner(
            availableBytes = 1_000,
            costs = mapOf(8_192 to 900L, 4_096 to 700L, 2_048 to 100L),
        )

        val decision = planner.plan(request(minimumTokens = 3_000))

        assertEquals(
            MemoryAwareContextDecision.Reject(
                reason = MemoryAwareContextRejectReason.MEMORY_BUDGET_REJECTED,
                admissionReason = MemoryAdmissionRejectReason.AVAILABLE_MEMORY_FLOOR,
            ),
            decision,
        )
    }

    @Test
    fun `fails closed when memory observation is unavailable`() {
        val planner = MemoryAwareContextPlanner(
            observationSource = RuntimeMemoryObservationSource { null },
            costEstimator = ContextMemoryCostEstimator { _, contextTokens -> estimate(100, contextTokens.toString()) },
            admissionController = controller(),
        )

        assertEquals(
            MemoryAwareContextDecision.Reject(MemoryAwareContextRejectReason.MEMORY_OBSERVATION_UNAVAILABLE),
            planner.plan(request()),
        )
    }

    @Test
    fun `fails closed when no eligible tier has a cost estimate`() {
        val planner = planner(availableBytes = 5_000, costs = emptyMap())

        assertEquals(
            MemoryAwareContextDecision.Reject(MemoryAwareContextRejectReason.MEMORY_COST_ESTIMATE_UNAVAILABLE),
            planner.plan(request()),
        )
    }

    @Test
    fun `scopes memory estimates to the requested model profile`() {
        var observedProfileId: String? = null
        val planner = MemoryAwareContextPlanner(
            observationSource = RuntimeMemoryObservationSource {
                RuntimeMemoryObservation(availableMemoryBytes = 5_000, lowMemory = false)
            },
            costEstimator = ContextMemoryCostEstimator { profileId, contextTokens ->
                observedProfileId = profileId
                estimate(200, contextTokens.toString())
            },
            admissionController = controller(),
        )

        planner.plan(request(modelProfileId = "model-a"))

        assertEquals("model-a", observedProfileId)
    }

    private fun request(
        modelProfileId: String = "model-profile",
        minimumTokens: Int = 1_500,
    ): MemoryAwareContextRequest = MemoryAwareContextRequest(
        modelProfileId = modelProfileId,
        requestedContextTokens = 8_192,
        minimumContextTokens = minimumTokens,
        approvedContextTiers = listOf(1_024, 2_048, 4_096, 8_192),
        residency = residency,
    )

    private fun planner(availableBytes: Long, costs: Map<Int, Long>): MemoryAwareContextPlanner = MemoryAwareContextPlanner(
        observationSource = RuntimeMemoryObservationSource {
            RuntimeMemoryObservation(
                availableMemoryBytes = availableBytes,
                lowMemory = false,
            )
        },
        costEstimator = ContextMemoryCostEstimator { _, contextTokens ->
            costs[contextTokens]?.let { estimate(it, contextTokens.toString()) }
        },
        admissionController = controller(),
    )

    private fun controller(): MemoryAdmissionController = MemoryAdmissionController(
        RuntimeMemoryBudget(
            minimumAvailableBytes = 256,
            safetyReserveBytes = 128,
            maxResidentContexts = 4,
        ),
    )

    private fun estimate(peakBytes: Long, profileId: String): MemoryCostEstimate = MemoryCostEstimate(
        residentBytes = peakBytes / 2,
        peakIncrementalBytes = peakBytes,
        source = MemoryCostSource.CANDIDATE,
        profileId = profileId,
    )
}
