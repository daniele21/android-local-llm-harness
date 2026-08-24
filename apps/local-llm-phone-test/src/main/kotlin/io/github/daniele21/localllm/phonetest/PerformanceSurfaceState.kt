package io.github.daniele21.localllm.phonetest

internal sealed interface PerformanceSurfaceState {
    data object Loading : PerformanceSurfaceState

    data class Failure(val message: String) : PerformanceSurfaceState

    data object Empty : PerformanceSurfaceState

    data class Available(val count: Int) : PerformanceSurfaceState
}

internal fun performanceDatasetSurfaceState(state: PerformanceDatasetState): PerformanceSurfaceState = when {
    state.loading -> PerformanceSurfaceState.Loading
    !state.error.isNullOrBlank() -> PerformanceSurfaceState.Failure(state.error)
    state.installedCount == 0 -> PerformanceSurfaceState.Empty
    else -> PerformanceSurfaceState.Available(state.installedCount)
}

internal fun performanceHistorySurfaceState(state: PerformanceHistoryState): PerformanceSurfaceState = when {
    state.loading -> PerformanceSurfaceState.Loading
    !state.error.isNullOrBlank() -> PerformanceSurfaceState.Failure(state.error)
    state.runCount == 0 -> PerformanceSurfaceState.Empty
    else -> PerformanceSurfaceState.Available(state.runCount)
}

internal fun performanceCompareSurfaceState(state: PerformanceState): PerformanceSurfaceState = when {
    state.compare.loading -> PerformanceSurfaceState.Loading
    !state.compare.error.isNullOrBlank() -> PerformanceSurfaceState.Failure(state.compare.error)
    state.history.runCount == 0 -> PerformanceSurfaceState.Empty
    else -> PerformanceSurfaceState.Available(state.history.runCount)
}
