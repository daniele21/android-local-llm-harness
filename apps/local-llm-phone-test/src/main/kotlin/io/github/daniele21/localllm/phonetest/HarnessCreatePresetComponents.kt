@file:Suppress("FunctionName")

package io.github.daniele21.localllm.phonetest

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.daniele21.localllm.ui.designsystem.HarnessCard
import io.github.daniele21.localllm.ui.designsystem.HarnessKeyValueRow
import io.github.daniele21.localllm.ui.designsystem.HarnessMinimumTouchTarget
import io.github.daniele21.localllm.ui.designsystem.HarnessPrimaryButton
import io.github.daniele21.localllm.ui.designsystem.HarnessRecoveryCard
import io.github.daniele21.localllm.ui.designsystem.HarnessSecondaryButton
import io.github.daniele21.localllm.ui.designsystem.HarnessStatusBadge
import io.github.daniele21.localllm.ui.designsystem.HarnessStatusTone
import io.github.daniele21.localllm.ui.designsystem.LocalHarnessSpacing

@Composable
internal fun HarnessCreatePresetHeader(assignmentName: String) {
    Column(verticalArrangement = Arrangement.spacedBy(LocalHarnessSpacing.current.xSmall)) {
        Text("Create preset", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Create a Custom configuration for $assignmentName",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun HarnessPresetNameField(value: String, enabled: Boolean, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().testTag("custom-preset-name"),
        enabled = enabled,
        singleLine = true,
        label = { Text("Preset name") },
        supportingText = { Text("Shown in this application’s assigned-use-case configuration.") },
    )
}

@Composable
internal fun HarnessPresetBaseCard(
    preset: HarnessPresetSummary,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    HarnessCard(
        emphasized = selected,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = HarnessMinimumTouchTarget)
            .clickable(enabled = enabled, onClick = onSelect),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LocalHarnessSpacing.current.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(LocalHarnessSpacing.current.xSmall),
            ) {
                Text(preset.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    preset.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HarnessStatusBadge(preset.originLabel(), HarnessStatusTone.INFO)
            if (selected) HarnessStatusBadge("Selected", HarnessStatusTone.SUCCESS)
        }
    }
}

@Composable
internal fun HarnessModelPolicyCard(
    selectedBase: HarnessPresetSummary,
    automaticModelSelection: Boolean,
    enabled: Boolean,
    onAutomaticModelSelectionChanged: (Boolean) -> Unit,
) {
    HarnessCard {
        Text("Model policy", style = MaterialTheme.typography.titleMedium)
        HarnessKeyValueRow(
            label = "Base preset",
            value = selectedBase.modelProfileId ?: "Automatic selection",
            monospacedValue = selectedBase.modelProfileId != null,
        )
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = HarnessMinimumTouchTarget),
            horizontalArrangement = Arrangement.spacedBy(LocalHarnessSpacing.current.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Automatic model selection", style = MaterialTheme.typography.labelLarge)
                Text(
                    if (selectedBase.modelProfileId == null) {
                        "The selected base preset already uses automatic selection."
                    } else {
                        "When disabled, the Custom preset keeps the base model policy."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = automaticModelSelection,
                onCheckedChange = onAutomaticModelSelectionChanged,
                enabled = enabled && selectedBase.modelProfileId != null,
            )
        }
    }
}

@Composable
internal fun HarnessContextTokensField(value: String, valid: Boolean, enabled: Boolean, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().testTag("custom-preset-context"),
        enabled = enabled,
        singleLine = true,
        isError = !valid,
        label = { Text("Context tokens") },
        supportingText = {
            Text(
                if (valid) {
                    "Blank uses the use-case default. The canonical minimum is enforced when saved."
                } else {
                    "Enter a positive whole number or leave the field blank."
                },
            )
        },
    )
}

@Composable
internal fun HarnessSavePresetButton(saving: Boolean, enabled: Boolean, onSave: () -> Unit) {
    HarnessPrimaryButton(
        text = if (saving) "Saving preset…" else "Save preset",
        enabled = enabled,
        onClick = onSave,
    )
}

@Composable
internal fun HarnessCustomPresetFeedback(state: HarnessApplicationsMutationState, actions: HarnessCreatePresetActions) {
    when (state) {
        HarnessApplicationsMutationState.Idle -> Unit

        HarnessApplicationsMutationState.Saving -> HarnessCard {
            HarnessStatusBadge("Saving", HarnessStatusTone.INFO)
            Text("Persisting the Custom preset in the Harness control plane.")
        }

        is HarnessApplicationsMutationState.Saved -> {
            val presetId = state.presetId
            val presetRevision = state.presetRevision
            if (presetId != null && presetRevision != null) {
                HarnessCard(emphasized = true) {
                    HarnessStatusBadge("Preset saved", HarnessStatusTone.SUCCESS)
                    Text(state.message)
                    HarnessPrimaryButton("View preset") { actions.onViewSavedPreset(presetId, presetRevision) }
                    HarnessSecondaryButton("Done", onClick = actions.onDone)
                }
            }
        }

        is HarnessApplicationsMutationState.Conflict -> HarnessRecoveryCard(
            title = "Configuration changed",
            detail = state.message,
            actionLabel = "Reload changes",
            onAction = actions.onReload,
            tone = HarnessStatusTone.WARNING,
        )

        is HarnessApplicationsMutationState.Failed -> HarnessRecoveryCard(
            title = "Preset not saved",
            detail = state.message,
            actionLabel = "Review fields",
            onAction = actions.onClearFeedback,
            tone = HarnessStatusTone.ERROR,
        )
    }
}
