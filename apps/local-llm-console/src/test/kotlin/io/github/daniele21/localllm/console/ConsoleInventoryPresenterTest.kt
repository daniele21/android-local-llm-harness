package io.github.daniele21.localllm.console

import io.github.daniele21.localllm.contracts.ModelDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class ConsoleInventoryPresenterTest {
    private val presenter = ConsolePresenter(ZoneOffset.UTC)

    @Test
    fun `model view marks the active model and blocks its removal`() {
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
            modelInventory = inventory(digest),
            modelControl = connectedControl(),
        )

        val screen = presenter.present(ConsoleTab.MODELS, snapshot)
        val model = screen.cards.first { it.title.startsWith("ACTIVE") }
        val remove = screen.actions.first { it.type == ConsoleActionType.REMOVE_MODEL }

        assertEquals("ACTIVE · ${digest.sha256.take(16)}", model.title)
        assertTrue(model.lines.contains("Size: 2.0 MiB"))
        assertTrue(model.lines.contains("Snapshot integrity: Verified"))
        assertTrue(model.lines.contains("Runtime role: Loaded"))
        assertFalse(remove.enabled)
        assertTrue(remove.label.startsWith("Cannot remove loaded"))
        assertTrue(screen.actions.any { it.type == ConsoleActionType.IMPORT_MODEL && it.enabled })
        assertTrue(screen.actions.any { it.type == ConsoleActionType.VERIFY_MODEL && it.enabled })
    }

    @Test
    fun `model actions are disabled while an operation is running`() {
        val digest = ModelDigest("e".repeat(64))
        val snapshot = emptySnapshot().copy(
            modelInventory = inventory(digest),
            modelControl = connectedControl().copy(executionInProgress = true),
        )

        val screen = presenter.present(ConsoleTab.MODELS, snapshot)

        assertTrue(screen.actions.isNotEmpty())
        assertTrue(screen.actions.none { it.enabled })
    }

    @Test
    fun `model view renders the latest explicit verification outcome`() {
        val digest = ModelDigest("f".repeat(64))
        val outcome = ConsoleModelOperationOutcome(
            operation = ConsoleModelOperation.VERIFY,
            digest = digest,
            success = true,
            detail = "Model integrity verified",
        )
        val snapshot = emptySnapshot().copy(
            modelInventory = inventory(digest),
            modelControl = connectedControl().copy(lastOperation = outcome),
        )

        val screen = presenter.present(ConsoleTab.MODELS, snapshot)
        val model = screen.cards.first { it.title == digest.sha256.take(16) }

        assertTrue(screen.cards.any { it.title == "Latest model operation · VERIFY" })
        assertTrue(model.lines.contains("Latest explicit verification: Passed"))
        assertEquals(ConsoleEmphasis.POSITIVE, model.emphasis)
    }

    @Test
    fun `model view distinguishes disconnected inventory from an empty store`() {
        val screen = presenter.present(ConsoleTab.MODELS, emptySnapshot())

        assertTrue(screen.cards.any { it.lines.contains("Availability: Not connected") })
        assertTrue(screen.cards.any { it.lines.contains("Model inventory is not connected") })
        assertTrue(screen.actions.isEmpty())
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

    private fun inventory(digest: ModelDigest) = ConsoleModelInventory(
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
    )

    private fun connectedControl() = ConsoleModelControlState(
        available = true,
        source = "Embedded runtime",
        importAvailable = true,
        verifyAvailable = true,
        removeAvailable = true,
    )

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
