package io.github.daniele21.localllm.phonetest

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal fun interface PerformanceCommandSink {
    fun accept(command: PerformanceCommand)
}

internal fun interface PerformanceEffectSink {
    fun accept(effect: PerformanceEffect)
}

internal class PerformanceViewModel(initialState: PerformanceState = PerformanceState()) : ViewModel() {
    private val mutableState = MutableStateFlow(initialState)
    private var commandSink: PerformanceCommandSink? = null
    private var effectSink: PerformanceEffectSink? = null

    val state: StateFlow<PerformanceState> = mutableState.asStateFlow()

    fun attachCommandSink(sink: PerformanceCommandSink) {
        commandSink = sink
    }

    fun detachCommandSink(sink: PerformanceCommandSink) {
        if (commandSink === sink) commandSink = null
    }

    fun attachEffectSink(sink: PerformanceEffectSink) {
        effectSink = sink
    }

    fun detachEffectSink(sink: PerformanceEffectSink) {
        if (effectSink === sink) effectSink = null
    }

    fun dispatch(intent: PerformanceIntent) {
        val reduction = PerformanceUiReducer.reduce(mutableState.value, intent)
        mutableState.value = reduction.state
        reduction.effects.forEach { effect -> effectSink?.accept(effect) }
        reduction.commands.forEach { command -> commandSink?.accept(command) }
    }

    fun applyDatasets(datasets: PerformanceDatasetState) {
        mutableState.value = PerformanceUiReducer.applyDatasetState(mutableState.value, datasets)
    }

    fun applyHistory(history: PerformanceHistoryState) {
        mutableState.value = PerformanceUiReducer.applyHistoryState(mutableState.value, history)
    }

    fun applyActiveRun(activeRun: PerformanceActiveRunState?) {
        mutableState.value = PerformanceUiReducer.applyActiveRun(mutableState.value, activeRun)
    }
}
