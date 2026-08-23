package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ThinkingMode
import io.github.daniele21.localllm.models.GenerationDefaults
import io.github.daniele21.localllm.models.Qwen35GenerationProfileId
import io.github.daniele21.localllm.models.Qwen35GenerationProfiles
import io.github.daniele21.localllm.models.Qwen35ModelTier

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
    val profileId: Qwen35GenerationProfileId,
    val label: String,
    val description: String,
    val preferredContextTokens: Int,
    val recommendedMaximumContextTokens: Int,
    val systemPrompt: String,
)

internal val playgroundPresetOptions = listOf(
    PlaygroundPresetOption(
        "qwen35-text-fast",
        Qwen35GenerationProfileId.QWEN35_TEXT_FAST,
        "Fast",
        "Short non-thinking answers with the Qwen3.5 mobile baseline",
        2_048,
        4_096,
        "Answer directly and concisely.",
    ),
    PlaygroundPresetOption(
        "qwen35-text-quality",
        Qwen35GenerationProfileId.QWEN35_TEXT_QUALITY,
        "Quality",
        "General-purpose non-thinking Qwen3.5 profile",
        4_096,
        8_192,
        "Be accurate, direct, and concise.",
    ),
    PlaygroundPresetOption(
        "qwen35-thinking",
        Qwen35GenerationProfileId.QWEN35_THINKING,
        "Thinking",
        "Qwen3.5 reasoning mode using enable_thinking",
        4_096,
        8_192,
        "Reason carefully before giving the final answer.",
    ),
    PlaygroundPresetOption(
        "qwen35-precise",
        Qwen35GenerationProfileId.QWEN35_PRECISE,
        "Precise",
        "Lower-temperature thinking profile for precise tasks",
        4_096,
        8_192,
        "Reason carefully and return a precise answer without unnecessary commentary.",
    ),
    PlaygroundPresetOption(
        "qwen35-json",
        Qwen35GenerationProfileId.QWEN35_JSON,
        "JSON",
        "Non-thinking structured-output profile",
        4_096,
        8_192,
        "Return only the requested structured result. Do not add commentary outside the required format.",
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
    val diagnosticsSection: DiagnosticsSection = DiagnosticsSection.OVERVIEW,
    val playgroundPrompt: String = DEFAULT_PROMPT,
    val playgroundMaxTokens: String = DEFAULT_MAX_OUTPUT_TOKENS,
    val playgroundTemperature: String = DEFAULT_TEMPERATURE,
    val playgroundPreset: String = DEFAULT_PRESET,
    val playgroundBasePreset: String? = DEFAULT_PRESET,
    val playgroundTopP: String = DEFAULT_TOP_P,
    val playgroundTopK: String = DEFAULT_TOP_K,
    val playgroundMinP: String = DEFAULT_MIN_P,
    val playgroundPresencePenalty: String = DEFAULT_PRESENCE_PENALTY,
    val playgroundThinkingMode: ThinkingMode = ThinkingMode.DISABLED,
    val playgroundRepeatPenalty: String = DEFAULT_REPEAT_PENALTY,
    val playgroundRepeatLastN: String = DEFAULT_REPEAT_LAST_N,
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
        const val DEFAULT_MAX_OUTPUT_TOKENS = "512"
        const val DEFAULT_TEMPERATURE = "1"
        const val DEFAULT_PRESET = "qwen35-text-quality"
        const val DEFAULT_TOP_P = "1"
        const val DEFAULT_TOP_K = "20"
        const val DEFAULT_MIN_P = "0"
        const val DEFAULT_PRESENCE_PENALTY = "2"
        const val DEFAULT_REPEAT_PENALTY = "1"
        const val DEFAULT_REPEAT_LAST_N = "64"
        const val DEFAULT_SEED = ""
        const val DEFAULT_PROMPT = "how much is the earth radius?"
    }
}

internal sealed interface HarnessUiEvent {
    sealed interface Runtime : HarnessUiEvent

    sealed interface Playground : HarnessUiEvent

    sealed interface PlaygroundControl : Playground

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

    data class PlaygroundMaxTokensChanged(val maxTokens: String) : PlaygroundControl

    data class PlaygroundTemperatureChanged(val temperature: String) : PlaygroundControl

    data class PlaygroundPresetChanged(val preset: String) : Playground

    data class PlaygroundTopPChanged(val topP: String) : PlaygroundControl

    data class PlaygroundTopKChanged(val topK: String) : PlaygroundControl

    data class PlaygroundMinPChanged(val minP: String) : PlaygroundControl

    data class PlaygroundPresencePenaltyChanged(val presencePenalty: String) : PlaygroundControl

    data class PlaygroundThinkingModeChanged(val mode: ThinkingMode) : PlaygroundControl

    data class PlaygroundRepeatPenaltyChanged(val repeatPenalty: String) : PlaygroundControl

    data class PlaygroundRepeatLastNChanged(val repeatLastN: String) : PlaygroundControl

    data class PlaygroundSeedChanged(val seed: String) : PlaygroundControl

    data class PlaygroundContextChanged(val context: String) : PlaygroundControl

    data class DiagnosticActionChanged(val action: HarnessDiagnosticAction, val running: Boolean) : Diagnostics

    data class DiagnosticsChanged(val state: DiagnosticsUiState) : Diagnostics

    data class BenchmarkChanged(val state: BenchmarkUiState) : Diagnostics

    data class LogFilterChanged(val filter: DiagnosticsLogFilter, val state: DiagnosticsLogUiState) : Diagnostics

    data class LogsChanged(val state: DiagnosticsLogUiState) : Diagnostics

    data class RequestTimelineChanged(val timeline: DiagnosticsRequestTimelineUi?) : Diagnostics

    data class DiagnosticsSectionChanged(val section: DiagnosticsSection) : Diagnostics

    data class ThemeChanged(val preference: HarnessThemePreference) : Preferences
}

private fun reducePlaygroundControl(state: HarnessUiState, event: HarnessUiEvent.PlaygroundControl): HarnessUiState = when (event) {
    is HarnessUiEvent.PlaygroundMaxTokensChanged -> state.copy(playgroundMaxTokens = event.maxTokens, playgroundPreset = "")

    is HarnessUiEvent.PlaygroundTemperatureChanged -> state.copy(
        playgroundTemperature = event.temperature,
        playgroundPreset = "",
    )

    is HarnessUiEvent.PlaygroundTopPChanged -> state.copy(playgroundTopP = event.topP, playgroundPreset = "")

    is HarnessUiEvent.PlaygroundTopKChanged -> state.copy(playgroundTopK = event.topK, playgroundPreset = "")

    is HarnessUiEvent.PlaygroundMinPChanged -> state.copy(playgroundMinP = event.minP, playgroundPreset = "")

    is HarnessUiEvent.PlaygroundPresencePenaltyChanged -> state.copy(
        playgroundPresencePenalty = event.presencePenalty,
        playgroundPreset = "",
    )

    is HarnessUiEvent.PlaygroundThinkingModeChanged -> state.copy(
        playgroundThinkingMode = event.mode,
        playgroundPreset = "",
    )

    is HarnessUiEvent.PlaygroundRepeatPenaltyChanged -> state.copy(
        playgroundRepeatPenalty = event.repeatPenalty,
        playgroundPreset = "",
    )

    is HarnessUiEvent.PlaygroundRepeatLastNChanged -> state.copy(
        playgroundRepeatLastN = event.repeatLastN,
        playgroundPreset = "",
    )

    is HarnessUiEvent.PlaygroundSeedChanged -> state.copy(playgroundSeed = event.seed, playgroundPreset = "")

    is HarnessUiEvent.PlaygroundContextChanged -> state.copy(playgroundContext = event.context, playgroundPreset = "")
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
            operationStatus = if (event.report.isBlank()) state.operationStatus else "Validation completed",
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
        is HarnessUiEvent.PlaygroundPresetChanged -> state.applyPreset(event.preset)
        is HarnessUiEvent.PlaygroundControl -> reducePlaygroundControl(state, event)
    }

    private fun HarnessUiState.applyPreset(id: String): HarnessUiState {
        val preset = playgroundPresetOptions.firstOrNull { it.id == id } ?: return copy(playgroundPreset = id)
        val defaults = preset.defaultsFor(importedModel)
        return copy(
            playgroundPreset = id,
            playgroundBasePreset = id,
            playgroundMaxTokens = defaults.maxOutputTokens.toString(),
            playgroundTemperature = defaults.temperature.toControlValue(),
            playgroundTopP = defaults.topP.toControlValue(),
            playgroundTopK = defaults.topK.toString(),
            playgroundMinP = defaults.minP.toControlValue(),
            playgroundPresencePenalty = defaults.presencePenalty.toControlValue(),
            playgroundThinkingMode = defaults.thinkingMode,
            playgroundRepeatPenalty = defaults.repeatPenalty.toControlValue(),
            playgroundRepeatLastN = defaults.repeatLastN.toString(),
            playgroundSeed = "",
            playgroundContext = "",
        )
    }

    private fun PlaygroundPresetOption.defaultsFor(model: ImportedPhoneModel?): GenerationDefaults {
        val tier = model?.let(::qwen35Tier) ?: Qwen35ModelTier.B0_8
        return Qwen35GenerationProfiles.forTier(tier).single { it.id == profileId }.defaults
    }

    private fun qwen35Tier(model: ImportedPhoneModel): Qwen35ModelTier {
        val stableId = Qwen35PhoneModelPolicy.requireCurated(model).id.modelId.value
        return if (stableId.startsWith("qwen35-08b-")) Qwen35ModelTier.B0_8 else Qwen35ModelTier.B2
    }

    private fun Float.toControlValue(): String = toString().removeSuffix(".0")

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
