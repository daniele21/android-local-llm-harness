package io.github.daniele21.localllm.evaluation.evaluators

import io.github.daniele21.localllm.evaluation.EvaluatorOutcomeCode
import io.github.daniele21.localllm.evaluation.EvaluatorSpec
import io.github.daniele21.localllm.evaluation.EvaluatorType
import org.junit.Assert.assertEquals
import org.junit.Test

class JsonFieldsEvaluatorTest {
    private val evaluator = JsonFieldsEvaluator()

    @Test
    fun `all required fields correct scores one and ignores extra fields`() {
        val outcome = evaluator.evaluate(
            expected = """{"label":"food","amount":12.5}""",
            generated = """{"amount":12.50,"label":"food","explanation":"ok"}""",
            spec = spec("label,amount"),
        )

        assertEquals(EvaluatorOutcomeCode.CORRECT, outcome.code)
        assertEquals(1.0, outcome.score.value, 0.0)
    }

    @Test
    fun `subset of required fields correct receives transparent partial score`() {
        val outcome = evaluator.evaluate(
            expected = """{"label":"food","amount":12.5}""",
            generated = """{"label":"food","amount":9}""",
            spec = spec("label,amount"),
        )

        assertEquals(EvaluatorOutcomeCode.PARTIAL, outcome.code)
        assertEquals(0.5, outcome.score.value, 0.0)
    }

    @Test
    fun `missing or incorrect required fields score incorrect`() {
        val outcome = evaluator.evaluate(
            expected = """{"label":"food","amount":12.5}""",
            generated = """{"other":true}""",
            spec = spec("label,amount"),
        )

        assertEquals(EvaluatorOutcomeCode.INCORRECT, outcome.code)
        assertEquals(0.0, outcome.score.value, 0.0)
    }

    @Test
    fun `malformed generated JSON is invalid output`() {
        val outcome = evaluator.evaluate(
            expected = """{"label":"food"}""",
            generated = """{"label":"food",}""",
            spec = spec("label"),
        )

        assertEquals(EvaluatorOutcomeCode.INVALID_OUTPUT, outcome.code)
    }

    @Test
    fun `nested objects arrays escapes and numeric scale compare structurally`() {
        val outcome = evaluator.evaluate(
            expected = """{"payload":{"items":[1,2.0,"a\n"]}}""",
            generated = """{"payload":{"items":[1.0,2,"a\u000a"]}}""",
            spec = spec("payload"),
        )

        assertEquals(EvaluatorOutcomeCode.CORRECT, outcome.code)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `required field absent from expected object is invalid dataset configuration`() {
        evaluator.evaluate(
            expected = """{"label":"food"}""",
            generated = """{"label":"food"}""",
            spec = spec("label,amount"),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `duplicate required fields are rejected`() {
        evaluator.evaluate(
            expected = """{"label":"food"}""",
            generated = """{"label":"food"}""",
            spec = spec("label,label"),
        )
    }

    @Test
    fun `duplicate generated object keys are invalid output`() {
        val outcome = evaluator.evaluate(
            expected = """{"label":"food"}""",
            generated = """{"label":"food","label":"other"}""",
            spec = spec("label"),
        )

        assertEquals(EvaluatorOutcomeCode.INVALID_OUTPUT, outcome.code)
    }

    private fun spec(requiredFields: String) = EvaluatorSpec(
        type = EvaluatorType.JSON_FIELDS,
        version = JsonFieldsEvaluator.VERSION,
        parameters = mapOf(JsonFieldsEvaluator.PARAM_REQUIRED_FIELDS to requiredFields),
    )
}
