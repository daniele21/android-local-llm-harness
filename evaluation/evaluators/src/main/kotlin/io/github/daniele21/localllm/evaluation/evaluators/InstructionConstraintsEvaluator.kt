package io.github.daniele21.localllm.evaluation.evaluators

import io.github.daniele21.localllm.evaluation.EvaluationOutcome
import io.github.daniele21.localllm.evaluation.EvaluatorOutcomeCode
import io.github.daniele21.localllm.evaluation.EvaluatorSpec
import io.github.daniele21.localllm.evaluation.EvaluatorType
import io.github.daniele21.localllm.evaluation.EvaluatorVersion
import io.github.daniele21.localllm.evaluation.NormalizedScore
import java.util.Locale

class InstructionConstraintsEvaluator(
    private val regexEvaluator: RegexFormatEvaluator = RegexFormatEvaluator(),
) {
    fun evaluate(generated: String, spec: EvaluatorSpec): EvaluationOutcome {
        require(spec.type == EvaluatorType.INSTRUCTION_CONSTRAINTS && spec.version == VERSION) {
            "Instruction constraints evaluator requires INSTRUCTION_CONSTRAINTS v${VERSION.value} spec"
        }
        require(REGISTRATION.parameters.validate(spec)) { "Invalid instruction-constraints evaluator parameters" }

        val constraints = parseConstraints(spec.parameters.getValue(PARAM_CONSTRAINTS))
        validateConstraintParameters(constraints, spec)
        val casePolicy = spec.parameters.getValue(PARAM_CASE)
        val checks = constraints.map { constraint -> evaluateConstraint(constraint, generated, spec, casePolicy) }
        val passed = checks.count { it }

        return when (passed) {
            checks.size -> EvaluationOutcome(NormalizedScore(1.0), EvaluatorOutcomeCode.CORRECT)
            0 -> EvaluationOutcome(NormalizedScore(0.0), EvaluatorOutcomeCode.CONSTRAINT_VIOLATION)
            else -> EvaluationOutcome(
                score = NormalizedScore(passed.toDouble() / checks.size.toDouble()),
                code = EvaluatorOutcomeCode.PARTIAL,
            )
        }
    }

    private fun evaluateConstraint(
        constraint: String,
        generated: String,
        spec: EvaluatorSpec,
        casePolicy: String,
    ): Boolean = when (constraint) {
        CONSTRAINT_NON_EMPTY -> generated.isNotBlank()
        CONSTRAINT_SINGLE_LINE -> '\n' !in generated && '\r' !in generated
        CONSTRAINT_CONTAINS -> contains(generated, spec.parameters.getValue(PARAM_CONTAINS_TEXT), casePolicy)
        CONSTRAINT_EXCLUDES -> !contains(generated, spec.parameters.getValue(PARAM_EXCLUDES_TEXT), casePolicy)
        CONSTRAINT_STARTS_WITH -> startsWith(generated, spec.parameters.getValue(PARAM_STARTS_WITH_TEXT), casePolicy)
        CONSTRAINT_ENDS_WITH -> endsWith(generated, spec.parameters.getValue(PARAM_ENDS_WITH_TEXT), casePolicy)
        CONSTRAINT_MIN_WORDS -> wordCount(generated) >= parseCount(spec.parameters.getValue(PARAM_MIN_WORDS))
        CONSTRAINT_MAX_WORDS -> wordCount(generated) <= parseCount(spec.parameters.getValue(PARAM_MAX_WORDS))
        CONSTRAINT_EXACT_LINES -> lineCount(generated) == parseCount(spec.parameters.getValue(PARAM_EXACT_LINES))
        CONSTRAINT_FORMAT -> evaluateFormat(generated, spec)
        else -> error("Unsupported instruction constraint")
    }

    private fun evaluateFormat(generated: String, spec: EvaluatorSpec): Boolean {
        val outcome = regexEvaluator.evaluate(
            generated = generated,
            spec = EvaluatorSpec(
                type = EvaluatorType.REGEX_FORMAT,
                version = RegexFormatEvaluator.VERSION,
                parameters = mapOf(
                    RegexFormatEvaluator.PARAM_PATTERN_ID to spec.parameters.getValue(PARAM_FORMAT_PATTERN_ID),
                    RegexFormatEvaluator.PARAM_MATCH_MODE to spec.parameters.getValue(PARAM_FORMAT_MATCH_MODE),
                ),
            ),
        )
        return outcome.code == EvaluatorOutcomeCode.CORRECT
    }

    private fun parseConstraints(raw: String): List<String> {
        val constraints = raw.split(',').map(String::trim)
        require(constraints.size in 1..MAX_CONSTRAINTS) {
            "Instruction constraints must contain 1..$MAX_CONSTRAINTS entries"
        }
        require(constraints.all { it in SUPPORTED_CONSTRAINTS }) {
            "Instruction constraints contain an unsupported constraint ID"
        }
        require(constraints.distinct().size == constraints.size) {
            "Instruction constraint IDs must be unique"
        }
        return constraints
    }

    private fun validateConstraintParameters(constraints: List<String>, spec: EvaluatorSpec) {
        val selected = constraints.toSet()
        REQUIRED_PARAMETERS_BY_CONSTRAINT.forEach { (constraint, parameterKeys) ->
            val present = parameterKeys.filter(spec.parameters::containsKey).toSet()
            if (constraint in selected) {
                require(present == parameterKeys) {
                    "Instruction constraint $constraint requires parameters ${parameterKeys.sorted()}"
                }
            } else {
                require(present.isEmpty()) {
                    "Instruction constraint parameters cannot be declared for an unselected constraint"
                }
            }
        }
        TEXT_PARAMETERS.forEach { key ->
            spec.parameters[key]?.let { require(it.isNotEmpty()) { "Instruction text constraint value must not be empty" } }
        }
        COUNT_PARAMETERS.forEach { key -> spec.parameters[key]?.let(::parseCount) }
    }

    private fun contains(source: String, target: String, casePolicy: String): Boolean = when (casePolicy) {
        CASE_SENSITIVE -> source.contains(target)
        CASE_INSENSITIVE -> source.lowercase(Locale.ROOT).contains(target.lowercase(Locale.ROOT))
        else -> error("Unsupported instruction constraint case policy")
    }

    private fun startsWith(source: String, target: String, casePolicy: String): Boolean = when (casePolicy) {
        CASE_SENSITIVE -> source.startsWith(target)
        CASE_INSENSITIVE -> source.lowercase(Locale.ROOT).startsWith(target.lowercase(Locale.ROOT))
        else -> error("Unsupported instruction constraint case policy")
    }

    private fun endsWith(source: String, target: String, casePolicy: String): Boolean = when (casePolicy) {
        CASE_SENSITIVE -> source.endsWith(target)
        CASE_INSENSITIVE -> source.lowercase(Locale.ROOT).endsWith(target.lowercase(Locale.ROOT))
        else -> error("Unsupported instruction constraint case policy")
    }

    private fun wordCount(value: String): Int = value.trim().takeIf(String::isNotEmpty)?.split(WHITESPACE_RUN)?.size ?: 0

    private fun lineCount(value: String): Int = LINE_BREAK.split(value).size

    private fun parseCount(raw: String): Int {
        require(COUNT.matches(raw)) { "Instruction count parameter must be a positive base-10 integer" }
        val value = raw.toIntOrNull() ?: error("Instruction count parameter exceeds integer range")
        require(value in 1..MAX_COUNT) { "Instruction count parameter exceeds evaluator bound" }
        return value
    }

    companion object {
        val VERSION = EvaluatorVersion(1)

        const val PARAM_CONSTRAINTS = "constraints"
        const val PARAM_CASE = "case"
        const val PARAM_CONTAINS_TEXT = "contains_text"
        const val PARAM_EXCLUDES_TEXT = "excludes_text"
        const val PARAM_STARTS_WITH_TEXT = "starts_with_text"
        const val PARAM_ENDS_WITH_TEXT = "ends_with_text"
        const val PARAM_MIN_WORDS = "min_words"
        const val PARAM_MAX_WORDS = "max_words"
        const val PARAM_EXACT_LINES = "exact_lines"
        const val PARAM_FORMAT_PATTERN_ID = "format_pattern_id"
        const val PARAM_FORMAT_MATCH_MODE = "format_match_mode"

        const val CASE_SENSITIVE = "sensitive"
        const val CASE_INSENSITIVE = "insensitive"

        const val CONSTRAINT_NON_EMPTY = "non_empty"
        const val CONSTRAINT_SINGLE_LINE = "single_line"
        const val CONSTRAINT_CONTAINS = "contains"
        const val CONSTRAINT_EXCLUDES = "excludes"
        const val CONSTRAINT_STARTS_WITH = "starts_with"
        const val CONSTRAINT_ENDS_WITH = "ends_with"
        const val CONSTRAINT_MIN_WORDS = "min_words"
        const val CONSTRAINT_MAX_WORDS = "max_words"
        const val CONSTRAINT_EXACT_LINES = "exact_lines"
        const val CONSTRAINT_FORMAT = "format"

        private val SUPPORTED_CONSTRAINTS = setOf(
            CONSTRAINT_NON_EMPTY,
            CONSTRAINT_SINGLE_LINE,
            CONSTRAINT_CONTAINS,
            CONSTRAINT_EXCLUDES,
            CONSTRAINT_STARTS_WITH,
            CONSTRAINT_ENDS_WITH,
            CONSTRAINT_MIN_WORDS,
            CONSTRAINT_MAX_WORDS,
            CONSTRAINT_EXACT_LINES,
            CONSTRAINT_FORMAT,
        )

        private val REQUIRED_PARAMETERS_BY_CONSTRAINT = mapOf(
            CONSTRAINT_CONTAINS to setOf(PARAM_CONTAINS_TEXT),
            CONSTRAINT_EXCLUDES to setOf(PARAM_EXCLUDES_TEXT),
            CONSTRAINT_STARTS_WITH to setOf(PARAM_STARTS_WITH_TEXT),
            CONSTRAINT_ENDS_WITH to setOf(PARAM_ENDS_WITH_TEXT),
            CONSTRAINT_MIN_WORDS to setOf(PARAM_MIN_WORDS),
            CONSTRAINT_MAX_WORDS to setOf(PARAM_MAX_WORDS),
            CONSTRAINT_EXACT_LINES to setOf(PARAM_EXACT_LINES),
            CONSTRAINT_FORMAT to setOf(PARAM_FORMAT_PATTERN_ID, PARAM_FORMAT_MATCH_MODE),
        )
        private val TEXT_PARAMETERS = setOf(
            PARAM_CONTAINS_TEXT,
            PARAM_EXCLUDES_TEXT,
            PARAM_STARTS_WITH_TEXT,
            PARAM_ENDS_WITH_TEXT,
        )
        private val COUNT_PARAMETERS = setOf(PARAM_MIN_WORDS, PARAM_MAX_WORDS, PARAM_EXACT_LINES)

        val REGISTRATION = EvaluatorRegistration(
            key = EvaluatorKey(EvaluatorType.INSTRUCTION_CONSTRAINTS, VERSION),
            parameters = EvaluatorParameterPolicy(
                requiredKeys = setOf(PARAM_CONSTRAINTS, PARAM_CASE),
                optionalKeys = REQUIRED_PARAMETERS_BY_CONSTRAINT.values.flatten().toSet(),
                allowedValues = mapOf(
                    PARAM_CASE to setOf(CASE_SENSITIVE, CASE_INSENSITIVE),
                    PARAM_FORMAT_PATTERN_ID to setOf(
                        RegexFormatEvaluator.PATTERN_SINGLE_LINE_NON_EMPTY,
                        RegexFormatEvaluator.PATTERN_INTEGER,
                        RegexFormatEvaluator.PATTERN_DECIMAL,
                        RegexFormatEvaluator.PATTERN_LABEL_TOKEN,
                        RegexFormatEvaluator.PATTERN_JSON_OBJECT_SHAPE,
                    ),
                    PARAM_FORMAT_MATCH_MODE to setOf(RegexFormatEvaluator.MATCH_FULL, RegexFormatEvaluator.MATCH_FIND),
                ),
            ),
        )

        private const val MAX_CONSTRAINTS = 16
        private const val MAX_COUNT = 100_000
        private val COUNT = Regex("[1-9]\\d{0,5}")
        private val WHITESPACE_RUN = Regex("\\s+")
        private val LINE_BREAK = Regex("\\r\\n|\\r|\\n")
    }
}
