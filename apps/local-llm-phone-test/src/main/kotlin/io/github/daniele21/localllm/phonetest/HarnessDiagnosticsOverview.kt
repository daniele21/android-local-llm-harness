@file:Suppress("FunctionName")

package io.github.daniele21.localllm.phonetest

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.daniele21.localllm.ui.designsystem.HarnessCard
import io.github.daniele21.localllm.ui.designsystem.HarnessStatusBadge
import io.github.daniele21.localllm.ui.designsystem.HarnessStatusTone

internal data class HarnessDiagnosticsOverviewState(
    val health: String,
    val runCount: Int,
    val resourceCount: Int,
    val benchmarkCount: Int,
    val logCount: Int,
    val validationAvailable: Boolean,
)

internal fun harnessDiagnosticsOverviewState(
    diagnostics: DiagnosticsUiState,
    resources: DiagnosticsResourceHistoryUi,
    benchmarks: BenchmarkUiState,
    logs: DiagnosticsLogUiState,
    validationReport: String,
): HarnessDiagnosticsOverviewState = HarnessDiagnosticsOverviewState(
    health = diagnostics.healthStatus,
    runCount = diagnostics.runs.size,
    resourceCount = resources.sampleCount,
    benchmarkCount = benchmarks.baselines.size,
    logCount = logs.totalCount,
    validationAvailable = validationReport.isNotBlank(),
)

@Composable
internal fun HarnessDiagnosticsOverview(
    state: HarnessDiagnosticsOverviewState,
    onOpen: (DiagnosticsSection) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DiagnosticEntryCard(
            title = "Health",
            detail = if (state.health == "Not run") {
                "Runtime and model checks have not been run yet."
            } else {
                "Latest aggregate status: ${state.health}."
            },
            status = state.health,
            tone = when (state.health) {
                "Pass" -> HarnessStatusTone.SUCCESS
                "Warning", "Fail" -> HarnessStatusTone.WARNING
                else -> HarnessStatusTone.NEUTRAL
            },
            onClick = { onOpen(DiagnosticsSection.HEALTH) },
        )
        DiagnosticEntryCard(
            title = "Runs",
            detail = if (state.runCount == 0) "No local inference runs recorded yet." else "${state.runCount} privacy-safe run records available.",
            status = state.runCount.toString(),
            tone = HarnessStatusTone.INFO,
            onClick = { onOpen(DiagnosticsSection.RUNS) },
        )
        DiagnosticEntryCard(
            title = "Resources",
            detail = if (state.resourceCount == 0) {
                "No explicit memory or thermal snapshot captured yet."
            } else {
                "${state.resourceCount} bounded device resource snapshots available."
            },
            status = if (state.resourceCount == 0) "Not captured" else state.resourceCount.toString(),
            tone = if (state.resourceCount == 0) HarnessStatusTone.NEUTRAL else HarnessStatusTone.INFO,
            onClick = { onOpen(DiagnosticsSection.RESOURCES) },
        )
        DiagnosticEntryCard(
            title = "Benchmarks",
            detail = if (state.benchmarkCount == 0) {
                "No active benchmark baseline for the current model."
            } else {
                "${state.benchmarkCount} active benchmark baseline(s) available."
            },
            status = state.benchmarkCount.toString(),
            tone = HarnessStatusTone.INFO,
            onClick = { onOpen(DiagnosticsSection.BENCHMARKS) },
        )
        DiagnosticEntryCard(
            title = "Logs",
            detail = if (state.logCount == 0) "No structured privacy-safe logs recorded yet." else "${state.logCount} bounded log entries available.",
            status = state.logCount.toString(),
            tone = HarnessStatusTone.INFO,
            onClick = { onOpen(DiagnosticsSection.LOGS) },
        )
        DiagnosticEntryCard(
            title = "Physical validation",
            detail = if (state.validationAvailable) {
                "A privacy-safe validation report is available."
            } else {
                "No physical-device validation report is available in this process."
            },
            status = if (state.validationAvailable) "Report ready" else "Not run",
            tone = if (state.validationAvailable) HarnessStatusTone.SUCCESS else HarnessStatusTone.NEUTRAL,
            onClick = { onOpen(DiagnosticsSection.VALIDATION) },
        )
    }
}

@Composable
private fun DiagnosticEntryCard(
    title: String,
    detail: String,
    status: String,
    tone: HarnessStatusTone,
    onClick: () -> Unit,
) {
    HarnessCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HarnessStatusBadge(status, tone)
            Text("›", modifier = Modifier.padding(start = 2.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
