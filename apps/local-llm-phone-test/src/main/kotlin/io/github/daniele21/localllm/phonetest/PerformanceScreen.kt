@file:Suppress("FunctionName")

package io.github.daniele21.localllm.phonetest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.daniele21.localllm.ui.designsystem.HarnessColors

@Composable
internal fun PerformanceScreen(
    state: PerformanceState,
    modelOptions: List<PerformanceModelOption>,
    profileOptions: List<PerformanceExecutionProfileOption>,
    runnerAvailable: Boolean,
    onIntent: (PerformanceIntent) -> Unit,
    onOpenModels: () -> Unit,
) {
    HarnessScreenList(title = null) {
        item {
            Text("Performance", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Find the model and configuration that fit this device and use case, using only evidence the Harness has actually collected.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Local evaluation · no synthetic benchmark evidence",
                style = MaterialTheme.typography.labelLarge,
                color = HarnessColors.Secondary,
            )
        }
        item { PerformanceSectionSelector(state.selectedSection, onIntent) }
        item {
            when (state.selectedSection) {
                PerformanceSection.RUN -> PerformanceRunSection(
                    state = state,
                    modelOptions = modelOptions,
                    profileOptions = profileOptions,
                    runnerAvailable = runnerAvailable,
                    onIntent = onIntent,
                    onOpenModels = onOpenModels,
                )

                PerformanceSection.DATASETS -> PerformanceCollectionSection(
                    title = "Datasets",
                    state = performanceDatasetSurfaceState(state.datasets),
                    emptyMessage = "No evaluation datasets are installed yet.",
                    availableLabel = "installed datasets",
                )

                PerformanceSection.HISTORY -> PerformanceCollectionSection(
                    title = "History",
                    state = performanceHistorySurfaceState(state.history),
                    emptyMessage = "No completed evaluation runs are available yet.",
                    availableLabel = "evaluation runs",
                )

                PerformanceSection.COMPARE -> PerformanceCompareSection(state)
            }
        }
    }
}

@Composable
private fun PerformanceSectionSelector(selected: PerformanceSection, onIntent: (PerformanceIntent) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.testTag("performance-sections"),
    ) {
        items(PerformanceSection.entries) { section ->
            FilterChip(
                selected = section == selected,
                onClick = { onIntent(PerformanceIntent.SelectSection(section)) },
                label = { Text(performanceSectionLabel(section)) },
                modifier = Modifier.testTag("performance-section-${section.name.lowercase()}"),
            )
        }
    }
}
