package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.ui.designsystem.HarnessStatusTone

internal data class HarnessDiagnosticsOverviewState(
    val health: String,
    val runCount: Int,
    val resourceCount: Int,
    val benchmarkCount: Int,
    val logCount: Int,
    val validationAvailable: Boolean,
)

internal enum class HarnessDiagnosticsEvidenceGroup {
    READINESS,
    MEASURED_EVIDENCE,
    PROOF_AND_TROUBLESHOOTING,
}

internal data class HarnessDiagnosticsOverviewEntry(
    val section: DiagnosticsSection,
    val group: HarnessDiagnosticsEvidenceGroup,
    val title: String,
    val detail: String,
    val status: String,
    val tone: HarnessStatusTone,
)

internal fun harnessDiagnosticsOverviewEntries(state: HarnessDiagnosticsOverviewState): List<HarnessDiagnosticsOverviewEntry> = listOf(
    HarnessDiagnosticsOverviewEntry(
        section = DiagnosticsSection.HEALTH,
        group = HarnessDiagnosticsEvidenceGroup.READINESS,
        title = "Health",
        detail = if (state.health == "Not run") {
            "Runtime and model checks have not been run yet."
        } else {
            "Latest aggregate status: ${state.health}."
        },
        status = state.health,
        tone = when (state.health.lowercase()) {
            "pass" -> HarnessStatusTone.SUCCESS
            "warning" -> HarnessStatusTone.WARNING
            "fail", "failed", "error" -> HarnessStatusTone.ERROR
            else -> HarnessStatusTone.NEUTRAL
        },
    ),
    HarnessDiagnosticsOverviewEntry(
        section = DiagnosticsSection.RUNS,
        group = HarnessDiagnosticsEvidenceGroup.MEASURED_EVIDENCE,
        title = "Runs",
        detail = if (state.runCount == 0) {
            "No local inference runs recorded yet."
        } else {
            "${state.runCount} privacy-safe run records available."
        },
        status = if (state.runCount == 0) "Not run" else state.runCount.toString(),
        tone = if (state.runCount == 0) HarnessStatusTone.NEUTRAL else HarnessStatusTone.INFO,
    ),
    HarnessDiagnosticsOverviewEntry(
        section = DiagnosticsSection.RESOURCES,
        group = HarnessDiagnosticsEvidenceGroup.MEASURED_EVIDENCE,
        title = "Resources",
        detail = if (state.resourceCount == 0) {
            "No explicit memory or thermal snapshot captured yet."
        } else {
            "${state.resourceCount} bounded device resource snapshots available."
        },
        status = if (state.resourceCount == 0) "Not captured" else state.resourceCount.toString(),
        tone = if (state.resourceCount == 0) HarnessStatusTone.NEUTRAL else HarnessStatusTone.INFO,
    ),
    HarnessDiagnosticsOverviewEntry(
        section = DiagnosticsSection.BENCHMARKS,
        group = HarnessDiagnosticsEvidenceGroup.MEASURED_EVIDENCE,
        title = "Benchmarks",
        detail = if (state.benchmarkCount == 0) {
            "No active benchmark baseline for the current model."
        } else {
            "${state.benchmarkCount} active benchmark baseline(s) available."
        },
        status = if (state.benchmarkCount == 0) "Not captured" else state.benchmarkCount.toString(),
        tone = if (state.benchmarkCount == 0) HarnessStatusTone.NEUTRAL else HarnessStatusTone.INFO,
    ),
    HarnessDiagnosticsOverviewEntry(
        section = DiagnosticsSection.VALIDATION,
        group = HarnessDiagnosticsEvidenceGroup.PROOF_AND_TROUBLESHOOTING,
        title = "Physical validation",
        detail = if (state.validationAvailable) {
            "A privacy-safe validation report is available."
        } else {
            "No physical-device validation report is available in this process."
        },
        status = if (state.validationAvailable) "Report ready" else "Not run",
        tone = if (state.validationAvailable) HarnessStatusTone.SUCCESS else HarnessStatusTone.NEUTRAL,
    ),
    HarnessDiagnosticsOverviewEntry(
        section = DiagnosticsSection.LOGS,
        group = HarnessDiagnosticsEvidenceGroup.PROOF_AND_TROUBLESHOOTING,
        title = "Logs",
        detail = if (state.logCount == 0) {
            "No structured privacy-safe logs recorded yet."
        } else {
            "${state.logCount} bounded log entries available."
        },
        status = if (state.logCount == 0) "Empty" else state.logCount.toString(),
        tone = if (state.logCount == 0) HarnessStatusTone.NEUTRAL else HarnessStatusTone.INFO,
    ),
)
