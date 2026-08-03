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
    val prefillMs: Long? = null,
    val decodeMs: Long? = null,
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

enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

data class HealthCheckResult(
    val id: String,
    val status: HealthStatus,
    val detail: String,
    val durationMs: Long,
)

enum class HealthStatus {
    PASS,
    WARN,
    FAIL,
    NOT_RUN,
}

data class DeveloperDashboardSnapshot(
    val runtime: RuntimeSnapshot,
    val recentRuns: List<GenerationRunRecord>,
    val recentLogs: List<StructuredLog>,
    val health: List<HealthCheckResult>,
    val modelStoreBytes: Long,
    val modelCount: Int,
)

data class TelemetryRetentionPolicy(
    val maxRuns: Int = 500,
    val maxLogs: Int = 2_000,
) {
    init {
        require(maxRuns > 0) { "maxRuns must be positive" }
        require(maxLogs > 0) { "maxLogs must be positive" }
    }
}

interface TelemetryRepository {
    fun recordRun(run: GenerationRunRecord)

    fun appendLog(log: StructuredLog)

    fun saveHealth(result: HealthCheckResult)

    fun recentRuns(limit: Int = 100): List<GenerationRunRecord>

    fun findRun(requestId: RequestId): GenerationRunRecord?

    fun recentLogs(
        limit: Int = 500,
        requestId: RequestId? = null,
    ): List<StructuredLog>

    fun healthResults(): List<HealthCheckResult>

    fun dashboard(runtime: RuntimeSnapshot): DeveloperDashboardSnapshot
}

object NoOpTelemetryRepository : TelemetryRepository {
    override fun recordRun(run: GenerationRunRecord) = Unit

    override fun appendLog(log: StructuredLog) = Unit

    override fun saveHealth(result: HealthCheckResult) = Unit

    override fun recentRuns(limit: Int): List<GenerationRunRecord> = emptyList()

    override fun findRun(requestId: RequestId): GenerationRunRecord? = null

    override fun recentLogs(
        limit: Int,
        requestId: RequestId?,
    ): List<StructuredLog> = emptyList()

    override fun healthResults(): List<HealthCheckResult> = emptyList()

    override fun dashboard(runtime: RuntimeSnapshot): DeveloperDashboardSnapshot = DeveloperDashboardSnapshot(
        runtime = runtime,
        recentRuns = emptyList(),
        recentLogs = emptyList(),
        health = emptyList(),
        modelStoreBytes = 0L,
        modelCount = 0,
    )
}
