package io.github.daniele21.localllm.phonetest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceNavigationTest {
    @Test
    fun `performance is a top-level Harness destination`() {
        assertTrue(HarnessDestination.PERFORMANCE in HarnessDestination.main)
        assertEquals(
            HarnessDestination.PERFORMANCE,
            HarnessDestination.fromRoute(PerformanceRoutes.ROOT),
        )
        assertEquals(
            HarnessDestination.PERFORMANCE,
            HarnessRoutes.shellState(PerformanceRoutes.ROOT).destination,
        )
    }

    @Test
    fun `compact label does not replace accessible destination identity`() {
        assertEquals("Performance", HarnessDestination.PERFORMANCE.label)
        assertEquals("Perf", HarnessDestination.PERFORMANCE.compactLabel)
    }
}
