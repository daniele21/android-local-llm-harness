package io.github.daniele21.localllm.evaluation.evaluators

import io.github.daniele21.localllm.evaluation.EvaluatorOutcomeCode
import io.github.daniele21.localllm.evaluation.EvaluatorSpec
import io.github.daniele21.localllm.evaluation.EvaluatorType
import org.junit.Assert.assertEquals
import org.junit.Test

class MultipleChoiceEvaluatorTest {
    private val evaluator = MultipleChoiceEvaluator()

    @Test
    fun `single allowed standalone label is scored`() {
        val spec = spec("A,B,C,D")

        assertEquals(EvaluatorOutcomeCode.CORRECT, evaluator.evaluate("B", "Answer: B", spec).code)
        assertEquals(EvaluatorOutcomeCode.INCORRECT, evaluator.evaluate("B", "Answer: C", spec).code)
    }

    @Test
    fun `label embedded in a word is not extracted`() {
        val spec = spec("A,B,C,D")

        assertEquals(EvaluatorOutcomeCode.INVALID_OUTPUT, evaluator.evaluate("A", "Answerable", spec).code)
    }

    @Test
    fun `multiple distinct allowed labels are ambiguous`() {
        val spec = spec("A,B,C,D")

        assertEquals(EvaluatorOutcomeCode.AMBIGUOUS_OUTPUT, evaluator.evaluate("A", "A, not B", spec).code)
    }

    @Test
    fun `repeated same label remains unambiguous`() {
        val spec = spec("A,B,C,D")

        assertEquals(EvaluatorOutcomeCode.CORRECT, evaluator.evaluate("A", "A. Final answer: A", spec).code)
    }

    @Test
    fun `case insensitive extraction is explicit`() {
        val spec = spec("A,B,C,D", MultipleChoiceEvaluator.CASE_INSENSITIVE)

        assertEquals(EvaluatorOutcomeCode.CORRECT, evaluator.evaluate("B", "answer: b", spec).code)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `duplicate labels after normalization are rejected`() {
        evaluator.evaluate("A", "A", spec("A,a", MultipleChoiceEvaluator.CASE_INSENSITIVE))
    }

    private fun spec(labels: String, casePolicy: String = MultipleChoiceEvaluator.CASE_SENSITIVE) = EvaluatorSpec(
        type = EvaluatorType.MULTIPLE_CHOICE,
        version = MultipleChoiceEvaluator.VERSION,
        parameters = mapOf(
            MultipleChoiceEvaluator.PARAM_LABELS to labels,
            MultipleChoiceEvaluator.PARAM_CASE to casePolicy,
        ),
    )
}
