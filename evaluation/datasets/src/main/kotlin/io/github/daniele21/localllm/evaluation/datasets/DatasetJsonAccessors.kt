package io.github.daniele21.localllm.evaluation.datasets

internal fun requireFields(value: JsonValue.ObjectValue, required: Set<String>, optional: Set<String>, lineNumber: Int) {
    val keys = value.fields.keys
    if (!keys.containsAll(required)) {
        datasetParseFailure(lineNumber, DatasetParseErrorCode.MISSING_FIELD)
    }
    if (!(required + optional).containsAll(keys)) {
        datasetParseFailure(lineNumber, DatasetParseErrorCode.UNKNOWN_FIELD)
    }
}

internal fun JsonValue.ObjectValue.requireString(name: String, lineNumber: Int): String = (fields[name] as? JsonValue.StringValue)?.value
    ?: datasetParseFailure(lineNumber, DatasetParseErrorCode.INVALID_FIELD)

internal fun JsonValue.ObjectValue.requireInt(name: String, lineNumber: Int): Int =
    (fields[name] as? JsonValue.NumberValue)?.value?.let { number ->
        runCatching { number.intValueExact() }.getOrNull()
    } ?: datasetParseFailure(lineNumber, DatasetParseErrorCode.INVALID_FIELD)

internal fun JsonValue.ObjectValue.optionalInt(name: String, lineNumber: Int): Int? {
    val value = fields[name] ?: return null
    return (value as? JsonValue.NumberValue)?.value?.let { number ->
        runCatching { number.intValueExact() }.getOrNull()
    } ?: datasetParseFailure(lineNumber, DatasetParseErrorCode.INVALID_FIELD)
}

internal fun JsonValue.ObjectValue.requireObject(name: String, lineNumber: Int): JsonValue.ObjectValue =
    fields[name] as? JsonValue.ObjectValue
        ?: datasetParseFailure(lineNumber, DatasetParseErrorCode.INVALID_FIELD)

internal fun JsonValue.ObjectValue.optionalObject(name: String, lineNumber: Int): JsonValue.ObjectValue? {
    val value = fields[name] ?: return null
    return value as? JsonValue.ObjectValue ?: datasetParseFailure(lineNumber, DatasetParseErrorCode.INVALID_FIELD)
}

internal fun JsonValue.ObjectValue.requireArray(name: String, lineNumber: Int): JsonValue.ArrayValue = fields[name] as? JsonValue.ArrayValue
    ?: datasetParseFailure(lineNumber, DatasetParseErrorCode.INVALID_FIELD)

internal fun JsonValue.ObjectValue.optionalArray(name: String, lineNumber: Int): JsonValue.ArrayValue? {
    val value = fields[name] ?: return null
    return value as? JsonValue.ArrayValue ?: datasetParseFailure(lineNumber, DatasetParseErrorCode.INVALID_FIELD)
}

internal fun JsonValue.ObjectValue.toStringMap(lineNumber: Int): Map<String, String> = fields.mapValues { (_, value) ->
    (value as? JsonValue.StringValue)?.value ?: datasetParseFailure(lineNumber, DatasetParseErrorCode.INVALID_FIELD)
}

internal fun datasetParseFailure(lineNumber: Int, code: DatasetParseErrorCode): Nothing =
    throw EvaluationDatasetParseException(lineNumber, code)
