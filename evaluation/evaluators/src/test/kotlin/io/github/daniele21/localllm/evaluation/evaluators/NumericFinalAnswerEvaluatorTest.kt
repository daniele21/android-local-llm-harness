package io.github.daniele21.localllm.evaluation.evaluators

import io.github.daniele21.localllm.evaluation.EvaluatorOutcomeCode
import io.github.daniele21.localllm.evaluation.EvaluatorSpec
import io.github.daniele21.localllm.evaluation.EvaluatorType
import org.junit.Assert.assertEquals
import org.junit.Test

class NumericFinalAnswerEvaluatorTest {
    private val evaluator = NumericFinalAnswerEvaluator()

    @Test
    fun `entire extraction parses locale independent decimals`() {
        val spec = spec(NumericFinalAnswerEvaluator.EXTRACTION_ENTIRE)

        assertEquals(EvaluatorOutcomeCode.CORRECT, evaluator.evaluate("42", "42.0", spec).code)
        assertEquals(EvaluatorOutcomeCode.INCORRECT, evaluator.evaluate("42", "41.999", spec).code)
    }

    @Test
    fun `last number extraction scores final numeric token`() {
        val spec = spec(NumericFinalAnswerEvaluator.EXTRACTION_LAST_NUMBER)

        assertEquals(EvaluatorOutcomeCode.CORRECT, evaluator.evaluate("4", "2 + 2 = 4", spec).code)
    }

    @Test
    fun `absolute tolerance is explicit and bounded`() {
        val spec = spec(
            NumericFinalAnswerEvaluator.EXTRACTION_ENTIRE,
            tolerance = "0.01",
        )

        assertEquals(EvaluatorOutcomeCode.CORRECT, evaluator.evaluate("1.0", "1.009", spec).code)
        assertEquals(EvaluatorOutcomeCode.INCORRECT, evaluator.evaluate("1.0", "1.011", spec).code)
    }

    @Test
    fun `scientific notation is parsed deterministically`() {
        val spec = spec(NumericFinalAnswerEvaluator.EXTRACTION_ENTIRE)

        assertEquals(EvaluatorOutcomeCode.CORRECT, evaluator.evaluate("1000", "1e3", spec).code)
    }

    @Test
    fun `decimal comma is rejected as invalid output`() {
        val spec = spec(NumericFinalAnswerEvaluator.EXTRACTION_LAST_NUMBER)

        assertEquals(EvaluatorOutcomeCode.INVALID_OUTPUT, evaluator.evaluate("4", "Final answer: 4,0", spec).code)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative tolerance is rejected`() {
        evaluator.evaluate("1", "1", spec(NumericFinalAnswerEvaluator.EXTRACTION_ENTIRE, tolerance = "-0.1"))
    }

    private fun spec(extraction: String, tolerance: String? = null): EvaluatorSpec {
        val parameters = linkedMapOf(NumericFinalAnswerEvaluator.PARAM_EXTRACTION to extraction)
        if (tolerance != null) parameters[NumericFinalAnswerEvaluator.PARAM_ABSOLUTE_TOLERANCE] = tolerance
        return EvaluatorSpec(
            type = EvaluatorType.NUMERIC_FINAL_ANSWER,
            version = NumericFinalAnswerEvaluator.VERSION,
            parameters = parameters,
        )
    }
}
