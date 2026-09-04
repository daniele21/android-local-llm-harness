package io.github.daniele21.localllm.audit

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.ModelLoadKind
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.StopReason
import io.github.daniele21.localllm.contracts.UseCaseId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class InferenceAuditContractTest {
    @Test
    fun `external origin requires verified package identity`() {
        assertThrows(IllegalArgumentException::class.java) {
            InferenceAuditOrigin(
                kind = InferenceAuditOriginKind.EXTERNAL_CONSUMER,
                applicationId = ApplicationId("redactguard"),
                useCaseId = UseCaseId("pii-redaction"),
            )
        }
    }

    @Test
    fun `internal origin rejects package identity`() {
        assertThrows(IllegalArgumentException::class.java) {
            InferenceAuditOrigin(
                kind = InferenceAuditOriginKind.HARNEX_INTERNAL,
                applicationId = ApplicationId("harnex"),
                useCaseId = UseCaseId("playground"),
                verifiedPackageName = "io.example.spoof",
            )
        }
    }

    @Test
    fun `sensitive contract strings redact content`() {
        val admission = InferenceAuditAdmission(
            requestId = RequestId("request-1"),
            origin = internalOrigin(),
            receivedAtEpochMs = 100,
            input = InferenceAuditInput.Text("secret prompt"),
        )
        val prepared = InferenceAuditPrepared(
            requestId = admission.requestId,
            preparedAtEpochMs = 110,
            effectivePrompt = "system + secret prompt",
            execution = executionIdentity(),
        )
        val terminal = InferenceAuditTerminal(
            requestId = admission.requestId,
            status = InferenceAuditStatus.COMPLETED,
            completedAtEpochMs = 200,
            content = InferenceAuditTerminalContent(
                answerOutput = "secret answer",
                reasoningOutput = "secret reasoning",
            ),
            metrics = completedMetrics(),
        )
        val record = InferenceAuditRecord(
            admission = admission,
            status = InferenceAuditStatus.COMPLETED,
            prepared = prepared,
            runningAtEpochMs = 120,
            terminal = terminal,
        )

        val rendered = listOf(admission, prepared, terminal, record).joinToString("\n")
        assertFalse(rendered.contains("secret prompt"))
        assertFalse(rendered.contains("secret answer"))
        assertFalse(rendered.contains("secret reasoning"))
        assertTrue(rendered.contains("<redacted>"))
    }

    @Test
    fun `completed terminal requires content and metrics`() {
        assertThrows(IllegalArgumentException::class.java) {
            InferenceAuditTerminal(
                requestId = RequestId("request-1"),
                status = InferenceAuditStatus.COMPLETED,
                completedAtEpochMs = 200,
            )
        }
    }

    @Test
    fun `failed terminal requires safe terminal code`() {
        assertThrows(IllegalArgumentException::class.java) {
            InferenceAuditTerminal(
                requestId = RequestId("request-1"),
                status = InferenceAuditStatus.FAILED,
                completedAtEpochMs = 200,
            )
        }
    }

    @Test
    fun `query and content bounds fail closed`() {
        assertThrows(IllegalArgumentException::class.java) {
            InferenceAuditQuery(limit = MAX_AUDIT_QUERY_LIMIT + 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            InferenceAuditInput.Text("x".repeat(MAX_AUDIT_INPUT_CHARACTERS + 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            InferenceAuditTerminalContent(answerOutput = "x".repeat(MAX_AUDIT_OUTPUT_CHARACTERS + 1))
        }
    }

    private fun internalOrigin() = InferenceAuditOrigin(
        kind = InferenceAuditOriginKind.HARNEX_INTERNAL,
        applicationId = ApplicationId("harnex"),
        useCaseId = UseCaseId("playground"),
    )

    private fun executionIdentity() = InferenceAuditExecutionIdentity(
        modelDigest = ModelDigest("a".repeat(64)),
        modelLoadKind = ModelLoadKind.WARM,
        presetId = "balanced",
        presetVersion = 1,
    )

    private fun completedMetrics() = InferenceAuditMetrics(
        queueMs = 2,
        modelLoadMs = null,
        timeToFirstTokenMs = 20,
        totalMs = 100,
        inputTokens = 10,
        outputTokens = 5,
        decodeTokensPerSecond = 50.0,
        modelLoadKind = ModelLoadKind.WARM,
        stopReason = StopReason.END_OF_GENERATION,
    )
}
