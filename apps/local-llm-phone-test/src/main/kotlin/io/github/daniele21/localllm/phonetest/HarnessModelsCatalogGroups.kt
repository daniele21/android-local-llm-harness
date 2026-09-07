@file:Suppress("FunctionName")

package io.github.daniele21.localllm.phonetest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.daniele21.localllm.ui.designsystem.HarnessCard

@Composable
internal fun ModelsCatalogGroups(environment: ModelsCatalogGroupEnvironment, visibleItems: List<HarnessModelInventoryItem>) {
    var expandedB08 by rememberSaveable { mutableStateOf(false) }
    var expandedB2 by rememberSaveable { mutableStateOf(false) }
    var expandedB4 by rememberSaveable { mutableStateOf(false) }

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
                environment = environment,
                group = group,
                items = groupItems,
                expanded = expanded,
                onExpandedChanged = { value ->
                    when (group) {
                        ModelsSizeFilter.B08 -> expandedB08 = value
                        ModelsSizeFilter.B2 -> expandedB2 = value
                        ModelsSizeFilter.B4 -> expandedB4 = value
                        ModelsSizeFilter.ALL -> Unit
                    }
                },
            )
        }
    }
}

@Composable
private fun ModelsGroupSection(
    environment: ModelsCatalogGroupEnvironment,
    group: ModelsSizeFilter,
    items: List<HarnessModelInventoryItem>,
    expanded: Boolean,
    onExpandedChanged: (Boolean) -> Unit,
) {
    val shownItems = if (expanded) items else items.take(1)
    val hiddenCount = items.size - shownItems.size
    val commonCompatibilityDetail = commonCompatibilityDetail(items, environment.distributionByStableId)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ModelsGroupHeader(group, items.size)
        HarnessCard {
            if (commonCompatibilityDetail != null) {
                Text(
                    text = "Unavailable on this device",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = commonCompatibilityDetail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider()
            }
            shownItems.forEachIndexed { index, item ->
                val model = environment.distributionByStableId[item.stableId] ?: return@forEachIndexed
                UnifiedModelVariantRow(
                    environment = environment,
                    item = item,
                    model = model,
                    loading = environment.loadingStableId == item.stableId,
                    suggested = item.stableId == group.suggestedModelId,
                    showCompatibilityDetail = commonCompatibilityDetail == null,
                )
                if (index < shownItems.lastIndex) {
                    HorizontalDivider()
                }
            }
            if (items.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { onExpandedChanged(!expanded) }) {
                        Text(
                            if (expanded) {
                                "Show fewer"
                            } else {
                                "$hiddenCount other variant${if (hiddenCount == 1) "" else "s"}"
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelsGroupHeader(group: ModelsSizeFilter, count: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(requireNotNull(group.groupLabel), style = MaterialTheme.typography.titleMedium)
            Text(
                text = "$count variant${if (count == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        group.groupSupportingText?.let { supportingText ->
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun commonCompatibilityDetail(
    items: List<HarnessModelInventoryItem>,
    distributionByStableId: Map<String, PhoneCatalogModelUi>,
): String? {
    val models = items.mapNotNull { distributionByStableId[it.stableId] }
    if (models.size != items.size || models.isEmpty() || models.any { it.status != PhoneCatalogModelStatus.INCOMPATIBLE }) {
        return null
    }
    return models.mapNotNull { it.detail?.trim()?.takeIf(String::isNotEmpty) }.distinct().singleOrNull()
}
