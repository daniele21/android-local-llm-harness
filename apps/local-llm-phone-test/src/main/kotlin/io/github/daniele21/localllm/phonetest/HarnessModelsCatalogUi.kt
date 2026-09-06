@file:Suppress("FunctionName")

package io.github.daniele21.localllm.phonetest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import io.github.daniele21.localllm.ui.designsystem.HarnessEmptyState
import io.github.daniele21.localllm.ui.designsystem.HarnessErrorState
import io.github.daniele21.localllm.ui.designsystem.HarnessLoadingState
import io.github.daniele21.localllm.ui.designsystem.HarnessMinimumTouchTarget
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
    ALL("All models"),
    INSTALLED("Installed"),
    AVAILABLE("Not installed"),
}

internal enum class ModelsSizeFilter(val label: String, val groupLabel: String?, val suggestedModelId: String? = null) {
    ALL("Any size", null),
    B08("0.8B", "Qwen3.5 · 0.8B", "qwen35-08b-q4-k-m"),
    B2("2B", "Qwen3.5 · 2B", "qwen35-2b-q4-k-m"),
    B4("4B", "Qwen3.5 · 4B · 4-bit only", "qwen35-4b-ud-q4-k-xl"),
}

@Composable
internal fun UnifiedModelsCatalog(
    state: HarnessUiState,
    actions: UnifiedModelsActions,
    onOpenModelDetails: (HarnessModelInventoryItem) -> Unit,
) {
    var availabilityFilter by rememberSaveable { mutableStateOf(ModelsAvailabilityFilter.ALL) }
    var sizeFilter by rememberSaveable { mutableStateOf(ModelsSizeFilter.ALL) }
    var expandedB08 by rememberSaveable { mutableStateOf(false) }
    var expandedB2 by rememberSaveable { mutableStateOf(false) }
    var expandedB4 by rememberSaveable { mutableStateOf(false) }
    val feedback by ModelActionFeedbackStore.state.collectAsState()
    val catalogItems = state.modelInventory.items.filter { it.origin == HarnessModelOrigin.CATALOG }
    val distributionByStableId = state.modelDistribution.models.associateBy(PhoneCatalogModelUi::stableId)

    if (catalogItems.isEmpty()) {
        EmptyCatalogState(state, actions)
        return
    }

    val loadingStableId = loadingStableId(state, feedback)
    val loadedItem = catalogItems.firstOrNull(HarnessModelInventoryItem::loaded)
    val loadedModel = loadedItem?.let { distributionByStableId[it.stableId] }
    val visibleItems = catalogItems.filter { item ->
        !item.loaded && availabilityFilter.matches(item) && sizeFilter.matches(item)
    }
    val explicitFilterActive = availabilityFilter != ModelsAvailabilityFilter.ALL || sizeFilter != ModelsSizeFilter.ALL

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ModelsLibrarySummary(state, catalogItems.size)
        if (loadedItem != null && loadedModel != null) {
            ActiveModelCard(
                state = state,
                item = loadedItem,
                model = loadedModel,
                actions = actions,
                onOpenModelDetails = { onOpenModelDetails(loadedItem) },
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
        listOf(ModelsSizeFilter.B08, ModelsSizeFilter.B2, ModelsSizeFilter.B4).forEach { group ->
            val groupItems = orderGroupItems(group, visibleItems.filter(group::matches))
            if (groupItems.isNotEmpty()) {
                val expanded = when (group) {
                    ModelsSizeFilter.B08 -> expandedB08
                    ModelsSizeFilter.B2 -> expandedB2
                    ModelsSizeFilter.B4 -> expandedB4
                    ModelsSizeFilter.ALL -> true
                }
                ModelsGroupSection(
                    state = state,
                    actions = actions,
                    group = group,
                    items = groupItems,
                    distributionByStableId = distributionByStableId,
                    loadingStableId = loadingStableId,
                    progressivelyDisclose = !explicitFilterActive,
                    expanded = expanded,
                    onExpandedChanged = { value ->
                        when (group) {
                            ModelsSizeFilter.B08 -> expandedB08 = value
                            ModelsSizeFilter.B2 -> expandedB2 = value
                            ModelsSizeFilter.B4 -> expandedB4 = value
                            ModelsSizeFilter.ALL -> Unit
                        }
                    },
                    onOpenModelDetails = onOpenModelDetails,
                )
            }
        }
        if (visibleItems.isEmpty()) {
            HarnessEmptyState(
                title = "No matching models",
                detail = modelsEmptyStateDetail(availabilityFilter, sizeFilter, loadedItem != null),
            )
        }
        ModelsCatalogFooter(state, actions)
    }
}

@Composable
private fun EmptyCatalogState(state: HarnessUiState, actions: UnifiedModelsActions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (state.modelDistribution.catalogStatus) {
            PhoneCatalogLoadStatus.LOADING -> HarnessLoadingState(
                title = "Loading models",
                detail = "Reading the curated model catalog and local installation state.",
            )

            PhoneCatalogLoadStatus.FAILED -> {
                HarnessErrorState(
                    title = "Models unavailable",
                    detail = state.modelDistribution.message,
                )
                HarnessSecondaryButton(
                    text = "Try again",
                    enabled = !state.busy,
                    onClick = actions.refresh,
                )
            }

            PhoneCatalogLoadStatus.READY -> HarnessEmptyState(
                title = "No models available",
                detail = "The current curated catalog has no models for this device and target.",
            )
        }
    }
}

@Composable
private fun ModelsGroupSection(
    state: HarnessUiState,
    actions: UnifiedModelsActions,
    group: ModelsSizeFilter,
    items: List<HarnessModelInventoryItem>,
    distributionByStableId: Map<String, PhoneCatalogModelUi>,
    loadingStableId: String?,
    progressivelyDisclose: Boolean,
    expanded: Boolean,
    onExpandedChanged: (Boolean) -> Unit,
    onOpenModelDetails: (HarnessModelInventoryItem) -> Unit,
) {
    val collapseAlternatives = progressivelyDisclose && items.size > 1
    val shownItems = if (collapseAlternatives && !expanded) items.take(1) else items
    val hiddenCount = items.size - shownItems.size

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ModelsGroupHeader(group, items.size)
        shownItems.forEach { item ->
            val model = distributionByStableId[item.stableId] ?: return@forEach
            UnifiedModelCard(
                state = state,
                item = item,
                model = model,
                actions = actions,
                onOpenModelDetails = onOpenModelDetails,
                loading = loadingStableId == item.stableId,
                suggested = item.stableId == group.suggestedModelId,
            )
        }
        if (collapseAlternatives) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = { onExpandedChanged(!expanded) },
                    modifier = Modifier.heightIn(min = HarnessMinimumTouchTarget),
                ) {
                    Text(
                        if (expanded) {
                            "Show fewer"
                        } else {
                            "Show $hiddenCount alternative${if (hiddenCount == 1) "" else "s"}"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelsGroupHeader(group: ModelsSizeFilter, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(requireNotNull(group.groupLabel), style = MaterialTheme.typography.titleLarge)
        Text(
            text = "$count option${if (count == 1) "" else "s"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
                ModelActionFeedbackTone.SUCCESS -> "DONE"
                ModelActionFeedbackTone.ERROR -> "NEEDS ATTENTION"
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
private fun ModelsLibrarySummary(state: HarnessUiState, catalogCount: Int) {
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
                    text = "${state.modelInventory.installedCount} installed · ${formatModelBytes(
                        state.modelInventory.installedBytes,
                    )} on device",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "$catalogCount curated models available",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.modelDistribution.catalogStatus != PhoneCatalogLoadStatus.READY) {
                HarnessStatusBadge(
                    label = if (state.modelDistribution.catalogStatus == PhoneCatalogLoadStatus.LOADING) "REFRESHING" else "CATALOG ISSUE",
                    tone = if (state.modelDistribution.catalogStatus == PhoneCatalogLoadStatus.LOADING) {
                        HarnessStatusTone.INFO
                    } else {
                        HarnessStatusTone.WARNING
                    },
                )
            }
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
        Text("Availability", style = MaterialTheme.typography.labelLarge)
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = when (state.modelDistribution.catalogStatus) {
                PhoneCatalogLoadStatus.READY -> "Catalog-managed models · verified before installation"
                PhoneCatalogLoadStatus.LOADING -> "Refreshing model catalog…"
                PhoneCatalogLoadStatus.FAILED -> state.modelDistribution.message
            },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = if (state.modelDistribution.catalogStatus == PhoneCatalogLoadStatus.FAILED) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        TextButton(
            onClick = actions.refresh,
            enabled = !state.busy,
            modifier = Modifier.heightIn(min = HarnessMinimumTouchTarget),
        ) {
            Text("Refresh")
        }
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
    ModelsSizeFilter.B4 -> item.stableId.startsWith("qwen35-4b-")
}

internal fun orderGroupItems(group: ModelsSizeFilter, items: List<HarnessModelInventoryItem>): List<HarnessModelInventoryItem> =
    items.sortedBy { item -> if (item.stableId == group.suggestedModelId) 0 else 1 }

internal fun modelsEmptyStateDetail(
    availabilityFilter: ModelsAvailabilityFilter,
    sizeFilter: ModelsSizeFilter,
    activeModelPresent: Boolean,
): String {
    val prefix = if (activeModelPresent) "No other" else "No"
    val size = sizeFilter.takeIf { it != ModelsSizeFilter.ALL }?.label?.let { " $it" }.orEmpty()
    return when (availabilityFilter) {
        ModelsAvailabilityFilter.INSTALLED -> "$prefix$size installed models match this filter."
        ModelsAvailabilityFilter.AVAILABLE -> "$prefix$size not-installed models match this filter."
        ModelsAvailabilityFilter.ALL -> "$prefix$size models match this filter."
    }
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
