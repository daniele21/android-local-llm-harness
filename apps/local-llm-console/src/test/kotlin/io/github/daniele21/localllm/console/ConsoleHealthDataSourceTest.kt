package io.github.daniele21.localllm.console

import io.github.daniele21.localllm.observability.store.InMemoryTelemetryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleHealthDataSourceTest {
    @Test
    fun `data source exposes connected health control state`() {
        val source = TelemetryConsoleDataSource(
            telemetryRepository = InMemoryTelemetryRepository(),
            healthControl = healthControl(
                ConsoleHealthControlState(
                    available = true,
                    source = "embedded runtime",
                    checkIds = listOf("model-integrity"),
                ),
            ),
            clockEpochMs = { 10L },
        )

        val snapshot = source.load()

        assertEquals(10L, snapshot.capturedAtEpochMs)
        assertTrue(snapshot.healthControl.available)
        assertEquals(listOf("model-integrity"), snapshot.healthControl.checkIds)
    }

    @Test
    fun `health control state failure is isolated with fixed error`() {
        val source = TelemetryConsoleDataSource(
            telemetryRepository = InMemoryTelemetryRepository(),
            healthControl = object : ConsoleHealthControl {
                override fun snapshot(): ConsoleHealthControlState = error("private failure")

                override fun runAll(): ConsoleHealthRunOutcome = error("not used")

                override fun run(checkIds: Collection<String>): ConsoleHealthRunOutcome = error("not used")
            },
        )

        val snapshot = source.load()

        assertFalse(snapshot.healthControl.available)
        assertEquals("Unavailable", snapshot.healthControl.source)
        assertEquals("Health execution unavailable", snapshot.healthControl.sourceError)
    }

    private fun healthControl(state: ConsoleHealthControlState): ConsoleHealthControl = object : ConsoleHealthControl {
        override fun snapshot(): ConsoleHealthControlState = state

        override fun runAll(): ConsoleHealthRunOutcome = error("not used")

        override fun run(checkIds: Collection<String>): ConsoleHealthRunOutcome = error("not used")
    }
}
