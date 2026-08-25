@file:Suppress("FunctionName")

package io.github.daniele21.localllm.phonetest

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.daniele21.localllm.ui.designsystem.HarnessCard
import io.github.daniele21.localllm.ui.designsystem.HarnessEmptyState
import io.github.daniele21.localllm.ui.designsystem.HarnessErrorState
import io.github.daniele21.localllm.ui.designsystem.HarnessKeyValueRow
import io.github.daniele21.localllm.ui.designsystem.HarnessMinimumTouchTarget
import io.github.daniele21.localllm.ui.designsystem.HarnessPrimaryButton
import io.github.daniele21.localllm.ui.designsystem.HarnessRecoveryCard
import io.github.daniele21.localllm.ui.designsystem.HarnessSecondaryButton
import io.github.daniele21.localllm.ui.designsystem.HarnessStatusBadge
import io.github.daniele21.localllm.ui.designsystem.HarnessStatusTone
import io.github.daniele21.localllm.ui.designsystem.LocalHarnessSpacing

@Composable
internal fun HarnessCreatePresetScreen(
    application: HarnessApplicationSummary?,
    assignment: HarnessAssignmentSummary?,
    mutationState: HarnessApplicationsMutationState,
    onSave: (HarnessPresetSummary, String, Boolean, Int?) -> Unit,
    onReload: () -> Unit,
    onClearFeedback: () -> Unit,
    onViewSavedPreset: (String, Int) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (application == null || assignment == null) {
        HarnessErrorState(
            title = "Preset creation unavailable",
            detail = "The application assignment may have changed. Return to the assigned use case and reload.",
            modifier = modifier,
        )
        return
    }
    val initialBase = assignment.defaultPreset ?: assignment.availablePresets.firstOrNull()
    if (initialBase == null) {
        HarnessEmptyState(
            title = "No base preset available",
            detail = "A published preset must be available before a Custom preset can be created.",
            modifier = modifier,
        )
        return
    }

    var displayName by rememberSaveable(application.applicationId, assignment.useCaseId) { mutableStateOf("") }
    var selectedBaseKey by rememberSaveable(application.applicationId, assignment.useCaseId) {
        mutableStateOf(initialBase.identityKey())
    }
    var contextText by rememberSaveable(application.applicationId, assignment.useCaseId) {
        mutableStateOf(initialBase.contextTokens?.toString().orEmpty())
    }
    var automaticModelSelection by rememberSaveable(application.applicationId, assignment.useCaseId) {
        mutableStateOf(initialBase.modelProfileId == null)
    }

    val selectedBase = assignment.availablePresets.firstOrNull { it.identityKey() == selectedBaseKey } ?: initialBase
    LaunchedEffect(assignment.bindingRevision, selectedBaseKey) {
        if (assignment.availablePresets.none { it.identityKey() == selectedBaseKey }) {
            selectedBaseKey = initialBase.identityKey()
            contextText = initialBase.contextTokens?.toString().orEmpty()
            automaticModelSelection = initialBase.modelProfileId == null
        }
    }

    val parsedContext = contextText.toIntOrNull()
    val contextValid = contextText.isBlank() || (parsedContext != null && parsedContext > 0)
    val saving = mutationState == HarnessApplicationsMutationState.Saving
    val saved = mutationState as? HarnessApplicationsMutationState.Saved
    val customSaved = saved?.takeIf { it.presetId != null && it.presetRevision != null }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("create-custom-preset"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(LocalHarnessSpacing.current.large),
        verticalArrangement = Arrangement.spacedBy(LocalHarnessSpacing.current.medium),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(LocalHarnessSpacing.current.xSmall)) {
                Text("Create preset", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Create a Custom configuration for ${assignment.displayName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                modifier = Modifier.fillMaxWidth().testTag("custom-preset-name"),
                enabled = !saving && customSaved == null,
                singleLine = true,
                label = { Text("Preset name") },
                supportingText = { Text("Shown in this application’s assigned-use-case configuration.") },
            )
        }
        item {
            Text("Start from", style = MaterialTheme.typography.titleMedium)
        }
        items(
            items = assignment.availablePresets,
            key = HarnessPresetSummary::identityKey,
        ) { preset ->
            val selected = preset.identityKey() == selectedBase.identityKey()
            HarnessCard(
                emphasized = selected,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = HarnessMinimumTouchTarget)
                    .clickable(enabled = !saving && customSaved == null) {
                        selectedBaseKey = preset.identityKey()
                        contextText = preset.contextTokens?.toString().orEmpty()
                        automaticModelSelection = preset.modelProfileId == null
                        onClearFeedback()
                    },
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
        item {
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
                        onCheckedChange = {
                            automaticModelSelection = it
                            onClearFeedback()
                        },
                        enabled = !saving && customSaved == null && selectedBase.modelProfileId != null,
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = contextText,
                onValueChange = { value ->
                    if (value.isEmpty() || value.all(Char::isDigit)) {
                        contextText = value
                        onClearFeedback()
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("custom-preset-context"),
                enabled = !saving && customSaved == null,
                singleLine = true,
                isError = !contextValid,
                label = { Text("Context tokens") },
                supportingText = {
                    Text(
                        if (contextValid) {
                            "Blank uses the use-case default. The canonical minimum is enforced when saved."
                        } else {
                            "Enter a positive whole number or leave the field blank."
                        },
                    )
                },
            )
        }
        item {
            HarnessCustomPresetFeedback(
                state = mutationState,
                onReload = onReload,
                onClearFeedback = onClearFeedback,
                onViewSavedPreset = onViewSavedPreset,
                onDone = onDone,
            )
        }
        if (customSaved == null) {
            item {
                HarnessPrimaryButton(
                    text = if (saving) "Saving preset…" else "Save preset",
                    enabled = !saving && displayName.isNotBlank() && contextValid,
                    onClick = {
                        onSave(
                            selectedBase,
                            displayName.trim(),
                            automaticModelSelection,
                            parsedContext,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun HarnessCustomPresetFeedback(
    state: HarnessApplicationsMutationState,
    onReload: () -> Unit,
    onClearFeedback: () -> Unit,
    onViewSavedPreset: (String, Int) -> Unit,
    onDone: () -> Unit,
) {
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
                    HarnessPrimaryButton("View preset") { onViewSavedPreset(presetId, presetRevision) }
                    HarnessSecondaryButton("Done", onClick = onDone)
                }
            }
        }

        is HarnessApplicationsMutationState.Conflict -> HarnessRecoveryCard(
            title = "Configuration changed",
            detail = state.message,
            actionLabel = "Reload changes",
            onAction = onReload,
            tone = HarnessStatusTone.WARNING,
        )

        is HarnessApplicationsMutationState.Failed -> HarnessRecoveryCard(
            title = "Preset not saved",
            detail = state.message,
            actionLabel = "Review fields",
            onAction = onClearFeedback,
            tone = HarnessStatusTone.ERROR,
        )
    }
}

private fun HarnessPresetSummary.identityKey(): String = "$presetId:$revision"
