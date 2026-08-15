package io.github.daniele21.localllm.evaluation.engine

import io.github.daniele21.localllm.evaluation.EvaluationCaseId
import io.github.daniele21.localllm.evaluation.EvaluationCaseMetrics
import io.github.daniele21.localllm.evaluation.EvaluationCaseResult
import io.github.daniele21.localllm.evaluation.EvaluationCaseStatus
import io.github.daniele21.localllm.evaluation.EvaluationCategoryId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetCategoryDefinition
import io.github.daniele21.localllm.evaluation.EvaluationFailure
import io.github.daniele21.localllm.evaluation.EvaluationFailureCode
import io.github.daniele21.localllm.evaluation.EvaluationFailureStage
import io.github.daniele21.localllm.evaluation.EvaluationOutcome
import io.github.daniele21.localllm.evaluation.EvaluatorOutcomeCode
import io.github.daniele21.localllm.evaluation.EvaluatorSpec
import io.github.daniele21.localllm.evaluation.EvaluatorType
import io.github.daniele21.localllm.evaluation.EvaluatorVersion
import io.github.daniele21.localllm.evaluation.NormalizedScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class EvaluationRunAggregatorTest {
    private val categoryA = EvaluationCategoryId("category-a")
    private val categoryB = EvaluationCategoryId("category-b")
    private val evaluator = EvaluatorSpec(EvaluatorType.EXACT_MATCH, EvaluatorVersion(1))

    @Test
    fun `aggregation preserves quality zero semantics and metric-specific missing values`() {
        val selected = (1..6).map { EvaluationCaseId("case-$it") }
        val results = listOf(
            scored(
                selected[0],
                categoryA,
                EvaluationOutcome(NormalizedScore(1.0), EvaluatorOutcomeCode.CORRECT),
                metrics = EvaluationCaseMetrics(
                    timeToFirstTokenMs = 10,
                    totalMs = 100,
                    decodeTokensPerSecond = 5.0,
                    processPssBytes = 1_000,
                    thermalStatus = "NORMAL",
                ),
            ),
            scored(
                selected[1],
                categoryA,
                EvaluationOutcome(NormalizedScore(0.5), EvaluatorOutcomeCode.PARTIAL),
                metrics = EvaluationCaseMetrics(
                    timeToFirstTokenMs = 20,
                    totalMs = 200,
                    decodeTokensPerSecond = 10.0,
                    processPssBytes = 3_000,
                    thermalStatus = "HOT",
                ),
            ),
            failed(
                selected[2],
                categoryA,
                EvaluationCaseStatus.TIMEOUT,
                EvaluationFailure(EvaluationFailureStage.GENERATION, EvaluationFailureCode.CASE_TIMEOUT, selected[2]),
                metrics = EvaluationCaseMetrics(timeToFirstTokenMs = 30, thermalStatus = "NORMAL"),
            ),
            invalid(selected[3], categoryB),
            failed(
                selected[4],
                categoryB,
                EvaluationCaseStatus.RUNTIME_FAILURE,
                EvaluationFailure(EvaluationFailureStage.GENERATION, EvaluationFailureCode.RUNTIME_FAILURE, selected[4]),
            ),
        )

        val aggregation = EvaluationRunAggregator().aggregate(
            selectedCaseIds = selected,
            categories = listOf(
                EvaluationDatasetCategoryDefinition(categoryA, "Category A", 0.6),
                EvaluationDatasetCategoryDefinition(categoryB, "Category B", 0.4),
            ),
            caseResults = results,
        )

        assertEquals(0.3, aggregation.quality.aggregateScore!!.value, 0.000_001)
        assertEquals(listOf(categoryA, categoryB), aggregation.quality.categoryScores.map { it.categoryId })
        assertEquals(0.5, aggregation.quality.categoryScores[0].score.value, 0.000_001)
        assertEquals(0.0, aggregation.quality.categoryScores[1].score.value, 0.000_001)

        assertEquals(EvaluationMetricDistribution(20.0, 30.0, 3), aggregation.runtime.timeToFirstTokenMs)
        assertEquals(EvaluationMetricDistribution(150.0, 200.0, 2), aggregation.runtime.totalMs)
        assertEquals(EvaluationMetricDistribution(7.5, 10.0, 2), aggregation.runtime.decodeTokensPerSecond)
        assertEquals(EvaluationMetricDistribution(null, null, 0), aggregation.runtime.prefillMs)
        assertEquals(EvaluationMetricDistribution(null, null, 0), aggregation.runtime.decodeMs)

        assertEquals(EvaluationMetricDistribution(2_000.0, 3_000.0, 2), aggregation.resources.processPssBytes)
        assertEquals(EvaluationMetricDistribution(null, null, 0), aggregation.resources.availableMemoryBytes)
        assertEquals(mapOf("HOT" to 1, "NORMAL" to 2), aggregation.resources.thermalStatusCounts)

        assertEquals(6, aggregation.reliability.totalCases)
        assertEquals(2, aggregation.reliability.completedAndScored)
        assertEquals(1, aggregation.reliability.incorrectButValid)
        assertEquals(1, aggregation.reliability.invalidOutput)
        assertEquals(1, aggregation.reliability.timeout)
        assertEquals(1, aggregation.reliability.runtimeFailure)
        assertEquals(0, aggregation.reliability.cancelled)
        assertEquals(1, aggregation.reliability.skipped)
    }

    @Test
    fun `cancelled cases are excluded from quality and not counted as skipped`() {
        val selected = listOf(EvaluationCaseId("case-1"), EvaluationCaseId("case-2"))
        val cancelled = failed(
            selected[0],
            categoryA,
            EvaluationCaseStatus.CANCELLED,
            EvaluationFailure(EvaluationFailureStage.CANCELLATION, EvaluationFailureCode.CANCELLED, selected[0]),
        )

        val aggregation = EvaluationRunAggregator().aggregate(
            selectedCaseIds = selected,
            categories = listOf(EvaluationDatasetCategoryDefinition(categoryA, "Category A")),
            caseResults = listOf(cancelled),
        )

        assertNull(aggregation.quality.aggregateScore)
        assertEquals(emptyList<Any>(), aggregation.quality.categoryScores)
        assertEquals(1, aggregation.reliability.cancelled)
        assertEquals(1, aggregation.reliability.skipped)
    }

    @Test
    fun `aggregation rejects results outside selected sample`() {
        val selected = listOf(EvaluationCaseId("case-1"))
        val foreign = scored(
            EvaluationCaseId("case-2"),
            categoryA,
            EvaluationOutcome(NormalizedScore(1.0), EvaluatorOutcomeCode.CORRECT),
        )

        assertThrows(IllegalArgumentException::class.java) {
            EvaluationRunAggregator().aggregate(
                selectedCaseIds = selected,
                categories = listOf(EvaluationDatasetCategoryDefinition(categoryA, "Category A")),
                caseResults = listOf(foreign),
            )
        }
    }

    private fun scored(
        caseId: EvaluationCaseId,
        categoryId: EvaluationCategoryId,
        outcome: EvaluationOutcome,
        metrics: EvaluationCaseMetrics = EvaluationCaseMetrics(),
    ) = EvaluationCaseResult(
        caseId = caseId,
        categoryId = categoryId,
        evaluator = evaluator,
        status = EvaluationCaseStatus.SCORED,
        outcome = outcome,
        requestId = null,
        metrics = metrics,
    )

    private fun invalid(caseId: EvaluationCaseId, categoryId: EvaluationCategoryId) = EvaluationCaseResult(
        caseId = caseId,
        categoryId = categoryId,
        evaluator = evaluator,
        status = EvaluationCaseStatus.INVALID_OUTPUT,
        outcome = EvaluationOutcome(NormalizedScore(0.0), EvaluatorOutcomeCode.INVALID_OUTPUT),
        requestId = null,
    )

    private fun failed(
        caseId: EvaluationCaseId,
        categoryId: EvaluationCategoryId,
        status: EvaluationCaseStatus,
        failure: EvaluationFailure,
        metrics: EvaluationCaseMetrics = EvaluationCaseMetrics(),
    ) = EvaluationCaseResult(
        caseId = caseId,
        categoryId = categoryId,
        evaluator = evaluator,
        status = status,
        outcome = null,
        requestId = null,
        metrics = metrics,
        failure = failure,
    )
}
