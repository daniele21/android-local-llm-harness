package io.github.daniele21.localllm.console

import io.github.daniele21.localllm.observability.CacheHealthSnapshot
import io.github.daniele21.localllm.observability.CacheRepairResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleCachePresenterTest {
    private val presenter = ConsoleCachePresenter()

    @Test
    fun `disconnected source exposes explicit empty state without actions`() {
        val screen = presenter.present(emptySnapshot())

        assertEquals("Cache diagnostics control", screen.cards[0].title)
        assertTrue(screen.cards[0].lines.contains("Connected: false"))
        assertTrue(screen.cards[1].lines.contains("Cache diagnostics source not connected"))
        assertTrue(screen.actions.isEmpty())
    }

    @Test
    fun `unhealthy repairable cache exposes targeted action`() {
        val snapshot = emptySnapshot().copy(
            cacheControl = ConsoleCacheControlState(
                available = true,
                source = "embedded runtime",
                caches = listOf(
                    ConsoleCacheDescriptor(
                        id = "model-integrity",
                        snapshot = CacheHealthSnapshot(3, 1, 1),
                        repairAvailable = true,
                    ),
                ),
            ),
        )

        val screen = presenter.present(snapshot)

        assertEquals(1, screen.actions.size)
        assertEquals(ConsoleActionType.REPAIR_CACHE, screen.actions.single().type)
        assertEquals("model-integrity", screen.actions.single().cacheId)
        assertTrue(screen.actions.single().enabled)
        assertTrue(screen.cards[1].lines.contains("Stale entries: 1"))
        assertTrue(screen.cards[1].lines.contains("Orphaned entries: 1"))
    }

    @Test
    fun `healthy cache does not expose unnecessary repair action`() {
        val snapshot = emptySnapshot().copy(
            cacheControl = ConsoleCacheControlState(
                available = true,
                source = "embedded runtime",
                caches = listOf(
                    ConsoleCacheDescriptor(
                        id = "model-integrity",
                        snapshot = CacheHealthSnapshot(1, 0, 0),
                        repairAvailable = true,
                    ),
                ),
            ),
        )

        val screen = presenter.present(snapshot)

        assertTrue(screen.actions.isEmpty())
        assertEquals(ConsoleEmphasis.POSITIVE, screen.cards[1].emphasis)
    }

    @Test
    fun `running state disables repair action`() {
        val snapshot = emptySnapshot().copy(
            cacheControl = ConsoleCacheControlState(
                available = true,
                source = "embedded runtime",
                caches = listOf(
                    ConsoleCacheDescriptor(
                        id = "model-integrity",
                        snapshot = CacheHealthSnapshot(1, 1, 0),
                        repairAvailable = true,
                    ),
                ),
                executionInProgress = true,
            ),
        )

        val screen = presenter.present(snapshot)

        assertFalse(screen.actions.single().enabled)
        assertTrue(screen.cards.first().lines.contains("Execution: Running"))
    }

    @Test
    fun `repair outcome reports before after and failures`() {
        val result = CacheRepairResult(
            before = CacheHealthSnapshot(2, 1, 1),
            after = CacheHealthSnapshot(1, 1, 0),
            revalidatedEntryCount = 0,
            removedEntryCount = 1,
            failedEntryCount = 1,
        )
        val snapshot = emptySnapshot().copy(
            cacheControl = ConsoleCacheControlState(
                available = true,
                source = "embedded runtime",
                caches = emptyList(),
                lastRepair = ConsoleCacheRepairOutcome("model-integrity", result),
            ),
        )

        val screen = presenter.present(snapshot)
        val outcomeCard = screen.cards.first { it.title == "Repair model-integrity" }

        assertTrue(outcomeCard.lines.contains("Removed: 1"))
        assertTrue(outcomeCard.lines.contains("Failed: 1"))
        assertTrue(outcomeCard.lines.contains("After: 1 stale · 0 orphaned"))
        assertEquals(ConsoleEmphasis.NEGATIVE, outcomeCard.emphasis)
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
