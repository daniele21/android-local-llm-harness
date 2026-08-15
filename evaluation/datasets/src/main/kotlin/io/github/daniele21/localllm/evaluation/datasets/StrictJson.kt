package io.github.daniele21.localllm.evaluation.datasets

import java.math.BigDecimal

internal sealed interface JsonValue {
    data class ObjectValue(val fields: Map<String, JsonValue>) : JsonValue

    data class ArrayValue(val values: List<JsonValue>) : JsonValue

    data class StringValue(val value: String) : JsonValue

    data class NumberValue(val value: BigDecimal) : JsonValue

    data class BooleanValue(val value: Boolean) : JsonValue

    data object NullValue : JsonValue
}

internal class StrictJsonParser private constructor(source: String) {
    private val cursor = JsonCursor(source)

    fun parseRootObject(): JsonValue.ObjectValue {
        require(cursor.length <= MAX_INPUT_LENGTH) { "JSON input exceeds dataset parser bound" }
        cursor.skipWhitespace()
        val value = parseValue(depth = 0)
        cursor.skipWhitespace()
        require(cursor.isAtEnd) { "Trailing JSON content" }
        return value as? JsonValue.ObjectValue ?: error("JSON root must be an object")
    }

    private fun parseValue(depth: Int): JsonValue {
        require(depth <= MAX_DEPTH) { "JSON nesting exceeds dataset parser bound" }
        cursor.skipWhitespace()
        require(!cursor.isAtEnd) { "Unexpected end of JSON" }
        return when (cursor.peek()) {
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
        cursor.expect('{')
        cursor.skipWhitespace()
        if (cursor.consumeIf('}')) return JsonValue.ObjectValue(emptyMap())
        val fields = linkedMapOf<String, JsonValue>()
        while (true) {
            cursor.skipWhitespace()
            require(cursor.peek() == '"') { "JSON object key must be a string" }
            val key = parseString()
            require(key !in fields) { "Duplicate JSON object key" }
            require(fields.size < MAX_OBJECT_FIELDS) { "JSON object exceeds dataset parser bound" }
            cursor.skipWhitespace()
            cursor.expect(':')
            fields[key] = parseValue(depth)
            cursor.skipWhitespace()
            if (cursor.consumeIf('}')) break
            cursor.expect(',')
        }
        return JsonValue.ObjectValue(fields)
    }

    private fun parseArray(depth: Int): JsonValue.ArrayValue {
        cursor.expect('[')
        cursor.skipWhitespace()
        if (cursor.consumeIf(']')) return JsonValue.ArrayValue(emptyList())
        val values = mutableListOf<JsonValue>()
        while (true) {
            require(values.size < MAX_ARRAY_ITEMS) { "JSON array exceeds dataset parser bound" }
            values += parseValue(depth)
            cursor.skipWhitespace()
            if (cursor.consumeIf(']')) break
            cursor.expect(',')
        }
        return JsonValue.ArrayValue(values)
    }

    private fun parseString(): String {
        cursor.expect('"')
        val result = StringBuilder()
        while (!cursor.isAtEnd) {
            val char = cursor.advance()
            when {
                char == '"' -> return result.toString()
                char == '\\' -> result.append(parseEscape())
                char.code < 0x20 -> error("Unescaped control character in JSON string")
                else -> result.append(char)
            }
            require(result.length <= MAX_STRING_LENGTH) { "JSON string exceeds dataset parser bound" }
        }
        error("Unterminated JSON string")
    }

    private fun parseEscape(): Char {
        require(!cursor.isAtEnd) { "Unterminated JSON escape" }
        return when (val escaped = cursor.advance()) {
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
        val hex = cursor.read(4)
        require(hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) { "Invalid JSON unicode escape" }
        return hex.toInt(16).toChar()
    }

    private fun parseNumber(): BigDecimal {
        val start = cursor.index
        cursor.consumeIf('-')
        when {
            cursor.consumeIf('0') -> require(cursor.peek()?.isDigit() != true) { "JSON number has a leading zero" }
            cursor.peek()?.let { it in '1'..'9' } == true -> cursor.consumeDigits()
            else -> error("Invalid JSON number")
        }
        if (cursor.consumeIf('.')) {
            require(cursor.peek()?.isDigit() == true) { "JSON fraction requires digits" }
            cursor.consumeDigits()
        }
        if (cursor.peek() == 'e' || cursor.peek() == 'E') {
            cursor.advance()
            if (cursor.peek() == '+' || cursor.peek() == '-') cursor.advance()
            require(cursor.peek()?.isDigit() == true) { "JSON exponent requires digits" }
            cursor.consumeDigits()
        }
        return BigDecimal(cursor.sliceFrom(start))
    }

    private fun <T : JsonValue> parseLiteral(literal: String, value: T): T {
        cursor.consumeLiteral(literal)
        return value
    }

    companion object {
        private const val MAX_INPUT_LENGTH = 1_048_576
        private const val MAX_STRING_LENGTH = 262_144
        private const val MAX_ARRAY_ITEMS = 16_384
        private const val MAX_OBJECT_FIELDS = 256
        private const val MAX_DEPTH = 48

        fun parseObject(source: String): JsonValue.ObjectValue = StrictJsonParser(source).parseRootObject()
    }
}

private class JsonCursor(private val source: String) {
    var index: Int = 0
        private set

    val length: Int
        get() = source.length

    val isAtEnd: Boolean
        get() = index >= source.length

    fun peek(): Char? = source.getOrNull(index)

    fun advance(): Char {
        require(!isAtEnd) { "Unexpected end of JSON" }
        return source[index++]
    }

    fun consumeIf(expected: Char): Boolean {
        if (peek() != expected) return false
        index += 1
        return true
    }

    fun expect(expected: Char) {
        require(consumeIf(expected)) { "Expected JSON token $expected" }
    }

    fun skipWhitespace() {
        while (true) {
            when (peek()) {
                ' ', '\n', '\r', '\t' -> index += 1
                else -> return
            }
        }
    }

    fun consumeDigits() {
        while (peek()?.isDigit() == true) index += 1
    }

    fun consumeLiteral(literal: String) {
        require(source.regionMatches(index, literal, 0, literal.length)) { "Invalid JSON literal" }
        index += literal.length
    }

    fun read(count: Int): String {
        require(index + count <= source.length) { "Incomplete JSON unicode escape" }
        val value = source.substring(index, index + count)
        index += count
        return value
    }

    fun sliceFrom(start: Int): String = source.substring(start, index)
}
