package io.github.daniele21.localllm.catalog

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

internal sealed interface CatalogJsonValue
internal data class CatalogJsonObject(val fields: LinkedHashMap<String, CatalogJsonValue>) : CatalogJsonValue
internal data class CatalogJsonArray(val values: List<CatalogJsonValue>) : CatalogJsonValue
internal data class CatalogJsonString(val value: String) : CatalogJsonValue
internal data class CatalogJsonNumber(val raw: String) : CatalogJsonValue
internal data class CatalogJsonBoolean(val value: Boolean) : CatalogJsonValue
internal data object CatalogJsonNull : CatalogJsonValue

internal class CatalogJsonSyntaxException(
    val code: CatalogCodecErrorCode,
    val position: Int,
) : IllegalArgumentException("Invalid JSON at position $position: $code")

internal class BoundedCatalogJsonParser(
    private val maxDepth: Int,
    private val maxNodes: Int,
    private val maxStringChars: Int,
) {
    init {
        require(maxDepth > 0)
        require(maxNodes > 0)
        require(maxStringChars > 0)
    }

    fun parse(bytes: ByteArray): CatalogJsonValue {
        val input = decodeUtf8(bytes)
        val state = ParserState(input)
        val value = state.parseValue(depth = 0)
        state.skipWhitespace()
        if (!state.atEnd()) state.fail(CatalogCodecErrorCode.MALFORMED_JSON)
        return value
    }

    private fun decodeUtf8(bytes: ByteArray): String {
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: java.nio.charset.CharacterCodingException) {
            throw CatalogJsonSyntaxException(CatalogCodecErrorCode.INVALID_UTF8, 0)
        }
    }

    @Suppress("TooManyFunctions")
    private inner class ParserState(private val input: String) {
        private var index = 0
        private var nodes = 0

        fun atEnd(): Boolean = index == input.length

        fun skipWhitespace() {
            while (index < input.length && input[index] in JSON_WHITESPACE) index += 1
        }

        fun parseValue(depth: Int): CatalogJsonValue {
            if (depth > maxDepth) fail(CatalogCodecErrorCode.JSON_LIMIT_EXCEEDED)
            skipWhitespace()
            countNode()
            return when (peek()) {
                '{' -> parseObject(depth)
                '[' -> parseArray(depth)
                '"' -> CatalogJsonString(parseString())
                't' -> parseLiteral("true", CatalogJsonBoolean(true))
                'f' -> parseLiteral("false", CatalogJsonBoolean(false))
                'n' -> parseLiteral("null", CatalogJsonNull)
                '-', in '0'..'9' -> CatalogJsonNumber(parseNumber())
                else -> fail(CatalogCodecErrorCode.MALFORMED_JSON)
            }
        }

        private fun parseObject(depth: Int): CatalogJsonObject {
            expect('{')
            skipWhitespace()
            val fields = linkedMapOf<String, CatalogJsonValue>()
            if (consumeIf('}')) return CatalogJsonObject(fields)
            while (true) {
                skipWhitespace()
                if (peek() != '"') fail(CatalogCodecErrorCode.MALFORMED_JSON)
                val key = parseString()
                if (key in fields) fail(CatalogCodecErrorCode.DUPLICATE_FIELD)
                skipWhitespace()
                expect(':')
                fields[key] = parseValue(depth + 1)
                skipWhitespace()
                when {
                    consumeIf('}') -> return CatalogJsonObject(fields)
                    consumeIf(',') -> Unit
                    else -> fail(CatalogCodecErrorCode.MALFORMED_JSON)
                }
            }
        }

        private fun parseArray(depth: Int): CatalogJsonArray {
            expect('[')
            skipWhitespace()
            val values = mutableListOf<CatalogJsonValue>()
            if (consumeIf(']')) return CatalogJsonArray(values)
            while (true) {
                values += parseValue(depth + 1)
                skipWhitespace()
                when {
                    consumeIf(']') -> return CatalogJsonArray(values)
                    consumeIf(',') -> Unit
                    else -> fail(CatalogCodecErrorCode.MALFORMED_JSON)
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val value = StringBuilder()
            while (index < input.length) {
                val char = input[index++]
                when {
                    char == '"' -> return finishString(value)
                    char == '\\' -> appendEscape(value)
                    char.code < CONTROL_LIMIT -> fail(CatalogCodecErrorCode.MALFORMED_JSON)
                    else -> value.append(char)
                }
                if (value.length > maxStringChars) fail(CatalogCodecErrorCode.JSON_LIMIT_EXCEEDED)
            }
            fail(CatalogCodecErrorCode.MALFORMED_JSON)
        }

        private fun appendEscape(output: StringBuilder) {
            if (index >= input.length) fail(CatalogCodecErrorCode.MALFORMED_JSON)
            when (val escaped = input[index++]) {
                '"', '\\', '/' -> output.append(escaped)
                'b' -> output.append('\b')
                'f' -> output.append('\u000c')
                'n' -> output.append('\n')
                'r' -> output.append('\r')
                't' -> output.append('\t')
                'u' -> output.append(parseUnicodeEscape())
                else -> fail(CatalogCodecErrorCode.MALFORMED_JSON)
            }
        }

        private fun parseUnicodeEscape(): Char {
            if (index + UNICODE_HEX_LENGTH > input.length) fail(CatalogCodecErrorCode.MALFORMED_JSON)
            var value = 0
            repeat(UNICODE_HEX_LENGTH) {
                val digit = input[index++].digitToIntOrNull(16)
                    ?: fail(CatalogCodecErrorCode.MALFORMED_JSON)
                value = value * HEX_RADIX + digit
            }
            return value.toChar()
        }

        private fun finishString(value: StringBuilder): String {
            val result = value.toString()
            var position = 0
            while (position < result.length) {
                val char = result[position]
                if (char.isHighSurrogate()) {
                    if (position + 1 >= result.length || !result[position + 1].isLowSurrogate()) {
                        fail(CatalogCodecErrorCode.MALFORMED_JSON)
                    }
                    position += 2
                } else {
                    if (char.isLowSurrogate()) fail(CatalogCodecErrorCode.MALFORMED_JSON)
                    position += 1
                }
            }
            return result
        }

        private fun parseNumber(): String {
            val start = index
            consumeIf('-')
            when {
                consumeIf('0') -> Unit
                peek() in '1'..'9' -> consumeDigits()
                else -> fail(CatalogCodecErrorCode.MALFORMED_JSON)
            }
            if (consumeIf('.')) {
                if (peek() !in '0'..'9') fail(CatalogCodecErrorCode.MALFORMED_JSON)
                consumeDigits()
            }
            if (peek() == 'e' || peek() == 'E') {
                index += 1
                if (peek() == '+' || peek() == '-') index += 1
                if (peek() !in '0'..'9') fail(CatalogCodecErrorCode.MALFORMED_JSON)
                consumeDigits()
            }
            return input.substring(start, index)
        }

        private fun consumeDigits() {
            while (peek() in '0'..'9') index += 1
        }

        private fun <T : CatalogJsonValue> parseLiteral(text: String, value: T): T {
            if (!input.regionMatches(index, text, 0, text.length)) fail(CatalogCodecErrorCode.MALFORMED_JSON)
            index += text.length
            return value
        }

        private fun countNode() {
            nodes += 1
            if (nodes > maxNodes) fail(CatalogCodecErrorCode.JSON_LIMIT_EXCEEDED)
        }

        private fun peek(): Char = if (index < input.length) input[index] else END_OF_INPUT

        private fun consumeIf(expected: Char): Boolean {
            if (peek() != expected) return false
            index += 1
            return true
        }

        private fun expect(expected: Char) {
            if (!consumeIf(expected)) fail(CatalogCodecErrorCode.MALFORMED_JSON)
        }

        fun fail(code: CatalogCodecErrorCode): Nothing = throw CatalogJsonSyntaxException(code, index)
    }

    private companion object {
        const val CONTROL_LIMIT = 0x20
        const val UNICODE_HEX_LENGTH = 4
        const val HEX_RADIX = 16
        const val END_OF_INPUT = '\u0000'
        val JSON_WHITESPACE = setOf(' ', '\t', '\r', '\n')
    }
}

internal class CatalogJsonWriteException(val code: CatalogCodecErrorCode) : IllegalArgumentException()

internal object CatalogJsonWriter {
    fun encode(value: CatalogJsonValue): ByteArray {
        return buildString { appendValue(value) }.toByteArray(StandardCharsets.UTF_8)
    }

    private fun StringBuilder.appendValue(value: CatalogJsonValue) {
        when (value) {
            is CatalogJsonObject -> appendObject(value)
            is CatalogJsonArray -> appendArray(value)
            is CatalogJsonString -> appendQuoted(value.value)
            is CatalogJsonNumber -> append(value.raw)
            is CatalogJsonBoolean -> append(value.value)
            CatalogJsonNull -> append("null")
        }
    }

    private fun StringBuilder.appendObject(value: CatalogJsonObject) {
        append('{')
        value.fields.entries.forEachIndexed { index, entry ->
            if (index > 0) append(',')
            appendQuoted(entry.key)
            append(':')
            appendValue(entry.value)
        }
        append('}')
    }

    private fun StringBuilder.appendArray(value: CatalogJsonArray) {
        append('[')
        value.values.forEachIndexed { index, item ->
            if (index > 0) append(',')
            appendValue(item)
        }
        append(']')
    }

    private fun StringBuilder.appendQuoted(value: String) {
        append('"')
        var index = 0
        while (index < value.length) {
            val char = value[index]
            when {
                char.isHighSurrogate() -> {
                    if (index + 1 >= value.length || !value[index + 1].isLowSurrogate()) {
                        throw CatalogJsonWriteException(CatalogCodecErrorCode.INVALID_UNICODE)
                    }
                    append(char)
                    append(value[index + 1])
                    index += 2
                }
                char.isLowSurrogate() -> throw CatalogJsonWriteException(CatalogCodecErrorCode.INVALID_UNICODE)
                else -> {
                    appendEscaped(char)
                    index += 1
                }
            }
        }
        append('"')
    }

    private fun StringBuilder.appendEscaped(char: Char) {
        when (char) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\b' -> append("\\b")
            '\u000c' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (char.code < CONTROL_LIMIT) {
                append("\\u")
                append(char.code.toString(HEX_RADIX).padStart(UNICODE_HEX_LENGTH, '0'))
            } else {
                append(char)
            }
        }
    }

    private const val CONTROL_LIMIT = 0x20
    private const val HEX_RADIX = 16
    private const val UNICODE_HEX_LENGTH = 4
}
