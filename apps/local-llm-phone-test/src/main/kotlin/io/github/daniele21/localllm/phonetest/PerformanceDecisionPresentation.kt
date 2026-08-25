package io.github.daniele21.localllm.phonetest

internal enum class PerformanceDecisionState {
    LOADING,
    UNAVAILABLE,
    NO_EVIDENCE,
    EVIDENCE_NOT_COMPARABLE,
}

internal data class PerformanceDecisionPresentation(
    val state: PerformanceDecisionState,
    val label: String,
    val title: String,
    val detail: String,
)

internal fun performanceDecisionPresentation(state: PerformanceState): PerformanceDecisionPresentation {
    val error = state.history.error ?: state.compare.error
    if (error != null) {
        return PerformanceDecisionPresentation(
            state = PerformanceDecisionState.UNAVAILABLE,
            label = "EVIDENCE UNAVAILABLE",
            title = "No supported choice can be made",
            detail = error,
        )
    }
    if (state.history.loading || state.compare.loading) {
        return PerformanceDecisionPresentation(
            state = PerformanceDecisionState.LOADING,
            label = "CHECKING EVIDENCE",
            title = "Decision evidence is loading",
            detail = "Harness is reading the recorded evaluation state before presenting a comparison decision.",
        )
    }
    if (state.history.runCount == 0) {
        return PerformanceDecisionPresentation(
            state = PerformanceDecisionState.NO_EVIDENCE,
            label = "NO DECISION EVIDENCE",
            title = "No supported choice yet",
            detail = "Complete repeatable evaluation runs before choosing a model or configuration from measured evidence.",
        )
    }
    val recordedRuns = state.history.runCount
    return PerformanceDecisionPresentation(
        state = PerformanceDecisionState.EVIDENCE_NOT_COMPARABLE,
        label = "COMPARISON PENDING",
        title = "Recorded runs are not enough to rank choices",
        detail =
        "$recordedRuns evaluation run(s) are recorded, but compatible aggregated latency, " +
            "throughput, memory and quality deltas are not connected to this surface. " +
            "Harness will not rank models or configurations yet.",
    )
}
