@file:Suppress("FunctionName")

package io.github.daniele21.localllm.phonetest

import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.daniele21.localllm.ui.designsystem.HarnessConfirmationDialog

@Composable
internal fun ModelOverflowMenu(
    state: HarnessUiState,
    item: HarnessModelInventoryItem,
    model: PhoneCatalogModelUi,
    actions: UnifiedModelsActions,
) {
    if (!item.installed) return
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics {
                contentDescription = "More actions for ${item.displayName}"
            },
        ) {
            Text(
                text = "⋮",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Verify integrity") },
                enabled = !state.busy,
                onClick = {
                    expanded = false
                    if (item.selected) actions.verifySelected() else actions.catalog.verifyInstalled(model.stableId)
                },
            )
            DropdownMenuItem(
                text = { Text("Remove from device") },
                enabled = !item.loaded && !state.busy,
                onClick = {
                    expanded = false
                    if (item.selected) actions.requestSelectedRemoval() else actions.catalog.requestRemove(model.stableId)
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

    HarnessConfirmationDialog(
        title = "Remove ${item.displayName}?",
        detail = if (selectedConfirmation) {
            "This removes the installed copy from this device and clears the current model selection. You can download it again later."
        } else {
            "This removes the installed copy from this device. You can download it again later."
        },
        confirmLabel = "Remove from device",
        dismissLabel = "Keep model",
        onConfirm = {
            if (selectedConfirmation) actions.confirmSelectedRemoval() else actions.catalog.confirmRemove(model.stableId)
        },
        onDismiss = {
            if (selectedConfirmation) actions.cancelSelectedRemoval() else actions.catalog.cancelRemove(model.stableId)
        },
    )
}
