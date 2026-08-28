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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.daniele21.localllm.contracts.ConsumerPreparationAction
import io.github.daniele21.localllm.contracts.ConsumerRuntimePhase
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.models.PresetCreationSource
import io.github.daniele21.localllm.ui.designsystem.HarnessCard
import io.github.daniele21.localllm.ui.designsystem.HarnessEmptyState
import io.github.daniele21.localllm.ui.designsystem.HarnessErrorState
import io.github.daniele21.localllm.ui.designsystem.HarnessKeyValueRow
import io.github.daniele21.localllm.ui.designsystem.HarnessMinimumTouchTarget
import io.github.daniele21.localllm.ui.designsystem.HarnessPrimaryButton
import io.github.daniele21.localllm.ui.designsystem.HarnessSecondaryButton
import io.github.daniele21.localllm.ui.designsystem.HarnessStatusBadge
import io.github.daniele21.localllm.ui.designsystem.HarnessStatusTone
import io.github.daniele21.localllm.ui.designsystem.LocalHarnessSpacing

@Composable
internal fun HarnessAssignedUseCaseScreen(
    applicationName: String,
    assignment: HarnessAssignmentSummary?,
    onOpenPreset: (HarnessPresetSummary) -> Unit,
    onCreatePreset: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (assignment == null) {
        HarnessErrorState(
            title = "Assignment unavailable",
            detail = "This use-case assignment may have changed. Reload the application configuration.",
            modifier = modifier,
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("assigned-use-case-${assignment.useCaseId}"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(LocalHarnessSpacing.current.large),
        verticalArrangement = Arrangement.spacedBy(LocalHarnessSpacing.current.medium),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(LocalHarnessSpacing.current.xSmall)) {
                Text(assignment.displayName, style = MaterialTheme.typography.headlineSmall)
                Text(
                    applicationName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HarnessStatusBadge(assignment.status.assignmentLabel(), assignment.status.assignmentTone())
            }
        }
        item {
            HarnessAssignmentRuntimeCard(assignment.runtime, assignment.useCaseId)
        }
        item {
            Text("Default configuration", style = MaterialTheme.typography.titleMedium)
            assignment.defaultPreset?.let { preset ->
                HarnessPresetCard(
                    preset = preset,
                    activePreset = assignment.runtime.activePreset,
                    emphasized = true,
                    onClick = { onOpenPreset(preset) },
                )
            } ?: HarnessEmptyState(
                title = "Default preset unavailable",
                detail = "No published default preset is available for this assignment.",
            )
        }
        item {
            Text("Available presets", style = MaterialTheme.typography.titleMedium)
        }
        if (assignment.availablePresets.isEmpty()) {
            item {
                HarnessEmptyState(
                    title = "No presets available",
                    detail = "Published presets for this use case will appear here.",
                )
            }
        } else {
            items(
                items = assignment.availablePresets,
                key = { "${it.presetId}:${it.revision}" },
            ) { preset ->
                HarnessPresetCard(
                    preset = preset,
                    activePreset = assignment.runtime.activePreset,
                    onClick = { onOpenPreset(preset) },
                )
            }
        }
        onCreatePreset?.let { create ->
            item {
                HarnessSecondaryButton(text = "Create preset", onClick = create)
            }
        }
    }
}

@Composable
internal fun HarnessPresetDetailScreen(
    preset: HarnessPresetSummary?,
    onUseAsDefault: (() -> Unit)? = null,
    onDuplicateAndCustomize: (() -> Unit)? = null,
    onEditCustom: (() -> Unit)? = null,
    onOpenAdvanced: (() -> Unit)? = null,
    onOpenTechnicalDetails: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (preset == null) {
        HarnessErrorState(
            title = "Preset unavailable",
            detail = "This preset revision may no longer be published. Reload the assignment.",
            modifier = modifier,
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("preset-detail-${preset.presetId}-${preset.revision}"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(LocalHarnessSpacing.current.large),
        verticalArrangement = Arrangement.spacedBy(LocalHarnessSpacing.current.medium),
    ) {
        item {
            HarnessCard(emphasized = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(LocalHarnessSpacing.current.medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(LocalHarnessSpacing.current.xSmall),
                    ) {
                        Text(preset.displayName, style = MaterialTheme.typography.titleLarge)
                        Text(
                            preset.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HarnessStatusBadge(preset.originLabel(), HarnessStatusTone.INFO)
                    if (preset.isDefault) {
                        HarnessStatusBadge("Default", HarnessStatusTone.SUCCESS)
                    }
                }
            }
        }
        item {
            HarnessCard {
                HarnessKeyValueRow("Model selection", preset.modelProfileId ?: "Automatic selection")
                HarnessKeyValueRow("Context", preset.contextTokens?.let { "$it tokens" } ?: "Use-case default")
                HarnessKeyValueRow("Revision", preset.revision.toString(), monospacedValue = true)
            }
        }
        if (!preset.isDefault && onUseAsDefault != null) {
            item { HarnessPrimaryButton(text = "Use as default", onClick = onUseAsDefault) }
        }
        when (preset.source) {
            PresetCreationSource.SUGGESTED -> onDuplicateAndCustomize?.let { duplicate ->
                item { HarnessSecondaryButton(text = "Duplicate & customize", onClick = duplicate) }
            }

            PresetCreationSource.CUSTOM -> onEditCustom?.let { edit ->
                item { HarnessSecondaryButton(text = "Edit", onClick = edit) }
            }
        }
        onOpenAdvanced?.let { advanced ->
            item { HarnessSecondaryButton(text = "Advanced settings", onClick = advanced) }
        }
        onOpenTechnicalDetails?.let { technical ->
            item { HarnessSecondaryButton(text = "Technical details", onClick = technical) }
        }
    }
}

@Composable
private fun HarnessAssignmentRuntimeCard(runtime: HarnessAssignmentRuntimeSummary, useCaseId: String) {
    HarnessCard(
        emphasized = runtime.activationActive,
        modifier = Modifier.testTag("assignment-runtime-$useCaseId"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(LocalHarnessSpacing.current.xSmall),
            ) {
                Text("Current runtime", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (runtime.activationActive) {
                        "Activation is owned by this assignment. Runtime activity below is the shared Harness runtime."
                    } else {
                        "Not activated. The assigned local model is prepared automatically when the consumer starts analysis."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HarnessStatusBadge(runtime.runtimeLabel(), runtime.runtimeTone())
        }
        HarnessKeyValueRow("Activation", if (runtime.activationActive) "Active" else "Inactive")
        if (runtime.activationActive) {
            HarnessKeyValueRow("Shared runtime", runtime.phase.runtimePhaseLabel())
            runtime.effectiveModelProfileId?.let { modelProfileId ->
                HarnessKeyValueRow("Effective model", modelProfileId, monospacedValue = true)
            }
            if (runtime.preparationAction != ConsumerPreparationAction.NONE) {
                HarnessKeyValueRow("Preparation", runtime.preparationAction.preparationLabel())
            }
        }
    }
}

@Composable
private fun HarnessPresetCard(
    preset: HarnessPresetSummary,
    activePreset: InferencePresetRef? = null,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    HarnessCard(
        emphasized = emphasized,
        modifier = Modifier
            .testTag("preset-${preset.presetId}-${preset.revision}")
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = HarnessMinimumTouchTarget),
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
                Text(
                    preset.modelProfileId ?: "Automatic model selection",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HarnessStatusBadge(preset.originLabel(), HarnessStatusTone.INFO)
            if (preset.isDefault) HarnessStatusBadge("Default", HarnessStatusTone.SUCCESS)
            if (activePreset?.id?.value == preset.presetId && activePreset.version == preset.revision) {
                HarnessStatusBadge("In use", HarnessStatusTone.SUCCESS)
            }
        }
    }
}

internal fun HarnessPresetSummary.originLabel(): String = when (source) {
    PresetCreationSource.SUGGESTED -> "Suggested"
    PresetCreationSource.CUSTOM -> "Custom"
}

internal fun HarnessAssignmentRuntimeSummary.runtimeLabel(): String = if (!activationActive) {
    "Inactive"
} else {
    phase.runtimePhaseLabel()
}

internal fun HarnessAssignmentRuntimeSummary.runtimeTone(): HarnessStatusTone = if (!activationActive) {
    HarnessStatusTone.NEUTRAL
} else {
    when (phase) {
        ConsumerRuntimePhase.IDLE -> HarnessStatusTone.INFO
        ConsumerRuntimePhase.PREPARING -> HarnessStatusTone.INFO
        ConsumerRuntimePhase.READY -> HarnessStatusTone.SUCCESS
        ConsumerRuntimePhase.GENERATING -> HarnessStatusTone.INFO
        ConsumerRuntimePhase.FAILED -> HarnessStatusTone.ERROR
    }
}

internal fun ConsumerRuntimePhase.runtimePhaseLabel(): String = when (this) {
    ConsumerRuntimePhase.IDLE -> "Activated"
    ConsumerRuntimePhase.PREPARING -> "Preparing"
    ConsumerRuntimePhase.READY -> "Ready"
    ConsumerRuntimePhase.GENERATING -> "Generating"
    ConsumerRuntimePhase.FAILED -> "Failed"
}

internal fun ConsumerPreparationAction.preparationLabel(): String = when (this) {
    ConsumerPreparationAction.NONE -> "None"
    ConsumerPreparationAction.LOADING -> "Loading model"
    ConsumerPreparationAction.REUSING -> "Reusing loaded model"
    ConsumerPreparationAction.SWITCHING -> "Switching model"
}

private fun HarnessAssignmentStatus.assignmentLabel(): String = when (this) {
    HarnessAssignmentStatus.ACTIVE -> "Active"
    HarnessAssignmentStatus.DISABLED -> "Disabled"
    HarnessAssignmentStatus.SETUP_REQUIRED -> "Setup required"
    HarnessAssignmentStatus.UNAVAILABLE -> "Unavailable"
}

private fun HarnessAssignmentStatus.assignmentTone(): HarnessStatusTone = when (this) {
    HarnessAssignmentStatus.ACTIVE -> HarnessStatusTone.SUCCESS
    HarnessAssignmentStatus.DISABLED -> HarnessStatusTone.NEUTRAL
    HarnessAssignmentStatus.SETUP_REQUIRED -> HarnessStatusTone.WARNING
    HarnessAssignmentStatus.UNAVAILABLE -> HarnessStatusTone.ERROR
}
