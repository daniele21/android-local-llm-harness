package io.github.daniele21.localllm.console

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.ModelLoadKind
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.observability.BenchmarkBaseline
import io.github.daniele21.localllm.observability.BenchmarkKey
import io.github.daniele21.localllm.observability.GenerationRunRecord
import io.github.daniele21.localllm.observability.HealthCheckResult
import io.github.daniele21.localllm.observability.HealthStatus
import io.github.daniele21.localllm.observability.LogLevel
import io.github.daniele21.localllm.observability.ResourceSnapshot
import io.github.daniele21.localllm.observability.RunStatus
import io.github.daniele21.localllm.observability.StructuredLog
import io.github.daniele21.localllm.observability.TelemetryRepository
import io.github.daniele21.localllm.observability.ThermalStatus
import io.github.daniele21.localllm.observability.store.InMemoryTelemetryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConsoleDataSourceTest {
    @Test
    fun `loads bounded telemetry and runtime state`() {
        val repository = InMemoryTelemetryRepository()
        repeat(3) { index -> repository.recordRun(run(index)) }
        repeat(4) { index -> repository.appendLog(log(index)) }
        repository.saveHealth(HealthCheckResult("model-integrity", HealthStatus.PASS, "verified", 8))
        repository.recordResourceSnapshot(resource())
        repository.saveBenchmarkBaseline(baseline())

        val dataSource = TelemetryConsoleDataSource(
            telemetryRepository = repository,
            runtimeStateProvider = ConsoleRuntimeStateProvider {
                ConsoleRuntimeState(
                    status = "Ready",
                    backend = "llama.cpp",
                    loadedModel = "abc123",
                    activeSessions = 1,
                    queueDepth = 2,
                    source = "In process",
                )
            },
            clockEpochMs = { 99L },
            runLimit = 2,
            logLimit = 3,
            resourceLimit = 1,
        )

        val snapshot = dataSource.load()

        assertEquals(99L, snapshot.capturedAtEpochMs)
        assertEquals("Ready", snapshot.runtime.status)
        assertEquals(2, snapshot.runs.size)
        assertEquals(3, snapshot.logs.size)
        assertEquals(1, snapshot.health.size)
        assertEquals(1, snapshot.resources.size)
        assertEquals(1, snapshot.benchmarkBaselines.size)
        assertNull(snapshot.sourceError)
    }

    @Test
    fun `loads request run and chronological correlated timeline`() {
        val repository = InMemoryTelemetryRepository()
        val requestId = RequestId("request-1")
        repository.recordRun(run(1))
        repository.appendLog(log(30, requestId))
        repository.appendLog(log(10, requestId))
        repository.appendLog(log(20, RequestId("other-request")))

        val detail = TelemetryConsoleDataSource(repository).loadRequest(requestId)

        assertEquals(requestId, detail.run?.requestId)
        assertEquals(listOf(10L, 30L), detail.timeline.map { it.timestampEpochMs })
        assertNull(detail.sourceError)
    }

    @Test
    fun `returns privacy safe error without exposing repository failure`() {
        val dataSource = TelemetryConsoleDataSource(
            telemetryRepository = FailingTelemetryRepository(),
            clockEpochMs = { 7L },
        )

        val snapshot = dataSource.load()
        val detail = dataSource.loadRequest(RequestId("request"))

        assertEquals("Telemetry source unavailable", snapshot.sourceError)
        assertEquals(emptyList<GenerationRunRecord>(), snapshot.runs)
        assertEquals("Not connected", snapshot.runtime.status)
        assertEquals("Telemetry source unavailable", detail.sourceError)
        assertNull(detail.run)
        assertEquals(emptyList<StructuredLog>(), detail.timeline)
    }

    private fun run(index: Int) = GenerationRunRecord(
        requestId = RequestId("request-$index"),
        applicationId = ApplicationId("app"),
        useCaseId = UseCaseId("chat"),
        modelDigest = ModelDigest("a".repeat(64)),
        startedAtEpochMs = index.toLong(),
        completedAtEpochMs = index.toLong() + 1,
        status = RunStatus.COMPLETED,
        queueMs = 1,
        modelLoadMs = null,
        timeToFirstTokenMs = 2,
        totalMs = 3,
        inputTokens = 4,
        outputTokens = 5,
        decodeTokensPerSecond = 6.0,
        modelLoadKind = ModelLoadKind.WARM,
        errorCode = null,
    )

    private fun log(index: Int, requestId: RequestId? = null) = StructuredLog(
        timestampEpochMs = index.toLong(),
        level = LogLevel.INFO,
        component = "runtime",
        event = "event-$index",
        requestId = requestId,
    )

    private fun resource() = ResourceSnapshot(
        timestampEpochMs = 1,
        processPssBytes = 10,
        nativeHeapBytes = 20,
        javaHeapUsedBytes = 30,
        availableMemoryBytes = 40,
        lowMemory = false,
        thermalStatus = ThermalStatus.NONE,
    )

    private fun baseline() = BenchmarkBaseline(
        key = BenchmarkKey(
            applicationId = ApplicationId("app"),
            useCaseId = UseCaseId("chat"),
            modelDigest = ModelDigest("b".repeat(64)),
            modelLoadKind = ModelLoadKind.COLD,
        ),
        capturedAtEpochMs = 1,
        sampleCount = 3,
        medianTimeToFirstTokenMs = 10.0,
        p95TimeToFirstTokenMs = 12.0,
        medianTotalMs = 20.0,
        p95TotalMs = 22.0,
        medianDecodeTokensPerSecond = 8.0,
    )

    private class FailingTelemetryRepository : TelemetryRepository by InMemoryTelemetryRepository() {
        override fun recentRuns(limit: Int): List<GenerationRunRecord> = error("private database path")

        override fun findRun(requestId: RequestId): GenerationRunRecord? = error("private database path")
    }
}
