package io.github.daniele21.localllm.evaluation.evaluators

import io.github.daniele21.localllm.evaluation.EvaluationOutcome
import io.github.daniele21.localllm.evaluation.EvaluatorOutcomeCode
import io.github.daniele21.localllm.evaluation.EvaluatorSpec
import io.github.daniele21.localllm.evaluation.EvaluatorType
import io.github.daniele21.localllm.evaluation.EvaluatorVersion
import io.github.daniele21.localllm.evaluation.NormalizedScore
import java.math.BigDecimal

class NumericFinalAnswerEvaluator {
    fun evaluate(expected: String, generated: String, spec: EvaluatorSpec): EvaluationOutcome {
        require(spec.type == EvaluatorType.NUMERIC_FINAL_ANSWER && spec.version == VERSION) {
            "Numeric evaluator requires NUMERIC_FINAL_ANSWER v${VERSION.value} spec"
        }
        require(REGISTRATION.parameters.validate(spec)) { "Invalid numeric evaluator parameters" }

        val expectedValue = parseCanonical(expected)
            ?: throw IllegalArgumentException("Expected numeric answer must be locale-independent decimal text")
        val tolerance = parseTolerance(spec.parameters[PARAM_ABSOLUTE_TOLERANCE])
        val actualValue = extractGenerated(generated, spec.parameters.getValue(PARAM_EXTRACTION))
            ?: return EvaluationOutcome(NormalizedScore(0.0), EvaluatorOutcomeCode.INVALID_OUTPUT)

        val correct = actualValue.subtract(expectedValue).abs() <= tolerance
        return if (correct) {
            EvaluationOutcome(NormalizedScore(1.0), EvaluatorOutcomeCode.CORRECT)
        } else {
            EvaluationOutcome(NormalizedScore(0.0), EvaluatorOutcomeCode.INCORRECT)
        }
    }

    private fun extractGenerated(value: String, mode: String): BigDecimal? {
        if (AMBIGUOUS_DECIMAL_COMMA.containsMatchIn(value)) return null
        return when (mode) {
            EXTRACTION_ENTIRE -> parseCanonical(value.trim())
            EXTRACTION_LAST_NUMBER -> NUMBER.findAll(value).lastOrNull()?.value?.let(::parseCanonical)
            else -> error("Unsupported numeric extraction policy")
        }
    }

    private fun parseCanonical(value: String): BigDecimal? {
        if (!NUMBER.matches(value)) return null
        return runCatching { BigDecimal(value) }.getOrNull()
    }

    private fun parseTolerance(raw: String?): BigDecimal {
        if (raw == null) return BigDecimal.ZERO
        val parsed = parseCanonical(raw)
            ?: throw IllegalArgumentException("Absolute tolerance must be locale-independent decimal text")
        require(parsed.signum() >= 0 && parsed <= MAX_ABSOLUTE_TOLERANCE) {
            "Absolute tolerance must be in [0, $MAX_ABSOLUTE_TOLERANCE]"
        }
        return parsed
    }

    companion object {
        val VERSION = EvaluatorVersion(1)
        const val PARAM_EXTRACTION = "extraction"
        const val PARAM_ABSOLUTE_TOLERANCE = "absolute_tolerance"
        const val EXTRACTION_ENTIRE = "entire"
        const val EXTRACTION_LAST_NUMBER = "last_number"

        val REGISTRATION = EvaluatorRegistration(
            key = EvaluatorKey(EvaluatorType.NUMERIC_FINAL_ANSWER, VERSION),
            parameters = EvaluatorParameterPolicy(
                requiredKeys = setOf(PARAM_EXTRACTION),
                optionalKeys = setOf(PARAM_ABSOLUTE_TOLERANCE),
                allowedValues = mapOf(PARAM_EXTRACTION to setOf(EXTRACTION_ENTIRE, EXTRACTION_LAST_NUMBER)),
            ),
        )

        private val NUMBER = Regex("[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][-+]?\\d+)?")
        private val AMBIGUOUS_DECIMAL_COMMA = Regex("\\d,\\d")
        private val MAX_ABSOLUTE_TOLERANCE = BigDecimal("1000000000000")
    }
}
