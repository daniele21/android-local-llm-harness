@file:Suppress("FunctionName")

package io.github.daniele21.localllm.phonetest

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.daniele21.localllm.ui.designsystem.HarnessCard
import io.github.daniele21.localllm.ui.designsystem.HarnessMinimumTouchTarget
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
    suggested: Boolean = false,
) {
    HarnessCard {
        ModelCardHeader(item, model, loading, suggested) { onOpenModelDetails(item) }
        ModelStateLine(item, loading)
        model.detail
            ?.takeIf { model.status == PhoneCatalogModelStatus.FAILED || model.status == PhoneCatalogModelStatus.INCOMPATIBLE }
            ?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (model.status == PhoneCatalogModelStatus.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
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
    onOpenModelDetails: () -> Unit,
) {
    HarnessCard(emphasized = true) {
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
            HarnessStatusBadge(
                label = if (item.lifecycle == HarnessModelLifecycle.DEGRADED) "NEEDS RECOVERY" else "IN MEMORY",
                tone = item.lifecycle.statusTone(),
            )
        }
        Text(
            text = if (item.lifecycle == HarnessModelLifecycle.DEGRADED) {
                item.detail ?: "Runtime ownership needs attention before the next inference."
            } else {
                "Ready in memory for Playground"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (item.lifecycle == HarnessModelLifecycle.DEGRADED) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModelDetailsButton(onOpenModelDetails)
            TextButton(
                enabled = !state.busy,
                onClick = actions.unloadLoaded,
                modifier = Modifier.heightIn(min = HarnessMinimumTouchTarget),
            ) {
                Text("Unload from memory")
            }
        }
    }
}

@Composable
private fun ModelCardHeader(
    item: HarnessModelInventoryItem,
    model: PhoneCatalogModelUi,
    loading: Boolean,
    suggested: Boolean,
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
            if (suggested) {
                Text(
                    text = "RECOMMENDED START",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
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
        loading -> "Loading into memory for Playground"
        item.loaded -> "Ready in memory for Playground"
        item.selected -> "Selected for Playground · currently unloaded"
        item.installed -> "Stored on this device · currently unloaded"
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

        PhoneCatalogModelStatus.VERIFIED_READY_TO_INSTALL -> ModelActionLayout(
            primary = {
                HarnessPrimaryButton(
                    text = "Install model",
                    enabled = !state.busy,
                    modifier = Modifier.weight(1f),
                ) {
                    actions.catalog.install(model.stableId)
                }
            },
            contextual = { ModelDetailsButton(onOpenModelDetails) },
        )

        PhoneCatalogModelStatus.INSTALLING -> {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Installing in private app storage…",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ModelDetailsButton(onOpenModelDetails)
            }
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
        -> ModelActionLayout(
            primary = {
                HarnessPrimaryButton(
                    text = if (model.status == PhoneCatalogModelStatus.READY_TO_DOWNLOAD) "Download" else "Retry download",
                    enabled = model.compatible && !state.modelDistribution.operationActive,
                    modifier = Modifier.weight(1f),
                ) {
                    actions.catalog.download(model.stableId)
                }
            },
            contextual = { ModelDetailsButton(onOpenModelDetails) },
        )

        PhoneCatalogModelStatus.INCOMPATIBLE -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Not compatible with this device",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ModelDetailsButton(onOpenModelDetails)
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
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${formatModelBytes(model.bytesDownloaded)} / ${formatModelBytes(model.expectedBytes)}",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = { actions.catalog.cancelDownload(model.stableId) },
            modifier = Modifier.heightIn(min = HarnessMinimumTouchTarget),
        ) {
            Text("Cancel")
        }
        ModelDetailsButton(onOpenModelDetails)
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
    ModelActionLayout(
        primary = {
            when {
                loading -> HarnessPrimaryButton(
                    text = "Loading…",
                    enabled = false,
                    modifier = Modifier.weight(1f),
                    onClick = {},
                )

                item.loaded -> HarnessSecondaryButton(
                    text = "Unload from memory",
                    enabled = !state.busy,
                    modifier = Modifier.weight(1f),
                    onClick = actions.unloadLoaded,
                )

                else -> HarnessPrimaryButton(
                    text = "Load for Playground",
                    enabled = !state.busy,
                    modifier = Modifier.weight(1f),
                ) {
                    actions.catalog.selectInstalled(installed)
                }
            }
        },
        contextual = {
            ModelDetailsButton(onOpenModelDetails)
            ModelOverflowMenu(state, item, model, actions)
        },
    )
}

@Composable
private fun ModelActionLayout(
    primary: @Composable RowScope.() -> Unit,
    contextual: @Composable RowScope.() -> Unit,
) {
    if (currentHarnessAdaptivePolicy().stackDenseContent) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                primary()
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                contextual()
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            primary()
            contextual()
        }
    }
}

@Composable
private fun ModelDetailsButton(onOpenModelDetails: () -> Unit) {
    TextButton(
        onClick = onOpenModelDetails,
        modifier = Modifier.heightIn(min = HarnessMinimumTouchTarget),
    ) {
        Text("Details")
    }
}

internal fun modelCardStatusLabel(item: HarnessModelInventoryItem, loading: Boolean): String = if (loading) {
    "LOADING"
} else {
    when (item.lifecycle) {
        HarnessModelLifecycle.INCOMPATIBLE -> "NOT COMPATIBLE"
        HarnessModelLifecycle.READY_TO_DOWNLOAD -> "AVAILABLE"
        HarnessModelLifecycle.DOWNLOADING -> "DOWNLOADING"
        HarnessModelLifecycle.VERIFIED_READY_TO_INSTALL -> "READY TO INSTALL"
        HarnessModelLifecycle.INSTALLING -> "INSTALLING"
        HarnessModelLifecycle.INSTALLED -> "INSTALLED"
        HarnessModelLifecycle.SELECTED -> "SELECTED"
        HarnessModelLifecycle.LOADED -> "IN MEMORY"
        HarnessModelLifecycle.CANCELLED -> "DOWNLOAD STOPPED"
        HarnessModelLifecycle.FAILED -> "NEEDS ATTENTION"
        HarnessModelLifecycle.DEGRADED -> "NEEDS RECOVERY"
    }
}
