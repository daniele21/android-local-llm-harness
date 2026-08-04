package io.github.daniele21.localllm.console

import io.github.daniele21.localllm.contracts.ModelDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class ConsoleInventoryPresenterTest {
    private val presenter = ConsolePresenter(ZoneOffset.UTC)

    @Test
    fun `model view marks the active installed model`() {
        val digest = ModelDigest("d".repeat(64))
        val snapshot = emptySnapshot().copy(
            runtime = ConsoleRuntimeState(
                status = "READY",
                backend = "llama.cpp",
                loadedModel = digest.sha256,
                activeSessions = 1,
                queueDepth = 0,
                source = "In process",
            ),
            modelInventory = ConsoleModelInventory(
                available = true,
                modelCount = 1,
                totalBytes = 2_097_152,
                entries = listOf(
                    ConsoleInstalledModel(
                        digest = digest,
                        sizeBytes = 2_097_152,
                        integrity = ConsoleModelIntegrity.VERIFIED,
                    ),
                ),
                source = "Embedded runtime",
            ),
        )

        val screen = presenter.present(ConsoleTab.MODELS, snapshot)
        val model = screen.cards.first { it.title.startsWith("ACTIVE") }

        assertEquals("ACTIVE · ${digest.sha256.take(16)}", model.title)
        assertTrue(model.lines.contains("Size: 2.0 MiB"))
        assertTrue(model.lines.contains("Integrity: Verified"))
        assertTrue(model.lines.contains("Runtime role: Loaded"))
    }

    @Test
    fun `model view distinguishes disconnected inventory from an empty store`() {
        val screen = presenter.present(ConsoleTab.MODELS, emptySnapshot())

        assertTrue(screen.cards.any { it.lines.contains("Availability: Not connected") })
        assertTrue(screen.cards.any { it.lines.contains("Model inventory is not connected") })
    }

    @Test
    fun `runtime view exposes snapshot values and explicit contract gaps`() {
        val snapshot = emptySnapshot().copy(
            runtime = ConsoleRuntimeState(
                status = "GENERATING",
                backend = "llama.cpp",
                loadedModel = "abc123",
                activeSessions = 2,
                queueDepth = 3,
                source = "In process",
            ),
        )

        val screen = presenter.present(ConsoleTab.RUNTIME, snapshot)

        assertTrue(screen.cards[0].lines.contains("Connection: Connected"))
        assertTrue(screen.cards[1].lines.contains("Active sessions: 2"))
        assertTrue(screen.cards[1].lines.contains("Queued requests: 3"))
        assertTrue(screen.cards[2].lines.contains("Context parameters: Not exposed by RuntimeSnapshot"))
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
