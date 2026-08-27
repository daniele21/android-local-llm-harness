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
internal fun HarnessPresetBaseCard(preset: HarnessPresetSummary, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
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
    modelOptions: List<HarnessPresetModelOption>,
    selectedModelProfileId: String?,
    selectionValid: Boolean,
    enabled: Boolean,
    onModelSelected: (String?) -> Unit,
) {
    HarnessCard(modifier = Modifier.testTag("custom-preset-model-policy")) {
        Text("Model target", style = MaterialTheme.typography.titleMedium)
        Text(
            "Choose the runtime model profile this preset targets. This does not download or load a model; availability is checked fail-closed when the preset is activated.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HarnessModelOptionCard(
            title = "Automatic compatible model",
            detail = "Harness resolves a compatible installed model at activation time.",
            selected = selectedModelProfileId == null,
            enabled = enabled,
            testTag = "custom-preset-model-automatic",
            onSelect = { onModelSelected(null) },
        )
        modelOptions.forEach { option ->
            HarnessModelOptionCard(
                title = option.displayName,
                detail = option.description,
                selected = selectedModelProfileId == option.modelProfileId,
                enabled = enabled,
                testTag = "custom-preset-model-${option.modelId}",
                technicalValue = option.modelProfileId,
                onSelect = { onModelSelected(option.modelProfileId) },
            )
        }
        if (!selectionValid && selectedModelProfileId != null) {
            HarnessRecoveryCard(
                title = "Selected model is no longer available",
                detail =
                "The saved runtime profile '$selectedModelProfileId' is not part of the current curated execution catalog. " +
                    "Choose Automatic or another model before saving.",
                actionLabel = "Use Automatic",
                onAction = { onModelSelected(null) },
                tone = HarnessStatusTone.WARNING,
            )
        }
    }
}

@Composable
private fun HarnessModelOptionCard(
    title: String,
    detail: String,
    selected: Boolean,
    enabled: Boolean,
    testTag: String,
    technicalValue: String? = null,
    onSelect: () -> Unit,
) {
    HarnessCard(
        emphasized = selected,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = HarnessMinimumTouchTarget)
            .testTag(testTag)
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
                Text(title, style = MaterialTheme.typography.labelLarge)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                technicalValue?.let { profileId ->
                    Text(
                        profileId,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (selected) HarnessStatusBadge("Selected", HarnessStatusTone.SUCCESS)
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
internal fun HarnessEffectivePresetConfigurationCard(summary: HarnessPresetConfigurationSummary) {
    HarnessCard(modifier = Modifier.testTag("custom-preset-effective-configuration")) {
        Text("Effective generation configuration", style = MaterialTheme.typography.titleMedium)
        Text(
            "Projected from the selected canonical inference profile and runtime model tier. These values are not duplicated as editable UI state.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (summary.available) {
            HarnessStatusBadge("Resolved", HarnessStatusTone.SUCCESS)
            summary.rows.forEach { row ->
                HarnessKeyValueRow(label = row.label, value = row.value)
            }
        } else {
            HarnessStatusBadge("Configuration unavailable", HarnessStatusTone.WARNING)
            Text(
                summary.unavailableReason.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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
