package io.github.daniele21.localllm.console.analysis

internal sealed interface OmbraJsonValue {
    data class ObjectValue(val fields: Map<String, OmbraJsonValue>) : OmbraJsonValue

    data class ArrayValue(val values: List<OmbraJsonValue>) : OmbraJsonValue

    data class StringValue(val value: String) : OmbraJsonValue

    data class IntegerValue(val value: Long) : OmbraJsonValue

    data class BooleanValue(val value: Boolean) : OmbraJsonValue

    data object NullValue : OmbraJsonValue
}

internal enum class OmbraJsonFailureCode {
    INPUT_TOO_LARGE,
    INVALID_JSON,
    DEPTH_EXCEEDED,
    CONTAINER_TOO_LARGE,
    STRING_TOO_LARGE,
    DUPLICATE_KEY,
}

internal class OmbraJsonException(val code: OmbraJsonFailureCode) : IllegalArgumentException("Invalid OMBRA JSON: $code")

/** Small dependency-free JSON reader with conservative bounds for untrusted model output. */
internal class OmbraStrictJsonReader(
    private val maxInputCharacters: Int = MAX_INPUT_CHARACTERS,
    private val maxDepth: Int = MAX_DEPTH,
    private val maxContainerEntries: Int = MAX_CONTAINER_ENTRIES,
    private val maxStringCharacters: Int = MAX_STRING_CHARACTERS,
) {
    fun parse(input: String): OmbraJsonValue {
        if (input.length > maxInputCharacters) failJson(OmbraJsonFailureCode.INPUT_TOO_LARGE)
        val cursor = OmbraJsonCursor(input)
        val value = parseValue(cursor, depth = 0)
        cursor.skipWhitespace()
        if (!cursor.isAtEnd()) failJson()
        return value
    }

    private fun parseValue(cursor: OmbraJsonCursor, depth: Int): OmbraJsonValue {
        if (depth > maxDepth) failJson(OmbraJsonFailureCode.DEPTH_EXCEEDED)
        cursor.skipWhitespace()
        val next = cursor.peek()
        return when {
            next == '{' -> parseObject(cursor, depth + 1)
            next == '[' -> parseArray(cursor, depth + 1)
            next == '"' -> OmbraJsonValue.StringValue(parseString(cursor))
            next == 't' -> cursor.consumeLiteral("true", OmbraJsonValue.BooleanValue(true))
            next == 'f' -> cursor.consumeLiteral("false", OmbraJsonValue.BooleanValue(false))
            next == 'n' -> cursor.consumeLiteral("null", OmbraJsonValue.NullValue)
            next == '-' || (next != null && next in '0'..'9') -> OmbraJsonValue.IntegerValue(parseInteger(cursor))
            else -> failJson()
        }
    }

    private fun parseObject(cursor: OmbraJsonCursor, depth: Int): OmbraJsonValue.ObjectValue {
        cursor.expect('{')
        cursor.skipWhitespace()
        if (cursor.consumeIf('}')) return OmbraJsonValue.ObjectValue(emptyMap())
        val fields = linkedMapOf<String, OmbraJsonValue>()
        while (true) {
            if (fields.size >= maxContainerEntries) failJson(OmbraJsonFailureCode.CONTAINER_TOO_LARGE)
            cursor.skipWhitespace()
            if (cursor.peek() != '"') failJson()
            val key = parseString(cursor)
            if (key in fields) failJson(OmbraJsonFailureCode.DUPLICATE_KEY)
            cursor.skipWhitespace()
            cursor.expect(':')
            fields[key] = parseValue(cursor, depth)
            cursor.skipWhitespace()
            when {
                cursor.consumeIf('}') -> return OmbraJsonValue.ObjectValue(fields)
                cursor.consumeIf(',') -> Unit
                else -> failJson()
            }
        }
    }

    private fun parseArray(cursor: OmbraJsonCursor, depth: Int): OmbraJsonValue.ArrayValue {
        cursor.expect('[')
        cursor.skipWhitespace()
        if (cursor.consumeIf(']')) return OmbraJsonValue.ArrayValue(emptyList())
        val values = mutableListOf<OmbraJsonValue>()
        while (true) {
            if (values.size >= maxContainerEntries) failJson(OmbraJsonFailureCode.CONTAINER_TOO_LARGE)
            values += parseValue(cursor, depth)
            cursor.skipWhitespace()
            when {
                cursor.consumeIf(']') -> return OmbraJsonValue.ArrayValue(values)
                cursor.consumeIf(',') -> Unit
                else -> failJson()
            }
        }
    }

    private fun parseString(cursor: OmbraJsonCursor): String {
        cursor.expect('"')
        val value = StringBuilder()
        while (true) {
            val character = cursor.take() ?: failJson()
            when {
                character == '"' -> return value.toString()
                character == '\\' -> appendEscaped(cursor, value)
                character.code < 0x20 -> failJson()
                else -> value.append(character)
            }
            if (value.length > maxStringCharacters) failJson(OmbraJsonFailureCode.STRING_TOO_LARGE)
        }
    }

    private fun appendEscaped(cursor: OmbraJsonCursor, value: StringBuilder) {
        when (val escape = cursor.take()) {
            '"', '\\', '/' -> value.append(escape)
            'b' -> value.append('\b')
            'f' -> value.append('\u000C')
            'n' -> value.append('\n')
            'r' -> value.append('\r')
            't' -> value.append('\t')
            'u' -> appendUnicodeEscape(cursor, value)
            else -> failJson()
        }
    }

    private fun appendUnicodeEscape(cursor: OmbraJsonCursor, value: StringBuilder) {
        val first = readHexCodeUnit(cursor)
        if (first.isHighSurrogate()) {
            cursor.expect('\\')
            cursor.expect('u')
            val second = readHexCodeUnit(cursor)
            if (!second.isLowSurrogate()) failJson()
            value.append(first)
            value.append(second)
        } else {
            if (first.isLowSurrogate()) failJson()
            value.append(first)
        }
    }

    private fun readHexCodeUnit(cursor: OmbraJsonCursor): Char {
        var code = 0
        repeat(4) {
            val digit = cursor.take()?.digitToIntOrNull(16) ?: failJson()
            code = code * 16 + digit
        }
        return code.toChar()
    }

    private fun parseInteger(cursor: OmbraJsonCursor): Long {
        val start = cursor.position
        cursor.consumeIf('-')
        val first = cursor.peek()
        when {
            first == '0' -> cursor.take()
            first != null && first in '1'..'9' -> cursor.consumeDigits()
            else -> failJson()
        }
        when (cursor.peek()) {
            '.', 'e', 'E' -> failJson()
            else -> Unit
        }
        return cursor.substring(start, cursor.position).toLongOrNull() ?: failJson()
    }

    private companion object {
        const val MAX_INPUT_CHARACTERS = 262_144
        const val MAX_DEPTH = 8
        const val MAX_CONTAINER_ENTRIES = 512
        const val MAX_STRING_CHARACTERS = 4_096
    }
}

private class OmbraJsonCursor(private val input: String) {
    var position: Int = 0
        private set

    fun isAtEnd(): Boolean = position == input.length

    fun peek(): Char? = input.getOrNull(position)

    fun take(): Char? = input.getOrNull(position)?.also { position += 1 }

    fun consumeIf(expected: Char): Boolean {
        if (peek() != expected) return false
        position += 1
        return true
    }

    fun expect(expected: Char) {
        if (!consumeIf(expected)) failJson()
    }

    fun skipWhitespace() {
        while (isJsonWhitespace(peek())) position += 1
    }

    fun consumeDigits() {
        while (peek()?.let { it in '0'..'9' } == true) position += 1
    }

    fun consumeLiteral(literal: String, value: OmbraJsonValue): OmbraJsonValue {
        literal.forEach(::expect)
        return value
    }

    fun substring(start: Int, end: Int): String = input.substring(start, end)
}

private fun isJsonWhitespace(character: Char?): Boolean =
    when (character) {
        ' ', '\n', '\r', '\t' -> true
        else -> false
    }

private fun failJson(code: OmbraJsonFailureCode = OmbraJsonFailureCode.INVALID_JSON): Nothing = throw OmbraJsonException(code)
