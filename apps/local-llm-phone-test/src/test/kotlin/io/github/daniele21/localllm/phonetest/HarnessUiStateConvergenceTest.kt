package io.github.daniele21.localllm.phonetest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessUiStateConvergenceTest {
    @Test
    fun diagnosticsStartsAtOverview() {
        assertEquals(DiagnosticsSection.OVERVIEW, HarnessUiState().diagnosticsSection)
    }

    @Test
    fun clearingReportDoesNotFabricateCompletedStatus() {
        val state = HarnessUiState(operationStatus = "Starting validation…")

        val reduced = HarnessUiReducer.reduce(state, HarnessUiEvent.ReportChanged(""))

        assertEquals("", reduced.latestReport)
        assertEquals("Starting validation…", reduced.operationStatus)
    }

    @Test
    fun completedReportPublishesCompletionStatus() {
        val reduced = HarnessUiReducer.reduce(
            HarnessUiState(operationStatus = "Running validation…"),
            HarnessUiEvent.ReportChanged("safe report"),
        )

        assertEquals("safe report", reduced.latestReport)
        assertEquals("Validation completed", reduced.operationStatus)
    }

    @Test
    fun diagnosticActionsAreOwnedByUiState() {
        val running = HarnessUiReducer.reduce(
            HarnessUiState(),
            HarnessUiEvent.DiagnosticActionChanged(HarnessDiagnosticAction.HEALTH, true),
        )
        assertTrue(running.diagnosticActionRunning)
        assertTrue(HarnessDiagnosticAction.HEALTH in running.activeDiagnosticActions)

        val stopped = HarnessUiReducer.reduce(
            running,
            HarnessUiEvent.DiagnosticActionChanged(HarnessDiagnosticAction.HEALTH, false),
        )
        assertFalse(stopped.diagnosticActionRunning)
    }
}
