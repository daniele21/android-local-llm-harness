package io.github.daniele21.localllm.phonetest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HarnessDiagnosticsOverviewTest {
    @Test
    fun `empty diagnostics remains explicitly not run and not captured`() {
        val overview = harnessDiagnosticsOverviewState(
            diagnostics = DiagnosticsUiState(null, emptyList(), emptyList()),
            resources = DiagnosticsResourceHistoryUi(),
            benchmarks = BenchmarkUiState(),
            logs = DiagnosticsLogUiState(),
            validationReport = "",
        )

        assertEquals("Not run", overview.health)
        assertEquals(0, overview.runCount)
        assertEquals(0, overview.resourceCount)
        assertEquals(0, overview.benchmarkCount)
        assertEquals(0, overview.logCount)
        assertFalse(overview.validationAvailable)
    }
}
