package io.github.daniele21.localllm.phonetest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceSurfaceStateTest {
    @Test
    fun `datasets preserve loading failure empty and available states`() {
        assertTrue(performanceDatasetSurfaceState(PerformanceDatasetState(loading = true)) is PerformanceSurfaceState.Loading)
        assertEquals(
            PerformanceSurfaceState.Failure("registry unavailable"),
            performanceDatasetSurfaceState(PerformanceDatasetState(error = "registry unavailable")),
        )
        assertTrue(performanceDatasetSurfaceState(PerformanceDatasetState()) is PerformanceSurfaceState.Empty)
        assertEquals(
            PerformanceSurfaceState.Available(3),
            performanceDatasetSurfaceState(PerformanceDatasetState(installedCount = 3)),
        )
    }

    @Test
    fun `history and compare never turn missing data into zero-value evidence`() {
        assertTrue(performanceHistorySurfaceState(PerformanceHistoryState()) is PerformanceSurfaceState.Empty)
        assertTrue(performanceCompareSurfaceState(PerformanceState()) is PerformanceSurfaceState.Empty)
    }
}
