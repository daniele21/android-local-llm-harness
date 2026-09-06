@file:Suppress("FunctionName")

package io.github.daniele21.localllm.phonetest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import io.github.daniele21.localllm.ui.designsystem.HarnessMinimumTouchTarget

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
    val collapseAlternatives = environment.progressivelyDisclose && items.size > 1
    val shownItems = if (collapseAlternatives && !expanded) items.take(1) else items
    val hiddenCount = items.size - shownItems.size

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ModelsGroupHeader(group, items.size)
        shownItems.forEach { item ->
            val model = environment.distributionByStableId[item.stableId] ?: return@forEach
            UnifiedModelCard(
                state = environment.state,
                item = item,
                model = model,
                actions = environment.actions,
                onOpenModelDetails = environment.onOpenModelDetails,
                loading = environment.loadingStableId == item.stableId,
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
