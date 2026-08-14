package io.github.daniele21.localllm.evaluation.evaluators

import io.github.daniele21.localllm.evaluation.EvaluationFailureCode
import io.github.daniele21.localllm.evaluation.EvaluatorOutcomeCode
import io.github.daniele21.localllm.evaluation.EvaluatorSpec
import io.github.daniele21.localllm.evaluation.EvaluatorType
import io.github.daniele21.localllm.evaluation.EvaluatorVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluatorAdversarialV1Test {
    @Test
    fun `exact match v1 does not collapse whitespace unless declared`() {
        val outcome = ExactMatchEvaluator().evaluate(
            expected = "A B",
            generated = "A  B",
            spec = EvaluatorSpec(
                type = EvaluatorType.EXACT_MATCH,
                version = ExactMatchEvaluator.VERSION,
                parameters = mapOf(
                    ExactMatchEvaluator.PARAM_CASE to ExactMatchEvaluator.CASE_SENSITIVE,
                    ExactMatchEvaluator.PARAM_WHITESPACE to ExactMatchEvaluator.WHITESPACE_EXACT,
                ),
            ),
        )

        assertEquals(EvaluatorOutcomeCode.INCORRECT, outcome.code)
    }

    @Test
    fun `multiple choice v1 rejects answers containing two allowed labels`() {
        val outcome = MultipleChoiceEvaluator().evaluate(
            expectedLabel = "A",
            generated = "Either A or B could be correct.",
            spec = EvaluatorSpec(
                type = EvaluatorType.MULTIPLE_CHOICE,
                version = MultipleChoiceEvaluator.VERSION,
                parameters = mapOf(
                    MultipleChoiceEvaluator.PARAM_LABELS to "A,B,C,D",
                    MultipleChoiceEvaluator.PARAM_CASE to MultipleChoiceEvaluator.CASE_SENSITIVE,
                ),
            ),
        )

        assertEquals(EvaluatorOutcomeCode.AMBIGUOUS_OUTPUT, outcome.code)
    }

    @Test
    fun `numeric v1 rejects decimal comma as ambiguous locale text`() {
        val outcome = NumericFinalAnswerEvaluator().evaluate(
            expected = "12.5",
            generated = "12,5",
            spec = EvaluatorSpec(
                type = EvaluatorType.NUMERIC_FINAL_ANSWER,
                version = NumericFinalAnswerEvaluator.VERSION,
                parameters = mapOf(
                    NumericFinalAnswerEvaluator.PARAM_EXTRACTION to NumericFinalAnswerEvaluator.EXTRACTION_ENTIRE,
                ),
            ),
        )

        assertEquals(EvaluatorOutcomeCode.INVALID_OUTPUT, outcome.code)
    }

    @Test
    fun `json fields v1 rejects duplicate generated object keys`() {
        val outcome = JsonFieldsEvaluator().evaluate(
            expected = """{"label":"food"}""",
            generated = """{"label":"food","label":"other"}""",
            spec = EvaluatorSpec(
                type = EvaluatorType.JSON_FIELDS,
                version = JsonFieldsEvaluator.VERSION,
                parameters = mapOf(JsonFieldsEvaluator.PARAM_REQUIRED_FIELDS to "label"),
            ),
        )

        assertEquals(EvaluatorOutcomeCode.INVALID_OUTPUT, outcome.code)
    }

    @Test
    fun `regex format v1 cannot accept text outside repository pattern`() {
        val outcome = RegexFormatEvaluator().evaluate(
            generated = "42 units",
            spec = EvaluatorSpec(
                type = EvaluatorType.REGEX_FORMAT,
                version = RegexFormatEvaluator.VERSION,
                parameters = mapOf(
                    RegexFormatEvaluator.PARAM_PATTERN_ID to RegexFormatEvaluator.PATTERN_INTEGER,
                    RegexFormatEvaluator.PARAM_MATCH_MODE to RegexFormatEvaluator.MATCH_FULL,
                ),
            ),
        )

        assertEquals(EvaluatorOutcomeCode.CONSTRAINT_VIOLATION, outcome.code)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `instruction constraints v1 rejects duplicate constraint ids`() {
        InstructionConstraintsEvaluator().evaluate(
            generated = "answer",
            spec = EvaluatorSpec(
                type = EvaluatorType.INSTRUCTION_CONSTRAINTS,
                version = InstructionConstraintsEvaluator.VERSION,
                parameters = mapOf(
                    InstructionConstraintsEvaluator.PARAM_CONSTRAINTS to "non_empty,non_empty",
                    InstructionConstraintsEvaluator.PARAM_CASE to InstructionConstraintsEvaluator.CASE_SENSITIVE,
                ),
            ),
        )
    }

    @Test
    fun `registry rejects unknown evaluator version fail closed`() {
        val result = EvaluatorRegistry(v1AdversarialRegistrations()).resolve(
            EvaluatorSpec(
                type = EvaluatorType.EXACT_MATCH,
                version = EvaluatorVersion(2),
                parameters = mapOf(
                    ExactMatchEvaluator.PARAM_CASE to ExactMatchEvaluator.CASE_SENSITIVE,
                    ExactMatchEvaluator.PARAM_WHITESPACE to ExactMatchEvaluator.WHITESPACE_EXACT,
                ),
            ),
        )

        assertTrue(result is EvaluatorLookupResult.Rejected)
        assertEquals(
            EvaluationFailureCode.UNKNOWN_EVALUATOR,
            (result as EvaluatorLookupResult.Rejected).failure.code,
        )
    }
}

private fun v1AdversarialRegistrations() = listOf(
    ExactMatchEvaluator.REGISTRATION,
    MultipleChoiceEvaluator.REGISTRATION,
    NumericFinalAnswerEvaluator.REGISTRATION,
    JsonFieldsEvaluator.REGISTRATION,
    RegexFormatEvaluator.REGISTRATION,
    InstructionConstraintsEvaluator.REGISTRATION,
)
