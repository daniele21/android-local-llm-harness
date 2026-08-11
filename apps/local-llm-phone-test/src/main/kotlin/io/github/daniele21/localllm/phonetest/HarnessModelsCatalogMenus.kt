@file:Suppress("FunctionName")

package io.github.daniele21.localllm.phonetest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.daniele21.localllm.ui.designsystem.HarnessPrimaryButton
import io.github.daniele21.localllm.ui.designsystem.HarnessSecondaryButton

@Composable
internal fun ModelOverflowMenu(
    state: HarnessUiState,
    item: HarnessModelInventoryItem,
    model: PhoneCatalogModelUi,
    actions: UnifiedModelsActions,
    onOpenModelDetails: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Text("⋮", style = MaterialTheme.typography.titleLarge)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("View details") },
                onClick = {
                    expanded = false
                    onOpenModelDetails()
                },
            )
            if (item.installed) {
                DropdownMenuItem(
                    text = { Text("Verify integrity") },
                    enabled = !state.busy,
                    onClick = {
                        expanded = false
                        if (item.selected) actions.verifySelected() else actions.catalog.verifyInstalled(model.stableId)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Remove model") },
                    enabled = !item.loaded && !state.busy,
                    onClick = {
                        expanded = false
                        if (item.selected) actions.requestSelectedRemoval() else actions.catalog.requestRemove(model.stableId)
                    },
                )
            }
        }
    }
}

@Composable
internal fun ModelOverflowButton(onOpenDetails: () -> Unit, onCancelDownload: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Text("⋮", style = MaterialTheme.typography.titleLarge)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("View details") },
                onClick = {
                    expanded = false
                    onOpenDetails()
                },
            )
            DropdownMenuItem(
                text = { Text("Cancel download") },
                onClick = {
                    expanded = false
                    onCancelDownload()
                },
            )
        }
    }
}

@Composable
internal fun ModelRemovalConfirmation(
    state: HarnessUiState,
    item: HarnessModelInventoryItem,
    model: PhoneCatalogModelUi,
    actions: UnifiedModelsActions,
) {
    val selectedConfirmation = item.selected && state.removalConfirmationPending
    val catalogConfirmation = !item.selected && model.removalConfirmationPending
    if (!selectedConfirmation && !catalogConfirmation) return

    Text(
        text = "Remove this model from local storage? This cannot be undone.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HarnessPrimaryButton(
            text = "Remove",
            enabled = !state.busy,
            modifier = Modifier.weight(1f),
        ) {
            if (selectedConfirmation) actions.confirmSelectedRemoval() else actions.catalog.confirmRemove(model.stableId)
        }
        HarnessSecondaryButton(
            text = "Cancel",
            modifier = Modifier.weight(1f),
        ) {
            if (selectedConfirmation) actions.cancelSelectedRemoval() else actions.catalog.cancelRemove(model.stableId)
        }
    }
}
