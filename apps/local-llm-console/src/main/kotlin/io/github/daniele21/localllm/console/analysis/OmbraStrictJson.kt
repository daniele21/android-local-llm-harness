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
        if (input.length > maxInputCharacters) throw OmbraJsonException(OmbraJsonFailureCode.INPUT_TOO_LARGE)
        val cursor = Cursor(input)
        val value = parseValue(cursor, depth = 0)
        cursor.skipWhitespace()
        if (!cursor.isAtEnd()) throw OmbraJsonException(OmbraJsonFailureCode.INVALID_JSON)
        return value
    }

    private fun parseValue(cursor: Cursor, depth: Int): OmbraJsonValue {
        if (depth > maxDepth) throw OmbraJsonException(OmbraJsonFailureCode.DEPTH_EXCEEDED)
        cursor.skipWhitespace()
        return when (cursor.peek()) {
            '{' -> parseObject(cursor, depth + 1)
            '[' -> parseArray(cursor, depth + 1)
            '"' -> OmbraJsonValue.StringValue(parseString(cursor))
            't' -> parseLiteral(cursor, "true", OmbraJsonValue.BooleanValue(true))
            'f' -> parseLiteral(cursor, "false", OmbraJsonValue.BooleanValue(false))
            'n' -> parseLiteral(cursor, "null", OmbraJsonValue.NullValue)
            '-', in '0'..'9' -> OmbraJsonValue.IntegerValue(parseInteger(cursor))
            else -> throw OmbraJsonException(OmbraJsonFailureCode.INVALID_JSON)
        }
    }

    private fun parseObject(cursor: Cursor, depth: Int): OmbraJsonValue.ObjectValue {
        cursor.expect('{')
        cursor.skipWhitespace()
        if (cursor.consumeIf('}')) return OmbraJsonValue.ObjectValue(emptyMap())
        val fields = linkedMapOf<String, OmbraJsonValue>()
        while (true) {
            if (fields.size >= maxContainerEntries) throw OmbraJsonException(OmbraJsonFailureCode.CONTAINER_TOO_LARGE)
            cursor.skipWhitespace()
            if (cursor.peek() != '"') throw OmbraJsonException(OmbraJsonFailureCode.INVALID_JSON)
            val key = parseString(cursor)
            if (key in fields) throw OmbraJsonException(OmbraJsonFailureCode.DUPLICATE_KEY)
            cursor.skipWhitespace()
            cursor.expect(':')
            fields[key] = parseValue(cursor, depth)
            cursor.skipWhitespace()
            when {
                cursor.consumeIf('}') -> return OmbraJsonValue.ObjectValue(fields)
                cursor.consumeIf(',') -> Unit
                else -> throw OmbraJsonException(OmbraJsonFailureCode.INVALID_JSON)
            }
        }
    }

    private fun parseArray(cursor: Cursor, depth: Int): OmbraJsonValue.ArrayValue {
        cursor.expect('[')
        cursor.skipWhitespace()
        if (cursor.consumeIf(']')) return OmbraJsonValue.ArrayValue(emptyList())
        val values = mutableListOf<OmbraJsonValue>()
        while (true) {
            if (values.size >= maxContainerEntries) throw OmbraJsonException(OmbraJsonFailureCode.CONTAINER_TOO_LARGE)
            values += parseValue(cursor, depth)
            cursor.skipWhitespace()
            when {
                cursor.consumeIf(']') -> return OmbraJsonValue.ArrayValue(values)
                cursor.consumeIf(',') -> Unit
                else -> throw OmbraJsonException(OmbraJsonFailureCode.INVALID_JSON)
            }
        }
    }

    private fun parseString(cursor: Cursor): String {
        cursor.expect('"')
        val value = StringBuilder()
        while (true) {
            val character = cursor.take() ?: throw OmbraJsonException(OmbraJsonFailureCode.INVALID_JSON)
            when {
                character == '"' -> return value.toString()
                character == '\\' -> appendEscaped(cursor, value)
                character.code < 0x20 -> throw OmbraJsonException(OmbraJsonFailureCode.INVALID_JSON)
                else -> value.append(character)
            }
            if (value.length > maxStringCharacters) throw OmbraJsonException(OmbraJsonFailureCode.STRING_TOO_LARGE)
        }
    }

    private fun appendEscaped(cursor: Cursor, value: StringBuilder) {
        when (val escape = cursor.take()) {
            '"', '\\', '/' -> value.append(escape)
            'b' -> value.append('\b')
            'f' -> value.append('\u000C')
            'n' -> value.append('\n')
            'r' -> value.append('\r')
            't' -> value.append('\t')
            'u' -> appendUnicodeEscape(cursor, value)
            else -> throw OmbraJsonException(OmbraJsonFailureCode.INVALID_JSON)
        }
    }

    private fun appendUnicodeEscape(cursor: Cursor, value: StringBuilder) {
        val first = readHexCodeUnit(cursor)
        if (first.isHighSurrogate()) {
            cursor.expect('\\')
            cursor.expect('u')
            val second = readHexCodeUnit(cursor)
            if (!second.isLowSurrogate()) throw OmbraJsonException(OmbraJsonFailureCode.INVALID_JSON)
            value.append(first)
            value.append(second)
        } else {
            if (first.isLowSurrogate()) throw OmbraJsonException(OmbraJsonFailureCode.INVALID_JSON)
            value.append(first)
        }
    }

    private fun readHexCodeUnit(cursor: Cursor): Char {
        var code = 0
        repeat(4) {
            val digit = cursor.take()?.digitToIntOrNull(16) ?: throw OmbraJsonException(OmbraJsonFailureCode.INVALID_JSON)
            code = code * 16 + digit
        }
        return code.toChar()
    }

    private fun parseInteger(cursor: Cursor): Long {
        val start = cursor.position
        cursor.consumeIf('-')
        val first = cursor.peek()
        when {
            first == '0' -> cursor.take()
            first in '1'..'9' -> consumeDigits(cursor)
            else -> throw OmbraJsonException(OmbraJsonFailureCode.INVALID_JSON)
        }
        if (cursor.peek() == '.' || cursor.peek() == 'e' || cursor.peek() == 'E') {
            throw OmbraJsonException(OmbraJsonFailureCode.INVALID_JSON)
        }
        return cursor.substring(start, cursor.position).toLongOrNull()
            ?: throw OmbraJsonException(OmbraJsonFailureCode.INVALID_JSON)
    }

    private fun consumeDigits(cursor: Cursor) {
        while (cursor.peek() in '0'..'9') cursor.take()
    }

    private fun parseLiteral(cursor: Cursor, literal: String, value: OmbraJsonValue): OmbraJsonValue {
        literal.forEach(cursor::expect)
        return value
    }

    private class Cursor(private val input: String) {
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
            if (!consumeIf(expected)) throw OmbraJsonException(OmbraJsonFailureCode.INVALID_JSON)
        }

        fun skipWhitespace() {
            while (peek() == ' ' || peek() == '\n' || peek() == '\r' || peek() == '\t') position += 1
        }

        fun substring(start: Int, end: Int): String = input.substring(start, end)
    }

    private companion object {
        const val MAX_INPUT_CHARACTERS = 262_144
        const val MAX_DEPTH = 8
        const val MAX_CONTAINER_ENTRIES = 512
        const val MAX_STRING_CHARACTERS = 4_096
    }
}
