package io.github.daniele21.localllm.observability.room

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.observability.InferenceSessionRecord
import io.github.daniele21.localllm.observability.SessionCloseReason
import io.github.daniele21.localllm.observability.SessionRunStatus

internal object SessionTelemetryEntityMapper {
    fun sessionEntity(session: InferenceSessionRecord): TelemetryEntities.InferenceSessionEntity =
        TelemetryEntities.InferenceSessionEntity().apply {
            sessionId = session.sessionId.value
            applicationId = session.applicationId.value
            useCaseId = session.useCaseId.value
            modelDigest = session.modelDigest.sha256
            sessionKind = session.sessionKind.name
            createdAtEpochMs = session.createdAtEpochMs
            closedAtEpochMs = session.closedAtEpochMs
            status = session.status.name
            closeReason = session.closeReason?.name
            presetId = session.presetId?.value
            presetVersion = session.presetVersion
            useCaseRevision = session.useCaseRevision
            bindingRevision = session.bindingRevision
        }

    fun sessionRecord(entity: TelemetryEntities.InferenceSessionEntity): InferenceSessionRecord = InferenceSessionRecord(
        sessionId = SessionId(entity.sessionId),
        applicationId = ApplicationId(entity.applicationId),
        useCaseId = UseCaseId(entity.useCaseId),
        modelDigest = ModelDigest(entity.modelDigest),
        sessionKind = SessionKind.valueOf(entity.sessionKind),
        createdAtEpochMs = entity.createdAtEpochMs,
        closedAtEpochMs = entity.closedAtEpochMs,
        status = SessionRunStatus.valueOf(entity.status),
        closeReason = entity.closeReason?.let(SessionCloseReason::valueOf),
        presetId = entity.presetId?.let(::InferencePresetId),
        presetVersion = entity.presetVersion,
        useCaseRevision = entity.useCaseRevision,
        bindingRevision = entity.bindingRevision,
    )
}
