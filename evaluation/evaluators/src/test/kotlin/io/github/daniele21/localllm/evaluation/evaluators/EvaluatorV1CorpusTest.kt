package io.github.daniele21.localllm.evaluation.evaluators

import io.github.daniele21.localllm.evaluation.EvaluationFailureCode
import io.github.daniele21.localllm.evaluation.EvaluatorSpec
import io.github.daniele21.localllm.evaluation.EvaluatorType
import io.github.daniele21.localllm.evaluation.EvaluatorVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluatorV1CorpusTest {
    @Test
    fun `corpus covers every output shape for every evaluator v1`() {
        assertEquals(24, EvaluatorV1Corpus.cases.size)
        assertEquals(EvaluatorV1Corpus.cases.size, EvaluatorV1Corpus.cases.map { it.id }.distinct().size)

        val expectedTypes =
            setOf(
                EvaluatorType.EXACT_MATCH,
                EvaluatorType.MULTIPLE_CHOICE,
                EvaluatorType.NUMERIC_FINAL_ANSWER,
                EvaluatorType.JSON_FIELDS,
                EvaluatorType.REGEX_FORMAT,
                EvaluatorType.INSTRUCTION_CONSTRAINTS,
            )
        assertEquals(expectedTypes, EvaluatorV1Corpus.cases.map { it.spec.type }.toSet())
        expectedTypes.forEach { type ->
            assertEquals(
                "Missing output shapes for $type",
                EvaluatorOutputShape.entries.toSet(),
                EvaluatorV1Corpus.cases.filter { it.spec.type == type }.map { it.shape }.toSet(),
            )
        }
    }

    @Test
    fun `corpus outcomes and scores remain deterministic`() {
        EvaluatorV1Corpus.cases.forEach { fixture ->
            val first = evaluateCorpusCase(fixture)
            val second = evaluateCorpusCase(fixture)

            assertEquals(fixture.id, fixture.expectedCode, first.code)
            assertEquals(fixture.id, fixture.expectedScore, first.score.value, 0.0)
            assertEquals("${fixture.id} changed between identical evaluations", first, second)
        }
    }

    @Test
    fun `registry fixture exposes every corpus evaluator registration`() {
        val registry = EvaluatorRegistry(v1CorpusRegistrations())

        assertEquals(6, registry.supportedKeys().size)
        EvaluatorV1Corpus.cases.forEach { fixture ->
            assertTrue(
                EvaluatorKey(fixture.spec.type, fixture.spec.version) in registry.supportedKeys(),
            )
            assertTrue(registry.resolve(fixture.spec) is EvaluatorLookupResult.Supported)
        }
    }

    @Test
    fun `registry rejects unknown evaluator version fail closed`() {
        val result =
            EvaluatorRegistry(v1CorpusRegistrations()).resolve(
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

    @Test(expected = IllegalArgumentException::class)
    fun `instruction constraints reject invalid spec outside output corpus`() {
        InstructionConstraintsEvaluator().evaluate(
            generated = "answer",
            spec =
                EvaluatorSpec(
                    type = EvaluatorType.INSTRUCTION_CONSTRAINTS,
                    version = InstructionConstraintsEvaluator.VERSION,
                    parameters = mapOf(
                        InstructionConstraintsEvaluator.PARAM_CONSTRAINTS to "non_empty,non_empty",
                        InstructionConstraintsEvaluator.PARAM_CASE to InstructionConstraintsEvaluator.CASE_SENSITIVE,
                    ),
                ),
        )
    }
}

private fun v1CorpusRegistrations() =
    listOf(
        ExactMatchEvaluator.REGISTRATION,
        MultipleChoiceEvaluator.REGISTRATION,
        NumericFinalAnswerEvaluator.REGISTRATION,
        JsonFieldsEvaluator.REGISTRATION,
        RegexFormatEvaluator.REGISTRATION,
        InstructionConstraintsEvaluator.REGISTRATION,
    )
