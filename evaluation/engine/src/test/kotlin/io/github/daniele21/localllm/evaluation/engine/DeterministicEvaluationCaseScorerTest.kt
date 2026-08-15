package io.github.daniele21.localllm.evaluation.engine

import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.evaluation.EvaluationCaseId
import io.github.daniele21.localllm.evaluation.EvaluationCaseMessage
import io.github.daniele21.localllm.evaluation.EvaluationCaseStatus
import io.github.daniele21.localllm.evaluation.EvaluationCategoryId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetCaseV1
import io.github.daniele21.localllm.evaluation.EvaluationExpectedAnswer
import io.github.daniele21.localllm.evaluation.EvaluationExpectedAnswerKind
import io.github.daniele21.localllm.evaluation.EvaluationFailureCode
import io.github.daniele21.localllm.evaluation.EvaluationFailureStage
import io.github.daniele21.localllm.evaluation.EvaluationMessageRole
import io.github.daniele21.localllm.evaluation.EvaluatorOutcomeCode
import io.github.daniele21.localllm.evaluation.EvaluatorSpec
import io.github.daniele21.localllm.evaluation.EvaluatorType
import io.github.daniele21.localllm.evaluation.evaluators.ExactMatchEvaluator
import io.github.daniele21.localllm.evaluation.evaluators.MultipleChoiceEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeterministicEvaluationCaseScorerTest {
    private val scorer = DeterministicEvaluationCaseScorer()
    private val requestId = RequestId("request-1")

    @Test
    fun `correct deterministic output becomes scored case result`() {
        val result = scorer.score(
            case = case(
                expected = "Paris",
                evaluator = EvaluatorSpec(
                    type = EvaluatorType.EXACT_MATCH,
                    version = ExactMatchEvaluator.VERSION,
                    parameters = mapOf(
                        ExactMatchEvaluator.PARAM_CASE to ExactMatchEvaluator.CASE_SENSITIVE,
                        ExactMatchEvaluator.PARAM_WHITESPACE to ExactMatchEvaluator.WHITESPACE_EXACT,
                    ),
                ),
            ),
            requestId = requestId,
            generated = "Paris",
        )

        assertEquals(EvaluationCaseStatus.SCORED, result.status)
        assertEquals(EvaluatorOutcomeCode.CORRECT, result.outcome?.code)
        assertEquals(1.0, result.outcome?.score?.value ?: -1.0, 0.0)
        assertEquals(requestId, result.requestId)
        assertNull(result.failure)
    }

    @Test
    fun `ambiguous deterministic output becomes invalid output case result`() {
        val result = scorer.score(
            case = case(
                expected = "A",
                expectedKind = EvaluationExpectedAnswerKind.LABEL,
                evaluator = EvaluatorSpec(
                    type = EvaluatorType.MULTIPLE_CHOICE,
                    version = MultipleChoiceEvaluator.VERSION,
                    parameters = mapOf(
                        MultipleChoiceEvaluator.PARAM_LABELS to "A,B,C,D",
                        MultipleChoiceEvaluator.PARAM_CASE_SENSITIVE to MultipleChoiceEvaluator.CASE_SENSITIVE,
                    ),
                ),
            ),
            requestId = requestId,
            generated = "A or B",
        )

        assertEquals(EvaluationCaseStatus.INVALID_OUTPUT, result.status)
        assertEquals(EvaluatorOutcomeCode.AMBIGUOUS_OUTPUT, result.outcome?.code)
        assertEquals(0.0, result.outcome?.score?.value ?: -1.0, 0.0)
        assertNull(result.failure)
    }

    @Test
    fun `unexpected evaluator configuration failure becomes typed evaluation failure`() {
        val result = scorer.score(
            case = case(
                expected = "Paris",
                evaluator = EvaluatorSpec(
                    type = EvaluatorType.EXACT_MATCH,
                    version = ExactMatchEvaluator.VERSION,
                    parameters = emptyMap(),
                ),
            ),
            requestId = requestId,
            generated = "Paris",
        )

        assertEquals(EvaluationCaseStatus.RUNTIME_FAILURE, result.status)
        assertNull(result.outcome)
        assertEquals(EvaluationFailureStage.EVALUATION, result.failure?.stage)
        assertEquals(EvaluationFailureCode.EVALUATOR_FAILURE, result.failure?.code)
        assertEquals(requestId, result.requestId)
    }

    private fun case(
        expected: String,
        evaluator: EvaluatorSpec,
        expectedKind: EvaluationExpectedAnswerKind = EvaluationExpectedAnswerKind.TEXT,
    ) = EvaluationDatasetCaseV1(
        id = EvaluationCaseId("case-1"),
        categoryId = EvaluationCategoryId("general"),
        messages = listOf(EvaluationCaseMessage(EvaluationMessageRole.USER, "Question")),
        expected = EvaluationExpectedAnswer(expectedKind, expected),
        evaluator = evaluator,
    )
}
