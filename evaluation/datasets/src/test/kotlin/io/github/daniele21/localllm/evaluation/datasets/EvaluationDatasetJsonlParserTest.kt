package io.github.daniele21.localllm.evaluation.datasets

import io.github.daniele21.localllm.evaluation.EvaluationExpectedAnswerKind
import io.github.daniele21.localllm.evaluation.EvaluationMessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream

class EvaluationDatasetJsonlParserTest {
    private val parser = EvaluationDatasetJsonlParser()

    @Test
    fun validCanonicalRecordParsesWithoutRetainingWireText() {
        val cases = parser.parse(input(validCaseLine()))

        assertEquals(1, cases.size)
        assertEquals("case-001", cases.single().id.value)
        assertEquals(EvaluationMessageRole.USER, cases.single().messages.single().role)
        assertEquals(EvaluationExpectedAnswerKind.TEXT, cases.single().expected.kind)
    }

    @Test
    fun blankLineFailsClosed() {
        val failure = assertThrows(EvaluationDatasetParseException::class.java) {
            parser.parse(input("\n"))
        }

        assertEquals(DatasetParseErrorCode.EMPTY_LINE, failure.code)
        assertEquals(1, failure.lineNumber)
    }

    @Test
    fun duplicateJsonKeyIsRejectedAsMalformedJson() {
        val duplicate = validCaseLine().replace(
            "\"id\":\"case-001\"",
            "\"id\":\"case-001\",\"id\":\"case-002\"",
        )
        val failure = assertThrows(EvaluationDatasetParseException::class.java) {
            parser.parse(input(duplicate))
        }

        assertEquals(DatasetParseErrorCode.MALFORMED_JSON, failure.code)
    }

    @Test
    fun unknownTopLevelFieldIsRejected() {
        val unknown = validCaseLine().replace(
            "\"metadata\":{\"source\":\"fixture\"}",
            "\"metadata\":{\"source\":\"fixture\"},\"script\":\"run-me\"",
        )
        val failure = assertThrows(EvaluationDatasetParseException::class.java) {
            parser.parse(input(unknown))
        }

        assertEquals(DatasetParseErrorCode.UNKNOWN_FIELD, failure.code)
    }

    @Test
    fun unsupportedSchemaVersionIsRejectedExplicitly() {
        val unsupported = validCaseLine().replace("\"schemaVersion\":1", "\"schemaVersion\":2")
        val failure = assertThrows(EvaluationDatasetParseException::class.java) {
            parser.parse(input(unsupported))
        }

        assertEquals(DatasetParseErrorCode.UNSUPPORTED_SCHEMA, failure.code)
    }

    @Test
    fun fractionalSchemaVersionIsRejectedAsInvalidField() {
        val fractional = validCaseLine().replace("\"schemaVersion\":1", "\"schemaVersion\":1.5")
        val failure = assertThrows(EvaluationDatasetParseException::class.java) {
            parser.parse(input(fractional))
        }

        assertEquals(DatasetParseErrorCode.INVALID_FIELD, failure.code)
    }

    @Test
    fun fractionalMaxOutputTokensIsRejectedAsInvalidField() {
        val fractional = validCaseLine().replace(
            "\"metadata\":{\"source\":\"fixture\"}",
            "\"output\":{\"responseFormat\":\"TEXT\",\"maxOutputTokens\":1.5},\"metadata\":{\"source\":\"fixture\"}",
        )
        val failure = assertThrows(EvaluationDatasetParseException::class.java) {
            parser.parse(input(fractional))
        }

        assertEquals(DatasetParseErrorCode.INVALID_FIELD, failure.code)
    }

    @Test
    fun crlfIsRejectedInsteadOfSilentlyCanonicalized() {
        val failure = assertThrows(EvaluationDatasetParseException::class.java) {
            parser.parse(input(validCaseLine().removeSuffix("\n") + "\r\n"))
        }

        assertEquals(DatasetParseErrorCode.CR_LINE_ENDING, failure.code)
    }

    @Test
    fun missingFinalLfIsRejected() {
        val failure = assertThrows(EvaluationDatasetParseException::class.java) {
            parser.parse(input(validCaseLine().removeSuffix("\n")))
        }

        assertEquals(DatasetParseErrorCode.MISSING_LF_TERMINATOR, failure.code)
    }

    @Test
    fun utf8BomIsRejected() {
        val payload = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + validCaseLine().toByteArray()
        val failure = assertThrows(EvaluationDatasetParseException::class.java) {
            parser.parse(ByteArrayInputStream(payload))
        }

        assertEquals(DatasetParseErrorCode.UTF8_BOM, failure.code)
    }

    private fun input(value: String) = ByteArrayInputStream(value.toByteArray(Charsets.UTF_8))

    private fun validCaseLine(): String = """{"schemaVersion":1,"id":"case-001","categoryId":"reasoning","messages":[""" +
        """{"role":"USER","content":"Answer alpha"}],"expected":{"kind":"TEXT","value":"alpha"},""" +
        """"evaluator":{"type":"EXACT_MATCH","version":1,"parameters":{"case":"sensitive","whitespace":"trim"}},""" +
        """"metadata":{"source":"fixture"}}""" +
        "\n"
}
