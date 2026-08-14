package io.github.daniele21.localllm.evaluation.evaluators

import io.github.daniele21.localllm.evaluation.EvaluatorOutcomeCode
import io.github.daniele21.localllm.evaluation.EvaluatorSpec
import io.github.daniele21.localllm.evaluation.EvaluatorType
import org.junit.Assert.assertEquals
import org.junit.Test

class RegexFormatEvaluatorTest {
    private val evaluator = RegexFormatEvaluator()

    @Test
    fun `full mode requires the complete output to match`() {
        val spec = spec(RegexFormatEvaluator.PATTERN_INTEGER, RegexFormatEvaluator.MATCH_FULL)

        assertEquals(EvaluatorOutcomeCode.CORRECT, evaluator.evaluate("42", spec).code)
        assertEquals(EvaluatorOutcomeCode.CONSTRAINT_VIOLATION, evaluator.evaluate("answer 42", spec).code)
    }

    @Test
    fun `find mode accepts a repository-defined pattern inside output`() {
        val spec = spec(RegexFormatEvaluator.PATTERN_INTEGER, RegexFormatEvaluator.MATCH_FIND)

        assertEquals(EvaluatorOutcomeCode.CORRECT, evaluator.evaluate("answer 42", spec).code)
    }

    @Test
    fun `single line pattern rejects newline output`() {
        val spec = spec(RegexFormatEvaluator.PATTERN_SINGLE_LINE_NON_EMPTY, RegexFormatEvaluator.MATCH_FULL)

        assertEquals(EvaluatorOutcomeCode.CORRECT, evaluator.evaluate("one line", spec).code)
        assertEquals(EvaluatorOutcomeCode.CONSTRAINT_VIOLATION, evaluator.evaluate("one\ntwo", spec).code)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `arbitrary dataset supplied regex is rejected`() {
        evaluator.evaluate(
            "anything",
            EvaluatorSpec(
                type = EvaluatorType.REGEX_FORMAT,
                version = RegexFormatEvaluator.VERSION,
                parameters = mapOf(
                    RegexFormatEvaluator.PARAM_PATTERN_ID to ".*",
                    RegexFormatEvaluator.PARAM_MATCH_MODE to RegexFormatEvaluator.MATCH_FULL,
                ),
            ),
        )
    }

    private fun spec(patternId: String, matchMode: String) = EvaluatorSpec(
        type = EvaluatorType.REGEX_FORMAT,
        version = RegexFormatEvaluator.VERSION,
        parameters = mapOf(
            RegexFormatEvaluator.PARAM_PATTERN_ID to patternId,
            RegexFormatEvaluator.PARAM_MATCH_MODE to matchMode,
        ),
    )
}
