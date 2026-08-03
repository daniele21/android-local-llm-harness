package io.github.daniele21.localllm.observability.store

import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.RuntimeSnapshot
import io.github.daniele21.localllm.observability.BenchmarkBaseline
import io.github.daniele21.localllm.observability.DeveloperDashboardSnapshot
import io.github.daniele21.localllm.observability.GenerationRunRecord
import io.github.daniele21.localllm.observability.HealthCheckResult
import io.github.daniele21.localllm.observability.ResourceSnapshot
import io.github.daniele21.localllm.observability.StructuredLog
import io.github.daniele21.localllm.observability.TelemetryRepository
import io.github.daniele21.localllm.observability.TelemetryRetentionPolicy
import java.util.ArrayDeque

@Suppress("TooManyFunctions")
class InMemoryTelemetryRepository(private val retention: TelemetryRetentionPolicy = TelemetryRetentionPolicy()) : TelemetryRepository {
    constructor(maxRuns: Int, maxLogs: Int) : this(TelemetryRetentionPolicy(maxRuns, maxLogs))

    private val lock = Any()
    private val runs = ArrayDeque<GenerationRunRecord>()
    private val logs = ArrayDeque<StructuredLog>()
    private val health = linkedMapOf<String, HealthCheckResult>()
    private val resources = ArrayDeque<ResourceSnapshot>()
    private val baselines = linkedMapOf<String, BenchmarkBaseline>()

    override fun recordRun(run: GenerationRunRecord) = synchronized(lock) {
        runs.removeAll { it.requestId == run.requestId }
        runs.offerFirst(run)
        while (runs.size > retention.maxRuns) runs.removeLast()
    }

    override fun appendLog(log: StructuredLog) = synchronized(lock) {
        logs.offerFirst(log)
        while (logs.size > retention.maxLogs) logs.removeLast()
    }

    override fun saveHealth(result: HealthCheckResult) = synchronized(lock) {
        health[result.id] = result
    }

    override fun recordResourceSnapshot(snapshot: ResourceSnapshot) = synchronized(lock) {
        resources.offerFirst(snapshot)
        while (resources.size > retention.maxResourceSnapshots) resources.removeLast()
    }

    override fun saveBenchmarkBaseline(baseline: BenchmarkBaseline) = synchronized(lock) {
        baselines[baseline.key.stableId] = baseline
    }

    override fun recentRuns(limit: Int): List<GenerationRunRecord> = synchronized(lock) {
        runs.asSequence().take(requirePositiveLimit(limit)).toList()
    }

    override fun findRun(requestId: RequestId): GenerationRunRecord? = synchronized(lock) {
        runs.firstOrNull { it.requestId == requestId }
    }

    override fun recentLogs(limit: Int, requestId: RequestId?): List<StructuredLog> = synchronized(lock) {
        logs.asSequence()
            .filter { requestId == null || it.requestId == requestId }
            .take(requirePositiveLimit(limit))
            .toList()
    }

    override fun healthResults(): List<HealthCheckResult> = synchronized(lock) {
        health.values.toList()
    }

    override fun recentResourceSnapshots(limit: Int): List<ResourceSnapshot> = synchronized(lock) {
        resources.asSequence().take(requirePositiveLimit(limit)).toList()
    }

    override fun benchmarkBaselines(): List<BenchmarkBaseline> = synchronized(lock) {
        baselines.values.sortedBy { it.key.stableId }
    }

    override fun dashboard(runtime: RuntimeSnapshot): DeveloperDashboardSnapshot = synchronized(lock) {
        DeveloperDashboardSnapshot(
            runtime = runtime,
            recentRuns = runs.toList(),
            recentLogs = logs.toList(),
            health = health.values.toList(),
            resources = resources.toList(),
            benchmarkBaselines = baselines.values.sortedBy { it.key.stableId },
            modelStoreBytes = 0L,
            modelCount = 0,
        )
    }

    private fun requirePositiveLimit(limit: Int): Int {
        require(limit > 0) { "Telemetry query limit must be positive" }
        return limit
    }
}
