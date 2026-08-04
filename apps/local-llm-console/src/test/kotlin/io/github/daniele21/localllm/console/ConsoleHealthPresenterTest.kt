package io.github.daniele21.localllm.console

import io.github.daniele21.localllm.observability.HealthCheckResult
import io.github.daniele21.localllm.observability.HealthStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleHealthPresenterTest {
    private val presenter = ConsoleHealthPresenter()

    @Test
    fun `disconnected source exposes no execution actions`() {
        val screen = presenter.present(emptySnapshot())

        assertEquals("Health execution", screen.cards.first().title)
        assertTrue(screen.cards.first().lines.contains("Status: Not connected"))
        assertTrue(screen.actions.isEmpty())
    }

    @Test
    fun `connected source exposes run all and individual actions`() {
        val snapshot = emptySnapshot().copy(
            healthControl = ConsoleHealthControlState(
                available = true,
                source = "embedded runtime",
                checkIds = listOf("generation-sanity:app:chat", "model-integrity"),
            ),
        )

        val screen = presenter.present(snapshot)

        assertEquals(3, screen.actions.size)
        assertEquals(ConsoleActionType.RUN_ALL_HEALTH_CHECKS, screen.actions[0].type)
        assertEquals(listOf("generation-sanity:app:chat"), screen.actions[1].healthCheckIds)
        assertEquals("Run model integrity", screen.actions[2].label)
        assertTrue(screen.actions.all { it.enabled })
    }

    @Test
    fun `running state disables all actions`() {
        val snapshot = emptySnapshot().copy(
            healthControl = ConsoleHealthControlState(
                available = true,
                source = "embedded runtime",
                checkIds = listOf("model-integrity"),
                executionInProgress = true,
            ),
        )

        val screen = presenter.present(snapshot)

        assertTrue(screen.subtitle.contains("in progress"))
        assertFalse(screen.actions.any { it.enabled })
        assertTrue(screen.cards.first().lines.contains("Status: Running"))
    }

    @Test
    fun `persisted results remain ordered by severity after control card`() {
        val snapshot = emptySnapshot().copy(
            health = listOf(
                HealthCheckResult("pass", HealthStatus.PASS, "ok", 1),
                HealthCheckResult("warn", HealthStatus.WARN, "slow", 2),
                HealthCheckResult("fail", HealthStatus.FAIL, "broken", 3),
            ),
        )

        val screen = presenter.present(snapshot)

        assertEquals("Health execution", screen.cards[0].title)
        assertEquals("FAIL · fail", screen.cards[1].title)
        assertEquals("WARN · warn", screen.cards[2].title)
        assertEquals("PASS · pass", screen.cards[3].title)
    }

    private fun emptySnapshot() = ConsoleSnapshot(
        capturedAtEpochMs = 0,
        runtime = DisconnectedRuntimeStateProvider.snapshot(),
        runs = emptyList(),
        logs = emptyList(),
        health = emptyList(),
        resources = emptyList(),
        benchmarkBaselines = emptyList(),
    )
}
