@file:Suppress("FunctionName")

package io.github.daniele21.localllm.phonetest

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.daniele21.localllm.ui.designsystem.HarnessCard
import io.github.daniele21.localllm.ui.designsystem.HarnessPrimaryButton
import io.github.daniele21.localllm.ui.designsystem.HarnessSecondaryButton
import io.github.daniele21.localllm.ui.designsystem.HarnessStatusBadge
import io.github.daniele21.localllm.ui.designsystem.HarnessStatusTone

@Composable
internal fun UnifiedModelCard(
    state: HarnessUiState,
    item: HarnessModelInventoryItem,
    model: PhoneCatalogModelUi,
    actions: UnifiedModelsActions,
    onOpenModelDetails: (HarnessModelInventoryItem) -> Unit,
    loading: Boolean = false,
) {
    HarnessCard {
        ModelCardHeader(item, model, loading) { onOpenModelDetails(item) }
        ModelStateLine(item, loading)
        model.detail
            ?.takeIf { model.status == PhoneCatalogModelStatus.FAILED || model.status == PhoneCatalogModelStatus.INCOMPATIBLE }
            ?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        ModelLifecycleActions(
            state = state,
            item = item,
            model = model,
            actions = actions,
            loading = loading,
            onOpenModelDetails = { onOpenModelDetails(item) },
        )
        ModelRemovalConfirmation(state, item, model, actions)
    }
}

@Composable
internal fun ActiveModelCard(
    state: HarnessUiState,
    item: HarnessModelInventoryItem,
    model: PhoneCatalogModelUi,
    actions: UnifiedModelsActions,
) {
    HarnessCard(emphasized = true) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "ACTIVE MODEL",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(item.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${item.quantization ?: model.quantization} · ${formatModelBytes(model.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HarnessStatusBadge("LOADED", HarnessStatusTone.SUCCESS)
        }
        Text(
            text = "Loaded in memory · ready for Playground",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HarnessSecondaryButton(
            text = "Unload model",
            enabled = !state.busy,
            onClick = actions.unloadLoaded,
        )
    }
}

@Composable
private fun ModelCardHeader(
    item: HarnessModelInventoryItem,
    model: PhoneCatalogModelUi,
    loading: Boolean,
    onOpenDetails: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenDetails),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(item.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${item.quantization ?: model.quantization} · ${formatModelBytes(model.sizeBytes)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HarnessStatusBadge(
            label = modelCardStatusLabel(item, loading),
            tone = if (loading) HarnessStatusTone.INFO else item.lifecycle.statusTone(),
        )
    }
}

@Composable
private fun ModelStateLine(item: HarnessModelInventoryItem, loading: Boolean) {
    val detail = when {
        loading -> "Preparing model in local memory"
        item.loaded -> "Loaded in memory · ready for Playground"
        item.selected -> "Selected for Playground · not loaded"
        item.installed -> "Installed on device · not loaded"
        else -> null
    } ?: return
    Text(
        text = detail,
        style = MaterialTheme.typography.bodySmall,
        color = if (item.loaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ModelLifecycleActions(
    state: HarnessUiState,
    item: HarnessModelInventoryItem,
    model: PhoneCatalogModelUi,
    actions: UnifiedModelsActions,
    loading: Boolean,
    onOpenModelDetails: () -> Unit,
) {
    when (model.status) {
        PhoneCatalogModelStatus.DOWNLOADING -> DownloadingActions(model, actions, onOpenModelDetails)
        PhoneCatalogModelStatus.VERIFIED_READY_TO_INSTALL -> ActionRow(
            menu = { ModelOverflowMenu(state, item, model, actions, onOpenModelDetails) },
        ) {
            HarnessPrimaryButton(
                text = "Install model",
                modifier = Modifier.weight(1f),
            ) {
                actions.catalog.install(model.stableId)
            }
        }

        PhoneCatalogModelStatus.INSTALLING -> {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                text = "Installing in private app storage…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        PhoneCatalogModelStatus.INSTALLED -> InstalledLifecycleActions(
            state = state,
            item = item,
            model = model,
            actions = actions,
            loading = loading,
            onOpenModelDetails = onOpenModelDetails,
        )

        PhoneCatalogModelStatus.READY_TO_DOWNLOAD,
        PhoneCatalogModelStatus.CANCELLED,
        PhoneCatalogModelStatus.FAILED,
        -> ActionRow(
            menu = { ModelOverflowMenu(state, item, model, actions, onOpenModelDetails) },
        ) {
            HarnessPrimaryButton(
                text = if (model.status == PhoneCatalogModelStatus.READY_TO_DOWNLOAD) "Download" else "Retry download",
                enabled = model.compatible && !state.modelDistribution.operationActive,
                modifier = Modifier.weight(1f),
            ) {
                actions.catalog.download(model.stableId)
            }
        }

        PhoneCatalogModelStatus.INCOMPATIBLE -> ActionRow(
            menu = { ModelOverflowMenu(state, item, model, actions, onOpenModelDetails) },
        ) {
            Text(
                text = "Not compatible with this device",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DownloadingActions(
    model: PhoneCatalogModelUi,
    actions: UnifiedModelsActions,
    onOpenModelDetails: () -> Unit,
) {
    val expected = model.expectedBytes.coerceAtLeast(1L)
    val progress = (model.bytesDownloaded.toDouble() / expected.toDouble()).coerceIn(0.0, 1.0)
    LinearProgressIndicator(progress = { progress.toFloat() }, modifier = Modifier.fillMaxWidth())
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${formatModelBytes(model.bytesDownloaded)} / ${formatModelBytes(model.expectedBytes)}",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ModelOverflowButton(
            onOpenDetails = onOpenModelDetails,
            onCancelDownload = { actions.catalog.cancelDownload(model.stableId) },
        )
    }
}

@Composable
private fun InstalledLifecycleActions(
    state: HarnessUiState,
    item: HarnessModelInventoryItem,
    model: PhoneCatalogModelUi,
    actions: UnifiedModelsActions,
    loading: Boolean,
    onOpenModelDetails: () -> Unit,
) {
    val installed = model.installedModel ?: return
    ActionRow(
        menu = { ModelOverflowMenu(state, item, model, actions, onOpenModelDetails) },
    ) {
        when {
            loading -> HarnessPrimaryButton(
                text = "Loading…",
                enabled = false,
                modifier = Modifier.weight(1f),
                onClick = {},
            )

            item.loaded -> HarnessSecondaryButton(
                text = "Unload model",
                enabled = !state.busy,
                modifier = Modifier.weight(1f),
                onClick = actions.unloadLoaded,
            )

            else -> HarnessPrimaryButton(
                text = "Load model",
                enabled = !state.busy,
                modifier = Modifier.weight(1f),
            ) {
                actions.catalog.selectInstalled(installed)
            }
        }
    }
}

@Composable
private fun ActionRow(
    menu: @Composable () -> Unit,
    primary: @Composable Row.() -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        primary()
        menu()
    }
}

@Composable
private fun ModelOverflowMenu(
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
private fun ModelOverflowButton(
    onOpenDetails: () -> Unit,
    onCancelDownload: () -> Unit,
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
private fun ModelRemovalConfirmation(
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

internal fun modelCardStatusLabel(item: HarnessModelInventoryItem, loading: Boolean): String =
    if (loading) "LOADING" else item.lifecycle.name.replace('_', ' ')
