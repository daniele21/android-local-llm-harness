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
    diagnosticsHealthEntry(state.health),
    diagnosticsRunsEntry(state.runCount),
    diagnosticsResourcesEntry(state.resourceCount),
    diagnosticsBenchmarksEntry(state.benchmarkCount),
    diagnosticsValidationEntry(state.validationAvailable),
    diagnosticsLogsEntry(state.logCount),
)

private fun diagnosticsHealthEntry(health: String): HarnessDiagnosticsOverviewEntry = HarnessDiagnosticsOverviewEntry(
    section = DiagnosticsSection.HEALTH,
    group = HarnessDiagnosticsEvidenceGroup.READINESS,
    title = "Health",
    detail = if (health == "Not run") {
        "Runtime and model checks have not been run yet."
    } else {
        "Latest aggregate status: $health."
    },
    status = health,
    tone = when (health.lowercase()) {
        "pass" -> HarnessStatusTone.SUCCESS
        "warning" -> HarnessStatusTone.WARNING
        "fail", "failed", "error" -> HarnessStatusTone.ERROR
        else -> HarnessStatusTone.NEUTRAL
    },
)

private fun diagnosticsRunsEntry(count: Int): HarnessDiagnosticsOverviewEntry = HarnessDiagnosticsOverviewEntry(
    section = DiagnosticsSection.RUNS,
    group = HarnessDiagnosticsEvidenceGroup.MEASURED_EVIDENCE,
    title = "Runs",
    detail = if (count == 0) {
        "No local inference runs recorded yet."
    } else {
        "$count privacy-safe run records available."
    },
    status = if (count == 0) "Not run" else count.toString(),
    tone = evidenceCountTone(count),
)

private fun diagnosticsResourcesEntry(count: Int): HarnessDiagnosticsOverviewEntry = HarnessDiagnosticsOverviewEntry(
    section = DiagnosticsSection.RESOURCES,
    group = HarnessDiagnosticsEvidenceGroup.MEASURED_EVIDENCE,
    title = "Resources",
    detail = if (count == 0) {
        "No explicit memory or thermal snapshot captured yet."
    } else {
        "$count bounded device resource snapshots available."
    },
    status = if (count == 0) "Not captured" else count.toString(),
    tone = evidenceCountTone(count),
)

private fun diagnosticsBenchmarksEntry(count: Int): HarnessDiagnosticsOverviewEntry = HarnessDiagnosticsOverviewEntry(
    section = DiagnosticsSection.BENCHMARKS,
    group = HarnessDiagnosticsEvidenceGroup.MEASURED_EVIDENCE,
    title = "Benchmarks",
    detail = if (count == 0) {
        "No active benchmark baseline for the current model."
    } else {
        "$count active benchmark baseline(s) available."
    },
    status = if (count == 0) "Not captured" else count.toString(),
    tone = evidenceCountTone(count),
)

private fun diagnosticsValidationEntry(available: Boolean): HarnessDiagnosticsOverviewEntry = HarnessDiagnosticsOverviewEntry(
    section = DiagnosticsSection.VALIDATION,
    group = HarnessDiagnosticsEvidenceGroup.PROOF_AND_TROUBLESHOOTING,
    title = "Physical validation",
    detail = if (available) {
        "A privacy-safe validation report is available."
    } else {
        "No physical-device validation report is available in this process."
    },
    status = if (available) "Report ready" else "Not run",
    tone = if (available) HarnessStatusTone.SUCCESS else HarnessStatusTone.NEUTRAL,
)

private fun diagnosticsLogsEntry(count: Int): HarnessDiagnosticsOverviewEntry = HarnessDiagnosticsOverviewEntry(
    section = DiagnosticsSection.LOGS,
    group = HarnessDiagnosticsEvidenceGroup.PROOF_AND_TROUBLESHOOTING,
    title = "Logs",
    detail = if (count == 0) {
        "No structured privacy-safe logs recorded yet."
    } else {
        "$count bounded log entries available."
    },
    status = if (count == 0) "Empty" else count.toString(),
    tone = evidenceCountTone(count),
)

private fun evidenceCountTone(count: Int): HarnessStatusTone =
    if (count == 0) HarnessStatusTone.NEUTRAL else HarnessStatusTone.INFO
