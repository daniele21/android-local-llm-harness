package io.github.daniele21.localllm.evaluation.evaluators

import io.github.daniele21.localllm.evaluation.EvaluationOutcome
import io.github.daniele21.localllm.evaluation.EvaluatorOutcomeCode
import io.github.daniele21.localllm.evaluation.EvaluatorSpec
import io.github.daniele21.localllm.evaluation.EvaluatorType
import io.github.daniele21.localllm.evaluation.EvaluatorVersion
import io.github.daniele21.localllm.evaluation.NormalizedScore
import java.math.BigDecimal

class JsonFieldsEvaluator {
    fun evaluate(expected: String, generated: String, spec: EvaluatorSpec): EvaluationOutcome {
        require(spec.type == EvaluatorType.JSON_FIELDS && spec.version == VERSION) {
            "JSON fields evaluator requires JSON_FIELDS v${VERSION.value} spec"
        }
        require(REGISTRATION.parameters.validate(spec)) { "Invalid JSON fields evaluator parameters" }

        val requiredFields = parseRequiredFields(spec.parameters.getValue(PARAM_REQUIRED_FIELDS))
        val expectedObject = StrictJsonParser.parseObject(expected)
            ?: throw IllegalArgumentException("Expected JSON value must be a valid bounded JSON object")
        require(requiredFields.all(expectedObject.fields::containsKey)) {
            "Every required JSON field must exist in the expected object"
        }
        val generatedObject = StrictJsonParser.parseObject(generated)
            ?: return EvaluationOutcome(NormalizedScore(0.0), EvaluatorOutcomeCode.INVALID_OUTPUT)

        val matched = requiredFields.count { field ->
            val actual = generatedObject.fields[field]
            actual != null && jsonEquals(expectedObject.fields.getValue(field), actual)
        }
        return when (matched) {
            requiredFields.size -> EvaluationOutcome(NormalizedScore(1.0), EvaluatorOutcomeCode.CORRECT)
            0 -> EvaluationOutcome(NormalizedScore(0.0), EvaluatorOutcomeCode.INCORRECT)
            else -> EvaluationOutcome(
                score = NormalizedScore(matched.toDouble() / requiredFields.size.toDouble()),
                code = EvaluatorOutcomeCode.PARTIAL,
            )
        }
    }

    private fun parseRequiredFields(raw: String): List<String> {
        val fields = raw.split(',').map(String::trim)
        require(fields.size in 1..MAX_REQUIRED_FIELDS) {
            "JSON required fields must contain 1..$MAX_REQUIRED_FIELDS entries"
        }
        require(fields.all(FIELD_NAME::matches)) { "JSON required field names are invalid" }
        require(fields.distinct().size == fields.size) { "JSON required fields must be unique" }
        return fields
    }

    companion object {
        val VERSION = EvaluatorVersion(1)
        const val PARAM_REQUIRED_FIELDS = "required_fields"

        val REGISTRATION = EvaluatorRegistration(
            key = EvaluatorKey(EvaluatorType.JSON_FIELDS, VERSION),
            parameters = EvaluatorParameterPolicy(requiredKeys = setOf(PARAM_REQUIRED_FIELDS)),
        )

        private const val MAX_REQUIRED_FIELDS = 32
        private val FIELD_NAME = Regex("[A-Za-z_][A-Za-z0-9_.-]{0,63}")
    }
}

private sealed interface JsonValue {
    data class ObjectValue(val fields: Map<String, JsonValue>) : JsonValue

    data class ArrayValue(val values: List<JsonValue>) : JsonValue

    data class StringValue(val value: String) : JsonValue

    data class NumberValue(val value: BigDecimal) : JsonValue

    data class BooleanValue(val value: Boolean) : JsonValue

    data object NullValue : JsonValue
}

private fun jsonEquals(expected: JsonValue, actual: JsonValue): Boolean = when {
    expected is JsonValue.NumberValue && actual is JsonValue.NumberValue -> expected.value.compareTo(actual.value) == 0
    expected is JsonValue.ObjectValue && actual is JsonValue.ObjectValue -> {
        expected.fields.keys == actual.fields.keys && expected.fields.all { (key, value) ->
            jsonEquals(value, actual.fields.getValue(key))
        }
    }
    expected is JsonValue.ArrayValue && actual is JsonValue.ArrayValue -> {
        expected.values.size == actual.values.size && expected.values.indices.all { index ->
            jsonEquals(expected.values[index], actual.values[index])
        }
    }
    else -> expected == actual
}

private class StrictJsonParser private constructor(private val source: String) {
    private var index = 0

    fun parseRootObject(): JsonValue.ObjectValue? = runCatching {
        require(source.length <= MAX_INPUT_LENGTH) { "JSON input exceeds evaluator bound" }
        skipWhitespace()
        val value = parseValue(depth = 0)
        skipWhitespace()
        require(index == source.length) { "Trailing JSON content" }
        value as? JsonValue.ObjectValue ?: error("JSON root must be an object")
    }.getOrNull()

    private fun parseValue(depth: Int): JsonValue {
        require(depth <= MAX_DEPTH) { "JSON nesting exceeds evaluator bound" }
        skipWhitespace()
        require(index < source.length) { "Unexpected end of JSON" }
        return when (source[index]) {
            '{' -> parseObject(depth + 1)
            '[' -> parseArray(depth + 1)
            '"' -> JsonValue.StringValue(parseString())
            't' -> parseLiteral("true", JsonValue.BooleanValue(true))
            'f' -> parseLiteral("false", JsonValue.BooleanValue(false))
            'n' -> parseLiteral("null", JsonValue.NullValue)
            '-', in '0'..'9' -> JsonValue.NumberValue(parseNumber())
            else -> error("Invalid JSON token")
        }
    }

    private fun parseObject(depth: Int): JsonValue.ObjectValue {
        expect('{')
        skipWhitespace()
        if (consumeIf('}')) return JsonValue.ObjectValue(emptyMap())
        val fields = linkedMapOf<String, JsonValue>()
        while (true) {
            skipWhitespace()
            require(peek() == '"') { "JSON object key must be a string" }
            val key = parseString()
            require(key !in fields) { "Duplicate JSON object key" }
            skipWhitespace()
            expect(':')
            fields[key] = parseValue(depth)
            skipWhitespace()
            if (consumeIf('}')) break
            expect(',')
        }
        return JsonValue.ObjectValue(fields)
    }

    private fun parseArray(depth: Int): JsonValue.ArrayValue {
        expect('[')
        skipWhitespace()
        if (consumeIf(']')) return JsonValue.ArrayValue(emptyList())
        val values = mutableListOf<JsonValue>()
        while (true) {
            require(values.size < MAX_ARRAY_ITEMS) { "JSON array exceeds evaluator bound" }
            values += parseValue(depth)
            skipWhitespace()
            if (consumeIf(']')) break
            expect(',')
        }
        return JsonValue.ArrayValue(values)
    }

    private fun parseString(): String {
        expect('"')
        val result = StringBuilder()
        while (index < source.length) {
            val char = source[index++]
            when {
                char == '"' -> return result.toString()
                char == '\\' -> result.append(parseEscape())
                char.code < 0x20 -> error("Unescaped control character in JSON string")
                else -> result.append(char)
            }
            require(result.length <= MAX_STRING_LENGTH) { "JSON string exceeds evaluator bound" }
        }
        error("Unterminated JSON string")
    }

    private fun parseEscape(): Char {
        require(index < source.length) { "Unterminated JSON escape" }
        return when (val escaped = source[index++]) {
            '"', '\\', '/' -> escaped
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> parseUnicodeEscape()
            else -> error("Invalid JSON escape")
        }
    }

    private fun parseUnicodeEscape(): Char {
        require(index + 4 <= source.length) { "Incomplete JSON unicode escape" }
        val hex = source.substring(index, index + 4)
        require(hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) { "Invalid JSON unicode escape" }
        index += 4
        return hex.toInt(16).toChar()
    }

    private fun parseNumber(): BigDecimal {
        val start = index
        consumeIf('-')
        when {
            consumeIf('0') -> require(peek()?.isDigit() != true) { "JSON number has a leading zero" }
            peek() in '1'..'9' -> consumeDigits()
            else -> error("Invalid JSON number")
        }
        if (consumeIf('.')) {
            require(peek()?.isDigit() == true) { "JSON fraction requires digits" }
            consumeDigits()
        }
        if (peek() == 'e' || peek() == 'E') {
            index += 1
            if (peek() == '+' || peek() == '-') index += 1
            require(peek()?.isDigit() == true) { "JSON exponent requires digits" }
            consumeDigits()
        }
        return BigDecimal(source.substring(start, index))
    }

    private fun consumeDigits() {
        while (peek()?.isDigit() == true) index += 1
    }

    private fun <T : JsonValue> parseLiteral(literal: String, value: T): T {
        require(source.regionMatches(index, literal, 0, literal.length)) { "Invalid JSON literal" }
        index += literal.length
        return value
    }

    private fun skipWhitespace() {
        while (peek() == ' ' || peek() == '\n' || peek() == '\r' || peek() == '\t') index += 1
    }

    private fun expect(expected: Char) {
        require(consumeIf(expected)) { "Expected JSON token $expected" }
    }

    private fun consumeIf(expected: Char): Boolean {
        if (peek() != expected) return false
        index += 1
        return true
    }

    private fun peek(): Char? = source.getOrNull(index)

    companion object {
        private const val MAX_INPUT_LENGTH = 65_536
        private const val MAX_STRING_LENGTH = 16_384
        private const val MAX_ARRAY_ITEMS = 1_024
        private const val MAX_DEPTH = 32

        fun parseObject(source: String): JsonValue.ObjectValue? = StrictJsonParser(source).parseRootObject()
    }
}
