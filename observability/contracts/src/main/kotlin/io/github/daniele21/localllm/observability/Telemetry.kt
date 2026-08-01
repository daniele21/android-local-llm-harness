package io.github.daniele21.localllm.observability

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.RuntimeSnapshot
import io.github.daniele21.localllm.contracts.UseCaseId

data class GenerationRunRecord(
    val requestId: RequestId,
    val applicationId: ApplicationId,
    val useCaseId: UseCaseId,
    val modelDigest: ModelDigest,
    val startedAtEpochMs: Long,
    val completedAtEpochMs: Long?,
    val status: RunStatus,
    val queueMs: Long?,
    val modelLoadMs: Long?,
    val timeToFirstTokenMs: Long?,
    val totalMs: Long?,
    val inputTokens: Int?,
    val outputTokens: Int?,
    val decodeTokensPerSecond: Double?,
    val errorCode: String?,
)

enum class RunStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

data class StructuredLog(
    val timestampEpochMs: Long,
    val level: LogLevel,
    val component: String,
    val event: String,
    val requestId: RequestId? = null,
    val fields: Map<String, String> = emptyMap(),
)

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

data class HealthCheckResult(
    val id: String,
    val status: HealthStatus,
    val detail: String,
    val durationMs: Long,
)

enum class HealthStatus { PASS, WARN, FAIL, NOT_RUN }

data class DeveloperDashboardSnapshot(
    val runtime: RuntimeSnapshot,
    val recentRuns: List<GenerationRunRecord>,
    val recentLogs: List<StructuredLog>,
    val health: List<HealthCheckResult>,
    val modelStoreBytes: Long,
    val modelCount: Int,
)

interface TelemetryRepository {
    fun recordRun(run: GenerationRunRecord)
    fun appendLog(log: StructuredLog)
    fun saveHealth(result: HealthCheckResult)
    fun dashboard(runtime: RuntimeSnapshot): DeveloperDashboardSnapshot
}
