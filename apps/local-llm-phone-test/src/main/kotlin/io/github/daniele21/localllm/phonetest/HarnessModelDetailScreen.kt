@file:Suppress("FunctionName")

package io.github.daniele21.localllm.phonetest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.daniele21.localllm.ui.designsystem.HarnessCard
import io.github.daniele21.localllm.ui.designsystem.HarnessMetric
import io.github.daniele21.localllm.ui.designsystem.HarnessMetricRow
import io.github.daniele21.localllm.ui.designsystem.HarnessPrimaryButton
import io.github.daniele21.localllm.ui.designsystem.HarnessSecondaryButton
import io.github.daniele21.localllm.ui.designsystem.HarnessStatusBadge
import io.github.daniele21.localllm.ui.designsystem.HarnessStatusTone

@Composable
internal fun HarnessModelDetailScreen(
    presentation: HarnessModelDetailPresentation?,
    pendingRecovery: HarnessModelRecoveryRequest?,
    busy: Boolean,
    onRequestRecovery: (String, HarnessModelRecoveryAction) -> Unit,
    onConfirmRecovery: () -> Unit,
    onCancelRecovery: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("model-detail"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (presentation == null) {
            item {
                HarnessCard {
                    HarnessStatusBadge("MODEL UNAVAILABLE", HarnessStatusTone.WARNING)
                    Text("This model is no longer present in the current inventory.")
                    Text(
                        "Return to Models and refresh the catalog or current selection.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@LazyColumn
        }

        item { ModelIdentityCard(presentation) }
        item { ModelStateCard(presentation) }
        item { ModelTechnicalCard(presentation) }
        if (presentation.recoveryOptions.isNotEmpty()) {
            item {
                ModelRecoveryCard(
                    presentation = presentation,
                    pendingRecovery = pendingRecovery,
                    busy = busy,
                    onRequestRecovery = onRequestRecovery,
                    onConfirmRecovery = onConfirmRecovery,
                    onCancelRecovery = onCancelRecovery,
                )
            }
        }
    }
}

@Composable
private fun ModelIdentityCard(presentation: HarnessModelDetailPresentation) {
    HarnessCard(emphasized = true) {
        HarnessStatusBadge(presentation.lifecycle.uppercase(), presentation.tone.statusTone())
        Text(presentation.title, style = MaterialTheme.typography.headlineMedium)
        Text(presentation.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        presentation.detail?.let {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ModelStateCard(presentation: HarnessModelDetailPresentation) {
    HarnessCard {
        Text("Model state", style = MaterialTheme.typography.titleLarge)
        HarnessMetricRow {
            HarnessMetric("Compatibility", presentation.compatibility, Modifier.weight(1f))
            HarnessMetric("Integrity", presentation.integrity, Modifier.weight(1f))
        }
        HarnessMetricRow {
            HarnessMetric("Installation", presentation.installation, Modifier.weight(1f))
            HarnessMetric("Selection", presentation.selection, Modifier.weight(1f))
        }
        HarnessMetric("Runtime", presentation.runtimeOwnership)
    }
}

@Composable
private fun ModelTechnicalCard(presentation: HarnessModelDetailPresentation) {
    HarnessCard {
        Text("Technical metadata", style = MaterialTheme.typography.titleLarge)
        HarnessMetricRow {
            HarnessMetric("Origin", presentation.origin, Modifier.weight(1f))
            HarnessMetric("Size", presentation.size, Modifier.weight(1f))
        }
        HarnessMetricRow {
            HarnessMetric("Architecture", presentation.architecture, Modifier.weight(1f))
            HarnessMetric("Quantization", presentation.quantization, Modifier.weight(1f))
        }
        HarnessMetric("Digest", presentation.digest)
    }
}

@Composable
private fun ModelRecoveryCard(
    presentation: HarnessModelDetailPresentation,
    pendingRecovery: HarnessModelRecoveryRequest?,
    busy: Boolean,
    onRequestRecovery: (String, HarnessModelRecoveryAction) -> Unit,
    onConfirmRecovery: () -> Unit,
    onCancelRecovery: () -> Unit,
) {
    HarnessCard {
        HarnessStatusBadge("RECOVERY", HarnessStatusTone.WARNING)
        Text("Runtime ownership and app selection need an explicit decision.")
        presentation.recoveryOptions.forEach { option ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(option.detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HarnessSecondaryButton(
                    text = option.label,
                    enabled = !busy && pendingRecovery == null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    onRequestRecovery(presentation.identity, option.action)
                }
            }
        }
        if (pendingRecovery?.identity == presentation.identity) {
            Text(
                "Confirm runtime release. This unloads the current owner but does not delete model files.",
                color = MaterialTheme.colorScheme.error,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HarnessPrimaryButton(
                    text = "Confirm release",
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    onClick = onConfirmRecovery,
                )
                HarnessSecondaryButton(
                    text = "Cancel",
                    modifier = Modifier.weight(1f),
                    onClick = onCancelRecovery,
                )
            }
        }
    }
}

private fun HarnessModelDetailTone.statusTone(): HarnessStatusTone = when (this) {
    HarnessModelDetailTone.NEUTRAL -> HarnessStatusTone.NEUTRAL
    HarnessModelDetailTone.SUCCESS -> HarnessStatusTone.SUCCESS
    HarnessModelDetailTone.WARNING -> HarnessStatusTone.WARNING
    HarnessModelDetailTone.ERROR -> HarnessStatusTone.ERROR
}
