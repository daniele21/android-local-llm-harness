package io.github.daniele21.localllm.observability.room

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.observability.GenerationRunRecord
import io.github.daniele21.localllm.observability.HealthCheckResult
import io.github.daniele21.localllm.observability.HealthStatus
import io.github.daniele21.localllm.observability.LogLevel
import io.github.daniele21.localllm.observability.RunStatus
import io.github.daniele21.localllm.observability.StructuredLog
import java.nio.charset.StandardCharsets
import java.util.Base64

internal object TelemetryEntityMapper {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun runEntity(run: GenerationRunRecord): TelemetryEntities.GenerationRunEntity =
        TelemetryEntities.GenerationRunEntity().apply {
            requestId = run.requestId.value
            applicationId = run.applicationId.value
            useCaseId = run.useCaseId.value
            modelDigest = run.modelDigest.sha256
            startedAtEpochMs = run.startedAtEpochMs
            completedAtEpochMs = run.completedAtEpochMs
            status = run.status.name
            queueMs = run.queueMs
            modelLoadMs = run.modelLoadMs
            timeToFirstTokenMs = run.timeToFirstTokenMs
            totalMs = run.totalMs
            inputTokens = run.inputTokens
            outputTokens = run.outputTokens
            decodeTokensPerSecond = run.decodeTokensPerSecond
            errorCode = run.errorCode
            prefillMs = run.prefillMs
            decodeMs = run.decodeMs
        }

    fun runRecord(entity: TelemetryEntities.GenerationRunEntity): GenerationRunRecord =
        GenerationRunRecord(
            requestId = RequestId(entity.requestId),
            applicationId = ApplicationId(entity.applicationId),
            useCaseId = UseCaseId(entity.useCaseId),
            modelDigest = ModelDigest(entity.modelDigest),
            startedAtEpochMs = entity.startedAtEpochMs,
            completedAtEpochMs = entity.completedAtEpochMs,
            status = RunStatus.valueOf(entity.status),
            queueMs = entity.queueMs,
            modelLoadMs = entity.modelLoadMs,
            timeToFirstTokenMs = entity.timeToFirstTokenMs,
            totalMs = entity.totalMs,
            inputTokens = entity.inputTokens,
            outputTokens = entity.outputTokens,
            decodeTokensPerSecond = entity.decodeTokensPerSecond,
            errorCode = entity.errorCode,
            prefillMs = entity.prefillMs,
            decodeMs = entity.decodeMs,
        )

    fun logEntity(log: StructuredLog): TelemetryEntities.StructuredLogEntity =
        TelemetryEntities.StructuredLogEntity().apply {
            timestampEpochMs = log.timestampEpochMs
            level = log.level.name
            component = log.component
            event = log.event
            requestId = log.requestId?.value
            encodedFields = encodeFields(log.fields)
        }

    fun structuredLog(entity: TelemetryEntities.StructuredLogEntity): StructuredLog =
        StructuredLog(
            timestampEpochMs = entity.timestampEpochMs,
            level = LogLevel.valueOf(entity.level),
            component = entity.component,
            event = entity.event,
            requestId = entity.requestId?.let(::RequestId),
            fields = decodeFields(entity.encodedFields),
        )

    fun healthEntity(result: HealthCheckResult): TelemetryEntities.HealthCheckEntity =
        TelemetryEntities.HealthCheckEntity().apply {
            id = result.id
            status = result.status.name
            detail = result.detail
            durationMs = result.durationMs
        }

    fun healthResult(entity: TelemetryEntities.HealthCheckEntity): HealthCheckResult =
        HealthCheckResult(
            id = entity.id,
            status = HealthStatus.valueOf(entity.status),
            detail = entity.detail,
            durationMs = entity.durationMs,
        )

    internal fun encodeFields(fields: Map<String, String>): String =
        fields.toSortedMap().entries.joinToString("\n") { entry ->
            "${encode(entry.key)}=${encode(entry.value)}"
        }

    internal fun decodeFields(encoded: String): Map<String, String> {
        if (encoded.isEmpty()) return emptyMap()
        return encoded.lineSequence().associate { line ->
            val separator = line.indexOf('=')
            require(separator >= 0) { "Invalid structured-log fields encoding" }
            decode(line.substring(0, separator)) to decode(line.substring(separator + 1))
        }
    }

    private fun encode(value: String): String = encoder.encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decode(value: String): String = String(decoder.decode(value), StandardCharsets.UTF_8)
}
