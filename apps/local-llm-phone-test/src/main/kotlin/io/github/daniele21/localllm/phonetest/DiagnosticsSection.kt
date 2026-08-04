@file:Suppress("FunctionName")

package io.github.daniele21.localllm.phonetest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

internal enum class DiagnosticsSection(val label: String) {
    RUNS("Runs"),
    HEALTH("Health"),
    RESOURCES("Resources"),
    BENCHMARKS("Benchmarks"),
    LOGS("Logs"),
    VALIDATION("Validation"),
}

@Composable
internal fun DiagnosticsSectionSelector(selected: DiagnosticsSection, onSelected: (DiagnosticsSection) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(DiagnosticsSection.entries, key = DiagnosticsSection::name) { section ->
            FilterChip(
                selected = selected == section,
                onClick = { onSelected(section) },
                label = { Text(section.label) },
            )
        }
    }
}
