package io.github.daniele21.localllm.observability

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ChatTemplateSource
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.ModelLoadKind
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.RuntimeSnapshot
import io.github.daniele21.localllm.contracts.SeedPolicyType
import io.github.daniele21.localllm.contracts.StopReason
import io.github.daniele21.localllm.contracts.ThinkingMode
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
    val presetId: InferencePresetId? = null,
    val presetVersion: Int? = null,
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val minP: Float? = null,
    val presencePenalty: Float? = null,
    val thinkingMode: ThinkingMode? = null,
    val repeatPenalty: Float? = null,
    val repeatLastN: Int? = null,
    val seedPolicy: SeedPolicyType? = null,
    val effectiveSeed: Long? = null,
    val maxOutputTokens: Int? = null,
    val contextSize: Int? = null,
    val promptTokenCount: Int? = null,
    val chatTemplateId: String? = null,
    val chatTemplateSource: ChatTemplateSource? = null,
    val systemPromptVersion: String? = null,
    val stopReason: StopReason? = null,
    val promptPlanningMs: Long? = null,
    val contextCreationMs: Long? = null,
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

data class BenchmarkExecutionIdentity(val fingerprint: String) {
    init {
        require(FINGERPRINT_PATTERN.matches(fingerprint)) { "Benchmark execution fingerprint must be SHA-256" }
    }

    companion object {
        private val FINGERPRINT_PATTERN = Regex("[0-9a-f]{64}")

        fun fromFingerprint(fingerprint: String): BenchmarkExecutionIdentity = BenchmarkExecutionIdentity(fingerprint.lowercase())

        fun fromRun(run: GenerationRunRecord): BenchmarkExecutionIdentity {
            val canonical = listOf(
                value(run.contextSize),
                value(run.promptTokenCount),
                value(run.presetId?.value),
                value(run.presetVersion),
                value(run.thinkingMode?.name),
                floatValue(run.temperature),
                floatValue(run.topP),
                value(run.topK),
                floatValue(run.minP),
                floatValue(run.presencePenalty),
                floatValue(run.repeatPenalty),
                value(run.repeatLastN),
                value(run.seedPolicy?.name),
                value(run.effectiveSeed),
                value(run.maxOutputTokens),
                value(run.chatTemplateId),
                value(run.chatTemplateSource?.name),
                value(run.systemPromptVersion),
            ).joinToString("|")
            val digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(canonical.toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte) }
            return BenchmarkExecutionIdentity(digest)
        }

        private fun value(value: Any?): String = value?.toString() ?: "~"

        private fun floatValue(value: Float?): String = value?.toRawBits()?.toString() ?: "~"
    }
}

data class BenchmarkKey(
    val applicationId: ApplicationId,
    val useCaseId: UseCaseId,
    val modelDigest: ModelDigest,
    val modelLoadKind: ModelLoadKind,
    val executionIdentity: BenchmarkExecutionIdentity,
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
            executionIdentity.fingerprint,
        ).joinToString("|")

    fun matches(run: GenerationRunRecord): Boolean = run.applicationId == applicationId &&
        run.useCaseId == useCaseId &&
        run.modelDigest == modelDigest &&
        run.modelLoadKind == modelLoadKind &&
        BenchmarkExecutionIdentity.fromRun(run) == executionIdentity

    companion object {
        fun fromRun(run: GenerationRunRecord): BenchmarkKey = BenchmarkKey(
            applicationId = run.applicationId,
            useCaseId = run.useCaseId,
            modelDigest = run.modelDigest,
            modelLoadKind = run.modelLoadKind,
            executionIdentity = BenchmarkExecutionIdentity.fromRun(run),
        )
    }
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
