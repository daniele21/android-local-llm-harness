package io.github.daniele21.localllm.evaluation.evaluators

import io.github.daniele21.localllm.evaluation.EvaluationOutcome
import io.github.daniele21.localllm.evaluation.EvaluatorOutcomeCode
import io.github.daniele21.localllm.evaluation.EvaluatorSpec
import io.github.daniele21.localllm.evaluation.EvaluatorType

internal enum class EvaluatorOutputShape {
    GOLDEN,
    AMBIGUOUS,
    MALFORMED,
    EDGE,
}

/**
 * Reusable v1 scorer fixture.
 *
 * [shape] describes the generated output presented to a scorer, not the outcome code. For example,
 * only MULTIPLE_CHOICE v1 assigns AMBIGUOUS_OUTPUT to text containing two declared labels; the other
 * evaluators retain their own frozen interpretation of the same broad output shape.
 */
internal data class EvaluatorV1CorpusCase(
    val id: String,
    val shape: EvaluatorOutputShape,
    val expected: String?,
    val generated: String,
    val spec: EvaluatorSpec,
    val expectedCode: EvaluatorOutcomeCode,
    val expectedScore: Double,
)

internal object EvaluatorV1Corpus {
    val cases: List<EvaluatorV1CorpusCase> =
        listOf(
            exactMatchCase(
                id = "exact-golden-literal",
                shape = EvaluatorOutputShape.GOLDEN,
                expected = "Local AI",
                generated = "Local AI",
                casePolicy = ExactMatchEvaluator.CASE_SENSITIVE,
                whitespace = ExactMatchEvaluator.WHITESPACE_EXACT,
                expectedCode = EvaluatorOutcomeCode.CORRECT,
                expectedScore = 1.0,
            ),
            exactMatchCase(
                id = "exact-ambiguous-candidates",
                shape = EvaluatorOutputShape.AMBIGUOUS,
                expected = "A",
                generated = "A or B",
                casePolicy = ExactMatchEvaluator.CASE_SENSITIVE,
                whitespace = ExactMatchEvaluator.WHITESPACE_EXACT,
                expectedCode = EvaluatorOutcomeCode.INCORRECT,
                expectedScore = 0.0,
            ),
            exactMatchCase(
                id = "exact-malformed-control",
                shape = EvaluatorOutputShape.MALFORMED,
                expected = "answer",
                generated = "\u0000",
                casePolicy = ExactMatchEvaluator.CASE_SENSITIVE,
                whitespace = ExactMatchEvaluator.WHITESPACE_EXACT,
                expectedCode = EvaluatorOutcomeCode.INCORRECT,
                expectedScore = 0.0,
            ),
            exactMatchCase(
                id = "exact-edge-root-case-and-whitespace",
                shape = EvaluatorOutputShape.EDGE,
                expected = "Local AI",
                generated = "\tLOCAL\r\nAI ",
                casePolicy = ExactMatchEvaluator.CASE_INSENSITIVE,
                whitespace = ExactMatchEvaluator.WHITESPACE_COLLAPSE,
                expectedCode = EvaluatorOutcomeCode.CORRECT,
                expectedScore = 1.0,
            ),
            multipleChoiceCase(
                id = "multiple-choice-golden-standalone",
                shape = EvaluatorOutputShape.GOLDEN,
                expected = "B",
                generated = "The final answer is B.",
                labels = "A,B,C,D",
                casePolicy = MultipleChoiceEvaluator.CASE_SENSITIVE,
                expectedCode = EvaluatorOutcomeCode.CORRECT,
                expectedScore = 1.0,
            ),
            multipleChoiceCase(
                id = "multiple-choice-ambiguous-distinct-labels",
                shape = EvaluatorOutputShape.AMBIGUOUS,
                expected = "A",
                generated = "Either A or B could be correct.",
                labels = "A,B,C,D",
                casePolicy = MultipleChoiceEvaluator.CASE_SENSITIVE,
                expectedCode = EvaluatorOutcomeCode.AMBIGUOUS_OUTPUT,
                expectedScore = 0.0,
            ),
            multipleChoiceCase(
                id = "multiple-choice-malformed-no-label",
                shape = EvaluatorOutputShape.MALFORMED,
                expected = "A",
                generated = "The answer is ?",
                labels = "A,B,C,D",
                casePolicy = MultipleChoiceEvaluator.CASE_SENSITIVE,
                expectedCode = EvaluatorOutcomeCode.INVALID_OUTPUT,
                expectedScore = 0.0,
            ),
            multipleChoiceCase(
                id = "multiple-choice-edge-repeated-label",
                shape = EvaluatorOutputShape.EDGE,
                expected = "a",
                generated = "A and A.",
                labels = "A,B",
                casePolicy = MultipleChoiceEvaluator.CASE_INSENSITIVE,
                expectedCode = EvaluatorOutcomeCode.CORRECT,
                expectedScore = 1.0,
            ),
            numericCase(
                id = "numeric-golden-entire",
                shape = EvaluatorOutputShape.GOLDEN,
                expected = "42",
                generated = "42",
                extraction = NumericFinalAnswerEvaluator.EXTRACTION_ENTIRE,
                expectedCode = EvaluatorOutcomeCode.CORRECT,
                expectedScore = 1.0,
            ),
            numericCase(
                id = "numeric-ambiguous-candidates",
                shape = EvaluatorOutputShape.AMBIGUOUS,
                expected = "42",
                generated = "41 or 42",
                extraction = NumericFinalAnswerEvaluator.EXTRACTION_ENTIRE,
                expectedCode = EvaluatorOutcomeCode.INVALID_OUTPUT,
                expectedScore = 0.0,
            ),
            numericCase(
                id = "numeric-malformed-exponent",
                shape = EvaluatorOutputShape.MALFORMED,
                expected = "1",
                generated = "1e",
                extraction = NumericFinalAnswerEvaluator.EXTRACTION_ENTIRE,
                expectedCode = EvaluatorOutcomeCode.INVALID_OUTPUT,
                expectedScore = 0.0,
            ),
            numericCase(
                id = "numeric-edge-last-scientific-number",
                shape = EvaluatorOutputShape.EDGE,
                expected = "42",
                generated = "intermediate -1e2; final +4.2e1",
                extraction = NumericFinalAnswerEvaluator.EXTRACTION_LAST_NUMBER,
                tolerance = "0",
                expectedCode = EvaluatorOutcomeCode.CORRECT,
                expectedScore = 1.0,
            ),
            jsonFieldsCase(
                id = "json-golden-structural-number",
                shape = EvaluatorOutputShape.GOLDEN,
                expected = """{"label":"food","amount":12.5}""",
                generated = """{"amount":12.50,"label":"food","note":"ignored"}""",
                requiredFields = "label,amount",
                expectedCode = EvaluatorOutcomeCode.CORRECT,
                expectedScore = 1.0,
            ),
            jsonFieldsCase(
                id = "json-ambiguous-array-candidates",
                shape = EvaluatorOutputShape.AMBIGUOUS,
                expected = """{"choice":"A"}""",
                generated = """{"choice":["A","B"]}""",
                requiredFields = "choice",
                expectedCode = EvaluatorOutcomeCode.INCORRECT,
                expectedScore = 0.0,
            ),
            jsonFieldsCase(
                id = "json-malformed-trailing-content",
                shape = EvaluatorOutputShape.MALFORMED,
                expected = """{"label":"food"}""",
                generated = """{"label":"food"} trailing""",
                requiredFields = "label",
                expectedCode = EvaluatorOutcomeCode.INVALID_OUTPUT,
                expectedScore = 0.0,
            ),
            jsonFieldsCase(
                id = "json-edge-partial-required-fields",
                shape = EvaluatorOutputShape.EDGE,
                expected = """{"label":"food","amount":12.5}""",
                generated = """{"label":"food","amount":12.6}""",
                requiredFields = "label,amount",
                expectedCode = EvaluatorOutcomeCode.PARTIAL,
                expectedScore = 0.5,
            ),
            regexCase(
                id = "regex-golden-integer-full",
                shape = EvaluatorOutputShape.GOLDEN,
                generated = "-42",
                patternId = RegexFormatEvaluator.PATTERN_INTEGER,
                matchMode = RegexFormatEvaluator.MATCH_FULL,
                expectedCode = EvaluatorOutcomeCode.CORRECT,
                expectedScore = 1.0,
            ),
            regexCase(
                id = "regex-ambiguous-label-candidates",
                shape = EvaluatorOutputShape.AMBIGUOUS,
                generated = "A or B",
                patternId = RegexFormatEvaluator.PATTERN_LABEL_TOKEN,
                matchMode = RegexFormatEvaluator.MATCH_FULL,
                expectedCode = EvaluatorOutcomeCode.CONSTRAINT_VIOLATION,
                expectedScore = 0.0,
            ),
            regexCase(
                id = "regex-malformed-json-shape-only",
                shape = EvaluatorOutputShape.MALFORMED,
                generated = "{not-json}",
                patternId = RegexFormatEvaluator.PATTERN_JSON_OBJECT_SHAPE,
                matchMode = RegexFormatEvaluator.MATCH_FULL,
                expectedCode = EvaluatorOutcomeCode.CORRECT,
                expectedScore = 1.0,
            ),
            regexCase(
                id = "regex-edge-decimal-find",
                shape = EvaluatorOutputShape.EDGE,
                generated = "value=-.5e+2 units",
                patternId = RegexFormatEvaluator.PATTERN_DECIMAL,
                matchMode = RegexFormatEvaluator.MATCH_FIND,
                expectedCode = EvaluatorOutcomeCode.CORRECT,
                expectedScore = 1.0,
            ),
            instructionCase(
                id = "instruction-golden-all-pass",
                shape = EvaluatorOutputShape.GOLDEN,
                generated = "RESULT: local AI",
                constraints = "non_empty,single_line,starts_with,max_words",
                extras = mapOf(
                    InstructionConstraintsEvaluator.PARAM_STARTS_WITH_TEXT to "RESULT:",
                    InstructionConstraintsEvaluator.PARAM_MAX_WORDS to "3",
                ),
                expectedCode = EvaluatorOutcomeCode.CORRECT,
                expectedScore = 1.0,
            ),
            instructionCase(
                id = "instruction-ambiguous-partial",
                shape = EvaluatorOutputShape.AMBIGUOUS,
                generated = "A or B",
                constraints = "non_empty,single_line,max_words",
                extras = mapOf(InstructionConstraintsEvaluator.PARAM_MAX_WORDS to "1"),
                expectedCode = EvaluatorOutcomeCode.PARTIAL,
                expectedScore = 2.0 / 3.0,
            ),
            instructionCase(
                id = "instruction-malformed-line-break-only",
                shape = EvaluatorOutputShape.MALFORMED,
                generated = "\r\n",
                constraints = "non_empty,single_line",
                expectedCode = EvaluatorOutcomeCode.CONSTRAINT_VIOLATION,
                expectedScore = 0.0,
            ),
            instructionCase(
                id = "instruction-edge-line-separators",
                shape = EvaluatorOutputShape.EDGE,
                generated = "one\r\ntwo\rthree\nfour",
                constraints = "min_words,max_words,exact_lines",
                extras = mapOf(
                    InstructionConstraintsEvaluator.PARAM_MIN_WORDS to "4",
                    InstructionConstraintsEvaluator.PARAM_MAX_WORDS to "4",
                    InstructionConstraintsEvaluator.PARAM_EXACT_LINES to "4",
                ),
                expectedCode = EvaluatorOutcomeCode.CORRECT,
                expectedScore = 1.0,
            ),
        )
}

internal fun evaluateCorpusCase(fixture: EvaluatorV1CorpusCase): EvaluationOutcome = when (fixture.spec.type) {
    EvaluatorType.EXACT_MATCH ->
        ExactMatchEvaluator().evaluate(
            expected = requireNotNull(fixture.expected),
            generated = fixture.generated,
            spec = fixture.spec,
        )

    EvaluatorType.MULTIPLE_CHOICE ->
        MultipleChoiceEvaluator().evaluate(
            expectedLabel = requireNotNull(fixture.expected),
            generated = fixture.generated,
            spec = fixture.spec,
        )

    EvaluatorType.NUMERIC_FINAL_ANSWER ->
        NumericFinalAnswerEvaluator().evaluate(
            expected = requireNotNull(fixture.expected),
            generated = fixture.generated,
            spec = fixture.spec,
        )

    EvaluatorType.JSON_FIELDS ->
        JsonFieldsEvaluator().evaluate(
            expected = requireNotNull(fixture.expected),
            generated = fixture.generated,
            spec = fixture.spec,
        )

    EvaluatorType.REGEX_FORMAT -> RegexFormatEvaluator().evaluate(fixture.generated, fixture.spec)
    EvaluatorType.INSTRUCTION_CONSTRAINTS ->
        InstructionConstraintsEvaluator().evaluate(fixture.generated, fixture.spec)
}

private fun exactMatchCase(
    id: String,
    shape: EvaluatorOutputShape,
    expected: String,
    generated: String,
    casePolicy: String,
    whitespace: String,
    expectedCode: EvaluatorOutcomeCode,
    expectedScore: Double,
) = EvaluatorV1CorpusCase(
    id = id,
    shape = shape,
    expected = expected,
    generated = generated,
    spec = EvaluatorSpec(
        type = EvaluatorType.EXACT_MATCH,
        version = ExactMatchEvaluator.VERSION,
        parameters = mapOf(
            ExactMatchEvaluator.PARAM_CASE to casePolicy,
            ExactMatchEvaluator.PARAM_WHITESPACE to whitespace,
        ),
    ),
    expectedCode = expectedCode,
    expectedScore = expectedScore,
)

private fun multipleChoiceCase(
    id: String,
    shape: EvaluatorOutputShape,
    expected: String,
    generated: String,
    labels: String,
    casePolicy: String,
    expectedCode: EvaluatorOutcomeCode,
    expectedScore: Double,
) = EvaluatorV1CorpusCase(
    id = id,
    shape = shape,
    expected = expected,
    generated = generated,
    spec = EvaluatorSpec(
        type = EvaluatorType.MULTIPLE_CHOICE,
        version = MultipleChoiceEvaluator.VERSION,
        parameters = mapOf(
            MultipleChoiceEvaluator.PARAM_LABELS to labels,
            MultipleChoiceEvaluator.PARAM_CASE to casePolicy,
        ),
    ),
    expectedCode = expectedCode,
    expectedScore = expectedScore,
)

private fun numericCase(
    id: String,
    shape: EvaluatorOutputShape,
    expected: String,
    generated: String,
    extraction: String,
    tolerance: String? = null,
    expectedCode: EvaluatorOutcomeCode,
    expectedScore: Double,
): EvaluatorV1CorpusCase {
    val parameters = linkedMapOf(NumericFinalAnswerEvaluator.PARAM_EXTRACTION to extraction)
    tolerance?.let { parameters[NumericFinalAnswerEvaluator.PARAM_ABSOLUTE_TOLERANCE] = it }
    return EvaluatorV1CorpusCase(
        id = id,
        shape = shape,
        expected = expected,
        generated = generated,
        spec = EvaluatorSpec(
            type = EvaluatorType.NUMERIC_FINAL_ANSWER,
            version = NumericFinalAnswerEvaluator.VERSION,
            parameters = parameters,
        ),
        expectedCode = expectedCode,
        expectedScore = expectedScore,
    )
}

private fun jsonFieldsCase(
    id: String,
    shape: EvaluatorOutputShape,
    expected: String,
    generated: String,
    requiredFields: String,
    expectedCode: EvaluatorOutcomeCode,
    expectedScore: Double,
) = EvaluatorV1CorpusCase(
    id = id,
    shape = shape,
    expected = expected,
    generated = generated,
    spec = EvaluatorSpec(
        type = EvaluatorType.JSON_FIELDS,
        version = JsonFieldsEvaluator.VERSION,
        parameters = mapOf(JsonFieldsEvaluator.PARAM_REQUIRED_FIELDS to requiredFields),
    ),
    expectedCode = expectedCode,
    expectedScore = expectedScore,
)

private fun regexCase(
    id: String,
    shape: EvaluatorOutputShape,
    generated: String,
    patternId: String,
    matchMode: String,
    expectedCode: EvaluatorOutcomeCode,
    expectedScore: Double,
) = EvaluatorV1CorpusCase(
    id = id,
    shape = shape,
    expected = null,
    generated = generated,
    spec = EvaluatorSpec(
        type = EvaluatorType.REGEX_FORMAT,
        version = RegexFormatEvaluator.VERSION,
        parameters = mapOf(
            RegexFormatEvaluator.PARAM_PATTERN_ID to patternId,
            RegexFormatEvaluator.PARAM_MATCH_MODE to matchMode,
        ),
    ),
    expectedCode = expectedCode,
    expectedScore = expectedScore,
)

private fun instructionCase(
    id: String,
    shape: EvaluatorOutputShape,
    generated: String,
    constraints: String,
    casePolicy: String = InstructionConstraintsEvaluator.CASE_SENSITIVE,
    extras: Map<String, String> = emptyMap(),
    expectedCode: EvaluatorOutcomeCode,
    expectedScore: Double,
) = EvaluatorV1CorpusCase(
    id = id,
    shape = shape,
    expected = null,
    generated = generated,
    spec = EvaluatorSpec(
        type = EvaluatorType.INSTRUCTION_CONSTRAINTS,
        version = InstructionConstraintsEvaluator.VERSION,
        parameters = mapOf(
            InstructionConstraintsEvaluator.PARAM_CONSTRAINTS to constraints,
            InstructionConstraintsEvaluator.PARAM_CASE to casePolicy,
        ) + extras,
    ),
    expectedCode = expectedCode,
    expectedScore = expectedScore,
)
