package io.github.daniele21.localllm.evaluation.evaluators

import io.github.daniele21.localllm.evaluation.EvaluatorOutcomeCode
import io.github.daniele21.localllm.evaluation.EvaluatorSpec
import io.github.daniele21.localllm.evaluation.EvaluatorType
import org.junit.Assert.assertEquals
import org.junit.Test

class InstructionConstraintsEvaluatorTest {
    private val evaluator = InstructionConstraintsEvaluator()

    @Test
    fun `all declared constraints satisfied scores one`() {
        val outcome = evaluator.evaluate(
            generated = "RESULT: local AI",
            spec = spec(
                constraints = "non_empty,single_line,starts_with,contains,excludes,max_words",
                extras = mapOf(
                    InstructionConstraintsEvaluator.PARAM_STARTS_WITH_TEXT to "RESULT:",
                    InstructionConstraintsEvaluator.PARAM_CONTAINS_TEXT to "local",
                    InstructionConstraintsEvaluator.PARAM_EXCLUDES_TEXT to "cloud",
                    InstructionConstraintsEvaluator.PARAM_MAX_WORDS to "3",
                ),
            ),
        )

        assertEquals(EvaluatorOutcomeCode.CORRECT, outcome.code)
        assertEquals(1.0, outcome.score.value, 0.0)
    }

    @Test
    fun `partially satisfied constraints expose transparent ratio`() {
        val outcome = evaluator.evaluate(
            generated = "hello world",
            spec = spec(
                constraints = "starts_with,contains,max_words",
                extras = mapOf(
                    InstructionConstraintsEvaluator.PARAM_STARTS_WITH_TEXT to "hello",
                    InstructionConstraintsEvaluator.PARAM_CONTAINS_TEXT to "missing",
                    InstructionConstraintsEvaluator.PARAM_MAX_WORDS to "2",
                ),
            ),
        )

        assertEquals(EvaluatorOutcomeCode.PARTIAL, outcome.code)
        assertEquals(2.0 / 3.0, outcome.score.value, 0.0)
    }

    @Test
    fun `zero satisfied constraints yields constraint violation`() {
        val outcome = evaluator.evaluate(
            generated = "two lines\nhere",
            spec = spec(
                constraints = "single_line,contains",
                extras = mapOf(InstructionConstraintsEvaluator.PARAM_CONTAINS_TEXT to "absent"),
            ),
        )

        assertEquals(EvaluatorOutcomeCode.CONSTRAINT_VIOLATION, outcome.code)
        assertEquals(0.0, outcome.score.value, 0.0)
    }

    @Test
    fun `case insensitive text checks are explicit`() {
        val outcome = evaluator.evaluate(
            generated = "PREFIX Answer END",
            spec = spec(
                constraints = "starts_with,ends_with,contains",
                casePolicy = InstructionConstraintsEvaluator.CASE_INSENSITIVE,
                extras = mapOf(
                    InstructionConstraintsEvaluator.PARAM_STARTS_WITH_TEXT to "prefix",
                    InstructionConstraintsEvaluator.PARAM_ENDS_WITH_TEXT to "end",
                    InstructionConstraintsEvaluator.PARAM_CONTAINS_TEXT to "answer",
                ),
            ),
        )

        assertEquals(EvaluatorOutcomeCode.CORRECT, outcome.code)
    }

    @Test
    fun `word and line counts are deterministic`() {
        val outcome = evaluator.evaluate(
            generated = "one two\r\nthree four",
            spec = spec(
                constraints = "min_words,max_words,exact_lines",
                extras = mapOf(
                    InstructionConstraintsEvaluator.PARAM_MIN_WORDS to "4",
                    InstructionConstraintsEvaluator.PARAM_MAX_WORDS to "4",
                    InstructionConstraintsEvaluator.PARAM_EXACT_LINES to "2",
                ),
            ),
        )

        assertEquals(EvaluatorOutcomeCode.CORRECT, outcome.code)
    }

    @Test
    fun `format constraint delegates to bounded regex evaluator`() {
        val outcome = evaluator.evaluate(
            generated = "42",
            spec = spec(
                constraints = "format",
                extras = mapOf(
                    InstructionConstraintsEvaluator.PARAM_FORMAT_PATTERN_ID to RegexFormatEvaluator.PATTERN_INTEGER,
                    InstructionConstraintsEvaluator.PARAM_FORMAT_MATCH_MODE to RegexFormatEvaluator.MATCH_FULL,
                ),
            ),
        )

        assertEquals(EvaluatorOutcomeCode.CORRECT, outcome.code)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unknown constraint id is rejected`() {
        evaluator.evaluate("answer", spec(constraints = "execute_script"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `selected constraint requires its declared parameter`() {
        evaluator.evaluate("answer", spec(constraints = "contains"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parameters for unselected constraints are rejected`() {
        evaluator.evaluate(
            "answer",
            spec(
                constraints = "non_empty",
                extras = mapOf(InstructionConstraintsEvaluator.PARAM_CONTAINS_TEXT to "answer"),
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `count constraints reject unbounded values`() {
        evaluator.evaluate(
            "answer",
            spec(
                constraints = "max_words",
                extras = mapOf(InstructionConstraintsEvaluator.PARAM_MAX_WORDS to "100001"),
            ),
        )
    }

    private fun spec(
        constraints: String,
        casePolicy: String = InstructionConstraintsEvaluator.CASE_SENSITIVE,
        extras: Map<String, String> = emptyMap(),
    ) = EvaluatorSpec(
        type = EvaluatorType.INSTRUCTION_CONSTRAINTS,
        version = InstructionConstraintsEvaluator.VERSION,
        parameters = linkedMapOf(
            InstructionConstraintsEvaluator.PARAM_CONSTRAINTS to constraints,
            InstructionConstraintsEvaluator.PARAM_CASE to casePolicy,
        ) + extras,
    )
}
