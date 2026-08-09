@file:Suppress("FunctionName")

package io.github.daniele21.localllm.phonetest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.daniele21.localllm.ui.designsystem.HarnessCard
import io.github.daniele21.localllm.ui.designsystem.HarnessMetric
import io.github.daniele21.localllm.ui.designsystem.HarnessMetricRow
import io.github.daniele21.localllm.ui.designsystem.HarnessSecondaryButton
import io.github.daniele21.localllm.ui.designsystem.HarnessStatusBadge
import io.github.daniele21.localllm.ui.designsystem.HarnessStatusTone
import java.util.Locale

internal data class UnifiedModelsActions(
    val catalog: PhoneModelDistributionActions,
    val verifySelected: () -> Unit,
    val unloadLoaded: () -> Unit,
    val requestSelectedRemoval: () -> Unit,
    val cancelSelectedRemoval: () -> Unit,
    val confirmSelectedRemoval: () -> Unit,
    val refresh: () -> Unit,
)

internal enum class ModelsAvailabilityFilter(val label: String) {
    ALL("All"),
    INSTALLED("Installed"),
    AVAILABLE("Available"),
}

internal enum class ModelsSizeFilter(val label: String, val groupLabel: String?) {
    ALL("All sizes", null),
    B08("0.8B", "Qwen3.5 · 0.8B"),
    B2("2B", "Qwen3.5 · 2B"),
}

@Composable
internal fun UnifiedModelsCatalog(
    state: HarnessUiState,
    actions: UnifiedModelsActions,
    onOpenModelDetails: (HarnessModelInventoryItem) -> Unit,
) {
    var availabilityFilter by rememberSaveable { mutableStateOf(ModelsAvailabilityFilter.ALL) }
    var sizeFilter by rememberSaveable { mutableStateOf(ModelsSizeFilter.ALL) }
    val feedback by ModelActionFeedbackStore.state.collectAsState()
    val catalogItems = state.modelInventory.items.filter { it.origin == HarnessModelOrigin.CATALOG }
    val visibleItems = catalogItems.filter { item ->
        availabilityFilter.matches(item) && sizeFilter.matches(item)
    }
    val distributionByStableId = state.modelDistribution.models.associateBy(PhoneCatalogModelUi::stableId)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ModelsCatalogSummary(state, catalogItems.size)
        ModelActionStatusCard(feedback)
        ModelsCatalogFilters(
            availabilityFilter = availabilityFilter,
            sizeFilter = sizeFilter,
            onAvailabilityChanged = { availabilityFilter = it },
            onSizeChanged = { sizeFilter = it },
        )
        listOf(ModelsSizeFilter.B08, ModelsSizeFilter.B2).forEach { group ->
            val groupItems = visibleItems.filter(group::matches)
            if (groupItems.isNotEmpty()) {
                Text(requireNotNull(group.groupLabel), style = MaterialTheme.typography.titleLarge)
                groupItems.forEach { item ->
                    UnifiedModelCard(
                        state = state,
                        item = item,
                        model = distributionByStableId.getValue(item.stableId),
                        actions = actions,
                        onOpenModelDetails = onOpenModelDetails,
                    )
                }
            }
        }
        if (visibleItems.isEmpty()) {
            HarnessCard {
                Text("No models match these filters.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            "${state.modelDistribution.sourceLabel} · revision ${state.modelDistribution.catalogRevision ?: "unavailable"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(state.modelDistribution.message, style = MaterialTheme.typography.bodySmall)
        HarnessSecondaryButton("Refresh model state", onClick = actions.refresh)
    }
}

@Composable
private fun ModelActionStatusCard(feedback: ModelActionFeedbackState) {
    HarnessCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Model activity", style = MaterialTheme.typography.titleMedium)
                Text(
                    feedback.latest,
                    color = if (feedback.tone == ModelActionFeedbackTone.ERROR) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            HarnessStatusBadge(
                label = when (feedback.tone) {
                    ModelActionFeedbackTone.INFO -> "STATUS"
                    ModelActionFeedbackTone.SUCCESS -> "OK"
                    ModelActionFeedbackTone.ERROR -> "ERROR"
                },
                tone = when (feedback.tone) {
                    ModelActionFeedbackTone.INFO -> HarnessStatusTone.INFO
                    ModelActionFeedbackTone.SUCCESS -> HarnessStatusTone.SUCCESS
                    ModelActionFeedbackTone.ERROR -> HarnessStatusTone.WARNING
                },
            )
        }
        val previous = feedback.history.drop(1).take(3)
        if (previous.isNotEmpty()) {
            Text(
                "Recent",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            previous.forEach { message ->
                Text(
                    "• $message",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ModelsCatalogSummary(state: HarnessUiState, catalogCount: Int) {
    val selected = state.modelInventory.selectedItem
    HarnessCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "$catalogCount models · ${state.modelInventory.installedCount} installed",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    selected?.let {
                        "Active: ${it.displayName} · ${if (it.loaded) "Loaded" else "Selected"}"
                    } ?: "No active model selected",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HarnessStatusBadge(
                label = state.modelDistribution.catalogStatus.name.replace('_', ' '),
                tone = if (state.modelDistribution.catalogStatus == PhoneCatalogLoadStatus.READY) {
                    HarnessStatusTone.SUCCESS
                } else {
                    HarnessStatusTone.WARNING
                },
            )
        }
        HarnessMetricRow {
            HarnessMetric("Storage", formatModelBytes(state.modelInventory.installedBytes), Modifier.weight(1f))
            HarnessMetric("Loaded", if (state.modelInventory.loadedDigest == null) "No" else "Yes", Modifier.weight(1f))
        }
    }
}

@Composable
private fun ModelsCatalogFilters(
    availabilityFilter: ModelsAvailabilityFilter,
    sizeFilter: ModelsSizeFilter,
    onAvailabilityChanged: (ModelsAvailabilityFilter) -> Unit,
    onSizeChanged: (ModelsSizeFilter) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Status", style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ModelsAvailabilityFilter.entries) { filter ->
                FilterChip(
                    selected = availabilityFilter == filter,
                    onClick = { onAvailabilityChanged(filter) },
                    label = { Text(filter.label) },
                )
            }
        }
        Text("Model size", style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ModelsSizeFilter.entries) { filter ->
                FilterChip(
                    selected = sizeFilter == filter,
                    onClick = { onSizeChanged(filter) },
                    label = { Text(filter.label) },
                )
            }
        }
    }
}

internal fun ModelsAvailabilityFilter.matches(item: HarnessModelInventoryItem): Boolean = when (this) {
    ModelsAvailabilityFilter.ALL -> true
    ModelsAvailabilityFilter.INSTALLED -> item.installed
    ModelsAvailabilityFilter.AVAILABLE -> !item.installed
}

internal fun ModelsSizeFilter.matches(item: HarnessModelInventoryItem): Boolean = when (this) {
    ModelsSizeFilter.ALL -> true
    ModelsSizeFilter.B08 -> item.stableId.startsWith("qwen35-08b-")
    ModelsSizeFilter.B2 -> item.stableId.startsWith("qwen35-2b-")
}

internal fun HarnessModelLifecycle.statusTone(): HarnessStatusTone = when (this) {
    HarnessModelLifecycle.SELECTED,
    HarnessModelLifecycle.LOADED,
    HarnessModelLifecycle.INSTALLED,
    -> HarnessStatusTone.SUCCESS

    HarnessModelLifecycle.DEGRADED,
    HarnessModelLifecycle.FAILED,
    HarnessModelLifecycle.INCOMPATIBLE,
    -> HarnessStatusTone.WARNING

    HarnessModelLifecycle.DOWNLOADING,
    HarnessModelLifecycle.INSTALLING,
    HarnessModelLifecycle.VERIFIED_READY_TO_INSTALL,
    -> HarnessStatusTone.INFO

    HarnessModelLifecycle.READY_TO_DOWNLOAD,
    HarnessModelLifecycle.CANCELLED,
    -> HarnessStatusTone.NEUTRAL
}

internal fun formatModelBytes(bytes: Long): String {
    if (bytes < 1_024L) return "$bytes B"
    val kib = bytes / 1_024.0
    if (kib < 1_024.0) return "%.1f KiB".format(Locale.US, kib)
    val mib = kib / 1_024.0
    if (mib < 1_024.0) return "%.1f MiB".format(Locale.US, mib)
    return "%.2f GiB".format(Locale.US, mib / 1_024.0)
}
