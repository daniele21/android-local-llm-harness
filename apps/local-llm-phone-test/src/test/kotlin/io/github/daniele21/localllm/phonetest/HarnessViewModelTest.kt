package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.SeedPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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

    @Test
    fun attachingPlaygroundEffectsPublishesItsSnapshot() {
        val snapshot = PlaygroundState(
            phase = PlaygroundPhase.COMPLETED,
            output = "local result",
        )
        val effects = FakePlaygroundEffects(snapshot)
        val viewModel = HarnessViewModel()

        viewModel.attachPlaygroundEffects(effects)

        assertEquals(snapshot, viewModel.uiState.value.playground)
    }

    @Test
    fun startingPlaygroundUsesCurrentStateAndParsedOptions() {
        val model = testModel()
        val effects = FakePlaygroundEffects()
        val viewModel = HarnessViewModel(
            HarnessUiState(
                importedModel = model,
                playgroundPrompt = "Explain local inference",
                playgroundMaxTokens = "64",
                playgroundTemperature = "0.4",
                playgroundSeed = "7",
            ),
        )
        viewModel.attachPlaygroundEffects(effects)

        val result = viewModel.startPlayground()

        assertEquals(PlaygroundStartResult.STARTED, result)
        assertSame(model, effects.startedModel)
        assertEquals("Explain local inference", effects.startedPrompt)
        assertEquals(64, effects.startedOptions?.maxOutputTokens)
        assertEquals(0.4f, effects.startedOptions?.temperature)
        assertEquals(1.05f, effects.startedOptions?.repeatPenalty)
        assertEquals(64, effects.startedOptions?.repeatLastN)
        assertEquals(SeedPolicy.Fixed(7), effects.startedOptions?.seedPolicy)
    }

    @Test
    fun invalidPlaygroundSettingsAreRejectedBeforeControllerInvocation() {
        val effects = FakePlaygroundEffects()
        val viewModel = HarnessViewModel(
            HarnessUiState(
                importedModel = testModel(),
                playgroundMaxTokens = "invalid",
            ),
        )
        viewModel.attachPlaygroundEffects(effects)

        val result = viewModel.startPlayground()

        assertEquals(PlaygroundStartResult.INVALID_SETTINGS, result)
        assertNull(effects.startedModel)
    }

    @Test
    fun activeOperationsPreventPlaygroundStart() {
        val effects = FakePlaygroundEffects()
        val viewModel = HarnessViewModel(
            HarnessUiState(
                importedModel = testModel(),
                controllerBusy = true,
            ),
        )
        viewModel.attachPlaygroundEffects(effects)

        val result = viewModel.startPlayground()

        assertEquals(PlaygroundStartResult.BUSY, result)
        assertNull(effects.startedModel)
    }

    @Test
    fun cancelAndRuntimeReleaseAreDelegatedToAttachedEffects() {
        val effects = FakePlaygroundEffects()
        val viewModel = HarnessViewModel()
        var releaseCompleted = false
        viewModel.attachPlaygroundEffects(effects)

        assertTrue(viewModel.cancelPlayground())
        assertTrue(viewModel.releasePlaygroundRuntime { releaseCompleted = true })
        assertTrue(effects.cancelCalled)
        assertTrue(effects.releaseCalled)
        assertTrue(releaseCompleted)
    }

    @Test
    fun manualSamplingChangeKeepsPresetAsCustomizationBase() {
        val viewModel = HarnessViewModel()

        viewModel.updatePlaygroundPreset("short-form")
        viewModel.updatePlaygroundTemperature("0.3")

        assertEquals("", viewModel.uiState.value.playgroundPreset)
        assertEquals("short-form", viewModel.uiState.value.playgroundBasePreset)
        assertEquals("0.3", viewModel.uiState.value.playgroundTemperature)
    }

    @Test
    fun repetitionOverridesKeepPresetAsCustomizationBase() {
        val viewModel = HarnessViewModel()

        viewModel.updatePlaygroundPreset("balanced-conversation")
        viewModel.updatePlaygroundRepeatPenalty("1.1")
        viewModel.updatePlaygroundRepeatLastN("96")

        assertEquals("", viewModel.uiState.value.playgroundPreset)
        assertEquals("balanced-conversation", viewModel.uiState.value.playgroundBasePreset)
        assertEquals("1.1", viewModel.uiState.value.playgroundRepeatPenalty)
        assertEquals("96", viewModel.uiState.value.playgroundRepeatLastN)
    }

    private fun testModel(): ImportedPhoneModel = ImportedPhoneModel(
        digest = ModelDigest("0".repeat(64)),
        fileName = "test.gguf",
        sizeBytes = 1234,
        architecture = "qwen3",
        quantization = "Q4_K_M",
    )

    private class FakePlaygroundEffects(private val current: PlaygroundState = PlaygroundState()) : PlaygroundEffects {
        var startedModel: ImportedPhoneModel? = null
        var startedPrompt: String? = null
        var startedOptions: PlaygroundRequestOptions? = null
        var cancelCalled: Boolean = false
        var releaseCalled: Boolean = false

        override fun snapshot(): PlaygroundState = current

        override fun start(model: ImportedPhoneModel, prompt: String, options: PlaygroundRequestOptions): Boolean {
            startedModel = model
            startedPrompt = prompt
            startedOptions = options
            return true
        }

        override fun cancel(): Boolean {
            cancelCalled = true
            return true
        }

        override fun releaseRuntime(onComplete: () -> Unit): Boolean {
            releaseCalled = true
            onComplete()
            return true
        }

        override fun close() = Unit
    }
}
