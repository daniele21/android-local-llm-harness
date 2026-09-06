package io.github.daniele21.localllm.audit.room

import io.github.daniele21.localllm.audit.InferenceAuditInput
import io.github.daniele21.localllm.audit.InferenceAuditMessage
import io.github.daniele21.localllm.audit.InferenceAuditTerminalContent
import io.github.daniele21.localllm.audit.MAX_AUDIT_MESSAGES
import io.github.daniele21.localllm.contracts.ConversationRole
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

internal data class InferenceAuditSensitivePayload(
    val input: InferenceAuditInput,
    val effectivePrompt: String?,
    val terminalContent: InferenceAuditTerminalContent?,
)

internal object InferenceAuditSensitiveCodec {
    fun encode(payload: InferenceAuditSensitivePayload): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeInt(FORMAT_VERSION)
            writeInput(output, payload.input)
            writeNullableString(output, payload.effectivePrompt)
            output.writeBoolean(payload.terminalContent != null)
            payload.terminalContent?.let { content ->
                writeString(output, content.answerOutput)
                writeString(output, content.reasoningOutput)
            }
        }
        bytes.toByteArray()
    }

    fun decode(value: ByteArray): InferenceAuditSensitivePayload = DataInputStream(ByteArrayInputStream(value)).use { input ->
        require(input.readInt() == FORMAT_VERSION) { "Unsupported audit content format" }
        val payload = InferenceAuditSensitivePayload(
            input = readInput(input),
            effectivePrompt = readNullableString(input),
            terminalContent = if (input.readBoolean()) {
                InferenceAuditTerminalContent(
                    answerOutput = readString(input),
                    reasoningOutput = readString(input),
                )
            } else {
                null
            },
        )
        require(input.read() == -1) { "Unexpected trailing audit content" }
        payload
    }

    private fun writeInput(output: DataOutputStream, input: InferenceAuditInput) {
        when (input) {
            is InferenceAuditInput.Text -> {
                output.writeInt(INPUT_TEXT)
                writeString(output, input.value)
            }

            is InferenceAuditInput.RawCompletion -> {
                output.writeInt(INPUT_RAW_COMPLETION)
                writeString(output, input.value)
            }

            is InferenceAuditInput.Messages -> {
                output.writeInt(INPUT_MESSAGES)
                output.writeInt(input.values.size)
                input.values.forEach { message ->
                    writeString(output, message.role.name)
                    writeString(output, message.content)
                }
            }
        }
    }

    private fun readInput(input: DataInputStream): InferenceAuditInput = when (input.readInt()) {
        INPUT_TEXT -> InferenceAuditInput.Text(readString(input))

        INPUT_RAW_COMPLETION -> InferenceAuditInput.RawCompletion(readString(input))

        INPUT_MESSAGES -> {
            val size = input.readInt()
            require(size in 1..MAX_AUDIT_MESSAGES) { "Invalid audit message count" }
            InferenceAuditInput.Messages(
                List(size) {
                    InferenceAuditMessage(
                        role = ConversationRole.valueOf(readString(input)),
                        content = readString(input),
                    )
                },
            )
        }

        else -> error("Unknown audit input kind")
    }

    private fun writeNullableString(output: DataOutputStream, value: String?) {
        output.writeBoolean(value != null)
        value?.let { writeString(output, it) }
    }

    private fun readNullableString(input: DataInputStream): String? = if (input.readBoolean()) readString(input) else null

    private fun writeString(output: DataOutputStream, value: String) {
        val encoded = value.toByteArray(Charsets.UTF_8)
        require(encoded.size <= MAX_STRING_BYTES) { "Audit content string exceeds encoded storage bound" }
        output.writeInt(encoded.size)
        output.write(encoded)
    }

    private fun readString(input: DataInputStream): String {
        val size = input.readInt()
        require(size in 0..MAX_STRING_BYTES) { "Invalid audit content string size" }
        return ByteArray(size).also(input::readFully).toString(Charsets.UTF_8)
    }

    private const val FORMAT_VERSION = 1
    private const val INPUT_TEXT = 1
    private const val INPUT_MESSAGES = 2
    private const val INPUT_RAW_COMPLETION = 3
    private const val MAX_STRING_BYTES = 1_048_576
}
