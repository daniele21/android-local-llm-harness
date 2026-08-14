package io.github.daniele21.localllm.evaluation.evaluators

import io.github.daniele21.localllm.evaluation.EvaluationOutcome
import io.github.daniele21.localllm.evaluation.EvaluatorOutcomeCode
import io.github.daniele21.localllm.evaluation.EvaluatorSpec
import io.github.daniele21.localllm.evaluation.EvaluatorType
import io.github.daniele21.localllm.evaluation.EvaluatorVersion
import io.github.daniele21.localllm.evaluation.NormalizedScore
import java.util.Locale

class ExactMatchEvaluator {
    fun evaluate(expected: String, generated: String, spec: EvaluatorSpec): EvaluationOutcome {
        require(spec.type == EvaluatorType.EXACT_MATCH && spec.version == VERSION) {
            "Exact-match evaluator requires EXACT_MATCH v${VERSION.value} spec"
        }
        require(REGISTRATION.parameters.validate(spec)) { "Invalid exact-match evaluator parameters" }

        val expectedNormalized = normalize(expected, spec)
        val generatedNormalized = normalize(generated, spec)
        return if (expectedNormalized == generatedNormalized) {
            EvaluationOutcome(NormalizedScore(1.0), EvaluatorOutcomeCode.CORRECT)
        } else {
            EvaluationOutcome(NormalizedScore(0.0), EvaluatorOutcomeCode.INCORRECT)
        }
    }

    private fun normalize(value: String, spec: EvaluatorSpec): String {
        val whitespace = spec.parameters.getValue(PARAM_WHITESPACE)
        val casePolicy = spec.parameters.getValue(PARAM_CASE)
        val whitespaceNormalized = when (whitespace) {
            WHITESPACE_EXACT -> value
            WHITESPACE_TRIM -> value.trim()
            WHITESPACE_COLLAPSE -> value.trim().replace(WHITESPACE_RUN, " ")
            else -> error("Unsupported exact-match whitespace policy")
        }
        return when (casePolicy) {
            CASE_SENSITIVE -> whitespaceNormalized
            CASE_INSENSITIVE -> whitespaceNormalized.lowercase(Locale.ROOT)
            else -> error("Unsupported exact-match case policy")
        }
    }

    companion object {
        val VERSION = EvaluatorVersion(1)
        const val PARAM_CASE = "case"
        const val PARAM_WHITESPACE = "whitespace"
        const val CASE_SENSITIVE = "sensitive"
        const val CASE_INSENSITIVE = "insensitive"
        const val WHITESPACE_EXACT = "exact"
        const val WHITESPACE_TRIM = "trim"
        const val WHITESPACE_COLLAPSE = "collapse"

        val REGISTRATION = EvaluatorRegistration(
            key = EvaluatorKey(EvaluatorType.EXACT_MATCH, VERSION),
            parameters = EvaluatorParameterPolicy(
                requiredKeys = setOf(PARAM_CASE, PARAM_WHITESPACE),
                allowedValues = mapOf(
                    PARAM_CASE to setOf(CASE_SENSITIVE, CASE_INSENSITIVE),
                    PARAM_WHITESPACE to setOf(WHITESPACE_EXACT, WHITESPACE_TRIM, WHITESPACE_COLLAPSE),
                ),
            ),
        )

        private val WHITESPACE_RUN = Regex("\\s+")
    }
}
