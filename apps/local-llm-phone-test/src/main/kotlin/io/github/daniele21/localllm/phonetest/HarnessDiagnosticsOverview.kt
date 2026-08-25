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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.daniele21.localllm.ui.designsystem.HarnessCard
import io.github.daniele21.localllm.ui.designsystem.HarnessStatusBadge

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
internal fun HarnessDiagnosticsOverview(state: HarnessDiagnosticsOverviewState, onOpen: (DiagnosticsSection) -> Unit) {
    val entries = harnessDiagnosticsOverviewEntries(state)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Evidence map", style = MaterialTheme.typography.titleLarge)
        Text(
            "Check readiness first, then measured evidence. Open validation or logs only when you need deeper proof or troubleshooting.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HarnessDiagnosticsEvidenceGroup.entries.forEach { group ->
            DiagnosticGroupLabel(group)
            entries.filter { it.group == group }.forEach { entry ->
                DiagnosticEntryCard(entry = entry, onClick = { onOpen(entry.section) })
            }
        }
    }
}

@Composable
private fun DiagnosticGroupLabel(group: HarnessDiagnosticsEvidenceGroup) {
    Text(
        text = when (group) {
            HarnessDiagnosticsEvidenceGroup.READINESS -> "READINESS"
            HarnessDiagnosticsEvidenceGroup.MEASURED_EVIDENCE -> "MEASURED EVIDENCE"
            HarnessDiagnosticsEvidenceGroup.PROOF_AND_TROUBLESHOOTING -> "PROOF & TROUBLESHOOTING"
        },
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DiagnosticEntryCard(entry: HarnessDiagnosticsOverviewEntry, onClick: () -> Unit) {
    HarnessCard(
        modifier = Modifier.clickable(
            onClickLabel = "Open ${entry.title} diagnostics",
            role = Role.Button,
            onClick = onClick,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    entry.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HarnessStatusBadge(entry.status, entry.tone)
            Text("›", modifier = Modifier.padding(start = 2.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
