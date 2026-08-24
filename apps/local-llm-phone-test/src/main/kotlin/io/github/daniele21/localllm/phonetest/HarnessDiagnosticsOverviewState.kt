package io.github.daniele21.localllm.phonetest

internal data class HarnessDiagnosticsOverviewState(
    val health: String,
    val runCount: Int,
    val resourceCount: Int,
    val benchmarkCount: Int,
    val logCount: Int,
    val validationAvailable: Boolean,
)
