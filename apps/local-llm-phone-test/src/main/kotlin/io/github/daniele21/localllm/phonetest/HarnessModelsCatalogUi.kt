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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.daniele21.localllm.ui.designsystem.HarnessCard
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
    val distributionByStableId = state.modelDistribution.models.associateBy(PhoneCatalogModelUi::stableId)
    val loadingStableId = loadingStableId(state, feedback)
    val loadedItem = catalogItems.firstOrNull(HarnessModelInventoryItem::loaded)
    val loadedModel = loadedItem?.let { distributionByStableId[it.stableId] }
    val visibleItems = catalogItems.filter { item ->
        !item.loaded && availabilityFilter.matches(item) && sizeFilter.matches(item)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ModelsCatalogSummary(state, catalogItems.size)
        if (loadedItem != null && loadedModel != null) {
            ActiveModelCard(
                state = state,
                item = loadedItem,
                model = loadedModel,
                actions = actions,
            )
        }
        if (feedback.history.isNotEmpty()) {
            ModelActionStatus(feedback)
        }
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
                        loading = loadingStableId == item.stableId,
                    )
                }
            }
        }
        if (visibleItems.isEmpty()) {
            HarnessCard {
                Text("No other models match these filters.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        ModelsCatalogFooter(state, actions)
    }
}

@Composable
private fun ModelActionStatus(feedback: ModelActionFeedbackState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HarnessStatusBadge(
            label = when (feedback.tone) {
                ModelActionFeedbackTone.INFO -> "STATUS"
                ModelActionFeedbackTone.SUCCESS -> "OK"
                ModelActionFeedbackTone.ERROR -> "ERROR"
            },
            tone = when (feedback.tone) {
                ModelActionFeedbackTone.INFO -> HarnessStatusTone.INFO
                ModelActionFeedbackTone.SUCCESS -> HarnessStatusTone.SUCCESS
                ModelActionFeedbackTone.ERROR -> HarnessStatusTone.ERROR
            },
        )
        Text(
            text = feedback.latest,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = if (feedback.tone == ModelActionFeedbackTone.ERROR) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun ModelsCatalogSummary(state: HarnessUiState, catalogCount: Int) {
    HarnessCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "$catalogCount models · ${state.modelInventory.installedCount} installed",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "${formatModelBytes(state.modelInventory.installedBytes)} used in local model storage",
                    style = MaterialTheme.typography.bodySmall,
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

@Composable
private fun ModelsCatalogFooter(state: HarnessUiState, actions: UnifiedModelsActions) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = "${state.modelDistribution.sourceLabel} · revision ${state.modelDistribution.catalogRevision ?: "unavailable"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = state.modelDistribution.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HarnessSecondaryButton(
            text = "Refresh model state",
            enabled = !state.busy,
            onClick = actions.refresh,
        )
    }
}

internal fun loadingStableId(state: HarnessUiState, feedback: ModelActionFeedbackState): String? {
    if (!state.controllerBusy) return null
    return state.modelDistribution.models
        .firstOrNull { feedback.latest == "Loading ${it.fileName} into memory" }
        ?.stableId
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
    HarnessModelLifecycle.LOADED -> HarnessStatusTone.SUCCESS

    HarnessModelLifecycle.SELECTED,
    HarnessModelLifecycle.DOWNLOADING,
    HarnessModelLifecycle.INSTALLING,
    HarnessModelLifecycle.VERIFIED_READY_TO_INSTALL,
    -> HarnessStatusTone.INFO

    HarnessModelLifecycle.DEGRADED,
    HarnessModelLifecycle.INCOMPATIBLE,
    -> HarnessStatusTone.WARNING

    HarnessModelLifecycle.FAILED -> HarnessStatusTone.ERROR

    HarnessModelLifecycle.INSTALLED,
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
