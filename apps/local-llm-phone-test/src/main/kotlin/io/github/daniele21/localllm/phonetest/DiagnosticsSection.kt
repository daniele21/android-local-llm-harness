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
import androidx.compose.ui.unit.dp

internal enum class DiagnosticsSection(val label: String) {
    OVERVIEW("Overview"),
    HEALTH("Health"),
    RUNS("Runs"),
    RESOURCES("Resources"),
    BENCHMARKS("Benchmarks"),
    LOGS("Logs"),
    VALIDATION("Validation"),
    ;

    companion object {
        val detailSections = entries.filterNot { it == OVERVIEW }
    }
}

@Composable
internal fun DiagnosticsSectionSelector(selected: DiagnosticsSection, onSelected: (DiagnosticsSection) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(end = 16.dp),
    ) {
        items(DiagnosticsSection.detailSections, key = DiagnosticsSection::name) { section ->
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
}
