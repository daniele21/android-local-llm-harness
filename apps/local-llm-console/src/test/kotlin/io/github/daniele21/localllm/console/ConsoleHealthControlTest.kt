package io.github.daniele21.localllm.console

import io.github.daniele21.localllm.observability.HealthStatus
import io.github.daniele21.localllm.observability.health.HealthAssessment
import io.github.daniele21.localllm.observability.health.HealthCheck
import io.github.daniele21.localllm.observability.health.HealthEngine
import io.github.daniele21.localllm.observability.store.InMemoryTelemetryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleHealthControlTest {
    @Test
    fun `snapshot exposes sorted registered check ids`() {
        val control = control(check("z-check"), check("a-check"))

        val state = control.snapshot()

        assertTrue(state.available)
        assertEquals("test source", state.source)
        assertEquals(listOf("a-check", "z-check"), state.checkIds)
    }

    @Test
    fun `selected execution delegates to health engine and persists result`() {
        val repository = InMemoryTelemetryRepository()
        val control = HealthEngineConsoleHealthControl(
            healthEngine = HealthEngine(
                checks = listOf(check("pass"), check("warn", HealthStatus.WARN)),
                telemetryRepository = repository,
            ),
            source = "test source",
        )

        val outcome = control.run(listOf("warn"))

        assertEquals(listOf("warn"), outcome.requestedCheckIds)
        assertEquals(HealthStatus.WARN, outcome.status)
        assertEquals(listOf("warn"), outcome.results.map { it.id })
        assertEquals(listOf("warn"), repository.healthResults().map { it.id })
        assertNull(outcome.sourceError)
    }

    @Test
    fun `run all reports worst status`() {
        val control = control(check("pass"), check("fail", HealthStatus.FAIL))

        val outcome = control.runAll()

        assertEquals(HealthStatus.FAIL, outcome.status)
        assertEquals(setOf("pass", "fail"), outcome.results.map { it.id }.toSet())
    }

    @Test
    fun `disconnected execution returns fixed privacy safe error`() {
        val outcome = DisconnectedHealthControl.runAll()

        assertNull(outcome.status)
        assertTrue(outcome.results.isEmpty())
        assertEquals("Health execution unavailable", outcome.sourceError)
    }

    private fun control(vararg checks: HealthCheck): ConsoleHealthControl = HealthEngineConsoleHealthControl(
        healthEngine = HealthEngine(
            checks = checks.toList(),
            telemetryRepository = InMemoryTelemetryRepository(),
        ),
        source = "test source",
    )

    private fun check(checkId: String, status: HealthStatus = HealthStatus.PASS): HealthCheck = object : HealthCheck {
        override val id: String = checkId

        override fun evaluate(): HealthAssessment = HealthAssessment(status, "safe detail")
    }
}
