package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.GenerationMetrics
import io.github.daniele21.localllm.contracts.GenerationRequest
import io.github.daniele21.localllm.contracts.LocalLlmError
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.ModelLoadKind
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.RuntimeSnapshot
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.observability.BenchmarkBaseline
import io.github.daniele21.localllm.observability.DeveloperDashboardSnapshot
import io.github.daniele21.localllm.observability.GenerationRunRecord
import io.github.daniele21.localllm.observability.HealthCheckResult
import io.github.daniele21.localllm.observability.ResourceSnapshot
import io.github.daniele21.localllm.observability.RunStatus
import io.github.daniele21.localllm.observability.StructuredLog
import io.github.daniele21.localllm.observability.TelemetryRepository
import io.github.daniele21.localllm.observability.store.InMemoryTelemetryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class RuntimeTelemetryTest {
    @Test
    fun `completed generation persists a privacy-safe timeline and metrics`() {
        val repository = InMemoryTelemetryRepository()
        val clock = SequenceEpochClock(1_000L, 1_001L, 1_002L, 1_003L, 1_004L, 1_005L)
        val telemetry = RuntimeTelemetry(repository, clock)
        val request = request(input = "secret prompt text")

        telemetry.queued(request, ModelDigest("a".repeat(64)))
        telemetry.queuedPosition(request.requestId, 2)
        telemetry.started(request.requestId)
        telemetry.completed(
            request.requestId,
            GenerationMetrics(
                queueMs = 11,
                modelLoadMs = 22,
                timeToFirstTokenMs = 33,
                totalMs = 44,
                inputTokens = 5,
                outputTokens = 6,
                decodeTokensPerSecond = 7.5,
                prefillMs = 8,
                decodeMs = 9,
                modelLoadKind = ModelLoadKind.COLD,
            ),
        )

        val run = requireNotNull(repository.findRun(request.requestId))
        assertEquals(RunStatus.COMPLETED, run.status)
        assertEquals(1_000L, run.startedAtEpochMs)
        assertEquals(1_004L, run.completedAtEpochMs)
        assertEquals(8L, run.prefillMs)
        assertEquals(9L, run.decodeMs)
        assertEquals(ModelLoadKind.COLD, run.modelLoadKind)

        val persistedText = repository.recentLogs()
            .flatMap { it.fields.entries }
            .flatMap { listOf(it.key, it.value) }
            .joinToString(" ")
        assertFalse(persistedText.contains("secret prompt text"))
        assertFalse(persistedText.contains("generated output"))
        assertEquals(
            listOf(
                "generation.completed",
                "generation.started",
                "generation.queue_position",
                "generation.queued",
            ),
            repository.recentLogs().map { it.event },
        )
    }

    @Test
    fun `cancelled generation receives a cancelled terminal run`() {
        val repository = InMemoryTelemetryRepository()
        val telemetry = RuntimeTelemetry(repository, SequenceEpochClock(10L, 11L, 12L, 13L, 14L))
        val request = request(input = "do not persist me")

        telemetry.queued(request, ModelDigest("b".repeat(64)))
        telemetry.started(request.requestId)
        telemetry.failed(request.requestId, LocalLlmError.Cancelled())

        val run = requireNotNull(repository.findRun(request.requestId))
        assertEquals(RunStatus.CANCELLED, run.status)
        assertEquals("CANCELLED", run.errorCode)
        assertNull(run.outputTokens)
    }

    @Test
    fun `telemetry storage failures never fail runtime instrumentation`() {
        val telemetry = RuntimeTelemetry(
            ThrowingTelemetryRepository,
            SequenceEpochClock(1L, 2L, 3L, 4L, 5L),
        )
        val request = request(input = "ignored")

        telemetry.queued(request, ModelDigest("c".repeat(64)))
        telemetry.started(request.requestId)
        telemetry.failed(request.requestId, LocalLlmError.NativeRuntime("native failure"))
    }

    private fun request(input: String): GenerationRequest = GenerationRequest(
        requestId = RequestId("request-1"),
        sessionId = SessionId("session-1"),
        applicationId = ApplicationId("app"),
        useCaseId = UseCaseId("assistant"),
        input = input,
    )
}

private class SequenceEpochClock(vararg values: Long) : EpochClock {
    private val iterator = values.iterator()

    override fun nowEpochMs(): Long = iterator.nextLong()
}

private object ThrowingTelemetryRepository : TelemetryRepository {
    override fun recordRun(run: GenerationRunRecord) = fail()

    override fun appendLog(log: StructuredLog) = fail()

    override fun saveHealth(result: HealthCheckResult) = fail()

    override fun recordResourceSnapshot(snapshot: ResourceSnapshot) = fail()

    override fun saveBenchmarkBaseline(baseline: BenchmarkBaseline) = fail()

    override fun recentRuns(limit: Int): List<GenerationRunRecord> = fail()

    override fun findRun(requestId: RequestId): GenerationRunRecord? = fail()

    override fun recentLogs(limit: Int, requestId: RequestId?): List<StructuredLog> = fail()

    override fun healthResults(): List<HealthCheckResult> = fail()

    override fun recentResourceSnapshots(limit: Int): List<ResourceSnapshot> = fail()

    override fun benchmarkBaselines(): List<BenchmarkBaseline> = fail()

    override fun benchmarkBaselineHistory(limit: Int): List<BenchmarkBaseline> = fail()

    override fun dashboard(runtime: RuntimeSnapshot): DeveloperDashboardSnapshot = fail()

    private fun fail(): Nothing = error("telemetry unavailable")
}
