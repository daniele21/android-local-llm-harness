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
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

enum class DatasetParseErrorCode {
    UTF8_BOM,
    CR_LINE_ENDING,
    MISSING_LF_TERMINATOR,
    EMPTY_LINE,
    LINE_TOO_LONG,
    TOO_MANY_CASES,
    MALFORMED_UTF8,
    MALFORMED_JSON,
    UNKNOWN_FIELD,
    MISSING_FIELD,
    INVALID_FIELD,
    UNSUPPORTED_SCHEMA,
}

class EvaluationDatasetParseException(
    val lineNumber: Int,
    val code: DatasetParseErrorCode,
) : IllegalArgumentException("Evaluation dataset parse failure at line $lineNumber: $code")

data class EvaluationDatasetParserLimits(
    val maxCases: Int = 10_000,
    val maxLineBytes: Int = 1_048_576,
) {
    init {
        require(maxCases > 0) { "Dataset parser max cases must be positive" }
        require(maxLineBytes > 0) { "Dataset parser max line bytes must be positive" }
    }
}

class EvaluationDatasetJsonlParser(
    private val limits: EvaluationDatasetParserLimits = EvaluationDatasetParserLimits(),
) {
    fun parse(input: InputStream): List<EvaluationDatasetCaseV1> = buildList {
        parse(input) { case -> add(case) }
    }

    fun parse(input: InputStream, onCase: (EvaluationDatasetCaseV1) -> Unit) {
        val reader = LfJsonlLineReader(input, limits.maxLineBytes)
        var caseCount = 0
        while (true) {
            val rawLine = reader.nextLine() ?: break
            caseCount += 1
            if (caseCount > limits.maxCases) {
                throw EvaluationDatasetParseException(rawLine.number, DatasetParseErrorCode.TOO_MANY_CASES)
            }
            val line = decodeUtf8(rawLine)
            if (line.isEmpty()) {
                throw EvaluationDatasetParseException(rawLine.number, DatasetParseErrorCode.EMPTY_LINE)
            }
            val root = try {
                StrictJsonParser.parseObject(line)
            } catch (_: RuntimeException) {
                throw EvaluationDatasetParseException(rawLine.number, DatasetParseErrorCode.MALFORMED_JSON)
            }
            onCase(decodeCase(root, rawLine.number))
        }
    }
}

private data class RawJsonlLine(val number: Int, val bytes: ByteArray)

private class LfJsonlLineReader(
    private val input: InputStream,
    private val maxLineBytes: Int,
) {
    private var lineNumber = 0
    private var firstLine = true

    fun nextLine(): RawJsonlLine? {
        val buffer = ByteArrayOutputStream()
        while (true) {
            when (val next = input.read()) {
                -1 -> {
                    if (buffer.size() == 0) return null
                    throw EvaluationDatasetParseException(lineNumber + 1, DatasetParseErrorCode.MISSING_LF_TERMINATOR)
                }

                CR_BYTE -> throw EvaluationDatasetParseException(lineNumber + 1, DatasetParseErrorCode.CR_LINE_ENDING)
                LF_BYTE -> return finishLine(buffer.toByteArray())
                else -> {
                    if (buffer.size() >= maxLineBytes) {
                        throw EvaluationDatasetParseException(lineNumber + 1, DatasetParseErrorCode.LINE_TOO_LONG)
                    }
                    buffer.write(next)
                }
            }
        }
    }

    private fun finishLine(bytes: ByteArray): RawJsonlLine {
        lineNumber += 1
        if (firstLine) {
            firstLine = false
            if (bytes.startsWithUtf8Bom()) {
                throw EvaluationDatasetParseException(lineNumber, DatasetParseErrorCode.UTF8_BOM)
            }
        }
        return RawJsonlLine(lineNumber, bytes)
    }

    private companion object {
        const val LF_BYTE = 0x0A
        const val CR_BYTE = 0x0D
    }
}

private fun ByteArray.startsWithUtf8Bom(): Boolean =
    size >= 3 && this[0] == 0xEF.toByte() && this[1] == 0xBB.toByte() && this[2] == 0xBF.toByte()

private fun decodeUtf8(line: RawJsonlLine): String = try {
    StandardCharsets.UTF_8
        .newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(line.bytes))
        .toString()
} catch (_: CharacterCodingException) {
    throw EvaluationDatasetParseException(line.number, DatasetParseErrorCode.MALFORMED_UTF8)
}

private fun decodeCase(root: JsonValue.ObjectValue, lineNumber: Int): EvaluationDatasetCaseV1 {
    requireFields(
        root,
        required = setOf("schemaVersion", "id", "categoryId", "messages", "expected", "evaluator"),
        optional = setOf("output", "metadata"),
        lineNumber = lineNumber,
    )
    val schemaVersion = root.requireInt("schemaVersion", lineNumber)
    if (schemaVersion != EVALUATION_DATASET_CASE_SCHEMA_VERSION) {
        throw EvaluationDatasetParseException(lineNumber, DatasetParseErrorCode.UNSUPPORTED_SCHEMA)
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
        ?: throw EvaluationDatasetParseException(lineNumber, DatasetParseErrorCode.INVALID_FIELD)
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
                    ?: throw EvaluationDatasetParseException(lineNumber, DatasetParseErrorCode.INVALID_FIELD)
            } ?: emptyList(),
        )
    }
}

private fun requireFields(
    value: JsonValue.ObjectValue,
    required: Set<String>,
    optional: Set<String>,
    lineNumber: Int,
) {
    val keys = value.fields.keys
    if (!keys.containsAll(required)) {
        throw EvaluationDatasetParseException(lineNumber, DatasetParseErrorCode.MISSING_FIELD)
    }
    if (!((required + optional).containsAll(keys))) {
        throw EvaluationDatasetParseException(lineNumber, DatasetParseErrorCode.UNKNOWN_FIELD)
    }
}

private fun JsonValue.ObjectValue.requireString(name: String, lineNumber: Int): String =
    (fields[name] as? JsonValue.StringValue)?.value
        ?: throw EvaluationDatasetParseException(lineNumber, DatasetParseErrorCode.INVALID_FIELD)

private fun JsonValue.ObjectValue.requireInt(name: String, lineNumber: Int): Int =
    (fields[name] as? JsonValue.NumberValue)?.value?.let { number ->
        runCatching { number.toIntExact() }.getOrNull()
    } ?: throw EvaluationDatasetParseException(lineNumber, DatasetParseErrorCode.INVALID_FIELD)

private fun JsonValue.ObjectValue.optionalInt(name: String, lineNumber: Int): Int? {
    val value = fields[name] ?: return null
    return (value as? JsonValue.NumberValue)?.value?.let { number ->
        runCatching { number.toIntExact() }.getOrNull()
    } ?: throw EvaluationDatasetParseException(lineNumber, DatasetParseErrorCode.INVALID_FIELD)
}

private fun JsonValue.ObjectValue.requireObject(name: String, lineNumber: Int): JsonValue.ObjectValue =
    fields[name] as? JsonValue.ObjectValue
        ?: throw EvaluationDatasetParseException(lineNumber, DatasetParseErrorCode.INVALID_FIELD)

private fun JsonValue.ObjectValue.optionalObject(name: String, lineNumber: Int): JsonValue.ObjectValue? {
    val value = fields[name] ?: return null
    return value as? JsonValue.ObjectValue
        ?: throw EvaluationDatasetParseException(lineNumber, DatasetParseErrorCode.INVALID_FIELD)
}

private fun JsonValue.ObjectValue.requireArray(name: String, lineNumber: Int): JsonValue.ArrayValue =
    fields[name] as? JsonValue.ArrayValue
        ?: throw EvaluationDatasetParseException(lineNumber, DatasetParseErrorCode.INVALID_FIELD)

private fun JsonValue.ObjectValue.optionalArray(name: String, lineNumber: Int): JsonValue.ArrayValue? {
    val value = fields[name] ?: return null
    return value as? JsonValue.ArrayValue
        ?: throw EvaluationDatasetParseException(lineNumber, DatasetParseErrorCode.INVALID_FIELD)
}

private fun JsonValue.ObjectValue.toStringMap(lineNumber: Int): Map<String, String> = fields.mapValues { (_, value) ->
    (value as? JsonValue.StringValue)?.value
        ?: throw EvaluationDatasetParseException(lineNumber, DatasetParseErrorCode.INVALID_FIELD)
}

private inline fun <reified T : Enum<T>> enumValue(raw: String, lineNumber: Int): T =
    runCatching { enumValueOf<T>(raw) }.getOrElse {
        throw EvaluationDatasetParseException(lineNumber, DatasetParseErrorCode.INVALID_FIELD)
    }

private inline fun <T> wrapInvalidField(lineNumber: Int, block: () -> T): T = try {
    block()
} catch (failure: EvaluationDatasetParseException) {
    throw failure
} catch (_: IllegalArgumentException) {
    throw EvaluationDatasetParseException(lineNumber, DatasetParseErrorCode.INVALID_FIELD)
}
