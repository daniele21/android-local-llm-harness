package io.github.daniele21.localllm.console

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.ModelLoadKind
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.observability.GenerationRunRecord
import io.github.daniele21.localllm.observability.HealthCheckResult
import io.github.daniele21.localllm.observability.HealthStatus
import io.github.daniele21.localllm.observability.LogLevel
import io.github.daniele21.localllm.observability.RunStatus
import io.github.daniele21.localllm.observability.StructuredLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class ConsolePresenterTest {
    private val presenter = ConsolePresenter(ZoneOffset.UTC)

    @Test
    fun `overview reports disconnected source without inventing runtime values`() {
        val screen = presenter.present(ConsoleTab.OVERVIEW, emptySnapshot())
        val runtime = screen.cards.first { it.title == "Runtime" }

        assertTrue(runtime.lines.contains("Status: Not connected"))
        assertTrue(runtime.lines.contains("Active sessions: Unavailable"))
        assertTrue(runtime.lines.contains("Queue depth: Unavailable"))
    }

    @Test
    fun `run view exposes metrics but not prompt or generated output`() {
        val snapshot = emptySnapshot().copy(runs = listOf(run()))

        val screen = presenter.present(ConsoleTab.RUNS, snapshot)
        val text = screen.cards.single().lines.joinToString("\n")

        assertTrue(text.contains("TTFT: 5 ms"))
        assertTrue(text.contains("Tokens: 4 in / 6 out"))
        assertFalse(text.contains("secret prompt"))
        assertFalse(text.contains("secret output"))
    }

    @Test
    fun `health view orders failures before warnings and passes`() {
        val snapshot =
            emptySnapshot().copy(
                health = listOf(
                    HealthCheckResult("pass", HealthStatus.PASS, "ok", 1),
                    HealthCheckResult("warn", HealthStatus.WARN, "slow", 2),
                    HealthCheckResult("fail", HealthStatus.FAIL, "broken", 3),
                ),
            )

        val screen = presenter.present(ConsoleTab.HEALTH, snapshot)

        assertEquals("FAIL · fail", screen.cards[0].title)
        assertEquals("WARN · warn", screen.cards[1].title)
        assertEquals("PASS · pass", screen.cards[2].title)
    }

    @Test
    fun `log view sorts structured fields deterministically`() {
        val snapshot =
            emptySnapshot().copy(
                logs = listOf(
                    StructuredLog(
                        timestampEpochMs = 0,
                        level = LogLevel.INFO,
                        component = "runtime",
                        event = "completed",
                        requestId = RequestId("request"),
                        fields = linkedMapOf("z" to "last", "a" to "first"),
                    ),
                ),
            )

        val screen = presenter.present(ConsoleTab.LOGS, snapshot)

        assertTrue(screen.cards.single().lines.contains("Fields: a=first · z=last"))
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

    private fun run() = GenerationRunRecord(
        requestId = RequestId("request-1234567890"),
        applicationId = ApplicationId("app"),
        useCaseId = UseCaseId("chat"),
        modelDigest = ModelDigest("c".repeat(64)),
        startedAtEpochMs = 0,
        completedAtEpochMs = 20,
        status = RunStatus.COMPLETED,
        queueMs = 1,
        modelLoadMs = 2,
        timeToFirstTokenMs = 5,
        totalMs = 20,
        inputTokens = 4,
        outputTokens = 6,
        decodeTokensPerSecond = 3.5,
        prefillMs = 7,
        decodeMs = 8,
        modelLoadKind = ModelLoadKind.COLD,
    )
}
