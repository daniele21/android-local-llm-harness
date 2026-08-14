package io.github.daniele21.localllm.evaluation.evaluators

import io.github.daniele21.localllm.evaluation.EvaluatorOutcomeCode
import io.github.daniele21.localllm.evaluation.EvaluatorSpec
import io.github.daniele21.localllm.evaluation.EvaluatorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluatorGoldenV1Test {
    @Test
    fun `exact match v1 golden normalization remains stable`() {
        val outcome = ExactMatchEvaluator().evaluate(
            expected = "Local AI",
            generated = "  local   AI  ",
            spec = EvaluatorSpec(
                type = EvaluatorType.EXACT_MATCH,
                version = ExactMatchEvaluator.VERSION,
                parameters = mapOf(
                    ExactMatchEvaluator.PARAM_CASE to ExactMatchEvaluator.CASE_INSENSITIVE,
                    ExactMatchEvaluator.PARAM_WHITESPACE to ExactMatchEvaluator.WHITESPACE_COLLAPSE,
                ),
            ),
        )

        assertEquals(EvaluatorOutcomeCode.CORRECT, outcome.code)
        assertEquals(1.0, outcome.score.value, 0.0)
    }

    @Test
    fun `multiple choice v1 golden label extraction remains stable`() {
        val outcome = MultipleChoiceEvaluator().evaluate(
            expectedLabel = "B",
            generated = "The final answer is B.",
            spec = EvaluatorSpec(
                type = EvaluatorType.MULTIPLE_CHOICE,
                version = MultipleChoiceEvaluator.VERSION,
                parameters = mapOf(
                    MultipleChoiceEvaluator.PARAM_LABELS to "A,B,C,D",
                    MultipleChoiceEvaluator.PARAM_CASE to MultipleChoiceEvaluator.CASE_SENSITIVE,
                ),
            ),
        )

        assertEquals(EvaluatorOutcomeCode.CORRECT, outcome.code)
    }

    @Test
    fun `numeric v1 golden last number and tolerance remain stable`() {
        val outcome = NumericFinalAnswerEvaluator().evaluate(
            expected = "42",
            generated = "intermediate 40; final 42.005",
            spec = EvaluatorSpec(
                type = EvaluatorType.NUMERIC_FINAL_ANSWER,
                version = NumericFinalAnswerEvaluator.VERSION,
                parameters = mapOf(
                    NumericFinalAnswerEvaluator.PARAM_EXTRACTION to NumericFinalAnswerEvaluator.EXTRACTION_LAST_NUMBER,
                    NumericFinalAnswerEvaluator.PARAM_ABSOLUTE_TOLERANCE to "0.01",
                ),
            ),
        )

        assertEquals(EvaluatorOutcomeCode.CORRECT, outcome.code)
    }

    @Test
    fun `json fields v1 golden structural comparison remains stable`() {
        val outcome = JsonFieldsEvaluator().evaluate(
            expected = """{"label":"food","amount":12.5}""",
            generated = """{"amount":12.50,"label":"food","note":"ignored"}""",
            spec = EvaluatorSpec(
                type = EvaluatorType.JSON_FIELDS,
                version = JsonFieldsEvaluator.VERSION,
                parameters = mapOf(JsonFieldsEvaluator.PARAM_REQUIRED_FIELDS to "label,amount"),
            ),
        )

        assertEquals(EvaluatorOutcomeCode.CORRECT, outcome.code)
    }

    @Test
    fun `regex format v1 golden bounded pattern remains stable`() {
        val outcome = RegexFormatEvaluator().evaluate(
            generated = "-42",
            spec = EvaluatorSpec(
                type = EvaluatorType.REGEX_FORMAT,
                version = RegexFormatEvaluator.VERSION,
                parameters = mapOf(
                    RegexFormatEvaluator.PARAM_PATTERN_ID to RegexFormatEvaluator.PATTERN_INTEGER,
                    RegexFormatEvaluator.PARAM_MATCH_MODE to RegexFormatEvaluator.MATCH_FULL,
                ),
            ),
        )

        assertEquals(EvaluatorOutcomeCode.CORRECT, outcome.code)
    }

    @Test
    fun `instruction constraints v1 golden aggregation remains stable`() {
        val outcome = InstructionConstraintsEvaluator().evaluate(
            generated = "RESULT: local AI",
            spec = EvaluatorSpec(
                type = EvaluatorType.INSTRUCTION_CONSTRAINTS,
                version = InstructionConstraintsEvaluator.VERSION,
                parameters = mapOf(
                    InstructionConstraintsEvaluator.PARAM_CONSTRAINTS to "non_empty,single_line,starts_with,max_words",
                    InstructionConstraintsEvaluator.PARAM_CASE to InstructionConstraintsEvaluator.CASE_SENSITIVE,
                    InstructionConstraintsEvaluator.PARAM_STARTS_WITH_TEXT to "RESULT:",
                    InstructionConstraintsEvaluator.PARAM_MAX_WORDS to "3",
                ),
            ),
        )

        assertEquals(EvaluatorOutcomeCode.CORRECT, outcome.code)
    }

    @Test
    fun `registry golden fixture exposes every evaluator v1 registration`() {
        val registry = EvaluatorRegistry(v1Registrations())

        assertEquals(6, registry.supportedKeys().size)
        v1Registrations().forEach { registration ->
            assertTrue(registration.key in registry.supportedKeys())
        }
    }
}

private fun v1Registrations() = listOf(
    ExactMatchEvaluator.REGISTRATION,
    MultipleChoiceEvaluator.REGISTRATION,
    NumericFinalAnswerEvaluator.REGISTRATION,
    JsonFieldsEvaluator.REGISTRATION,
    RegexFormatEvaluator.REGISTRATION,
    InstructionConstraintsEvaluator.REGISTRATION,
)
