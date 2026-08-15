package io.github.daniele21.localllm.evaluation.datasets

import io.github.daniele21.localllm.evaluation.EVALUATION_DATASET_CASE_SCHEMA_VERSION
import io.github.daniele21.localllm.evaluation.EvaluationCaseId
import io.github.daniele21.localllm.evaluation.EvaluationCaseMessage
import io.github.daniele21.localllm.evaluation.EvaluationCaseOutputContract
import io.github.daniele21.localllm.evaluation.EvaluationCategoryId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetCaseV1
import io.github.daniele21.localllm.evaluation.EvaluationExpectedAnswer
import io.github.daniele21.localllm.evaluation.EvaluationExpectedAnswerKind
import io.github.daniele21.localllm.evaluation.EvaluationMessageRole
import io.github.daniele21.localllm.evaluation.EvaluationResponseFormat
import io.github.daniele21.localllm.evaluation.EvaluatorSpec
import io.github.daniele21.localllm.evaluation.EvaluatorType
import io.github.daniele21.localllm.evaluation.EvaluatorVersion

internal fun decodeCase(root: JsonValue.ObjectValue, lineNumber: Int): EvaluationDatasetCaseV1 {
    requireFields(
        root,
        required = setOf("schemaVersion", "id", "categoryId", "messages", "expected", "evaluator"),
        optional = setOf("output", "metadata"),
        lineNumber = lineNumber,
    )
    val schemaVersion = root.requireInt("schemaVersion", lineNumber)
    if (schemaVersion != EVALUATION_DATASET_CASE_SCHEMA_VERSION) {
        datasetParseFailure(lineNumber, DatasetParseErrorCode.UNSUPPORTED_SCHEMA)
    }
    return wrapInvalidField(lineNumber) {
        EvaluationDatasetCaseV1(
            schemaVersion = schemaVersion,
            id = EvaluationCaseId(root.requireString("id", lineNumber)),
            categoryId = EvaluationCategoryId(root.requireString("categoryId", lineNumber)),
            messages = root.requireArray("messages", lineNumber).values.map { decodeMessage(it, lineNumber) },
            expected = decodeExpected(root.requireObject("expected", lineNumber), lineNumber),
            evaluator = decodeEvaluator(root.requireObject("evaluator", lineNumber), lineNumber),
            output = root.optionalObject("output", lineNumber)?.let { decodeOutput(it, lineNumber) }
                ?: EvaluationCaseOutputContract(),
            metadata = root.optionalObject("metadata", lineNumber)?.toStringMap(lineNumber) ?: emptyMap(),
        )
    }
}

private fun decodeMessage(value: JsonValue, lineNumber: Int): EvaluationCaseMessage {
    val objectValue = value as? JsonValue.ObjectValue
        ?: datasetParseFailure(lineNumber, DatasetParseErrorCode.INVALID_FIELD)
    requireFields(objectValue, setOf("role", "content"), emptySet(), lineNumber)
    return wrapInvalidField(lineNumber) {
        EvaluationCaseMessage(
            role = enumValue<EvaluationMessageRole>(objectValue.requireString("role", lineNumber), lineNumber),
            content = objectValue.requireString("content", lineNumber),
        )
    }
}

private fun decodeExpected(value: JsonValue.ObjectValue, lineNumber: Int): EvaluationExpectedAnswer {
    requireFields(value, setOf("kind", "value"), emptySet(), lineNumber)
    return wrapInvalidField(lineNumber) {
        EvaluationExpectedAnswer(
            kind = enumValue<EvaluationExpectedAnswerKind>(value.requireString("kind", lineNumber), lineNumber),
            value = value.requireString("value", lineNumber),
        )
    }
}

private fun decodeEvaluator(value: JsonValue.ObjectValue, lineNumber: Int): EvaluatorSpec {
    requireFields(value, setOf("type", "version"), setOf("parameters"), lineNumber)
    return wrapInvalidField(lineNumber) {
        EvaluatorSpec(
            type = enumValue<EvaluatorType>(value.requireString("type", lineNumber), lineNumber),
            version = EvaluatorVersion(value.requireInt("version", lineNumber)),
            parameters = value.optionalObject("parameters", lineNumber)?.toStringMap(lineNumber) ?: emptyMap(),
        )
    }
}

private fun decodeOutput(value: JsonValue.ObjectValue, lineNumber: Int): EvaluationCaseOutputContract {
    requireFields(value, setOf("responseFormat"), setOf("maxOutputTokens", "stopSequences"), lineNumber)
    return wrapInvalidField(lineNumber) {
        EvaluationCaseOutputContract(
            responseFormat = enumValue<EvaluationResponseFormat>(
                value.requireString("responseFormat", lineNumber),
                lineNumber,
            ),
            maxOutputTokens = value.optionalInt("maxOutputTokens", lineNumber),
            stopSequences = value.optionalArray("stopSequences", lineNumber)?.values?.map { item ->
                (item as? JsonValue.StringValue)?.value
                    ?: datasetParseFailure(lineNumber, DatasetParseErrorCode.INVALID_FIELD)
            } ?: emptyList(),
        )
    }
}

private inline fun <reified T : Enum<T>> enumValue(raw: String, lineNumber: Int): T = runCatching {
    enumValueOf<T>(raw)
}.getOrElse {
    datasetParseFailure(lineNumber, DatasetParseErrorCode.INVALID_FIELD)
}

private inline fun <T> wrapInvalidField(lineNumber: Int, block: () -> T): T = try {
    block()
} catch (failure: EvaluationDatasetParseException) {
    throw failure
} catch (_: IllegalArgumentException) {
    datasetParseFailure(lineNumber, DatasetParseErrorCode.INVALID_FIELD)
}
