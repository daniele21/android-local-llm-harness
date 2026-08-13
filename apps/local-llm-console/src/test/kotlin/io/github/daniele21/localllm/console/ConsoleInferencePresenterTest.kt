package io.github.daniele21.localllm.console

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.UseCaseId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleInferencePresenterTest {
    private val presenter = ConsolePresenter()
    private val target = ConsoleInferenceTarget(
        applicationId = ApplicationId("app"),
        useCaseId = UseCaseId("chat"),
        label = "Local chat",
    )

    @Test
    fun `disconnected playground exposes explicit connect action`() {
        val screen = presenter.present(ConsoleTab.PLAYGROUND, emptySnapshot())

        assertTrue(screen.cards.any { it.lines.contains("Inference playground is not connected") })
        assertEquals(ConsoleActionType.CONNECT_SHARED_RUNTIME, screen.actions.single().type)
        assertEquals("Connect shared runtime", screen.actions.single().label)
    }

    @Test
    fun `connecting playground disables duplicate connect`() {
        val snapshot = emptySnapshot().copy(
            inference = DisconnectedConsoleInferenceControl.snapshot().copy(
                source = "Shared Android runtime (Binder)",
                connectionState = ConsoleInferenceConnectionState.CONNECTING,
                detail = "Connecting to shared runtime",
            ),
        )

        val screen = presenter.present(ConsoleTab.PLAYGROUND, snapshot)

        assertEquals(ConsoleActionType.CONNECT_SHARED_RUNTIME, screen.actions.single().type)
        assertFalse(screen.actions.single().enabled)
    }

    @Test
    fun `incompatible playground exposes retry without generation action`() {
        val snapshot = emptySnapshot().copy(
            inference = DisconnectedConsoleInferenceControl.snapshot().copy(
                source = "Shared Android runtime (Binder)",
                connectionState = ConsoleInferenceConnectionState.INCOMPATIBLE,
                detail = "Shared-runtime protocol is incompatible",
                sourceError = "Shared-runtime protocol is incompatible",
            ),
        )

        val screen = presenter.present(ConsoleTab.PLAYGROUND, snapshot)

        assertEquals(ConsoleActionType.CONNECT_SHARED_RUNTIME, screen.actions.single().type)
        assertEquals("Retry shared runtime connection", screen.actions.single().label)
        assertTrue(screen.actions.none { it.type == ConsoleActionType.START_INFERENCE })
    }

    @Test
    fun `idle connected playground exposes start action and targets`() {
        val snapshot = emptySnapshot().copy(
            inference = state(ConsoleInferencePhase.IDLE),
        )

        val screen = presenter.present(ConsoleTab.PLAYGROUND, snapshot)

        assertEquals(ConsoleActionType.START_INFERENCE, screen.actions.single().type)
        assertTrue(screen.actions.single().enabled)
        assertTrue(screen.cards.any { it.lines.any { line -> "Local chat" in line } })
    }

    @Test
    fun `generating playground exposes cancel streaming output and disabled start`() {
        val snapshot = emptySnapshot().copy(
            inference = state(ConsoleInferencePhase.GENERATING).copy(
                activeTargetId = target.id,
                output = "partial output",
                generatedTokens = 3,
                sessionActive = true,
                cancellationAvailable = true,
            ),
        )

        val screen = presenter.present(ConsoleTab.PLAYGROUND, snapshot)
        val start = screen.actions.single { it.type == ConsoleActionType.START_INFERENCE }

        assertFalse(start.enabled)
        assertTrue(screen.actions.any { it.type == ConsoleActionType.CANCEL_INFERENCE && it.enabled })
        assertTrue(screen.cards.any { it.lines.contains("partial output") })
    }

    @Test
    fun `completed playground exposes metrics and clear action`() {
        val snapshot = emptySnapshot().copy(
            inference = state(ConsoleInferencePhase.COMPLETED).copy(
                activeTargetId = target.id,
                output = "completed output",
                generatedTokens = 4,
                metrics = ConsoleInferenceMetrics(
                    queueMs = 1,
                    modelLoadMs = 2,
                    timeToFirstTokenMs = 3,
                    prefillMs = 4,
                    decodeMs = 5,
                    totalMs = 6,
                    inputTokens = 7,
                    outputTokens = 4,
                    decodeTokensPerSecond = 8.5,
                    modelLoadKind = "WARM",
                ),
            ),
        )

        val screen = presenter.present(ConsoleTab.PLAYGROUND, snapshot)

        assertTrue(screen.actions.any { it.type == ConsoleActionType.START_INFERENCE })
        assertTrue(screen.actions.any { it.type == ConsoleActionType.CLEAR_INFERENCE })
        assertTrue(screen.cards.any { it.lines.contains("Decode throughput: 8.50 tok/s") })
        assertTrue(screen.cards.any { it.lines.contains("Prompt persistence: Disabled") })
    }

    private fun state(phase: ConsoleInferencePhase) = ConsoleInferenceState(
        available = true,
        source = "Embedded runtime",
        targets = listOf(target),
        phase = phase,
        connectionState = ConsoleInferenceConnectionState.CONNECTED,
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
