package io.github.daniele21.localllm.phonetest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal sealed interface HarnessApplicationsReadState {
    data object Loading : HarnessApplicationsReadState

    data class Loaded(val snapshot: HarnessApplicationsSnapshot) : HarnessApplicationsReadState

    data class Failed(val message: String) : HarnessApplicationsReadState
}

internal class HarnessApplicationsReadViewModel : ViewModel() {
    private val mutableState = MutableStateFlow<HarnessApplicationsReadState>(HarnessApplicationsReadState.Loading)
    private val generation = java.util.concurrent.atomic.AtomicLong(0)
    private var gateway: HarnessApplicationsGateway? = null

    val state: StateFlow<HarnessApplicationsReadState> = mutableState.asStateFlow()

    fun attach(gateway: HarnessApplicationsGateway) {
        if (this.gateway === gateway) return
        this.gateway = gateway
        refresh()
    }

    fun detach(gateway: HarnessApplicationsGateway) {
        if (this.gateway !== gateway) return
        this.gateway = null
        generation.incrementAndGet()
    }

    fun refresh() {
        val attached = gateway
        if (attached == null) {
            mutableState.value = HarnessApplicationsReadState.Failed("Applications source is unavailable")
            return
        }
        val token = generation.incrementAndGet()
        mutableState.value = HarnessApplicationsReadState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching(attached::snapshot)
            if (generation.get() != token || gateway !== attached) return@launch
            mutableState.value = result.fold(
                onSuccess = HarnessApplicationsReadState::Loaded,
                onFailure = { HarnessApplicationsReadState.Failed("Applications could not be loaded") },
            )
        }
    }

    override fun onCleared() {
        generation.incrementAndGet()
        gateway = null
        super.onCleared()
    }
}
