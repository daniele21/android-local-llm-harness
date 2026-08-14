package io.github.daniele21.localllm.evaluation

@JvmInline
value class EvaluatorVersion(val value: Int) {
    init {
        require(value > 0) { "Evaluator version must be positive" }
    }
}

@JvmInline
value class NormalizedScore(val value: Double) {
    init {
        require(value.isFinite() && value in 0.0..1.0) { "Normalized score must be finite and in [0, 1]" }
    }
}

enum class EvaluatorType {
    EXACT_MATCH,
    MULTIPLE_CHOICE,
    NUMERIC_FINAL_ANSWER,
    JSON_FIELDS,
    REGEX_FORMAT,
    INSTRUCTION_CONSTRAINTS,
}

data class EvaluatorSpec(
    val type: EvaluatorType,
    val version: EvaluatorVersion,
    val parameters: Map<String, String> = emptyMap(),
) {
    init {
        require(parameters.size <= MAX_EVALUATOR_PARAMETERS) {
            "Evaluator parameters must not exceed $MAX_EVALUATOR_PARAMETERS entries"
        }
        parameters.forEach { (key, value) ->
            validateStableText(key, "Evaluator parameter key", MAX_PARAMETER_KEY_LENGTH)
            require(value.length <= MAX_PARAMETER_VALUE_LENGTH) {
                "Evaluator parameter value must not exceed $MAX_PARAMETER_VALUE_LENGTH characters"
            }
            require('\u0000' !in value) { "Evaluator parameter value must not contain NUL" }
        }
    }
}

data class CaseEvaluatorIdentity(
    val caseId: EvaluationCaseId,
    val evaluator: EvaluatorSpec,
)

enum class EvaluatorOutcomeCode {
    CORRECT,
    INCORRECT,
    PARTIAL,
    INVALID_OUTPUT,
    AMBIGUOUS_OUTPUT,
    CONSTRAINT_VIOLATION,
}

data class EvaluationOutcome(
    val score: NormalizedScore,
    val code: EvaluatorOutcomeCode,
) {
    init {
        when (code) {
            EvaluatorOutcomeCode.CORRECT -> require(score.value == 1.0) {
                "Correct evaluator outcome must have score 1"
            }

            EvaluatorOutcomeCode.PARTIAL -> require(score.value > 0.0 && score.value < 1.0) {
                "Partial evaluator outcome must have score strictly between 0 and 1"
            }

            EvaluatorOutcomeCode.INCORRECT,
            EvaluatorOutcomeCode.INVALID_OUTPUT,
            EvaluatorOutcomeCode.AMBIGUOUS_OUTPUT,
            EvaluatorOutcomeCode.CONSTRAINT_VIOLATION,
            -> require(score.value == 0.0) { "Failed evaluator outcome must have score 0" }
        }
    }
}

private const val MAX_EVALUATOR_PARAMETERS = 32
private const val MAX_PARAMETER_KEY_LENGTH = 64
private const val MAX_PARAMETER_VALUE_LENGTH = 512
