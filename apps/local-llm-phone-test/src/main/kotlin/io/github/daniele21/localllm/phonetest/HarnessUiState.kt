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

internal data class PlaygroundPresetOption(
    val id: String,
    val label: String,
    val description: String,
    val maxOutputTokens: String,
    val temperature: String,
    val topP: String,
    val topK: String,
    val seed: String,
    val preferredContextTokens: Int,
    val recommendedMaximumContextTokens: Int,
    val systemPrompt: String,
)

internal val playgroundPresetOptions = listOf(
    PlaygroundPresetOption(
        "precise-structured", "Preciso e strutturato", "Estrazione, JSON e benchmark riproducibili",
        "256", "0", "1", "40", "42", 2_048, 4_096,
        "Return only the requested structured result. Do not add commentary outside the required format.",
    ),
    PlaygroundPresetOption(
        "short-form", "Titoli e sintesi brevi", "Risposte concise senza preamboli",
        "384", "0.25", "0.85", "30", "42", 4_096, 4_096,
        "Return only the requested result. Avoid introductions and conclusions. Be concise and informative.",
    ),
    PlaygroundPresetOption(
        "accurate-summary", "Riassunto accurato", "Aderenza al testo e nessuna aggiunta",
        "768", "0.2", "0.9", "30", "42", 4_096, 8_192,
        "Summarize accurately using only information supported by the input. Do not invent details.",
    ),
    PlaygroundPresetOption(
        "balanced-conversation", "Conversazione bilanciata", "Impostazione generale naturale e controllata",
        "768", "0.6", "0.9", "40", "", 4_096, 8_192,
        "Be natural, accurate, and concise.",
    ),
    PlaygroundPresetOption(
        "creative-conversation", "Conversazione creativa", "Brainstorming e variazioni meno deterministiche",
        "1024", "0.85", "0.95", "50", "", 8_192, 8_192,
        "Be imaginative and offer useful variations while respecting the user's constraints.",
    ),
)

internal data class HarnessUiState(
    val importedModel: ImportedPhoneModel? = null,
    val modelDistribution: PhoneModelDistributionState = PhoneModelDistributionState(),
    val modelInventory: HarnessModelInventoryState = HarnessModelInventoryState(),
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
    val playgroundPreset: String = DEFAULT_PRESET,
    val playgroundBasePreset: String? = DEFAULT_PRESET,
    val playgroundTopP: String = DEFAULT_TOP_P,
    val playgroundTopK: String = DEFAULT_TOP_K,
    val playgroundSeed: String = DEFAULT_SEED,
    val playgroundContext: String = "",
    val removalConfirmationPending: Boolean = false,
    val modelRecoveryConfirmation: HarnessModelRecoveryRequest? = null,
    val themePreference: HarnessThemePreference = HarnessThemePreference.DARK,
) {
    val diagnosticActionRunning: Boolean
        get() = activeDiagnosticActions.isNotEmpty()

    val busy: Boolean
        get() = controllerBusy || modelDistribution.operationActive || playground.active

    val keepScreenOn: Boolean
        get() = busy

    private companion object {
        const val DEFAULT_MAX_OUTPUT_TOKENS = "768"
        const val DEFAULT_TEMPERATURE = "0.6"
        const val DEFAULT_PRESET = "balanced-conversation"
        const val DEFAULT_TOP_P = "0.9"
        const val DEFAULT_TOP_K = "40"
        const val DEFAULT_SEED = ""
        const val DEFAULT_PROMPT = "Explain in two sentences why local inference improves privacy."
    }
}

internal sealed interface HarnessUiEvent {
    sealed interface Runtime : HarnessUiEvent

    sealed interface Playground : HarnessUiEvent

    sealed interface Diagnostics : HarnessUiEvent

    sealed interface Preferences : HarnessUiEvent

    data class ControllerBusyChanged(val busy: Boolean) : Runtime

    data class ModelDistributionChanged(val state: PhoneModelDistributionState) : Runtime

    data class ModelChanged(val model: ImportedPhoneModel?) : Runtime

    data class LoadedModelChanged(val digest: String?) : Runtime

    data class ReportChanged(val report: String) : Runtime

    data class OperationStatusChanged(val status: String) : Runtime

    data class RemovalConfirmationChanged(val pending: Boolean) : Runtime

    data class ModelRecoveryConfirmationChanged(val request: HarnessModelRecoveryRequest?) : Runtime

    data class PlaygroundChanged(val state: PlaygroundState) : Playground

    data class PlaygroundPromptChanged(val prompt: String) : Playground

    data class PlaygroundMaxTokensChanged(val maxTokens: String) : Playground

    data class PlaygroundTemperatureChanged(val temperature: String) : Playground

    data class PlaygroundPresetChanged(val preset: String) : Playground

    data class PlaygroundTopPChanged(val topP: String) : Playground

    data class PlaygroundTopKChanged(val topK: String) : Playground

    data class PlaygroundSeedChanged(val seed: String) : Playground

    data class PlaygroundContextChanged(val context: String) : Playground

    data class DiagnosticActionChanged(val action: HarnessDiagnosticAction, val running: Boolean) : Diagnostics

    data class DiagnosticsChanged(val state: DiagnosticsUiState) : Diagnostics

    data class BenchmarkChanged(val state: BenchmarkUiState) : Diagnostics

    data class LogFilterChanged(val filter: DiagnosticsLogFilter, val state: DiagnosticsLogUiState) : Diagnostics

    data class LogsChanged(val state: DiagnosticsLogUiState) : Diagnostics

    data class RequestTimelineChanged(val timeline: DiagnosticsRequestTimelineUi?) : Diagnostics

    data class DiagnosticsSectionChanged(val section: DiagnosticsSection) : Diagnostics

    data class ThemeChanged(val preference: HarnessThemePreference) : Preferences
}

internal object HarnessUiReducer {
    fun reduce(state: HarnessUiState, event: HarnessUiEvent): HarnessUiState = when (event) {
        is HarnessUiEvent.Runtime -> reduceRuntime(state, event)
        is HarnessUiEvent.Playground -> reducePlayground(state, event)
        is HarnessUiEvent.Diagnostics -> reduceDiagnostics(state, event)
        is HarnessUiEvent.Preferences -> reducePreferences(state, event)
    }

    private fun reduceRuntime(state: HarnessUiState, event: HarnessUiEvent.Runtime): HarnessUiState = when (event) {
        is HarnessUiEvent.ControllerBusyChanged -> state.copy(controllerBusy = event.busy)

        is HarnessUiEvent.ModelDistributionChanged -> state.copy(
            modelDistribution = event.state,
            modelInventory = HarnessModelInventoryReconciler.reconcile(
                distribution = event.state,
                selectedModel = state.importedModel,
                loadedDigest = state.modelInventory.loadedDigest,
            ),
            operationStatus = event.state.message,
            modelRecoveryConfirmation = null,
        )

        is HarnessUiEvent.ModelChanged -> state.copy(
            importedModel = event.model,
            modelInventory = HarnessModelInventoryReconciler.reconcile(
                distribution = state.modelDistribution,
                selectedModel = event.model,
                loadedDigest = state.modelInventory.loadedDigest,
            ),
            removalConfirmationPending = false,
            modelRecoveryConfirmation = null,
        )

        is HarnessUiEvent.LoadedModelChanged -> state.copy(
            modelInventory = HarnessModelInventoryReconciler.reconcile(
                distribution = state.modelDistribution,
                selectedModel = state.importedModel,
                loadedDigest = event.digest,
            ),
            modelRecoveryConfirmation = null,
        )

        is HarnessUiEvent.ReportChanged -> state.copy(
            latestReport = event.report,
            operationStatus = "Validation completed",
        )

        is HarnessUiEvent.OperationStatusChanged -> state.copy(operationStatus = event.status)

        is HarnessUiEvent.RemovalConfirmationChanged -> state.copy(
            removalConfirmationPending = event.pending,
        )

        is HarnessUiEvent.ModelRecoveryConfirmationChanged -> state.copy(
            modelRecoveryConfirmation = event.request,
        )
    }

    private fun reducePlayground(state: HarnessUiState, event: HarnessUiEvent.Playground): HarnessUiState = when (event) {
        is HarnessUiEvent.PlaygroundChanged -> state.copy(playground = event.state)

        is HarnessUiEvent.PlaygroundPromptChanged -> state.copy(playgroundPrompt = event.prompt)

        is HarnessUiEvent.PlaygroundMaxTokensChanged -> state.copy(playgroundMaxTokens = event.maxTokens, playgroundPreset = "")

        is HarnessUiEvent.PlaygroundTemperatureChanged -> state.copy(
            playgroundTemperature = event.temperature,
            playgroundPreset = "",
        )

        is HarnessUiEvent.PlaygroundPresetChanged -> state.applyPreset(event.preset)

        is HarnessUiEvent.PlaygroundTopPChanged -> state.copy(playgroundTopP = event.topP, playgroundPreset = "")

        is HarnessUiEvent.PlaygroundTopKChanged -> state.copy(playgroundTopK = event.topK, playgroundPreset = "")

        is HarnessUiEvent.PlaygroundSeedChanged -> state.copy(playgroundSeed = event.seed, playgroundPreset = "")

        is HarnessUiEvent.PlaygroundContextChanged -> state.copy(playgroundContext = event.context, playgroundPreset = "")
    }

    private fun HarnessUiState.applyPreset(id: String): HarnessUiState {
        val preset = playgroundPresetOptions.firstOrNull { it.id == id } ?: return copy(playgroundPreset = id)
        return copy(
            playgroundPreset = id,
            playgroundBasePreset = id,
            playgroundMaxTokens = preset.maxOutputTokens,
            playgroundTemperature = preset.temperature,
            playgroundTopP = preset.topP,
            playgroundTopK = preset.topK,
            playgroundSeed = preset.seed,
            playgroundContext = "",
        )
    }

    private fun reduceDiagnostics(state: HarnessUiState, event: HarnessUiEvent.Diagnostics): HarnessUiState = when (event) {
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
    }

    private fun reducePreferences(state: HarnessUiState, event: HarnessUiEvent.Preferences): HarnessUiState = when (event) {
        is HarnessUiEvent.ThemeChanged -> state.copy(themePreference = event.preference)
    }

    private fun Set<HarnessDiagnosticAction>.withAction(action: HarnessDiagnosticAction, running: Boolean): Set<HarnessDiagnosticAction> =
        if (running) this + action else this - action
}
