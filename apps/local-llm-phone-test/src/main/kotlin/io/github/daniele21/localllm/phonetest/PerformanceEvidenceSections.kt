@file:Suppress("FunctionName")

package io.github.daniele21.localllm.phonetest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import io.github.daniele21.localllm.ui.designsystem.HarnessCard
import io.github.daniele21.localllm.ui.designsystem.HarnessStatusBadge
import io.github.daniele21.localllm.ui.designsystem.HarnessStatusTone

@Composable
internal fun PerformanceCollectionSection(title: String, state: PerformanceSurfaceState, emptyMessage: String, availableLabel: String) {
    HarnessCard {
        Text(title, style = MaterialTheme.typography.titleLarge)
        when (state) {
            PerformanceSurfaceState.Loading -> Text("Loading…")

            is PerformanceSurfaceState.Failure -> Text(
                state.message,
                color = MaterialTheme.colorScheme.error,
            )

            PerformanceSurfaceState.Empty -> Text(
                emptyMessage,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            is PerformanceSurfaceState.Available -> Text(
                "${state.count} $availableLabel available.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun PerformanceHistorySection(state: PerformanceState) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PerformanceDecisionCard(performanceDecisionPresentation(state))
        PerformanceCollectionSection(
            title = "History",
            state = performanceHistorySurfaceState(state.history),
            emptyMessage = "No completed evaluation runs are available yet.",
            availableLabel = "evaluation runs",
        )
    }
}

@Composable
internal fun PerformanceCompareSection(state: PerformanceState) {
    val surface = performanceCompareSurfaceState(state)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PerformanceDecisionCard(performanceDecisionPresentation(state))
        HarnessCard {
            Text("Compare", style = MaterialTheme.typography.titleLarge)
            when (surface) {
                PerformanceSurfaceState.Loading -> Text("Loading comparison candidates…")

                is PerformanceSurfaceState.Failure -> Text(surface.message, color = MaterialTheme.colorScheme.error)

                PerformanceSurfaceState.Empty -> Text(
                    "Complete evaluation runs before comparison becomes available.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                is PerformanceSurfaceState.Available -> Text(
                    "${surface.count} historical runs are known. Compatible deltas remain unavailable until terminal result aggregation and comparison binding are connected.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Selected for comparison: ${state.compare.selectedRunIds.size}/2",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PerformanceDecisionCard(presentation: PerformanceDecisionPresentation) {
    val tone = when (presentation.state) {
        PerformanceDecisionState.LOADING -> HarnessStatusTone.INFO
        PerformanceDecisionState.UNAVAILABLE -> HarnessStatusTone.ERROR
        PerformanceDecisionState.NO_EVIDENCE -> HarnessStatusTone.NEUTRAL
        PerformanceDecisionState.EVIDENCE_NOT_COMPARABLE -> HarnessStatusTone.WARNING
    }
    HarnessCard(emphasized = true) {
        HarnessStatusBadge(label = presentation.label, tone = tone)
        Text(presentation.title, style = MaterialTheme.typography.titleMedium)
        Text(
            presentation.detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
