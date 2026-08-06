package io.github.daniele21.localllm.phonetest

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal class HarnessViewModel(initialState: HarnessUiState = HarnessUiState()) : ViewModel() {
    private val mutableUiState = MutableStateFlow(initialState)
    private var playgroundEffects: PlaygroundEffects? = null

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
