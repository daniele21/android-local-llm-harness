package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.RuntimeSnapshot
import io.github.daniele21.localllm.observability.GenerationRunRecord
import io.github.daniele21.localllm.observability.HealthCheckResult
import io.github.daniele21.localllm.observability.HealthStatus
import io.github.daniele21.localllm.observability.RunStatus
import io.github.daniele21.localllm.observability.StructuredLog
import io.github.daniele21.localllm.observability.TelemetryRepository

internal data class DiagnosticsRunUi(
    val requestId: String,
    val useCase: String,
    val modelDigestPrefix: String,
    val status: String,
    val modelLoadKind: String,
    val timeToFirstToken: String,
    val totalDuration: String,
    val throughput: String,
    val startedAtEpochMs: Long,
)

internal data class DiagnosticsHealthUi(val id: String, val status: String, val detail: String, val duration: String)

internal data class DiagnosticsUiState(
    val runtime: RuntimeSnapshot?,
    val runs: List<DiagnosticsRunUi>,
    val logs: List<StructuredLog>,
    val health: List<DiagnosticsHealthUi> = emptyList(),
    val healthStatus: String = "Not run",
    val resources: List<DiagnosticsResourceUi> = emptyList(),
    val sourceError: String? = null,
) {
    val hasTelemetry: Boolean
        get() = runs.isNotEmpty() || logs.isNotEmpty() || health.isNotEmpty() || resources.isNotEmpty()
}

internal class HarnessDiagnosticsSource(
    private val telemetryRepository: TelemetryRepository,
    private val runtimeSnapshot: () -> RuntimeSnapshot?,
    private val resourceSnapshots: () -> List<DiagnosticsResourceUi> = { emptyList() },
) {
    fun snapshot(runLimit: Int = DEFAULT_RUN_LIMIT, logLimit: Int = DEFAULT_LOG_LIMIT): DiagnosticsUiState = runCatching {
        val healthResults = telemetryRepository.healthResults()
        DiagnosticsUiState(
            runtime = runtimeSnapshot(),
            runs = telemetryRepository.recentRuns(runLimit).map { it.toUi() },
            logs = telemetryRepository.recentLogs(logLimit),
            health = healthResults.map { it.toUi() },
            healthStatus = healthResults.aggregateStatus().uiLabel(),
            resources = resourceSnapshots(),
        )
    }.getOrElse {
        DiagnosticsUiState(
            runtime = runtimeSnapshot(),
            runs = emptyList(),
            logs = emptyList(),
            health = emptyList(),
            resources = emptyList(),
            sourceError = SOURCE_ERROR,
        )
    }

    private fun GenerationRunRecord.toUi(): DiagnosticsRunUi = DiagnosticsRunUi(
        requestId = requestId.value,
        useCase = useCaseId.value,
        modelDigestPrefix = modelDigest.sha256.take(DIGEST_PREFIX_LENGTH),
        status = status.uiLabel(),
        modelLoadKind = modelLoadKind.name,
        timeToFirstToken = timeToFirstTokenMs.asDuration(),
        totalDuration = totalMs.asDuration(),
        throughput = decodeTokensPerSecond.asThroughput(),
        startedAtEpochMs = startedAtEpochMs,
    )

    private fun HealthCheckResult.toUi(): DiagnosticsHealthUi = DiagnosticsHealthUi(
        id = id,
        status = status.uiLabel(),
        detail = detail,
        duration = "$durationMs ms",
    )

    private fun List<HealthCheckResult>.aggregateStatus(): HealthStatus = when {
        any { it.status == HealthStatus.FAIL } -> HealthStatus.FAIL
        any { it.status == HealthStatus.WARN } -> HealthStatus.WARN
        isEmpty() || all { it.status == HealthStatus.NOT_RUN } -> HealthStatus.NOT_RUN
        else -> HealthStatus.PASS
    }

    private fun RunStatus.uiLabel(): String = when (this) {
        RunStatus.QUEUED -> "Queued"
        RunStatus.RUNNING -> "Running"
        RunStatus.COMPLETED -> "Completed"
        RunStatus.FAILED -> "Failed"
        RunStatus.CANCELLED -> "Cancelled"
    }

    private fun HealthStatus.uiLabel(): String = when (this) {
        HealthStatus.PASS -> "Pass"
        HealthStatus.WARN -> "Warning"
        HealthStatus.FAIL -> "Fail"
        HealthStatus.NOT_RUN -> "Not run"
    }

    private fun Long?.asDuration(): String = this?.let { "$it ms" } ?: "Unavailable"

    private fun Double?.asThroughput(): String = this?.let { "%.2f tok/s".format(it) } ?: "Unavailable"

    private companion object {
        const val DEFAULT_RUN_LIMIT = 30
        const val DEFAULT_LOG_LIMIT = 100
        const val DIGEST_PREFIX_LENGTH = 12
        const val SOURCE_ERROR = "Diagnostics telemetry is temporarily unavailable."
    }
}
