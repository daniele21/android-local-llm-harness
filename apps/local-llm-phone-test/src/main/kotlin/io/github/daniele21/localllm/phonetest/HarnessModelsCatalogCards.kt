@file:Suppress("FunctionName")

package io.github.daniele21.localllm.phonetest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.daniele21.localllm.ui.designsystem.HarnessCard
import io.github.daniele21.localllm.ui.designsystem.HarnessPrimaryButton
import io.github.daniele21.localllm.ui.designsystem.HarnessSecondaryButton
import io.github.daniele21.localllm.ui.designsystem.HarnessStatusBadge

@Composable
internal fun UnifiedModelCard(
    state: HarnessUiState,
    item: HarnessModelInventoryItem,
    model: PhoneCatalogModelUi,
    actions: UnifiedModelsActions,
    onOpenModelDetails: (HarnessModelInventoryItem) -> Unit,
) {
    HarnessCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${item.quantization ?: model.quantization} · ${formatModelBytes(model.sizeBytes)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HarnessStatusBadge(
                label = item.lifecycle.name.replace('_', ' '),
                tone = item.lifecycle.statusTone(),
            )
        }
        if (item.selected) {
            Text(
                if (item.loaded) "Active in Playground · loaded in memory" else "Active in Playground · runtime unloaded",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        model.detail?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        ModelLifecycleActions(state, item, model, actions)
        HarnessSecondaryButton("View details") { onOpenModelDetails(item) }
    }
}

@Composable
private fun ModelLifecycleActions(
    state: HarnessUiState,
    item: HarnessModelInventoryItem,
    model: PhoneCatalogModelUi,
    actions: UnifiedModelsActions,
) {
    when (model.status) {
        PhoneCatalogModelStatus.DOWNLOADING -> {
            val expected = model.expectedBytes.coerceAtLeast(1L)
            val progress = (model.bytesDownloaded.toDouble() / expected.toDouble()).coerceIn(0.0, 1.0)
            LinearProgressIndicator(progress = { progress.toFloat() }, modifier = Modifier.fillMaxWidth())
            Text("${formatModelBytes(model.bytesDownloaded)} / ${formatModelBytes(model.expectedBytes)}")
            HarnessSecondaryButton("Cancel download") { actions.catalog.cancelDownload(model.stableId) }
        }

        PhoneCatalogModelStatus.VERIFIED_READY_TO_INSTALL ->
            HarnessPrimaryButton("Install verified model") { actions.catalog.install(model.stableId) }

        PhoneCatalogModelStatus.INSTALLING -> {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("Installing in the app-private model store.")
        }

        PhoneCatalogModelStatus.INSTALLED -> InstalledLifecycleActions(state, item, model, actions)

        PhoneCatalogModelStatus.READY_TO_DOWNLOAD,
        PhoneCatalogModelStatus.CANCELLED,
        PhoneCatalogModelStatus.FAILED,
        -> HarnessPrimaryButton(
            text = if (model.status == PhoneCatalogModelStatus.READY_TO_DOWNLOAD) "Download" else "Retry download",
            enabled = model.compatible && !state.modelDistribution.operationActive,
        ) {
            actions.catalog.download(model.stableId)
        }

        PhoneCatalogModelStatus.INCOMPATIBLE -> Unit
    }
}

@Composable
private fun InstalledLifecycleActions(
    state: HarnessUiState,
    item: HarnessModelInventoryItem,
    model: PhoneCatalogModelUi,
    actions: UnifiedModelsActions,
) {
    val installed = model.installedModel ?: return
    if (!item.loaded) {
        HarnessPrimaryButton(
            "Use in Playground",
            enabled = !state.busy,
        ) {
            actions.catalog.selectInstalled(installed)
        }
    }
    if (item.loaded) {
        HarnessSecondaryButton(
            "Unload from memory",
            enabled = !state.busy,
            onClick = actions.unloadLoaded,
        )
    }
    HarnessSecondaryButton(
        "Verify integrity",
        enabled = !state.busy,
    ) {
        if (item.selected) actions.verifySelected() else actions.catalog.verifyInstalled(model.stableId)
    }
    ModelRemovalActions(state, item, model, actions)
}

@Composable
private fun ModelRemovalActions(
    state: HarnessUiState,
    item: HarnessModelInventoryItem,
    model: PhoneCatalogModelUi,
    actions: UnifiedModelsActions,
) {
    if (item.loaded) {
        Text("Unload this model before removing its local copy.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    if (item.selected) {
        if (state.removalConfirmationPending) {
            Text("Removal permanently deletes the app-private model copy.", color = MaterialTheme.colorScheme.error)
            HarnessPrimaryButton("Confirm removal", enabled = !state.busy, onClick = actions.confirmSelectedRemoval)
            HarnessSecondaryButton("Cancel removal", onClick = actions.cancelSelectedRemoval)
        } else {
            HarnessSecondaryButton("Remove installed model", enabled = !state.busy, onClick = actions.requestSelectedRemoval)
        }
        return
    }
    if (model.removalConfirmationPending) {
        Text("Removal permanently deletes the app-private model copy.", color = MaterialTheme.colorScheme.error)
        HarnessPrimaryButton(
            "Confirm removal",
            enabled = !state.modelDistribution.operationActive,
        ) {
            actions.catalog.confirmRemove(model.stableId)
        }
        HarnessSecondaryButton("Cancel removal") { actions.catalog.cancelRemove(model.stableId) }
    } else {
        HarnessSecondaryButton(
            "Remove installed model",
            enabled = !state.modelDistribution.operationActive,
        ) {
            actions.catalog.requestRemove(model.stableId)
        }
    }
}
