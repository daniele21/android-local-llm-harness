package io.github.daniele21.localllm.evaluation.evaluators

import io.github.daniele21.localllm.evaluation.EvaluationCaseId
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
import org.junit.Test

class EvaluationQualityAggregatorTest {
    private val aggregator = EvaluationQualityAggregator()

    @Test
    fun `weighted suite aggregate uses manifest category weights`() {
        val categories = listOf(category("knowledge", 0.75), category("format", 0.25))
        val results = listOf(
            result("k1", "knowledge", score = 1.0),
            result("k2", "knowledge", score = 0.0),
            result("f1", "format", score = 1.0),
        )

        val summary = aggregator.aggregate(categories, results)

        assertEquals(0.625, summary.aggregateScore!!.value, 0.0)
        assertEquals(0.5, summary.categoryScores[0].score.value, 0.0)
        assertEquals(2, summary.categoryScores[0].scoredCaseCount)
        assertEquals(1.0, summary.categoryScores[1].score.value, 0.0)
    }

    @Test
    fun `unweighted categories use arithmetic category mean`() {
        val categories = listOf(category("a"), category("b"))
        val results = listOf(
            result("a1", "a", score = 1.0),
            result("a2", "a", score = 1.0),
            result("b1", "b", score = 0.0),
        )

        val summary = aggregator.aggregate(categories, results)

        assertEquals(0.5, summary.aggregateScore!!.value, 0.0)
    }

    @Test
    fun `invalid output timeout and runtime failure contribute zero quality`() {
        val category = category("reliability-sensitive")
        val results = listOf(
            result("ok", category.id.value, score = 1.0),
            result("invalid", category.id.value, status = EvaluationCaseStatus.INVALID_OUTPUT),
            result("timeout", category.id.value, status = EvaluationCaseStatus.TIMEOUT),
            result("runtime", category.id.value, status = EvaluationCaseStatus.RUNTIME_FAILURE),
        )

        val summary = aggregator.aggregate(listOf(category), results)

        assertEquals(0.25, summary.aggregateScore!!.value, 0.0)
        assertEquals(4, summary.categoryScores.single().scoredCaseCount)
    }

    @Test
    fun `cancelled cases are excluded and weighted aggregate renormalizes`() {
        val categories = listOf(category("a", 0.8), category("b", 0.2))
        val results = listOf(
            result("a1", "a", score = 1.0),
            result("b1", "b", status = EvaluationCaseStatus.CANCELLED),
        )

        val summary = aggregator.aggregate(categories, results)

        assertEquals(1.0, summary.aggregateScore!!.value, 0.0)
        assertEquals(listOf(EvaluationCategoryId("a")), summary.categoryScores.map { it.categoryId })
    }

    @Test
    fun `all cancelled cases produce no aggregate quality`() {
        val summary = aggregator.aggregate(
            categories = listOf(category("a", 1.0)),
            caseResults = listOf(result("a1", "a", status = EvaluationCaseStatus.CANCELLED)),
        )

        assertNull(summary.aggregateScore)
        assertEquals(emptyList<Any>(), summary.categoryScores)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unknown result category is rejected`() {
        aggregator.aggregate(
            categories = listOf(category("declared")),
            caseResults = listOf(result("case", "unknown", score = 1.0)),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `duplicate case ids are rejected`() {
        aggregator.aggregate(
            categories = listOf(category("a")),
            caseResults = listOf(result("same", "a", score = 1.0), result("same", "a", score = 0.0)),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `mixed weight declarations among scored categories are rejected`() {
        aggregator.aggregate(
            categories = listOf(category("weighted", 0.5), category("unweighted")),
            caseResults = listOf(result("w1", "weighted", score = 1.0), result("u1", "unweighted", score = 1.0)),
        )
    }
}

private fun category(id: String, weight: Double? = null) = EvaluationDatasetCategoryDefinition(
    id = EvaluationCategoryId(id),
    displayName = id,
    weight = weight,
)

private fun result(
    caseId: String,
    categoryId: String,
    status: EvaluationCaseStatus = EvaluationCaseStatus.SCORED,
    score: Double? = null,
): EvaluationCaseResult {
    val id = EvaluationCaseId(caseId)
    val outcome = when (status) {
        EvaluationCaseStatus.SCORED -> EvaluationOutcome(
            score = NormalizedScore(requireNotNull(score)),
            code = if (score == 1.0) EvaluatorOutcomeCode.CORRECT else EvaluatorOutcomeCode.INCORRECT,
        )

        EvaluationCaseStatus.INVALID_OUTPUT -> EvaluationOutcome(
            score = NormalizedScore(0.0),
            code = EvaluatorOutcomeCode.INVALID_OUTPUT,
        )

        else -> null
    }
    val failure = when (status) {
        EvaluationCaseStatus.TIMEOUT -> EvaluationFailure(
            stage = EvaluationFailureStage.GENERATION,
            code = EvaluationFailureCode.CASE_TIMEOUT,
            caseId = id,
        )

        EvaluationCaseStatus.RUNTIME_FAILURE -> EvaluationFailure(
            stage = EvaluationFailureStage.GENERATION,
            code = EvaluationFailureCode.RUNTIME_FAILURE,
            caseId = id,
        )

        EvaluationCaseStatus.CANCELLED -> EvaluationFailure(
            stage = EvaluationFailureStage.CANCELLATION,
            code = EvaluationFailureCode.CANCELLED,
            caseId = id,
        )

        else -> null
    }
    return EvaluationCaseResult(
        caseId = id,
        categoryId = EvaluationCategoryId(categoryId),
        evaluator = EvaluatorSpec(EvaluatorType.EXACT_MATCH, EvaluatorVersion(1)),
        status = status,
        outcome = outcome,
        requestId = null,
        failure = failure,
    )
}
