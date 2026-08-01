package io.github.daniele21.localllm.observability.store

import io.github.daniele21.localllm.contracts.RuntimeSnapshot
import io.github.daniele21.localllm.observability.DeveloperDashboardSnapshot
import io.github.daniele21.localllm.observability.GenerationRunRecord
import io.github.daniele21.localllm.observability.HealthCheckResult
import io.github.daniele21.localllm.observability.StructuredLog
import io.github.daniele21.localllm.observability.TelemetryRepository
import java.util.ArrayDeque

class InMemoryTelemetryRepository(private val maxRuns: Int = 500, private val maxLogs: Int = 2_000) : TelemetryRepository {
    private val lock = Any()
    private val runs = ArrayDeque<GenerationRunRecord>()
    private val logs = ArrayDeque<StructuredLog>()
    private val health = linkedMapOf<String, HealthCheckResult>()

    override fun recordRun(run: GenerationRunRecord) = synchronized(lock) {
        runs.removeAll { it.requestId == run.requestId }
        runs.offerFirst(run)
        while (runs.size > maxRuns) runs.removeLast()
    }

    override fun appendLog(log: StructuredLog) = synchronized(lock) {
        logs.offerFirst(log)
        while (logs.size > maxLogs) logs.removeLast()
    }

    override fun saveHealth(result: HealthCheckResult) = synchronized(lock) {
        health[result.id] = result
    }

    override fun dashboard(runtime: RuntimeSnapshot): DeveloperDashboardSnapshot = synchronized(lock) {
        DeveloperDashboardSnapshot(
            runtime = runtime,
            recentRuns = runs.toList(),
            recentLogs = logs.toList(),
            health = health.values.toList(),
            modelStoreBytes = 0L,
            modelCount = 0,
        )
    }
}
