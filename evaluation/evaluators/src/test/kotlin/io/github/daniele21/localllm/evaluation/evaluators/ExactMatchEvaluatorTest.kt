package io.github.daniele21.localllm.evaluation.evaluators

import io.github.daniele21.localllm.evaluation.EvaluatorOutcomeCode
import io.github.daniele21.localllm.evaluation.EvaluatorSpec
import io.github.daniele21.localllm.evaluation.EvaluatorType
import org.junit.Assert.assertEquals
import org.junit.Test

class ExactMatchEvaluatorTest {
    private val evaluator = ExactMatchEvaluator()

    @Test
    fun `exact sensitive policy preserves case and whitespace`() {
        val spec = spec(casePolicy = ExactMatchEvaluator.CASE_SENSITIVE, whitespace = ExactMatchEvaluator.WHITESPACE_EXACT)

        assertEquals(EvaluatorOutcomeCode.CORRECT, evaluator.evaluate("Answer", "Answer", spec).code)
        assertEquals(EvaluatorOutcomeCode.INCORRECT, evaluator.evaluate("Answer", "answer", spec).code)
        assertEquals(EvaluatorOutcomeCode.INCORRECT, evaluator.evaluate("Answer", " Answer ", spec).code)
    }

    @Test
    fun `trim policy removes edge whitespace only`() {
        val spec = spec(casePolicy = ExactMatchEvaluator.CASE_SENSITIVE, whitespace = ExactMatchEvaluator.WHITESPACE_TRIM)

        assertEquals(EvaluatorOutcomeCode.CORRECT, evaluator.evaluate("Answer", "  Answer\n", spec).code)
        assertEquals(EvaluatorOutcomeCode.INCORRECT, evaluator.evaluate("A B", "A   B", spec).code)
    }

    @Test
    fun `collapse policy normalizes whitespace runs`() {
        val spec = spec(casePolicy = ExactMatchEvaluator.CASE_SENSITIVE, whitespace = ExactMatchEvaluator.WHITESPACE_COLLAPSE)

        assertEquals(EvaluatorOutcomeCode.CORRECT, evaluator.evaluate("A B", " A\n\tB ", spec).code)
    }

    @Test
    fun `case insensitive policy uses deterministic root casing`() {
        val spec = spec(casePolicy = ExactMatchEvaluator.CASE_INSENSITIVE, whitespace = ExactMatchEvaluator.WHITESPACE_EXACT)

        assertEquals(EvaluatorOutcomeCode.CORRECT, evaluator.evaluate("ANSWER", "answer", spec).code)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `missing normalization policy is rejected`() {
        evaluator.evaluate(
            "answer",
            "answer",
            EvaluatorSpec(
                type = EvaluatorType.EXACT_MATCH,
                version = ExactMatchEvaluator.VERSION,
                parameters = mapOf(ExactMatchEvaluator.PARAM_CASE to ExactMatchEvaluator.CASE_SENSITIVE),
            ),
        )
    }

    private fun spec(casePolicy: String, whitespace: String) = EvaluatorSpec(
        type = EvaluatorType.EXACT_MATCH,
        version = ExactMatchEvaluator.VERSION,
        parameters = mapOf(
            ExactMatchEvaluator.PARAM_CASE to casePolicy,
            ExactMatchEvaluator.PARAM_WHITESPACE to whitespace,
        ),
    )
}
