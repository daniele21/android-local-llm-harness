package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ModelDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessViewModelTest {
    @Test
    fun modelSelectionClearsPendingRemovalConfirmation() {
        val viewModel = HarnessViewModel(
            HarnessUiState(removalConfirmationPending = true),
        )
        val model = testModel()

        viewModel.dispatch(HarnessUiEvent.ModelChanged(model))

        assertEquals(model, viewModel.uiState.value.importedModel)
        assertFalse(viewModel.uiState.value.removalConfirmationPending)
    }

    @Test
    fun distributionUpdatesOperationStatusAndBusyState() {
        val viewModel = HarnessViewModel()
        val distribution = PhoneModelDistributionState(
            message = "Downloading model…",
            operationActive = true,
        )

        viewModel.dispatch(HarnessUiEvent.ModelDistributionChanged(distribution))

        assertEquals("Downloading model…", viewModel.uiState.value.operationStatus)
        assertTrue(viewModel.uiState.value.busy)
        assertTrue(viewModel.uiState.value.keepScreenOn)
    }

    @Test
    fun diagnosticActionsAreTrackedIndependently() {
        val viewModel = HarnessViewModel()

        viewModel.dispatch(
            HarnessUiEvent.DiagnosticActionChanged(
                HarnessDiagnosticAction.HEALTH,
                running = true,
            ),
        )
        viewModel.dispatch(
            HarnessUiEvent.DiagnosticActionChanged(
                HarnessDiagnosticAction.RESOURCE_CAPTURE,
                running = true,
            ),
        )
        viewModel.dispatch(
            HarnessUiEvent.DiagnosticActionChanged(
                HarnessDiagnosticAction.HEALTH,
                running = false,
            ),
        )

        assertTrue(viewModel.uiState.value.diagnosticActionRunning)
        assertEquals(
            setOf(HarnessDiagnosticAction.RESOURCE_CAPTURE),
            viewModel.uiState.value.activeDiagnosticActions,
        )
        assertFalse(viewModel.uiState.value.keepScreenOn)
    }

    @Test
    fun activePlaygroundKeepsTheScreenOn() {
        val viewModel = HarnessViewModel()

        viewModel.dispatch(
            HarnessUiEvent.PlaygroundChanged(
                PlaygroundState(phase = PlaygroundPhase.GENERATING),
            ),
        )

        assertTrue(viewModel.uiState.value.busy)
        assertTrue(viewModel.uiState.value.keepScreenOn)
    }

    @Test
    fun leavingLogsClosesTheSelectedRequestTimeline() {
        val timeline = DiagnosticsRequestTimelineUi(
            requestId = "request-1",
            requestIdPrefix = "request-1",
            runStatus = "COMPLETED",
        )
        val viewModel = HarnessViewModel(
            HarnessUiState(
                diagnosticsSection = DiagnosticsSection.LOGS,
                selectedRequestTimeline = timeline,
            ),
        )

        viewModel.dispatch(
            HarnessUiEvent.DiagnosticsSectionChanged(DiagnosticsSection.HEALTH),
        )

        assertEquals(DiagnosticsSection.HEALTH, viewModel.uiState.value.diagnosticsSection)
        assertNull(viewModel.uiState.value.selectedRequestTimeline)
    }

    @Test
    fun completedReportUsesTheCanonicalOperationMessage() {
        val viewModel = HarnessViewModel()

        viewModel.dispatch(HarnessUiEvent.ReportChanged("validation report"))

        assertEquals("validation report", viewModel.uiState.value.latestReport)
        assertEquals("Validation completed", viewModel.uiState.value.operationStatus)
    }

    private fun testModel(): ImportedPhoneModel = ImportedPhoneModel(
        digest = ModelDigest("0".repeat(64)),
        fileName = "test.gguf",
        sizeBytes = 1234,
        architecture = "qwen3",
        quantization = "Q4_K_M",
    )
}