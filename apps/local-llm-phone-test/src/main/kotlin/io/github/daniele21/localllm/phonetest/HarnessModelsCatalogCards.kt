@file:Suppress("FunctionName")

package io.github.daniele21.localllm.phonetest

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import io.github.daniele21.localllm.ui.designsystem.HarnessInlinePrimaryButton
import io.github.daniele21.localllm.ui.designsystem.HarnessInlineSecondaryButton
import io.github.daniele21.localllm.ui.designsystem.HarnessMinimumTouchTarget

@Composable
internal fun UnifiedModelVariantRow(
    environment: ModelsCatalogGroupEnvironment,
    item: HarnessModelInventoryItem,
    model: PhoneCatalogModelUi,
    loading: Boolean = false,
    suggested: Boolean = false,
    showCompatibilityDetail: Boolean = true,
) {
    val state = environment.state
    val actions = environment.actions
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModelVariantIdentity(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = HarnessMinimumTouchTarget)
                    .clickable(onClickLabel = "Open model details") { environment.onOpenModelDetails(item) },
                item = item,
                model = model,
                loading = loading,
                suggested = suggested,
            )
            ModelVariantAction(
                state = state,
                item = item,
                model = model,
                actions = actions,
                loading = loading,
            )
            ModelOverflowMenu(state, item, model, actions)
        }
        if (model.status == PhoneCatalogModelStatus.DOWNLOADING) {
            DownloadingVariantContent(model)
        }
        ModelVariantDetail(model, showCompatibilityDetail)
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
        Text(
            text = "Active model",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = HarnessMinimumTouchTarget)
                    .clickable(onClickLabel = "Open active model details", onClick = onOpenModelDetails),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(item.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${item.quantization ?: model.quantization} · ${formatModelBytes(model.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HarnessInlineSecondaryButton(
                text = "Unload",
                enabled = !state.busy,
                onClick = actions.unloadLoaded,
            )
        }
        Text(
            text = if (item.lifecycle == HarnessModelLifecycle.DEGRADED) {
                item.detail ?: "Runtime ownership needs attention before the next inference."
            } else {
                "In memory · ready for Playground"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (item.lifecycle == HarnessModelLifecycle.DEGRADED) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
    }
}

@Composable
private fun ModelVariantIdentity(
    modifier: Modifier,
    item: HarnessModelInventoryItem,
    model: PhoneCatalogModelUi,
    loading: Boolean,
    suggested: Boolean,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.quantization ?: model.quantization,
                style = MaterialTheme.typography.titleMedium,
            )
            if (suggested) {
                Text(
                    text = "Recommended",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            text = "${formatModelBytes(model.sizeBytes)} · ${modelVariantStatusLabel(item, loading)}",
            style = MaterialTheme.typography.bodySmall,
            color = when (item.lifecycle) {
                HarnessModelLifecycle.FAILED -> MaterialTheme.colorScheme.error
                HarnessModelLifecycle.LOADED -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun ModelVariantAction(
    state: HarnessUiState,
    item: HarnessModelInventoryItem,
    model: PhoneCatalogModelUi,
    actions: UnifiedModelsActions,
    loading: Boolean,
) {
    when (model.status) {
        PhoneCatalogModelStatus.DOWNLOADING -> TextButton(
            onClick = { actions.catalog.cancelDownload(model.stableId) },
            modifier = Modifier.heightIn(min = HarnessMinimumTouchTarget),
        ) {
            Text("Cancel")
        }

        PhoneCatalogModelStatus.VERIFIED_READY_TO_INSTALL -> HarnessInlinePrimaryButton(
            text = "Install",
            enabled = !state.busy,
        ) {
            actions.catalog.install(model.stableId)
        }

        PhoneCatalogModelStatus.INSTALLING -> Text(
            text = "Installing…",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        PhoneCatalogModelStatus.INSTALLED -> InstalledVariantAction(state, item, model, actions, loading)

        PhoneCatalogModelStatus.READY_TO_DOWNLOAD,
        PhoneCatalogModelStatus.CANCELLED,
        PhoneCatalogModelStatus.FAILED,
        -> HarnessInlinePrimaryButton(
            text = if (model.status == PhoneCatalogModelStatus.READY_TO_DOWNLOAD) "Download" else "Retry",
            enabled = model.compatible && !state.modelDistribution.operationActive,
        ) {
            actions.catalog.download(model.stableId)
        }

        PhoneCatalogModelStatus.INCOMPATIBLE -> Text(
            text = "›",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DownloadingVariantContent(model: PhoneCatalogModelUi) {
    val expected = model.expectedBytes.coerceAtLeast(1L)
    val progress = (model.bytesDownloaded.toDouble() / expected.toDouble()).coerceIn(0.0, 1.0)
    LinearProgressIndicator(progress = { progress.toFloat() }, modifier = Modifier.fillMaxWidth())
    Text(
        text = "${formatModelBytes(model.bytesDownloaded)} / ${formatModelBytes(model.expectedBytes)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun InstalledVariantAction(
    state: HarnessUiState,
    item: HarnessModelInventoryItem,
    model: PhoneCatalogModelUi,
    actions: UnifiedModelsActions,
    loading: Boolean,
) {
    val installed = model.installedModel ?: return
    when {
        loading -> HarnessInlinePrimaryButton(text = "Loading…", enabled = false, onClick = {})

        item.loaded -> HarnessInlineSecondaryButton(
            text = "Unload",
            enabled = !state.busy,
            onClick = actions.unloadLoaded,
        )

        else -> HarnessInlinePrimaryButton(
            text = "Load",
            enabled = !state.busy,
        ) {
            actions.catalog.selectInstalled(installed)
        }
    }
}

@Composable
private fun ModelVariantDetail(model: PhoneCatalogModelUi, showCompatibilityDetail: Boolean) {
    val shouldShow = model.status == PhoneCatalogModelStatus.FAILED ||
        (showCompatibilityDetail && model.status == PhoneCatalogModelStatus.INCOMPATIBLE)
    if (!shouldShow) return
    model.detail?.let { detail ->
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = if (model.status == PhoneCatalogModelStatus.FAILED) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

internal fun modelVariantStatusLabel(item: HarnessModelInventoryItem, loading: Boolean): String = if (loading) {
    "Loading"
} else {
    when (item.lifecycle) {
        HarnessModelLifecycle.INCOMPATIBLE -> "Unavailable"
        HarnessModelLifecycle.READY_TO_DOWNLOAD -> "Available"
        HarnessModelLifecycle.DOWNLOADING -> "Downloading"
        HarnessModelLifecycle.VERIFIED_READY_TO_INSTALL -> "Ready to install"
        HarnessModelLifecycle.INSTALLING -> "Installing"
        HarnessModelLifecycle.INSTALLED -> "Installed"
        HarnessModelLifecycle.SELECTED -> "Selected"
        HarnessModelLifecycle.LOADED -> "In memory"
        HarnessModelLifecycle.CANCELLED -> "Download stopped"
        HarnessModelLifecycle.FAILED -> "Needs attention"
        HarnessModelLifecycle.DEGRADED -> "Needs recovery"
    }
}
