package io.github.daniele21.localllm.observability.room

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.ModelLoadKind
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.observability.GenerationRunRecord
import io.github.daniele21.localllm.observability.InferenceSessionRecord
import io.github.daniele21.localllm.observability.RunStatus
import io.github.daniele21.localllm.observability.SessionCloseReason
import io.github.daniele21.localllm.observability.SessionRunStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionTelemetryEntityMapperTest {
    @Test
    fun `session mapper round trips execution identity`() {
        val record = InferenceSessionRecord(
            sessionId = SESSION_ID,
            applicationId = APP_ID,
            useCaseId = USE_CASE_ID,
            modelDigest = MODEL,
            sessionKind = SessionKind.STATELESS,
            createdAtEpochMs = 10,
            closedAtEpochMs = 20,
            status = SessionRunStatus.CLOSED,
            closeReason = SessionCloseReason.NORMAL,
            presetId = InferencePresetId("quality"),
            presetVersion = 3,
            useCaseRevision = 4,
            bindingRevision = 7,
        )

        val roundTrip = SessionTelemetryEntityMapper.sessionRecord(SessionTelemetryEntityMapper.sessionEntity(record))

        assertEquals(record, roundTrip)
    }

    @Test
    fun `generation mapper preserves optional session and binding identity`() {
        val record = GenerationRunRecord(
            requestId = RequestId("request-1"),
            applicationId = APP_ID,
            useCaseId = USE_CASE_ID,
            modelDigest = MODEL,
            startedAtEpochMs = 10,
            completedAtEpochMs = 20,
            status = RunStatus.COMPLETED,
            queueMs = 1,
            modelLoadMs = 2,
            timeToFirstTokenMs = 3,
            totalMs = 10,
            inputTokens = 4,
            outputTokens = 5,
            decodeTokensPerSecond = 6.0,
            errorCode = null,
            modelLoadKind = ModelLoadKind.WARM,
            sessionId = SESSION_ID,
            useCaseRevision = 4,
            bindingRevision = 7,
        )

        val roundTrip = TelemetryEntityMapper.runRecord(TelemetryEntityMapper.runEntity(record))

        assertEquals(SESSION_ID, roundTrip.sessionId)
        assertEquals(4, roundTrip.useCaseRevision)
        assertEquals(7, roundTrip.bindingRevision)
    }

    private companion object {
        val APP_ID = ApplicationId("redactguard")
        val USE_CASE_ID = UseCaseId("document-pii-detection")
        val MODEL = ModelDigest("a".repeat(64))
        val SESSION_ID = SessionId("session-1")
    }
}
