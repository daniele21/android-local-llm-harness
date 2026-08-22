package io.github.daniele21.localllm.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryAwareEvaluationBatchContextPlannerTest {
    private val residency = RuntimeResidencySnapshot(
        modelLoaded = true,
        residentContexts = 0,
        activeGeneration = true,
        queuedGenerations = 0,
    )

    @Test
    fun `estimates aggregate context rather than per-sequence context`() {
        var estimatedTokens: Int? = null
        val planner = planner { contextTokens ->
            estimatedTokens = contextTokens
            estimate(500, contextTokens.toString())
        }

        val decision = planner.plan(request(perSequenceTokens = 2_048, sequenceCount = 4))

        assertEquals(8_192, estimatedTokens)
        assertEquals(
            MemoryAwareEvaluationBatchContextDecision.Allow(
                perSequenceContextTokens = 2_048,
                aggregateContextTokens = 8_192,
                downshifted = false,
                estimate = estimate(500, "8192"),
            ),
            decision,
        )
    }

    @Test
    fun `downshifts per-sequence tier using aggregate estimates`() {
        val planner = planner { aggregateTokens ->
            when (aggregateTokens) {
                8_192 -> estimate(900, "8192")
                4_096 -> estimate(300, "4096")
                else -> null
            }
        }

        val decision = planner.plan(
            request(
                perSequenceTokens = 4_096,
                minimumTokens = 2_048,
                approvedTiers = listOf(2_048, 4_096),
                sequenceCount = 2,
            ),
        )

        assertEquals(
            MemoryAwareEvaluationBatchContextDecision.Allow(
                perSequenceContextTokens = 2_048,
                aggregateContextTokens = 4_096,
                downshifted = true,
                estimate = estimate(300, "4096"),
            ),
            decision,
        )
    }

    @Test
    fun `fails closed when aggregate estimate is unavailable`() {
        val planner = planner { null }

        assertEquals(
            MemoryAwareEvaluationBatchContextDecision.Reject(
                MemoryAwareContextRejectReason.MEMORY_COST_ESTIMATE_UNAVAILABLE,
            ),
            planner.plan(request()),
        )
    }

    @Test
    fun `fails closed on aggregate token arithmetic overflow`() {
        val planner = planner { contextTokens -> estimate(100, contextTokens.toString()) }

        assertEquals(
            MemoryAwareEvaluationBatchContextDecision.Reject(
                reason = MemoryAwareContextRejectReason.MEMORY_BUDGET_REJECTED,
                admissionReason = MemoryAdmissionRejectReason.BYTE_ARITHMETIC_OVERFLOW,
            ),
            planner.plan(
                request(
                    perSequenceTokens = Int.MAX_VALUE,
                    minimumTokens = Int.MAX_VALUE,
                    approvedTiers = listOf(Int.MAX_VALUE),
                    sequenceCount = 2,
                ),
            ),
        )
    }

    private fun request(
        perSequenceTokens: Int = 2_048,
        minimumTokens: Int = 1_024,
        approvedTiers: List<Int> = listOf(1_024, 2_048),
        sequenceCount: Int = 2,
    ): MemoryAwareEvaluationBatchContextRequest = MemoryAwareEvaluationBatchContextRequest(
        modelProfileId = "model-profile",
        requestedPerSequenceContextTokens = perSequenceTokens,
        minimumPerSequenceContextTokens = minimumTokens,
        approvedPerSequenceContextTiers = approvedTiers,
        sequenceCount = sequenceCount,
        residency = residency,
    )

    private fun planner(estimateFor: (Int) -> MemoryCostEstimate?): MemoryAwareEvaluationBatchContextPlanner =
        MemoryAwareEvaluationBatchContextPlanner(
            observationSource = RuntimeMemoryObservationSource {
                RuntimeMemoryObservation(availableMemoryBytes = 1_000, lowMemory = false)
            },
            costEstimator = ContextMemoryCostEstimator { _, contextTokens -> estimateFor(contextTokens) },
            admissionController = MemoryAdmissionController(
                RuntimeMemoryBudget(
                    minimumAvailableBytes = 50,
                    safetyReserveBytes = 50,
                    maxResidentContexts = 4,
                ),
            ),
        )

    private fun estimate(peakBytes: Long, profileId: String): MemoryCostEstimate = MemoryCostEstimate(
        residentBytes = peakBytes / 2,
        peakIncrementalBytes = peakBytes,
        source = MemoryCostSource.CANDIDATE,
        profileId = profileId,
    )
}
