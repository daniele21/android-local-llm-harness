package io.github.daniele21.localllm.phonetest

internal fun performanceSectionLabel(section: PerformanceSection): String = when (section) {
    PerformanceSection.RUN -> "Run"
    PerformanceSection.DATASETS -> "Datasets"
    PerformanceSection.HISTORY -> "History"
    PerformanceSection.COMPARE -> "Compare"
}

internal fun performanceSampleLabel(selection: PerformanceSampleSelection): String = when (selection) {
    PerformanceSampleSelection.Smoke -> "Smoke · 20"
    PerformanceSampleSelection.Quick -> "Quick · 50"
    PerformanceSampleSelection.Standard -> "Standard · 100"
    PerformanceSampleSelection.Extended -> "Extended · 200"
    PerformanceSampleSelection.All -> "All cases"
    is PerformanceSampleSelection.Custom -> "Custom · ${selection.count}"
}

internal val performanceFixedSamples: List<PerformanceSampleSelection> = listOf(
    PerformanceSampleSelection.Smoke,
    PerformanceSampleSelection.Quick,
    PerformanceSampleSelection.Standard,
    PerformanceSampleSelection.Extended,
    PerformanceSampleSelection.All,
)

internal fun performanceSampleEnabled(
    selection: PerformanceSampleSelection,
    caseCount: Int?,
): Boolean {
    if (caseCount == null) return selection == PerformanceSampleSelection.All
    val requested = when (selection) {
        PerformanceSampleSelection.Smoke -> 20
        PerformanceSampleSelection.Quick -> 50
        PerformanceSampleSelection.Standard -> 100
        PerformanceSampleSelection.Extended -> 200
        PerformanceSampleSelection.All -> return true
        is PerformanceSampleSelection.Custom -> selection.count
    }
    return requested <= caseCount
}

internal fun performanceReadinessDetail(
    readiness: PerformanceRunReadiness,
    runnerAvailable: Boolean,
): String {
    if (!runnerAvailable) {
        return "Evaluation execution is not connected in this build. Setup remains inspectable without claiming executable readiness."
    }
    return when (readiness) {
        PerformanceRunReadiness.Incomplete -> "Choose the required evaluation inputs."
        PerformanceRunReadiness.Ready -> "All required source-backed inputs are available."
        is PerformanceRunReadiness.Blocked -> readiness.reasons.joinToString(", ") { reason ->
            performanceBlockReasonLabel(reason)
        }
    }
}

internal fun performanceBlockReasonLabel(reason: PerformanceBlockReason): String = when (reason) {
    PerformanceBlockReason.MODEL_REQUIRED -> "model required"
    PerformanceBlockReason.DATASET_REQUIRED -> "dataset required"
    PerformanceBlockReason.SAMPLE_SELECTION_UNAVAILABLE -> "sample selection unavailable"
    PerformanceBlockReason.EXECUTION_PROFILE_REQUIRED -> "execution profile required"
    PerformanceBlockReason.MODEL_UNAVAILABLE -> "model unavailable"
}
