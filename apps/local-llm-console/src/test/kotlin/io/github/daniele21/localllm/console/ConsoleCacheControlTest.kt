package io.github.daniele21.localllm.console

import io.github.daniele21.localllm.observability.CacheHealthProbe
import io.github.daniele21.localllm.observability.CacheHealthSnapshot
import io.github.daniele21.localllm.observability.CacheMaintenanceControl
import io.github.daniele21.localllm.observability.CacheRepairResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleCacheControlTest {
    @Test
    fun `snapshot exposes sorted probes and repair capability`() {
        val control = ContractConsoleCacheControl(
            probes = listOf(probe("z-cache"), probe("a-cache")),
            maintenanceControls = listOf(maintenance("z-cache")),
            source = "embedded runtime",
        )

        val state = control.snapshot()

        assertTrue(state.available)
        assertEquals("embedded runtime", state.source)
        assertEquals(listOf("a-cache", "z-cache"), state.caches.map { it.id })
        assertFalse(state.caches[0].repairAvailable)
        assertTrue(state.caches[1].repairAvailable)
    }

    @Test
    fun `one failing probe does not hide healthy cache snapshots`() {
        val failing = object : CacheHealthProbe {
            override val id: String = "broken"

            override fun snapshot(): CacheHealthSnapshot = error("private failure")
        }
        val control = ContractConsoleCacheControl(
            probes = listOf(failing, probe("healthy")),
            maintenanceControls = emptyList(),
            source = "embedded runtime",
        )

        val state = control.snapshot()

        assertEquals("Cache health unavailable", state.caches.first { it.id == "broken" }.sourceError)
        assertTrue(state.caches.first { it.id == "healthy" }.snapshot?.healthy == true)
        assertNull(state.sourceError)
    }

    @Test
    fun `repair delegates only to matching maintenance control`() {
        val expected = repairResult()
        val control = ContractConsoleCacheControl(
            probes = listOf(probe("model-integrity", expected.before)),
            maintenanceControls = listOf(maintenance("model-integrity", expected)),
            source = "embedded runtime",
        )

        val outcome = control.repair("model-integrity")

        assertEquals("model-integrity", outcome.cacheId)
        assertEquals(expected, outcome.result)
        assertNull(outcome.sourceError)
    }

    @Test
    fun `missing repair capability returns fixed privacy safe error`() {
        val control = ContractConsoleCacheControl(
            probes = listOf(probe("read-only")),
            maintenanceControls = emptyList(),
            source = "embedded runtime",
        )

        val outcome = control.repair("read-only")

        assertNull(outcome.result)
        assertEquals("Cache repair unavailable", outcome.sourceError)
    }

    private fun probe(id: String, snapshot: CacheHealthSnapshot = CacheHealthSnapshot(1, 0, 0)): CacheHealthProbe =
        object : CacheHealthProbe {
            override val id: String = id

            override fun snapshot(): CacheHealthSnapshot = snapshot
        }

    private fun maintenance(id: String, result: CacheRepairResult = repairResult()): CacheMaintenanceControl =
        object : CacheMaintenanceControl {
            override val id: String = id

            override fun repair(): CacheRepairResult = result
        }

    private fun repairResult(): CacheRepairResult = CacheRepairResult(
        before = CacheHealthSnapshot(entryCount = 2, staleEntryCount = 1, orphanedEntryCount = 1),
        after = CacheHealthSnapshot(entryCount = 1, staleEntryCount = 0, orphanedEntryCount = 0),
        revalidatedEntryCount = 1,
        removedEntryCount = 1,
        failedEntryCount = 0,
    )
}
