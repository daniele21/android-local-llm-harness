@file:Suppress("FunctionName")

package io.github.daniele21.localllm.phonetest

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.daniele21.localllm.ui.designsystem.HarnessSecondaryButton

internal enum class DiagnosticsSection(val label: String) {
    HEALTH("Health"),
    RUNS("Runs"),
    RESOURCES("Resources"),
    BENCHMARKS("Benchmarks"),
    LOGS("Logs"),
    VALIDATION("Validation"),
}

@Composable
internal fun DiagnosticsSectionSelector(selected: DiagnosticsSection, onSelected: (DiagnosticsSection) -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(end = 16.dp),
        ) {
            items(DiagnosticsSection.entries, key = DiagnosticsSection::name) { section ->
                val isSelected = selected == section
                Column(
                    modifier = Modifier.clickable { onSelected(section) }.padding(horizontal = 9.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        section.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,
                            ),
                    )
                }
            }
        }
        DiagnosticsSectionHint(selected)
        HarnessSecondaryButton(
            text = "Copy all diagnostics",
            modifier = Modifier.fillMaxWidth(),
            onClick = { HarnessDiagnosticsExport.copy(context) },
        )
        HarnessSecondaryButton(
            text = "Export all diagnostics",
            modifier = Modifier.fillMaxWidth(),
            onClick = { HarnessDiagnosticsExport.share(context) },
        )
    }
}

@Composable
private fun DiagnosticsSectionHint(selected: DiagnosticsSection) {
    val hint = when (selected) {
        DiagnosticsSection.RESOURCES ->
            "Capture resource snapshot records current PSS, heaps, available RAM, low-memory state and thermal status."

        DiagnosticsSection.BENCHMARKS ->
            "Benchmark baselines are built from matching completed inference runs; a key needs 5 samples before capture."

        else -> null
    }
    if (hint != null) {
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
