package io.github.daniele21.localllm.phonetest

internal enum class HarnessThemePreference(val label: String) {
    DARK("Dark"),
    LIGHT("Light"),
    SYSTEM("System"),
}

internal enum class HarnessDiagnosticAction {
    HEALTH,
    RESOURCE_CAPTURE,
    BENCHMARK_CAPTURE,
}

internal data class HarnessUiState(
    val importedModel: ImportedPhoneModel? = null,
    val modelDistribution: PhoneModelDistributionState = PhoneModelDistributionState(),
    val latestReport: String = "",
    val controllerBusy: Boolean = false,
    val activeDiagnosticActions: Set<HarnessDiagnosticAction> = emptySet(),
    val operationStatus: String = "Ready",
    val playground: PlaygroundState = PlaygroundState(),
    val diagnostics: DiagnosticsUiState = DiagnosticsUiState(null, emptyList(), emptyList()),
    val benchmark: BenchmarkUiState = BenchmarkUiState(),
    val logFilter: DiagnosticsLogFilter = DiagnosticsLogFilter(),
    val logs: DiagnosticsLogUiState = DiagnosticsLogUiState(),
    val selectedRequestTimeline: DiagnosticsRequestTimelineUi? = null,
    val diagnosticsSection: DiagnosticsSection = DiagnosticsSection.HEALTH,
    val playgroundPrompt: String = DEFAULT_PROMPT,
    val playgroundMaxTokens: String = DEFAULT_MAX_OUTPUT_TOKENS,
    val playgroundTemperature: String = DEFAULT_TEMPERATURE,
    val playgroundSeed: String = DEFAULT_SEED,
    val removalConfirmationPending: Boolean = false,
    val themePreference: HarnessThemePreference = HarnessThemePreference.DARK,
) {
    val diagnosticActionRunning: Boolean
        get() = activeDiagnosticActions.isNotEmpty()

    val busy: Boolean
        get() = controllerBusy || modelDistribution.operationActive || playground.active

    val keepScreenOn: Boolean
        get() = busy

    private companion object {
        const val DEFAULT_MAX_OUTPUT_TOKENS = "128"
        const val DEFAULT_TEMPERATURE = "0.2"
        const val DEFAULT_SEED = "42"
        const val DEFAULT_PROMPT = "Explain in two sentences why local inference improves privacy."
    }
}

internal sealed interface HarnessUiEvent {
    data class ControllerBusyChanged(val busy: Boolean) : HarnessUiEvent

    data class ModelDistributionChanged(val state: PhoneModelDistributionState) : HarnessUiEvent

    data class ModelChanged(val model: ImportedPhoneModel?) : HarnessUiEvent

    data class ReportChanged(val report: String) : HarnessUiEvent

    data class OperationStatusChanged(val status: String) : HarnessUiEvent

    data class PlaygroundChanged(val state: PlaygroundState) : HarnessUiEvent

    data class DiagnosticActionChanged(val action: HarnessDiagnosticAction, val running: Boolean) : HarnessUiEvent

    data class DiagnosticsChanged(val state: DiagnosticsUiState) : HarnessUiEvent

    data class BenchmarkChanged(val state: BenchmarkUiState) : HarnessUiEvent

    data class LogFilterChanged(val filter: DiagnosticsLogFilter, val state: DiagnosticsLogUiState) : HarnessUiEvent

    data class LogsChanged(val state: DiagnosticsLogUiState) : HarnessUiEvent

    data class RequestTimelineChanged(val timeline: DiagnosticsRequestTimelineUi?) : HarnessUiEvent

    data class DiagnosticsSectionChanged(val section: DiagnosticsSection) : HarnessUiEvent

    data class PlaygroundPromptChanged(val prompt: String) : HarnessUiEvent

    data class PlaygroundMaxTokensChanged(val maxTokens: String) : HarnessUiEvent

    data class PlaygroundTemperatureChanged(val temperature: String) : HarnessUiEvent

    data class PlaygroundSeedChanged(val seed: String) : HarnessUiEvent

    data class RemovalConfirmationChanged(val pending: Boolean) : HarnessUiEvent

    data class ThemeChanged(val preference: HarnessThemePreference) : HarnessUiEvent
}

internal object HarnessUiReducer {
    fun reduce(state: HarnessUiState, event: HarnessUiEvent): HarnessUiState = when (event) {
        is HarnessUiEvent.ControllerBusyChanged -> state.copy(controllerBusy = event.busy)

        is HarnessUiEvent.ModelDistributionChanged -> state.copy(
            modelDistribution = event.state,
            operationStatus = event.state.message,
        )

        is HarnessUiEvent.ModelChanged -> state.copy(
            importedModel = event.model,
            removalConfirmationPending = false,
        )

        is HarnessUiEvent.ReportChanged -> state.copy(
            latestReport = event.report,
            operationStatus = "Validation completed",
        )

        is HarnessUiEvent.OperationStatusChanged -> state.copy(operationStatus = event.status)

        is HarnessUiEvent.PlaygroundChanged -> state.copy(playground = event.state)

        is HarnessUiEvent.DiagnosticActionChanged -> state.copy(
            activeDiagnosticActions = state.activeDiagnosticActions.withAction(event.action, event.running),
        )

        is HarnessUiEvent.DiagnosticsChanged -> state.copy(diagnostics = event.state)

        is HarnessUiEvent.BenchmarkChanged -> state.copy(benchmark = event.state)

        is HarnessUiEvent.LogFilterChanged -> state.copy(
            logFilter = event.filter,
            logs = event.state,
        )

        is HarnessUiEvent.LogsChanged -> state.copy(logs = event.state)

        is HarnessUiEvent.RequestTimelineChanged -> state.copy(selectedRequestTimeline = event.timeline)

        is HarnessUiEvent.DiagnosticsSectionChanged -> state.copy(
            diagnosticsSection = event.section,
            selectedRequestTimeline = state.selectedRequestTimeline.takeIf {
                event.section == DiagnosticsSection.LOGS
            },
        )

        is HarnessUiEvent.PlaygroundPromptChanged -> state.copy(playgroundPrompt = event.prompt)

        is HarnessUiEvent.PlaygroundMaxTokensChanged -> state.copy(playgroundMaxTokens = event.maxTokens)

        is HarnessUiEvent.PlaygroundTemperatureChanged -> state.copy(
            playgroundTemperature = event.temperature,
        )

        is HarnessUiEvent.PlaygroundSeedChanged -> state.copy(playgroundSeed = event.seed)

        is HarnessUiEvent.RemovalConfirmationChanged -> state.copy(
            removalConfirmationPending = event.pending,
        )

        is HarnessUiEvent.ThemeChanged -> state.copy(themePreference = event.preference)
    }

    private fun Set<HarnessDiagnosticAction>.withAction(action: HarnessDiagnosticAction, running: Boolean): Set<HarnessDiagnosticAction> =
        if (running) this + action else this - action
}
