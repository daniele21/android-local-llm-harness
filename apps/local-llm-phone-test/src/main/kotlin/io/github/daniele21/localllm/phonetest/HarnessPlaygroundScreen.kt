@file:Suppress("FunctionName", "LongParameterList")

package io.github.daniele21.localllm.phonetest

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.daniele21.localllm.contracts.ThinkingMode
import io.github.daniele21.localllm.ui.designsystem.HarnessCard
import io.github.daniele21.localllm.ui.designsystem.HarnessColors
import io.github.daniele21.localllm.ui.designsystem.HarnessNumberField
import io.github.daniele21.localllm.ui.designsystem.HarnessNumberInputMode
import io.github.daniele21.localllm.ui.designsystem.HarnessPrimaryButton
import io.github.daniele21.localllm.ui.designsystem.HarnessSecondaryButton

@Composable
internal fun HarnessPlaygroundScreen(state: HarnessUiState, actions: HarnessPlaygroundActions) {
    var advancedVisible by rememberSaveable { mutableStateOf(false) }
    var expertVisible by rememberSaveable { mutableStateOf(false) }
    val presentation = state.toPlaygroundPresentation()

    HarnessScreenList(title = null) {
        item {
            Text(
                "Runs entirely on this device",
                style = MaterialTheme.typography.labelLarge,
                color = HarnessColors.Secondary,
            )
        }
        item { PlaygroundModelState(state.importedModel, actions.openModels) }
        item {
            PlaygroundPromptCard(
                state = state,
                presentation = presentation,
                actions = actions,
                advancedVisible = advancedVisible,
                expertVisible = expertVisible,
                onToggleAdvanced = {
                    advancedVisible = !advancedVisible
                    if (!advancedVisible) expertVisible = false
                },
                onToggleExpert = { expertVisible = !expertVisible },
            )
        }
        item { ModernPlaygroundResponseCard(presentation) }
    }
}

@Composable
private fun PlaygroundModelState(model: ImportedPhoneModel?, onOpenModels: () -> Unit) {
    HarnessCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = if (model == null) "Choose model" else "Change model",
                role = Role.Button,
                onClick = onOpenModels,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HarnessDestinationIcon(HarnessDestination.MODELS, selected = model != null)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (model == null) "NO MODEL SELECTED" else "SELECTED MODEL",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (model == null) HarnessColors.Warning else HarnessColors.Secondary,
                )
                Text(model?.fileName ?: "Choose a reviewed Qwen3.5 model", style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                if (model == null) "Choose ›" else "Change ›",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun PlaygroundPromptCard(
    state: HarnessUiState,
    presentation: PlaygroundPresentation,
    actions: HarnessPlaygroundActions,
    advancedVisible: Boolean,
    expertVisible: Boolean,
    onToggleAdvanced: () -> Unit,
    onToggleExpert: () -> Unit,
) {
    HarnessCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Prompt", style = MaterialTheme.typography.titleLarge)
            TextButton(
                enabled = presentation.inputsEnabled && state.playgroundPrompt.isNotEmpty(),
                onClick = { actions.updatePrompt("") },
            ) {
                Text("Clear")
            }
        }
        OutlinedTextField(
            value = state.playgroundPrompt,
            onValueChange = actions.updatePrompt,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Prompt") },
            minLines = 4,
            enabled = presentation.inputsEnabled,
        )

        PlaygroundPresetControls(state, presentation, actions.updatePreset)

        HarnessSecondaryButton(
            text = if (advancedVisible) "Advanced settings · Hide" else "Advanced settings · Show",
            modifier = Modifier
                .fillMaxWidth()
                .testTag("playground-advanced-toggle")
                .semantics { stateDescription = if (advancedVisible) "Expanded" else "Collapsed" },
            onClick = onToggleAdvanced,
        )
        if (advancedVisible) {
            Text(
                "Tune sampling and output only when the preset is not enough.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PlaygroundAdvancedControls(state, presentation, actions)
            HarnessSecondaryButton(
                text = if (expertVisible) "Expert settings · Hide" else "Expert settings · Show",
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("playground-expert-toggle")
                    .semantics { stateDescription = if (expertVisible) "Expanded" else "Collapsed" },
                onClick = onToggleExpert,
            )
            if (expertVisible) {
                PlaygroundExpertControls(state, presentation, actions)
            }
        }

        playgroundSettingsValidationMessage(state)?.let { validation ->
            Text(
                text = validation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("playground-settings-error"),
            )
        }
        PlaygroundRunControls(presentation, actions)
    }
}

@Composable
private fun PlaygroundPresetControls(state: HarnessUiState, presentation: PlaygroundPresentation, onPresetChanged: (String) -> Unit) {
    val selectedPreset = playgroundPresetOptions.firstOrNull { it.id == state.playgroundPreset }
    val basePreset = playgroundPresetOptions.firstOrNull { it.id == state.playgroundBasePreset }
    Text(
        text = selectedPreset?.let { "Preset · ${it.label}" }
            ?: "Preset · Custom${basePreset?.let { " · Based on ${it.label}" }.orEmpty()}",
        style = MaterialTheme.typography.titleSmall,
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(playgroundPresetOptions) { preset ->
            FilterChip(
                selected = state.playgroundPreset == preset.id,
                onClick = { onPresetChanged(preset.id) },
                label = { Text(preset.label) },
                enabled = presentation.inputsEnabled,
            )
        }
    }
    Text(
        text = selectedPreset?.description ?: basePreset?.description.orEmpty(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PlaygroundAdvancedControls(state: HarnessUiState, presentation: PlaygroundPresentation, actions: HarnessPlaygroundActions) {
    Text("Thinking", style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = state.playgroundThinkingMode == ThinkingMode.DISABLED,
            onClick = { actions.updateThinkingMode(ThinkingMode.DISABLED) },
            label = { Text("Off") },
            enabled = presentation.inputsEnabled,
        )
        FilterChip(
            selected = state.playgroundThinkingMode == ThinkingMode.ENABLED,
            onClick = { actions.updateThinkingMode(ThinkingMode.ENABLED) },
            label = { Text("On") },
            enabled = presentation.inputsEnabled,
            modifier = Modifier.testTag("playground-thinking-on"),
        )
    }

    val temperature = playgroundTemperature(state)
    HarnessNumberField(
        value = state.playgroundTemperature,
        onValueChange = actions.updateTemperature,
        label = "Temperature",
        mode = HarnessNumberInputMode.DECIMAL,
        enabled = presentation.inputsEnabled,
        modifier = Modifier.fillMaxWidth().testTag("playground-temperature-field"),
        supportingText = "0–2. Slider and field stay synchronized.",
    )
    Slider(
        value = temperature,
        onValueChange = { actions.updateTemperature(formatPlaygroundControlValue(it)) },
        valueRange = 0f..2f,
        enabled = presentation.inputsEnabled,
        modifier = Modifier.fillMaxWidth().testTag("playground-temperature-slider"),
    )

    val topP = state.playgroundTopP.toFloatOrNull()?.coerceIn(0.01f, 1f) ?: 0.9f
    HarnessNumberField(
        value = state.playgroundTopP,
        onValueChange = actions.updateTopP,
        label = "Top-p",
        mode = HarnessNumberInputMode.DECIMAL,
        enabled = presentation.inputsEnabled && temperature != 0f,
        modifier = Modifier.fillMaxWidth().testTag("playground-top-p-field"),
        supportingText = "0.01–1. Slider and field stay synchronized.",
    )
    Slider(
        value = topP,
        onValueChange = { actions.updateTopP(formatPlaygroundControlValue(it)) },
        valueRange = 0.01f..1f,
        enabled = presentation.inputsEnabled && temperature != 0f,
        modifier = Modifier.fillMaxWidth().testTag("playground-top-p-slider"),
    )
    playgroundSamplingGuidance(state)?.let { guidance ->
        Text(
            guidance,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("playground-sampling-guidance"),
        )
    }

    HarnessNumberField(
        value = state.playgroundMaxTokens,
        onValueChange = actions.updateMaxTokens,
        label = "Max output tokens",
        enabled = presentation.inputsEnabled,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PlaygroundExpertControls(state: HarnessUiState, presentation: PlaygroundPresentation, actions: HarnessPlaygroundActions) {
    val parsedTemperature = state.playgroundTemperature.toFloatOrNull()
    val samplingEnabled = presentation.inputsEnabled && parsedTemperature != null && parsedTemperature != 0f

    Text("Expert overrides", style = MaterialTheme.typography.titleSmall)
    Text(
        "These controls override preset behavior and are intended for measured configuration experiments.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    HarnessNumberField(
        value = state.playgroundTopK,
        onValueChange = actions.updateTopK,
        label = "Top-k",
        enabled = samplingEnabled,
        modifier = Modifier.fillMaxWidth(),
    )
    HarnessNumberField(
        value = state.playgroundMinP,
        onValueChange = actions.updateMinP,
        label = "Min-p",
        mode = HarnessNumberInputMode.DECIMAL,
        enabled = samplingEnabled,
        modifier = Modifier.fillMaxWidth().testTag("playground-min-p"),
    )
    HarnessNumberField(
        value = state.playgroundPresencePenalty,
        onValueChange = actions.updatePresencePenalty,
        label = "Presence penalty",
        mode = HarnessNumberInputMode.DECIMAL,
        enabled = presentation.inputsEnabled,
        modifier = Modifier.fillMaxWidth().testTag("playground-presence-penalty"),
    )
    HarnessNumberField(
        value = state.playgroundRepeatPenalty,
        onValueChange = actions.updateRepeatPenalty,
        label = "Repeat penalty",
        mode = HarnessNumberInputMode.DECIMAL,
        supportingText = "1 = off",
        enabled = presentation.inputsEnabled,
        modifier = Modifier.fillMaxWidth().testTag("playground-repeat-penalty"),
    )
    HarnessNumberField(
        value = state.playgroundRepeatLastN,
        onValueChange = actions.updateRepeatLastN,
        label = "Repeat last N",
        supportingText = "0 = off",
        enabled = presentation.inputsEnabled,
        modifier = Modifier.fillMaxWidth().testTag("playground-repeat-last-n"),
    )

    Text("Seed policy", style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = state.playgroundSeed.isBlank(),
            onClick = { actions.updateSeed("") },
            label = { Text("Random each run") },
            enabled = samplingEnabled,
        )
        FilterChip(
            selected = state.playgroundSeed.isNotBlank(),
            onClick = { if (state.playgroundSeed.isBlank()) actions.updateSeed("42") },
            label = { Text("Fixed") },
            enabled = samplingEnabled,
        )
    }
    if (state.playgroundSeed.isNotBlank()) {
        HarnessNumberField(
            value = state.playgroundSeed,
            onValueChange = actions.updateSeed,
            label = "Seed",
            enabled = samplingEnabled,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Text("Context policy", style = MaterialTheme.typography.labelLarge)
    FilterChip(
        selected = state.playgroundContext.isBlank(),
        onClick = { actions.updateContext("") },
        label = { Text("Auto") },
        enabled = presentation.inputsEnabled,
    )
    HarnessNumberField(
        value = state.playgroundContext,
        onValueChange = actions.updateContext,
        label = "Context size · blank = Auto",
        supportingText = "Manual values are exact; insufficient context fails without truncation.",
        enabled = presentation.inputsEnabled,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PlaygroundRunControls(presentation: PlaygroundPresentation, actions: HarnessPlaygroundActions) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HarnessPrimaryButton(
            text = presentation.runLabel,
            enabled = presentation.runEnabled,
            modifier = Modifier.weight(1f),
            onClick = actions.run,
        )
        if (presentation.stopVisible) {
            HarnessSecondaryButton(
                text = "Stop",
                enabled = presentation.stopEnabled,
                modifier = Modifier.weight(0.62f),
                onClick = actions.cancel,
            )
        }
    }
}
