package io.github.daniele21.localllm.observability

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.UseCaseId
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTelemetryTest {
    @Test
    fun `active session cannot carry terminal close fields`() {
        val result = runCatching {
            record(
                status = SessionRunStatus.ACTIVE,
                closedAtEpochMs = 20,
                closeReason = SessionCloseReason.NORMAL,
            )
        }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `terminal session requires close reason and timestamp`() {
        val result = runCatching {
            record(
                status = SessionRunStatus.CLOSED,
                closedAtEpochMs = null,
                closeReason = null,
            )
        }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `session preserves host execution revision identity`() {
        val session = record(
            status = SessionRunStatus.CLOSED,
            closedAtEpochMs = 20,
            closeReason = SessionCloseReason.NORMAL,
        )

        assertTrue(session.presetVersion == 3)
        assertTrue(session.useCaseRevision == 4)
        assertTrue(session.bindingRevision == 7)
    }

    private fun record(status: SessionRunStatus, closedAtEpochMs: Long?, closeReason: SessionCloseReason?): InferenceSessionRecord =
        InferenceSessionRecord(
            sessionId = SessionId("session-1"),
            applicationId = ApplicationId("redactguard"),
            useCaseId = UseCaseId("document-pii-detection"),
            modelDigest = ModelDigest("a".repeat(64)),
            sessionKind = SessionKind.STATELESS,
            createdAtEpochMs = 10,
            closedAtEpochMs = closedAtEpochMs,
            status = status,
            closeReason = closeReason,
            presetId = InferencePresetId("quality"),
            presetVersion = 3,
            useCaseRevision = 4,
            bindingRevision = 7,
        )
}
