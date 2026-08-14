package io.github.daniele21.localllm.evaluation.evaluators

import io.github.daniele21.localllm.evaluation.EvaluationOutcome
import io.github.daniele21.localllm.evaluation.EvaluatorOutcomeCode
import io.github.daniele21.localllm.evaluation.EvaluatorSpec
import io.github.daniele21.localllm.evaluation.EvaluatorType
import io.github.daniele21.localllm.evaluation.EvaluatorVersion
import io.github.daniele21.localllm.evaluation.NormalizedScore
import java.util.Locale

class MultipleChoiceEvaluator {
    fun evaluate(expectedLabel: String, generated: String, spec: EvaluatorSpec): EvaluationOutcome {
        require(spec.type == EvaluatorType.MULTIPLE_CHOICE && spec.version == VERSION) {
            "Multiple-choice evaluator requires MULTIPLE_CHOICE v${VERSION.value} spec"
        }
        require(REGISTRATION.parameters.validate(spec)) { "Invalid multiple-choice evaluator parameters" }

        val casePolicy = spec.parameters.getValue(PARAM_CASE)
        val labels = parseLabels(spec.parameters.getValue(PARAM_LABELS), casePolicy)
        val expected = normalize(expectedLabel, casePolicy)
        require(expected in labels) { "Expected multiple-choice label must be declared in evaluator labels" }

        val matched = labels.filterTo(linkedSetOf()) { label ->
            standaloneLabelPattern(label, casePolicy).containsMatchIn(generated)
        }
        return when {
            matched.isEmpty() -> EvaluationOutcome(NormalizedScore(0.0), EvaluatorOutcomeCode.INVALID_OUTPUT)
            matched.size > 1 -> EvaluationOutcome(NormalizedScore(0.0), EvaluatorOutcomeCode.AMBIGUOUS_OUTPUT)
            matched.single() == expected -> EvaluationOutcome(NormalizedScore(1.0), EvaluatorOutcomeCode.CORRECT)
            else -> EvaluationOutcome(NormalizedScore(0.0), EvaluatorOutcomeCode.INCORRECT)
        }
    }

    private fun parseLabels(raw: String, casePolicy: String): Set<String> {
        val values = raw.split(',').map { it.trim() }
        require(values.size in 2..MAX_LABELS) { "Multiple-choice labels must contain 2..$MAX_LABELS entries" }
        require(values.all { LABEL.matches(it) }) {
            "Multiple-choice labels must be alphanumeric tokens of bounded length"
        }
        val normalized = values.map { normalize(it, casePolicy) }
        require(normalized.distinct().size == normalized.size) { "Multiple-choice labels must be unique" }
        return normalized.toSet()
    }

    private fun normalize(value: String, casePolicy: String): String = when (casePolicy) {
        CASE_SENSITIVE -> value
        CASE_INSENSITIVE -> value.lowercase(Locale.ROOT)
        else -> error("Unsupported multiple-choice case policy")
    }

    private fun standaloneLabelPattern(label: String, casePolicy: String): Regex {
        val options = if (casePolicy == CASE_INSENSITIVE) setOf(RegexOption.IGNORE_CASE) else emptySet()
        return Regex("(?<![A-Za-z0-9_])${Regex.escape(label)}(?![A-Za-z0-9_])", options)
    }

    companion object {
        val VERSION = EvaluatorVersion(1)
        const val PARAM_LABELS = "labels"
        const val PARAM_CASE = "case"
        const val CASE_SENSITIVE = "sensitive"
        const val CASE_INSENSITIVE = "insensitive"

        val REGISTRATION = EvaluatorRegistration(
            key = EvaluatorKey(EvaluatorType.MULTIPLE_CHOICE, VERSION),
            parameters = EvaluatorParameterPolicy(
                requiredKeys = setOf(PARAM_LABELS, PARAM_CASE),
                allowedValues = mapOf(PARAM_CASE to setOf(CASE_SENSITIVE, CASE_INSENSITIVE)),
            ),
        )

        private const val MAX_LABELS = 32
        private val LABEL = Regex("[A-Za-z0-9]{1,16}")
    }
}
