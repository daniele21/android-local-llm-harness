package io.github.daniele21.localllm.evaluation.evaluators

import io.github.daniele21.localllm.evaluation.EvaluationOutcome
import io.github.daniele21.localllm.evaluation.EvaluatorOutcomeCode
import io.github.daniele21.localllm.evaluation.EvaluatorSpec
import io.github.daniele21.localllm.evaluation.EvaluatorType
import io.github.daniele21.localllm.evaluation.EvaluatorVersion
import io.github.daniele21.localllm.evaluation.NormalizedScore

class RegexFormatEvaluator {
    fun evaluate(generated: String, spec: EvaluatorSpec): EvaluationOutcome {
        require(spec.type == EvaluatorType.REGEX_FORMAT && spec.version == VERSION) {
            "Regex evaluator requires REGEX_FORMAT v${VERSION.value} spec"
        }
        require(REGISTRATION.parameters.validate(spec)) { "Invalid regex evaluator parameters" }

        val pattern = PATTERNS.getValue(spec.parameters.getValue(PARAM_PATTERN_ID))
        val matches = when (spec.parameters.getValue(PARAM_MATCH_MODE)) {
            MATCH_FULL -> pattern.matches(generated)
            MATCH_FIND -> pattern.containsMatchIn(generated)
            else -> error("Unsupported regex match mode")
        }
        return if (matches) {
            EvaluationOutcome(NormalizedScore(1.0), EvaluatorOutcomeCode.CORRECT)
        } else {
            EvaluationOutcome(NormalizedScore(0.0), EvaluatorOutcomeCode.CONSTRAINT_VIOLATION)
        }
    }

    companion object {
        val VERSION = EvaluatorVersion(1)
        const val PARAM_PATTERN_ID = "pattern_id"
        const val PARAM_MATCH_MODE = "match_mode"
        const val MATCH_FULL = "full"
        const val MATCH_FIND = "find"

        const val PATTERN_SINGLE_LINE_NON_EMPTY = "single_line_non_empty"
        const val PATTERN_INTEGER = "integer"
        const val PATTERN_DECIMAL = "decimal"
        const val PATTERN_LABEL_TOKEN = "label_token"
        const val PATTERN_JSON_OBJECT_SHAPE = "json_object_shape"

        private val PATTERNS = linkedMapOf(
            PATTERN_SINGLE_LINE_NON_EMPTY to Regex("[^\\r\\n]*\\S[^\\r\\n]*"),
            PATTERN_INTEGER to Regex("[-+]?\\d+"),
            PATTERN_DECIMAL to Regex("[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][-+]?\\d+)?"),
            PATTERN_LABEL_TOKEN to Regex("[A-Za-z0-9]{1,16}"),
            PATTERN_JSON_OBJECT_SHAPE to Regex("\\s*\\{[\\s\\S]*}\\s*"),
        )

        val REGISTRATION = EvaluatorRegistration(
            key = EvaluatorKey(EvaluatorType.REGEX_FORMAT, VERSION),
            parameters = EvaluatorParameterPolicy(
                requiredKeys = setOf(PARAM_PATTERN_ID, PARAM_MATCH_MODE),
                allowedValues = mapOf(
                    PARAM_PATTERN_ID to PATTERNS.keys,
                    PARAM_MATCH_MODE to setOf(MATCH_FULL, MATCH_FIND),
                ),
            ),
        )
    }
}
