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
import androidx.compose.ui.unit.dp
import io.github.daniele21.localllm.ui.designsystem.HarnessCard
import io.github.daniele21.localllm.ui.designsystem.HarnessMetric
import io.github.daniele21.localllm.ui.designsystem.HarnessMetricRow
import io.github.daniele21.localllm.ui.designsystem.HarnessPrimaryButton
import io.github.daniele21.localllm.ui.designsystem.HarnessSecondaryButton

@Composable
internal fun PhoneModelDistributionCatalog(
    state: PhoneModelDistributionState,
    onDownload: (String) -> Unit,
    onCancel: (String) -> Unit,
    onInstall: (String) -> Unit,
    onSelectInstalled: (InstalledCatalogModelMetadata) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HarnessCard {
            Text("Model catalog", style = MaterialTheme.typography.titleLarge)
            Text(state.sourceLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HarnessMetricRow {
                HarnessMetric(
                    "Status",
                    state.catalogStatus.name.replace('_', ' ').lowercase(),
                    Modifier.weight(1f),
                )
                HarnessMetric(
                    "Revision",
                    state.catalogRevision?.toString() ?: "Unavailable",
                    Modifier.weight(1f),
                )
            }
            Text(state.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        state.models.forEach { model ->
            CatalogModelCard(
                model = model,
                operationActive = state.operationActive,
                onDownload = onDownload,
                onCancel = onCancel,
                onInstall = onInstall,
                onSelectInstalled = onSelectInstalled,
            )
        }
    }
}

@Composable
private fun CatalogModelCard(
    model: PhoneCatalogModelUi,
    operationActive: Boolean,
    onDownload: (String) -> Unit,
    onCancel: (String) -> Unit,
    onInstall: (String) -> Unit,
    onSelectInstalled: (InstalledCatalogModelMetadata) -> Unit,
) {
    HarnessCard {
        Text(model.displayName, style = MaterialTheme.typography.titleLarge)
        Text(model.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HarnessMetricRow {
            HarnessMetric("Architecture", model.architecture, Modifier.weight(1f))
            HarnessMetric("Quantization", model.quantization, Modifier.weight(1f))
        }
        HarnessMetricRow {
            HarnessMetric("Size", formatDistributionBytes(model.sizeBytes), Modifier.weight(1f))
            HarnessMetric("License", model.licenseName, Modifier.weight(1f))
        }
        HarnessMetric("Profile", model.profileKey)
        HarnessMetric(
            "State",
            model.status.name.replace('_', ' ').lowercase(),
        )

        if (model.compatibilityWarnings.isNotEmpty()) {
            Text(
                "Warnings: ${model.compatibilityWarnings.joinToString()}",
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        if (model.compatibilityReasons.isNotEmpty()) {
            Text(
                "Unavailable: ${model.compatibilityReasons.joinToString()}",
                color = MaterialTheme.colorScheme.error,
            )
        }
        model.detail?.let { detail ->
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        when (model.status) {
            PhoneCatalogModelStatus.DOWNLOADING -> DownloadProgress(model, onCancel)

            PhoneCatalogModelStatus.VERIFIED_READY_TO_INSTALL ->
                HarnessPrimaryButton("Install verified model") {
                    onInstall(model.stableId)
                }

            PhoneCatalogModelStatus.INSTALLING -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Installation is running in the app-private model store.")
            }

            PhoneCatalogModelStatus.INSTALLED -> {
                val installed = model.installedModel
                if (installed != null) {
                    HarnessMetric("SHA-256", installed.digest.sha256.take(24) + "…")
                    Text(
                        "Installed. Runtime activation remains an explicit action.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HarnessPrimaryButton(
                        "Use in Playground",
                        enabled = !operationActive,
                    ) {
                        onSelectInstalled(installed)
                    }
                }
            }

            PhoneCatalogModelStatus.READY_TO_DOWNLOAD,
            PhoneCatalogModelStatus.CANCELLED,
            PhoneCatalogModelStatus.FAILED,
            -> HarnessPrimaryButton(
                if (model.status == PhoneCatalogModelStatus.READY_TO_DOWNLOAD) {
                    "Download"
                } else {
                    "Retry download"
                },
                enabled = model.compatible && !operationActive,
            ) {
                onDownload(model.stableId)
            }

            PhoneCatalogModelStatus.INCOMPATIBLE -> Unit
        }
    }
}

@Composable
private fun DownloadProgress(
    model: PhoneCatalogModelUi,
    onCancel: (String) -> Unit,
) {
    val expected = model.expectedBytes.coerceAtLeast(1L)
    val progress = (model.bytesDownloaded.toDouble() / expected.toDouble()).coerceIn(0.0, 1.0)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(
            progress = { progress.toFloat() },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "${formatDistributionBytes(model.bytesDownloaded)} / " +
                    formatDistributionBytes(model.expectedBytes),
            )
            Text("${(progress * 100.0).toInt()}%")
        }
        HarnessSecondaryButton("Cancel download") { onCancel(model.stableId) }
    }
}

private fun formatDistributionBytes(bytes: Long): String {
    if (bytes < 1_024L) return "$bytes B"
    val kib = bytes / 1_024.0
    if (kib < 1_024.0) return "%.1f KiB".format(kib)
    val mib = kib / 1_024.0
    if (mib < 1_024.0) return "%.1f MiB".format(mib)
    return "%.2f GiB".format(mib / 1_024.0)
}
