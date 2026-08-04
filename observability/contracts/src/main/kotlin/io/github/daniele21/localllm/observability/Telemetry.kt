package io.github.daniele21.localllm.observability

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.ModelLoadKind
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
    val modelLoadKind: ModelLoadKind = ModelLoadKind.UNKNOWN,
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

data class HealthCheckResult(val id: String, val status: HealthStatus, val detail: String, val durationMs: Long)

enum class HealthStatus {
    PASS,
    WARN,
    FAIL,
    NOT_RUN,
}

data class ResourceSnapshot(
    val timestampEpochMs: Long,
    val processPssBytes: Long?,
    val nativeHeapBytes: Long?,
    val javaHeapUsedBytes: Long?,
    val availableMemoryBytes: Long?,
    val lowMemory: Boolean?,
    val thermalStatus: ThermalStatus,
) {
    init {
        require(timestampEpochMs >= 0) { "Resource snapshot timestamp must not be negative" }
        listOf(processPssBytes, nativeHeapBytes, javaHeapUsedBytes, availableMemoryBytes).forEach { value ->
            require(value == null || value >= 0) { "Resource snapshot byte values must not be negative" }
        }
    }
}

enum class ThermalStatus {
    NONE,
    LIGHT,
    MODERATE,
    SEVERE,
    CRITICAL,
    EMERGENCY,
    SHUTDOWN,
    UNKNOWN,
}

fun interface ResourceSnapshotProvider {
    fun snapshot(): ResourceSnapshot
}

data class BenchmarkKey(
    val applicationId: ApplicationId,
    val useCaseId: UseCaseId,
    val modelDigest: ModelDigest,
    val modelLoadKind: ModelLoadKind,
) {
    init {
        require(modelLoadKind != ModelLoadKind.UNKNOWN) { "Benchmark load kind must be explicit" }
    }

    val stableId: String
        get() = listOf(
            applicationId.value,
            useCaseId.value,
            modelDigest.sha256,
            modelLoadKind.name,
        ).joinToString("|")
}

data class BenchmarkBaseline(
    val key: BenchmarkKey,
    val capturedAtEpochMs: Long,
    val sampleCount: Int,
    val medianTimeToFirstTokenMs: Double?,
    val p95TimeToFirstTokenMs: Double?,
    val medianTotalMs: Double?,
    val p95TotalMs: Double?,
    val medianDecodeTokensPerSecond: Double?,
) {
    init {
        require(capturedAtEpochMs >= 0) { "Benchmark capture timestamp must not be negative" }
        require(sampleCount > 0) { "Benchmark sample count must be positive" }
    }
}

data class DeveloperDashboardSnapshot(
    val runtime: RuntimeSnapshot,
    val recentRuns: List<GenerationRunRecord>,
    val recentLogs: List<StructuredLog>,
    val health: List<HealthCheckResult>,
    val resources: List<ResourceSnapshot> = emptyList(),
    val benchmarkBaselines: List<BenchmarkBaseline> = emptyList(),
    val modelStoreBytes: Long,
    val modelCount: Int,
)

data class TelemetryRetentionPolicy(
    val maxRuns: Int = 500,
    val maxLogs: Int = 2_000,
    val maxResourceSnapshots: Int = 500,
    val maxBenchmarkBaselines: Int = 200,
) {
    init {
        require(maxRuns > 0) { "maxRuns must be positive" }
        require(maxLogs > 0) { "maxLogs must be positive" }
        require(maxResourceSnapshots > 0) { "maxResourceSnapshots must be positive" }
        require(maxBenchmarkBaselines > 0) { "maxBenchmarkBaselines must be positive" }
    }
}

@Suppress("TooManyFunctions")
interface TelemetryRepository {
    fun recordRun(run: GenerationRunRecord)

    fun appendLog(log: StructuredLog)

    fun saveHealth(result: HealthCheckResult)

    fun recordResourceSnapshot(snapshot: ResourceSnapshot)

    fun saveBenchmarkBaseline(baseline: BenchmarkBaseline)

    fun recentRuns(limit: Int = 100): List<GenerationRunRecord>

    fun findRun(requestId: RequestId): GenerationRunRecord?

    fun recentLogs(limit: Int = 500, requestId: RequestId? = null): List<StructuredLog>

    fun healthResults(): List<HealthCheckResult>

    fun recentResourceSnapshots(limit: Int = 100): List<ResourceSnapshot>

    fun benchmarkBaselines(): List<BenchmarkBaseline>

    fun benchmarkBaselineHistory(limit: Int = 100): List<BenchmarkBaseline>

    fun dashboard(runtime: RuntimeSnapshot): DeveloperDashboardSnapshot
}

@Suppress("TooManyFunctions")
object NoOpTelemetryRepository : TelemetryRepository {
    override fun recordRun(run: GenerationRunRecord) = Unit

    override fun appendLog(log: StructuredLog) = Unit

    override fun saveHealth(result: HealthCheckResult) = Unit

    override fun recordResourceSnapshot(snapshot: ResourceSnapshot) = Unit

    override fun saveBenchmarkBaseline(baseline: BenchmarkBaseline) = Unit

    override fun recentRuns(limit: Int): List<GenerationRunRecord> = emptyList()

    override fun findRun(requestId: RequestId): GenerationRunRecord? = null

    override fun recentLogs(limit: Int, requestId: RequestId?): List<StructuredLog> = emptyList()

    override fun healthResults(): List<HealthCheckResult> = emptyList()

    override fun recentResourceSnapshots(limit: Int): List<ResourceSnapshot> = emptyList()

    override fun benchmarkBaselines(): List<BenchmarkBaseline> = emptyList()

    override fun benchmarkBaselineHistory(limit: Int): List<BenchmarkBaseline> = emptyList()

    override fun dashboard(runtime: RuntimeSnapshot): DeveloperDashboardSnapshot = DeveloperDashboardSnapshot(
        runtime = runtime,
        recentRuns = emptyList(),
        recentLogs = emptyList(),
        health = emptyList(),
        resources = emptyList(),
        benchmarkBaselines = emptyList(),
        modelStoreBytes = 0L,
        modelCount = 0,
    )
}