package io.github.daniele21.localllm.console

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.ModelLoadKind
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.observability.BenchmarkBaseline
import io.github.daniele21.localllm.observability.BenchmarkKey
import io.github.daniele21.localllm.observability.GenerationRunRecord
import io.github.daniele21.localllm.observability.HealthStatus
import io.github.daniele21.localllm.observability.RunStatus
import io.github.daniele21.localllm.observability.benchmark.BenchmarkPolicy
import io.github.daniele21.localllm.observability.store.InMemoryTelemetryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleBenchmarkDataSourceTest {
    private val key = BenchmarkKey(
        applicationId = ApplicationId("app"),
        useCaseId = UseCaseId("assistant"),
        modelDigest = ModelDigest("a".repeat(64)),
        modelLoadKind = ModelLoadKind.WARM,
    )

    @Test
    fun `loads active baseline retained history and an independent comparison window`() {
        val repository = InMemoryTelemetryRepository()
        val previous = baseline(capturedAt = 50L, medianTtft = 90.0)
        val active = baseline(capturedAt = 100L, medianTtft = 100.0)
        repository.saveBenchmarkBaseline(previous)
        repository.saveBenchmarkBaseline(active)
        listOf(
            run("current-1", completedAt = 101L, ttft = 140L),
            run("current-2", completedAt = 102L, ttft = 150L),
            run("current-3", completedAt = 103L, ttft = 160L),
        ).forEach(repository::recordRun)

        val snapshot = TelemetryConsoleDataSource(
            telemetryRepository = repository,
            runLimit = 1,
            benchmarkPolicy = BenchmarkPolicy(
                baselineWindowSize = 5,
                comparisonWindowSize = 3,
                minimumBaselineSamples = 5,
                minimumComparisonSamples = 3,
            ),
        ).load()

        assertEquals(listOf(active), snapshot.benchmarkBaselines)
        assertEquals(listOf(active, previous), snapshot.benchmarkHistory)
        assertEquals(1, snapshot.runs.size)
        assertEquals(1, snapshot.benchmarkComparisons.size)
        assertEquals(3, snapshot.benchmarkComparisons.single().availableSamples)
        assertEquals(HealthStatus.FAIL, snapshot.benchmarkComparisons.single().status)
        assertTrue(snapshot.benchmarkComparisons.single().comparisonReady)
    }

    private fun baseline(capturedAt: Long, medianTtft: Double): BenchmarkBaseline = BenchmarkBaseline(
        key = key,
        capturedAtEpochMs = capturedAt,
        sampleCount = 5,
        medianTimeToFirstTokenMs = medianTtft,
        p95TimeToFirstTokenMs = 120.0,
        medianTotalMs = 1_000.0,
        p95TotalMs = 1_100.0,
        medianDecodeTokensPerSecond = 100.0,
    )

    private fun run(id: String, completedAt: Long, ttft: Long): GenerationRunRecord = GenerationRunRecord(
        requestId = RequestId(id),
        applicationId = key.applicationId,
        useCaseId = key.useCaseId,
        modelDigest = key.modelDigest,
        startedAtEpochMs = completedAt - 1,
        completedAtEpochMs = completedAt,
        status = RunStatus.COMPLETED,
        queueMs = 1,
        modelLoadMs = null,
        timeToFirstTokenMs = ttft,
        totalMs = 1_500,
        inputTokens = 10,
        outputTokens = 10,
        decodeTokensPerSecond = 70.0,
        errorCode = null,
        modelLoadKind = key.modelLoadKind,
    )
}
