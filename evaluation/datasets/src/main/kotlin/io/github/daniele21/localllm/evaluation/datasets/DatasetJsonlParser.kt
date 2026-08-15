package io.github.daniele21.localllm.evaluation.datasets

import io.github.daniele21.localllm.evaluation.EvaluationDatasetCaseV1
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

class EvaluationDatasetParseException(val lineNumber: Int, val code: DatasetParseErrorCode) :
    IllegalArgumentException("Evaluation dataset parse failure at line $lineNumber: $code")

data class EvaluationDatasetParserLimits(val maxCases: Int = 10_000, val maxLineBytes: Int = 1_048_576) {
    init {
        require(maxCases > 0) { "Dataset parser max cases must be positive" }
        require(maxLineBytes > 0) { "Dataset parser max line bytes must be positive" }
    }
}

class EvaluationDatasetJsonlParser(private val limits: EvaluationDatasetParserLimits = EvaluationDatasetParserLimits()) {
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
                datasetParseFailure(rawLine.number, DatasetParseErrorCode.TOO_MANY_CASES)
            }
            val line = decodeUtf8(rawLine)
            if (line.isEmpty()) {
                datasetParseFailure(rawLine.number, DatasetParseErrorCode.EMPTY_LINE)
            }
            val root = runCatching { StrictJsonParser.parseObject(line) }.getOrElse {
                datasetParseFailure(rawLine.number, DatasetParseErrorCode.MALFORMED_JSON)
            }
            onCase(decodeCase(root, rawLine.number))
        }
    }
}

private data class RawJsonlLine(val number: Int, val bytes: ByteArray)

private class LfJsonlLineReader(private val input: InputStream, private val maxLineBytes: Int) {
    private var lineNumber = 0
    private var firstLine = true

    fun nextLine(): RawJsonlLine? {
        val buffer = ByteArrayOutputStream()
        while (true) {
            when (val next = input.read()) {
                -1 -> {
                    if (buffer.size() == 0) return null
                    datasetParseFailure(lineNumber + 1, DatasetParseErrorCode.MISSING_LF_TERMINATOR)
                }

                CR_BYTE -> datasetParseFailure(lineNumber + 1, DatasetParseErrorCode.CR_LINE_ENDING)

                LF_BYTE -> return finishLine(buffer.toByteArray())

                else -> {
                    if (buffer.size() >= maxLineBytes) {
                        datasetParseFailure(lineNumber + 1, DatasetParseErrorCode.LINE_TOO_LONG)
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
                datasetParseFailure(lineNumber, DatasetParseErrorCode.UTF8_BOM)
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
    datasetParseFailure(line.number, DatasetParseErrorCode.MALFORMED_UTF8)
}
