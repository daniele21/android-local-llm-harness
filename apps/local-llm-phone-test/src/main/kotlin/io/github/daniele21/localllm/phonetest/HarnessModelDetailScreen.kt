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
    val stackDenseContent = currentHarnessAdaptivePolicy().stackDenseContent
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
        item { ModelStateCard(presentation, stackDenseContent) }
        item { ModelTechnicalCard(presentation, stackDenseContent) }
        if (presentation.recoveryOptions.isNotEmpty()) {
            item {
                ModelRecoveryCard(
                    presentation = presentation,
                    pendingRecovery = pendingRecovery,
                    busy = busy,
                    stackDenseContent = stackDenseContent,
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
private fun ModelStateCard(
    presentation: HarnessModelDetailPresentation,
    stackDenseContent: Boolean,
) {
    HarnessCard {
        Text("Model state", style = MaterialTheme.typography.titleLarge)
        ModelMetricPair(
            firstLabel = "Compatibility",
            firstValue = presentation.compatibility,
            secondLabel = "Integrity",
            secondValue = presentation.integrity,
            stackDenseContent = stackDenseContent,
        )
        ModelMetricPair(
            firstLabel = "Installation",
            firstValue = presentation.installation,
            secondLabel = "Selection",
            secondValue = presentation.selection,
            stackDenseContent = stackDenseContent,
        )
        HarnessMetric("Runtime", presentation.runtimeOwnership)
    }
}

@Composable
private fun ModelTechnicalCard(
    presentation: HarnessModelDetailPresentation,
    stackDenseContent: Boolean,
) {
    HarnessCard {
        Text("Technical metadata", style = MaterialTheme.typography.titleLarge)
        ModelMetricPair(
            firstLabel = "Origin",
            firstValue = presentation.origin,
            secondLabel = "Size",
            secondValue = presentation.size,
            stackDenseContent = stackDenseContent,
        )
        ModelMetricPair(
            firstLabel = "Architecture",
            firstValue = presentation.architecture,
            secondLabel = "Quantization",
            secondValue = presentation.quantization,
            stackDenseContent = stackDenseContent,
        )
        HarnessMetric("Digest", presentation.digest)
    }
}

@Composable
private fun ModelMetricPair(
    firstLabel: String,
    firstValue: String,
    secondLabel: String,
    secondValue: String,
    stackDenseContent: Boolean,
) {
    if (stackDenseContent) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HarnessMetric(firstLabel, firstValue, Modifier.fillMaxWidth())
            HarnessMetric(secondLabel, secondValue, Modifier.fillMaxWidth())
        }
    } else {
        HarnessMetricRow {
            HarnessMetric(firstLabel, firstValue, Modifier.weight(1f))
            HarnessMetric(secondLabel, secondValue, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ModelRecoveryCard(
    presentation: HarnessModelDetailPresentation,
    pendingRecovery: HarnessModelRecoveryRequest?,
    busy: Boolean,
    stackDenseContent: Boolean,
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
            if (stackDenseContent) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HarnessPrimaryButton(
                        text = "Confirm release",
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onConfirmRecovery,
                    )
                    HarnessSecondaryButton(
                        text = "Cancel",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onCancelRecovery,
                    )
                }
            } else {
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
}

private fun HarnessModelDetailTone.statusTone(): HarnessStatusTone = when (this) {
    HarnessModelDetailTone.NEUTRAL -> HarnessStatusTone.NEUTRAL
    HarnessModelDetailTone.SUCCESS -> HarnessStatusTone.SUCCESS
    HarnessModelDetailTone.WARNING -> HarnessStatusTone.WARNING
    HarnessModelDetailTone.ERROR -> HarnessStatusTone.ERROR
}
