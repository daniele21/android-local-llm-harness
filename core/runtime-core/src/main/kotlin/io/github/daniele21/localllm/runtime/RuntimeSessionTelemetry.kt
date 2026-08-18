package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.observability.InferenceSessionRecord
import io.github.daniele21.localllm.observability.SessionCloseReason
import io.github.daniele21.localllm.observability.SessionRunStatus
import io.github.daniele21.localllm.observability.SessionTelemetryRepository
import java.util.concurrent.ConcurrentHashMap

internal class RuntimeSessionTelemetry(
    private val repository: SessionTelemetryRepository,
    private val clock: EpochClock,
) {
    private val activeSessions = ConcurrentHashMap<SessionId, InferenceSessionRecord>()

    fun opened(
        sessionId: SessionId,
        applicationId: ApplicationId,
        useCaseId: UseCaseId,
        modelDigest: ModelDigest,
        sessionKind: SessionKind,
    ) {
        val record = InferenceSessionRecord(
            sessionId = sessionId,
            applicationId = applicationId,
            useCaseId = useCaseId,
            modelDigest = modelDigest,
            sessionKind = sessionKind,
            createdAtEpochMs = clock.nowEpochMs(),
            closedAtEpochMs = null,
            status = SessionRunStatus.ACTIVE,
            closeReason = null,
        )
        activeSessions[sessionId] = record
        safely { repository.recordSession(record) }
    }

    fun closed(sessionId: SessionId, reason: SessionCloseReason) {
        val current = activeSessions.remove(sessionId) ?: return
        val status = when (reason) {
            SessionCloseReason.CANCELLED -> SessionRunStatus.CANCELLED
            SessionCloseReason.RUNTIME_FAILURE -> SessionRunStatus.FAILED
            SessionCloseReason.HOST_RESTART -> SessionRunStatus.ABANDONED_HOST_RESTART
            else -> SessionRunStatus.CLOSED
        }
        safely {
            repository.recordSession(
                current.copy(
                    closedAtEpochMs = clock.nowEpochMs(),
                    status = status,
                    closeReason = reason,
                ),
            )
        }
    }

    private inline fun safely(operation: () -> Unit) {
        runCatching(operation)
    }
}
