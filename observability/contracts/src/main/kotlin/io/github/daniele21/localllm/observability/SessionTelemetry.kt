package io.github.daniele21.localllm.observability

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.UseCaseId

data class InferenceSessionRecord(
    val sessionId: SessionId,
    val applicationId: ApplicationId,
    val useCaseId: UseCaseId,
    val modelDigest: ModelDigest,
    val sessionKind: SessionKind,
    val createdAtEpochMs: Long,
    val closedAtEpochMs: Long?,
    val status: SessionRunStatus,
    val closeReason: SessionCloseReason?,
    val presetId: InferencePresetId? = null,
    val presetVersion: Int? = null,
    val useCaseRevision: Int? = null,
    val bindingRevision: Int? = null,
) {
    init {
        require(createdAtEpochMs >= 0) { "Session creation timestamp must not be negative" }
        require(closedAtEpochMs == null || closedAtEpochMs >= createdAtEpochMs) {
            "Session close timestamp must not precede session creation"
        }
        require((presetId == null) == (presetVersion == null)) {
            "Preset ID and version must be provided together"
        }
        require(presetVersion == null || presetVersion > 0) { "Preset version must be positive" }
        require(useCaseRevision == null || useCaseRevision > 0) { "Use-case revision must be positive" }
        require(bindingRevision == null || bindingRevision > 0) { "Binding revision must be positive" }
        if (status == SessionRunStatus.ACTIVE) {
            require(closedAtEpochMs == null) { "Active session must not have a close timestamp" }
            require(closeReason == null) { "Active session must not have a close reason" }
        } else {
            require(closedAtEpochMs != null) { "Terminal session must have a close timestamp" }
            require(closeReason != null) { "Terminal session must have a close reason" }
        }
    }
}

enum class SessionRunStatus {
    ACTIVE,
    CLOSED,
    CANCELLED,
    FAILED,
    ABANDONED_HOST_RESTART,
}

enum class SessionCloseReason {
    NORMAL,
    CLIENT_REQUEST,
    CLIENT_DISCONNECTED,
    HOST_SHUTDOWN,
    HOST_RESTART,
    MODEL_REVOKED,
    MEMORY_PRESSURE,
    RUNTIME_FAILURE,
    CANCELLED,
}

data class SessionTelemetryRetentionPolicy(val maxSessions: Int = 500) {
    init {
        require(maxSessions > 0) { "maxSessions must be positive" }
    }
}

interface SessionTelemetryRepository {
    fun recordSession(session: InferenceSessionRecord)

    fun recentSessions(limit: Int = 100): List<InferenceSessionRecord>

    fun findSession(sessionId: SessionId): InferenceSessionRecord?
}

object NoOpSessionTelemetryRepository : SessionTelemetryRepository {
    override fun recordSession(session: InferenceSessionRecord) = Unit

    override fun recentSessions(limit: Int): List<InferenceSessionRecord> = emptyList()

    override fun findSession(sessionId: SessionId): InferenceSessionRecord? = null
}
