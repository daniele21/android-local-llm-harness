package io.github.daniele21.localllm.phonetest

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal class HarnessViewModel(initialState: HarnessUiState = HarnessUiState()) : ViewModel() {
    private val mutableUiState = MutableStateFlow(initialState)
    private var playgroundEffects: PlaygroundEffects? = null
    private var modelEffects: ModelEffects? = null

    val uiState: StateFlow<HarnessUiState> = mutableUiState.asStateFlow()

    fun dispatch(event: HarnessUiEvent) {
        mutableUiState.update { current -> HarnessUiReducer.reduce(current, event) }
    }

    fun attachPlaygroundEffects(effects: PlaygroundEffects) {
        playgroundEffects = effects
        dispatch(HarnessUiEvent.PlaygroundChanged(effects.snapshot()))
    }

    fun detachPlaygroundEffects(effects: PlaygroundEffects) {
        if (playgroundEffects === effects) playgroundEffects = null
    }

    fun attachModelEffects(effects: ModelEffects) {
        modelEffects = effects
        val snapshot = effects.snapshot()
        dispatch(HarnessUiEvent.ModelDistributionChanged(snapshot.distribution))
        dispatch(HarnessUiEvent.ModelChanged(snapshot.selectedModel))
        dispatch(HarnessUiEvent.LoadedModelChanged(snapshot.loadedDigest))
    }

    fun detachModelEffects(effects: ModelEffects) {
        if (modelEffects === effects) modelEffects = null
    }

    fun requestModelImport(): Boolean {
        if (uiState.value.busy) return false
        return executeModelCommand(ModelEffects::requestImport)
    }

    fun refreshModels(): Boolean = executeModelCommand(ModelEffects::refresh)

    fun downloadModel(stableId: String): Boolean = executeModelCommand { it.download(stableId) }

    fun cancelModelDownload(stableId: String): Boolean = executeModelCommand { it.cancelDownload(stableId) }

    fun installModel(stableId: String): Boolean = executeModelCommand { it.install(stableId) }

    fun verifyInstalledModel(stableId: String): Boolean = executeModelCommand { it.verifyInstalled(stableId) }

    fun requestCatalogModelRemoval(stableId: String): Boolean =
        executeModelCommand { it.requestCatalogRemoval(stableId) }

    fun cancelCatalogModelRemoval(stableId: String): Boolean =
        executeModelCommand { it.cancelCatalogRemoval(stableId) }

    fun confirmCatalogModelRemoval(stableId: String): Boolean =
        executeModelCommand { it.confirmCatalogRemoval(stableId) }

    fun selectInstalledModel(metadata: InstalledCatalogModelMetadata): Boolean =
        executeModelCommand { it.selectInstalled(metadata) }

    fun verifySelectedModel(): Boolean {
        val state = uiState.value
        if (state.importedModel == null || state.busy) return false
        return executeModelCommand(ModelEffects::verifySelected)
    }

    fun requestSelectedModelRemoval(): Boolean {
        val state = uiState.value
        if (state.importedModel == null || state.busy) return false
        dispatch(HarnessUiEvent.RemovalConfirmationChanged(true))
        return true
    }

    fun cancelSelectedModelRemoval() {
        dispatch(HarnessUiEvent.RemovalConfirmationChanged(false))
    }

    fun confirmSelectedModelRemoval(): Boolean {
        val state = uiState.value
        if (!state.removalConfirmationPending || state.importedModel == null || state.busy) return false
        val accepted = executeModelCommand(ModelEffects::removeSelected)
        if (accepted) dispatch(HarnessUiEvent.RemovalConfirmationChanged(false))
        return accepted
    }

    fun updatePlaygroundPrompt(prompt: String) {
        dispatch(HarnessUiEvent.PlaygroundPromptChanged(prompt))
    }

    fun updatePlaygroundMaxTokens(maxTokens: String) {
        dispatch(HarnessUiEvent.PlaygroundMaxTokensChanged(maxTokens))
    }

    fun updatePlaygroundTemperature(temperature: String) {
        dispatch(HarnessUiEvent.PlaygroundTemperatureChanged(temperature))
    }

    fun updatePlaygroundSeed(seed: String) {
        dispatch(HarnessUiEvent.PlaygroundSeedChanged(seed))
    }

    fun startPlayground(): PlaygroundStartResult {
        val state = uiState.value
        val model = state.importedModel
        return when {
            model == null -> PlaygroundStartResult.MODEL_REQUIRED
            state.busy -> PlaygroundStartResult.BUSY
            else -> executePlaygroundStart(state, model, playgroundEffects)
        }
    }

    fun cancelPlayground(): Boolean = runCatching {
        playgroundEffects?.cancel() ?: false
    }.getOrDefault(false)

    fun releasePlaygroundRuntime(onComplete: () -> Unit): Boolean = runCatching {
        playgroundEffects?.releaseRuntime(onComplete) ?: false
    }.getOrDefault(false)

    private fun executeModelCommand(command: (ModelEffects) -> Boolean): Boolean = runCatching {
        modelEffects?.let(command) ?: false
    }.getOrDefault(false)
}

private fun executePlaygroundStart(state: HarnessUiState, model: ImportedPhoneModel, effects: PlaygroundEffects?): PlaygroundStartResult {
    val options = runCatching {
        PlaygroundRequestOptions.parse(
            state.playgroundMaxTokens,
            state.playgroundTemperature,
            state.playgroundSeed,
        )
    }.getOrElse { return PlaygroundStartResult.INVALID_SETTINGS }
    val attachedEffects = effects ?: return PlaygroundStartResult.CONTROLLER_UNAVAILABLE
    val started = runCatching {
        attachedEffects.start(model, state.playgroundPrompt, options)
    }.getOrDefault(false)
    return if (started) PlaygroundStartResult.STARTED else PlaygroundStartResult.REJECTED
}
