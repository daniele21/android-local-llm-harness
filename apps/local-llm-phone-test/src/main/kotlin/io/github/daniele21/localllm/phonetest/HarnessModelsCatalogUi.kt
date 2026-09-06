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

internal data class UnifiedModelsActions(
    val catalog: PhoneModelDistributionActions,
    val verifySelected: () -> Unit,
    val unloadLoaded: () -> Unit,
    val requestSelectedRemoval: () -> Unit,
    val cancelSelectedRemoval: () -> Unit,
    val confirmSelectedRemoval: () -> Unit,
    val refresh: () -> Unit,
)

internal data class ModelsCatalogGroupEnvironment(
    val state: HarnessUiState,
    val actions: UnifiedModelsActions,
    val distributionByStableId: Map<String, PhoneCatalogModelUi>,
    val loadingStableId: String?,
    val progressivelyDisclose: Boolean,
    val onOpenModelDetails: (HarnessModelInventoryItem) -> Unit,
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
    val groupEnvironment = ModelsCatalogGroupEnvironment(
        state = state,
        actions = actions,
        distributionByStableId = distributionByStableId,
        loadingStableId = loadingStableId,
        progressivelyDisclose = !explicitFilterActive,
        onOpenModelDetails = onOpenModelDetails,
    )

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
        ModelsCatalogGroups(
            environment = groupEnvironment,
            visibleItems = visibleItems,
        )
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
