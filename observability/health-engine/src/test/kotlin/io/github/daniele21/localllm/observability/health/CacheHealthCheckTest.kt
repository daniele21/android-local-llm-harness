package io.github.daniele21.localllm.observability.health

import io.github.daniele21.localllm.observability.CacheHealthProbe
import io.github.daniele21.localllm.observability.CacheHealthSnapshot
import io.github.daniele21.localllm.observability.HealthStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheHealthCheckTest {
    @Test
    fun `passes for an empty consistent cache`() {
        val check = CacheHealthCheck(FixedProbe(CacheHealthSnapshot(0, 0, 0)))

        val result = check.evaluate()

        assertEquals("cache-health:model-integrity", check.id)
        assertEquals(HealthStatus.PASS, result.status)
        assertTrue("0 tracked entry" in result.detail)
    }

    @Test
    fun `passes when every tracked entry is current`() {
        val result = CacheHealthCheck(FixedProbe(CacheHealthSnapshot(3, 0, 0))).evaluate()

        assertEquals(HealthStatus.PASS, result.status)
        assertTrue("3 tracked entry" in result.detail)
    }

    @Test
    fun `fails with aggregate counts for stale and orphaned entries`() {
        val result = CacheHealthCheck(
            FixedProbe(CacheHealthSnapshot(entryCount = 4, staleEntryCount = 2, orphanedEntryCount = 1)),
        ).evaluate()

        assertEquals(HealthStatus.FAIL, result.status)
        assertTrue("2 stale" in result.detail)
        assertTrue("1 orphaned" in result.detail)
        assertFalse("private" in result.detail)
    }

    private class FixedProbe(
        private val result: CacheHealthSnapshot,
    ) : CacheHealthProbe {
        override val id: String = "model-integrity"

        override fun snapshot(): CacheHealthSnapshot = result
    }
}
