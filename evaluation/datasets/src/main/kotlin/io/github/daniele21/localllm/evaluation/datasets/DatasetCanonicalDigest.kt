package io.github.daniele21.localllm.evaluation.datasets

import io.github.daniele21.localllm.evaluation.EvaluationCaseMessage
import io.github.daniele21.localllm.evaluation.EvaluationCaseOutputContract
import io.github.daniele21.localllm.evaluation.EvaluationDatasetCaseV1
import io.github.daniele21.localllm.evaluation.EvaluationDatasetDigest
import io.github.daniele21.localllm.evaluation.EvaluationDatasetManifestV1
import io.github.daniele21.localllm.evaluation.EvaluationExpectedAnswer
import io.github.daniele21.localllm.evaluation.EvaluatorSpec
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

sealed interface DatasetDigestVerification {
    data object Match : DatasetDigestVerification

    data class Mismatch(val actualDigest: EvaluationDatasetDigest) : DatasetDigestVerification
}

object EvaluationDatasetContentDigester {
    fun digest(cases: List<EvaluationDatasetCaseV1>): EvaluationDatasetDigest {
        val digest = MessageDigest.getInstance("SHA-256")
        cases.forEach { case ->
            digest.update(EvaluationDatasetCanonicalJson.encodeCase(case).toByteArray(StandardCharsets.UTF_8))
            digest.update(LF_BYTE)
        }
        return EvaluationDatasetDigest(digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) })
    }

    fun verify(
        manifest: EvaluationDatasetManifestV1,
        cases: List<EvaluationDatasetCaseV1>,
    ): DatasetDigestVerification {
        val actual = digest(cases)
        return if (actual == manifest.contentDigest) {
            DatasetDigestVerification.Match
        } else {
            DatasetDigestVerification.Mismatch(actual)
        }
    }

    private const val LF_BYTE: Byte = 0x0A
}

object EvaluationDatasetCanonicalJson {
    fun encodeCase(case: EvaluationDatasetCaseV1): String = buildString {
        append('{')
        appendFieldName("schemaVersion")
        append(case.schemaVersion)
        append(',')
        appendFieldName("id")
        appendJsonString(case.id.value)
        append(',')
        appendFieldName("categoryId")
        appendJsonString(case.categoryId.value)
        append(',')
        appendFieldName("messages")
        appendMessages(case.messages)
        append(',')
        appendFieldName("expected")
        appendExpected(case.expected)
        append(',')
        appendFieldName("evaluator")
        appendEvaluator(case.evaluator)
        if (case.output != EvaluationCaseOutputContract()) {
            append(',')
            appendFieldName("output")
            appendOutput(case.output)
        }
        if (case.metadata.isNotEmpty()) {
            append(',')
            appendFieldName("metadata")
            appendStringMap(case.metadata)
        }
        append('}')
    }
}

private fun StringBuilder.appendMessages(messages: List<EvaluationCaseMessage>) {
    append('[')
    messages.forEachIndexed { index, message ->
        if (index > 0) append(',')
        append('{')
        appendFieldName("role")
        appendJsonString(message.role.name)
        append(',')
        appendFieldName("content")
        appendJsonString(message.content)
        append('}')
    }
    append(']')
}

private fun StringBuilder.appendExpected(expected: EvaluationExpectedAnswer) {
    append('{')
    appendFieldName("kind")
    appendJsonString(expected.kind.name)
    append(',')
    appendFieldName("value")
    appendJsonString(expected.value)
    append('}')
}

private fun StringBuilder.appendEvaluator(evaluator: EvaluatorSpec) {
    append('{')
    appendFieldName("type")
    appendJsonString(evaluator.type.name)
    append(',')
    appendFieldName("version")
    append(evaluator.version.value)
    if (evaluator.parameters.isNotEmpty()) {
        append(',')
        appendFieldName("parameters")
        appendStringMap(evaluator.parameters)
    }
    append('}')
}

private fun StringBuilder.appendOutput(output: EvaluationCaseOutputContract) {
    append('{')
    appendFieldName("responseFormat")
    appendJsonString(output.responseFormat.name)
    output.maxOutputTokens?.let { maxOutputTokens ->
        append(',')
        appendFieldName("maxOutputTokens")
        append(maxOutputTokens)
    }
    append(',')
    appendFieldName("stopSequences")
    append('[')
    output.stopSequences.forEachIndexed { index, stopSequence ->
        if (index > 0) append(',')
        appendJsonString(stopSequence)
    }
    append(']')
    append('}')
}

private fun StringBuilder.appendStringMap(values: Map<String, String>) {
    append('{')
    values.toSortedMap().entries.forEachIndexed { index, (key, value) ->
        if (index > 0) append(',')
        appendFieldName(key)
        appendJsonString(value)
    }
    append('}')
}

private fun StringBuilder.appendFieldName(name: String) {
    appendJsonString(name)
    append(':')
}

private fun StringBuilder.appendJsonString(value: String) {
    append('"')
    value.forEach { char ->
        when (char) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> {
                if (char.code < 0x20) {
                    append("\\u")
                    append(char.code.toString(16).padStart(4, '0'))
                } else {
                    append(char)
                }
            }
        }
    }
    append('"')
}
