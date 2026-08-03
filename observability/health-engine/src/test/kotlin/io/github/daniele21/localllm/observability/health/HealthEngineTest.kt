package io.github.daniele21.localllm.observability.health

import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.RuntimeSnapshot
import io.github.daniele21.localllm.observability.DeveloperDashboardSnapshot
import io.github.daniele21.localllm.observability.GenerationRunRecord
import io.github.daniele21.localllm.observability.HealthCheckResult
import io.github.daniele21.localllm.observability.HealthStatus
import io.github.daniele21.localllm.observability.ResourceSnapshot
import io.github.daniele21.localllm.observability.StructuredLog
import io.github.daniele21.localllm.observability.TelemetryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthEngineTest {
    @Test
    fun `runs checks persists results and aggregates worst status`() {
        val repository = RecordingTelemetryRepository()
        val clock = SequenceClock(0L, 2_000_000L, 3_000_000L, 8_000_000L)
        val engine = HealthEngine(
            checks = listOf(
                FixedCheck("healthy", HealthStatus.PASS),
                FixedCheck("warning", HealthStatus.WARN),
            ),
            telemetryRepository = repository,
            clock = clock,
        )

        val report = engine.runAll()

        assertEquals(HealthStatus.WARN, report.status)
        assertEquals(listOf(2L, 5L), report.results.map(HealthCheckResult::durationMs))
        assertEquals(report.results, repository.health)
    }

    @Test
    fun `converts unexpected check failure to privacy safe result`() {
        val repository = RecordingTelemetryRepository()
        val engine = HealthEngine(
            checks = listOf(
                object : HealthCheck {
                    override val id: String = "explodes"

                    override fun evaluate(): HealthAssessment = error("secret path and prompt")
                },
            ),
            telemetryRepository = repository,
            clock = SequenceClock(0L, 1_000_000L),
        )

        val result = engine.runAll().results.single()

        assertEquals(HealthStatus.FAIL, result.status)
        assertEquals("Health check failed unexpectedly", result.detail)
        assertTrue("secret" !in result.detail)
    }

    @Test
    fun `returns not run for unknown checks`() {
        val repository = RecordingTelemetryRepository()
        val result = HealthEngine(emptyList(), repository).run(listOf("missing")).results.single()

        assertEquals(HealthStatus.NOT_RUN, result.status)
        assertEquals(0L, result.durationMs)
    }

    private data class FixedCheck(override val id: String, val status: HealthStatus) : HealthCheck {
        override fun evaluate(): HealthAssessment = HealthAssessment(status, id)
    }

    private class SequenceClock(vararg values: Long) : HealthClock {
        private val iterator = values.iterator()

        override fun nowNanos(): Long = iterator.next()
    }

    private class RecordingTelemetryRepository : TelemetryRepository {
        val health = mutableListOf<HealthCheckResult>()

        override fun recordRun(run: GenerationRunRecord) = Unit

        override fun appendLog(log: StructuredLog) = Unit

        override fun saveHealth(result: HealthCheckResult) {
            health += result
        }

        override fun recordResourceSnapshot(snapshot: ResourceSnapshot) = Unit

        override fun recentRuns(limit: Int): List<GenerationRunRecord> = emptyList()

        override fun findRun(requestId: RequestId): GenerationRunRecord? = null

        override fun recentLogs(limit: Int, requestId: RequestId?): List<StructuredLog> = emptyList()

        override fun healthResults(): List<HealthCheckResult> = health

        override fun recentResourceSnapshots(limit: Int): List<ResourceSnapshot> = emptyList()

        override fun dashboard(runtime: RuntimeSnapshot): DeveloperDashboardSnapshot = error("Not needed")
    }
}
