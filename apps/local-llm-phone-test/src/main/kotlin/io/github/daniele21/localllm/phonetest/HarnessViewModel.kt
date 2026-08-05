package io.github.daniele21.localllm.phonetest

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal class HarnessViewModel(initialState: HarnessUiState = HarnessUiState()) : ViewModel() {
    private val mutableUiState = MutableStateFlow(initialState)

    val uiState: StateFlow<HarnessUiState> = mutableUiState.asStateFlow()

    fun dispatch(event: HarnessUiEvent) {
        mutableUiState.update { current -> HarnessUiReducer.reduce(current, event) }
    }
}
