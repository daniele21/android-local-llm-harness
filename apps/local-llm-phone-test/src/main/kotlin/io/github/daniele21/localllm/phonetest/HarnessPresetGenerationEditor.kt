@file:Suppress("FunctionName")

package io.github.daniele21.localllm.phonetest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.daniele21.localllm.models.PresetGenerationOverrides
import io.github.daniele21.localllm.models.PresetSeedMode
import io.github.daniele21.localllm.models.ThinkingMode
import io.github.daniele21.localllm.ui.designsystem.HarnessCard
import io.github.daniele21.localllm.ui.designsystem.HarnessNumberField
import io.github.daniele21.localllm.ui.designsystem.HarnessNumberInputMode
import io.github.daniele21.localllm.ui.designsystem.HarnessSecondaryButton
import io.github.daniele21.localllm.ui.designsystem.LocalHarnessSpacing

internal data class HarnessPresetGenerationDraft(
    val maxOutputTokens: String = "",
    val temperature: String = "",
    val topP: String = "",
    val topK: String = "",
    val minP: String = "",
    val presencePenalty: String = "",
    val repeatPenalty: String = "",
    val repeatLastN: String = "",
    val thinkingMode: ThinkingMode? = null,
    val seedMode: PresetSeedMode = PresetSeedMode.INHERIT,
    val fixedSeed: String = "",
) {
    val hasOverrides: Boolean
        get() = maxOutputTokens.isNotBlank() ||
            temperature.isNotBlank() ||
            topP.isNotBlank() ||
            topK.isNotBlank() ||
            minP.isNotBlank() ||
            presencePenalty.isNotBlank() ||
            repeatPenalty.isNotBlank() ||
            repeatLastN.isNotBlank() ||
            thinkingMode != null ||
            seedMode != PresetSeedMode.INHERIT

    fun overridesResult(): Result<PresetGenerationOverrides?> = runCatching {
        if (seedMode == PresetSeedMode.FIXED && fixedSeed.isBlank()) {
            error("Enter a fixed seed or choose Base/Random.")
        }
        PresetGenerationOverrides(
            maxOutputTokens = maxOutputTokens.parseOptionalInt("Maximum output tokens"),
            temperature = temperature.parseOptionalFloat("Temperature"),
            topP = topP.parseOptionalFloat("Top-p"),
            topK = topK.parseOptionalInt("Top-k"),
            minP = minP.parseOptionalFloat("Min-p"),
            presencePenalty = presencePenalty.parseOptionalFloat("Presence penalty"),
            repeatPenalty = repeatPenalty.parseOptionalFloat("Repeat penalty"),
            repeatLastN = repeatLastN.parseOptionalInt("Repeat window"),
            thinkingMode = thinkingMode,
            seedMode = seedMode,
            fixedSeed = if (seedMode == PresetSeedMode.FIXED) fixedSeed.parseOptionalLong("Seed") else null,
        ).takeUnless(PresetGenerationOverrides::isEmpty)
    }

    companion object {
        val Saver: Saver<HarnessPresetGenerationDraft, Any> = listSaver(
            save = { draft ->
                listOf(
                    draft.maxOutputTokens,
                    draft.temperature,
                    draft.topP,
                    draft.topK,
                    draft.minP,
                    draft.presencePenalty,
                    draft.repeatPenalty,
                    draft.repeatLastN,
                    draft.thinkingMode?.name.orEmpty(),
                    draft.seedMode.name,
                    draft.fixedSeed,
                )
            },
            restore = { values ->
                HarnessPresetGenerationDraft(
                    maxOutputTokens = values[0] as String,
                    temperature = values[1] as String,
                    topP = values[2] as String,
                    topK = values[3] as String,
                    minP = values[4] as String,
                    presencePenalty = values[5] as String,
                    repeatPenalty = values[6] as String,
                    repeatLastN = values[7] as String,
                    thinkingMode = (values[8] as String).takeIf(String::isNotBlank)?.let(ThinkingMode::valueOf),
                    seedMode = PresetSeedMode.valueOf(values[9] as String),
                    fixedSeed = values[10] as String,
                )
            },
        )
    }
}

@Composable
internal fun rememberHarnessPresetGenerationDraft(vararg keys: Any?): HarnessPresetGenerationDraftState {
    var draft by rememberSaveable(*keys, stateSaver = HarnessPresetGenerationDraft.Saver) {
        mutableStateOf(HarnessPresetGenerationDraft())
    }
    return HarnessPresetGenerationDraftState(draft = draft, update = { draft = it })
}

internal data class HarnessPresetGenerationDraftState(
    val draft: HarnessPresetGenerationDraft,
    val update: (HarnessPresetGenerationDraft) -> Unit,
)

@Composable
internal fun HarnessPresetGenerationEditor(
    draft: HarnessPresetGenerationDraft,
    enabled: Boolean,
    onDraftChanged: (HarnessPresetGenerationDraft) -> Unit,
) {
    var advancedVisible by rememberSaveable { mutableStateOf(false) }
    val spacing = LocalHarnessSpacing.current
    val validation = draft.overridesResult()

    HarnessCard(modifier = Modifier.testTag("custom-preset-generation-editor")) {
        Text("Generation parameters", style = MaterialTheme.typography.titleMedium)
        Text(
            "Blank values inherit the selected base preset. Enter a value only when this custom preset should override it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HarnessNumberField(
            value = draft.maxOutputTokens,
            onValueChange = { onDraftChanged(draft.copy(maxOutputTokens = it)) },
            label = "Max output tokens",
            enabled = enabled,
            isError = !draft.maxOutputTokens.validInt { it > 0 },
            supportingText = "Positive whole number · blank uses base",
            modifier = Modifier.fillMaxWidth().testTag("custom-preset-max-output-tokens"),
        )
        HarnessThinkingOverride(draft, enabled, onDraftChanged)
        HarnessNumberField(
            value = draft.temperature,
            onValueChange = { onDraftChanged(draft.copy(temperature = it)) },
            label = "Temperature",
            mode = HarnessNumberInputMode.DECIMAL,
            enabled = enabled,
            isError = !draft.temperature.validFloat { it in 0f..2f },
            supportingText = "0–2 · blank uses base",
            modifier = Modifier.fillMaxWidth(),
        )
        HarnessNumberField(
            value = draft.topP,
            onValueChange = { onDraftChanged(draft.copy(topP = it)) },
            label = "Top-p",
            mode = HarnessNumberInputMode.DECIMAL,
            enabled = enabled,
            isError = !draft.topP.validFloat { it > 0f && it <= 1f },
            supportingText = ">0–1 · blank uses base",
            modifier = Modifier.fillMaxWidth(),
        )

        HarnessSecondaryButton(
            text = if (advancedVisible) "Advanced settings · Hide" else "Advanced settings · Show",
            enabled = enabled,
            onClick = { advancedVisible = !advancedVisible },
        )
        if (advancedVisible) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                HarnessNumberField(
                    value = draft.topK,
                    onValueChange = { onDraftChanged(draft.copy(topK = it)) },
                    label = "Top-k",
                    enabled = enabled,
                    isError = !draft.topK.validInt { it in 0..1_000 },
                    supportingText = "0–1000 · blank uses base",
                    modifier = Modifier.fillMaxWidth(),
                )
                HarnessNumberField(
                    value = draft.minP,
                    onValueChange = { onDraftChanged(draft.copy(minP = it)) },
                    label = "Min-p",
                    mode = HarnessNumberInputMode.DECIMAL,
                    enabled = enabled,
                    isError = !draft.minP.validFloat { it in 0f..1f },
                    supportingText = "0–1 · blank uses base",
                    modifier = Modifier.fillMaxWidth(),
                )
                HarnessNumberField(
                    value = draft.presencePenalty,
                    onValueChange = { onDraftChanged(draft.copy(presencePenalty = it)) },
                    label = "Presence penalty",
                    mode = HarnessNumberInputMode.DECIMAL,
                    enabled = enabled,
                    isError = !draft.presencePenalty.validFloat { it in 0f..2f },
                    supportingText = "0–2 · blank uses base",
                    modifier = Modifier.fillMaxWidth(),
                )
                HarnessNumberField(
                    value = draft.repeatPenalty,
                    onValueChange = { onDraftChanged(draft.copy(repeatPenalty = it)) },
                    label = "Repeat penalty",
                    mode = HarnessNumberInputMode.DECIMAL,
                    enabled = enabled,
                    isError = !draft.repeatPenalty.validFloat { it in 1f..2f },
                    supportingText = "1–2 · blank uses base",
                    modifier = Modifier.fillMaxWidth(),
                )
                HarnessNumberField(
                    value = draft.repeatLastN,
                    onValueChange = { onDraftChanged(draft.copy(repeatLastN = it)) },
                    label = "Repeat last N",
                    enabled = enabled,
                    isError = !draft.repeatLastN.validInt { it in 0..4_096 },
                    supportingText = "0–4096 · blank uses base",
                    modifier = Modifier.fillMaxWidth(),
                )
                HarnessSeedOverride(draft, enabled, onDraftChanged)
            }
        }

        if (validation.isFailure) {
            Text(
                validation.exceptionOrNull()?.message ?: "Review generation parameters.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("custom-preset-generation-error"),
            )
        }
        if (draft.hasOverrides) {
            HarnessSecondaryButton(
                text = "Reset generation overrides",
                enabled = enabled,
                onClick = { onDraftChanged(HarnessPresetGenerationDraft()) },
            )
        }
    }
}

@Composable
private fun HarnessThinkingOverride(
    draft: HarnessPresetGenerationDraft,
    enabled: Boolean,
    onDraftChanged: (HarnessPresetGenerationDraft) -> Unit,
) {
    Text("Thinking", style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(LocalHarnessSpacing.current.small)) {
        FilterChip(
            selected = draft.thinkingMode == null,
            onClick = { onDraftChanged(draft.copy(thinkingMode = null)) },
            label = { Text("Base") },
            enabled = enabled,
        )
        FilterChip(
            selected = draft.thinkingMode == ThinkingMode.DISABLED,
            onClick = { onDraftChanged(draft.copy(thinkingMode = ThinkingMode.DISABLED)) },
            label = { Text("Off") },
            enabled = enabled,
        )
        FilterChip(
            selected = draft.thinkingMode == ThinkingMode.ENABLED,
            onClick = { onDraftChanged(draft.copy(thinkingMode = ThinkingMode.ENABLED)) },
            label = { Text("On") },
            enabled = enabled,
        )
    }
}

@Composable
private fun HarnessSeedOverride(
    draft: HarnessPresetGenerationDraft,
    enabled: Boolean,
    onDraftChanged: (HarnessPresetGenerationDraft) -> Unit,
) {
    Text("Seed policy", style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(LocalHarnessSpacing.current.small)) {
        FilterChip(
            selected = draft.seedMode == PresetSeedMode.INHERIT,
            onClick = { onDraftChanged(draft.copy(seedMode = PresetSeedMode.INHERIT, fixedSeed = "")) },
            label = { Text("Base") },
            enabled = enabled,
        )
        FilterChip(
            selected = draft.seedMode == PresetSeedMode.RANDOM,
            onClick = { onDraftChanged(draft.copy(seedMode = PresetSeedMode.RANDOM, fixedSeed = "")) },
            label = { Text("Random") },
            enabled = enabled,
        )
        FilterChip(
            selected = draft.seedMode == PresetSeedMode.FIXED,
            onClick = { onDraftChanged(draft.copy(seedMode = PresetSeedMode.FIXED)) },
            label = { Text("Fixed") },
            enabled = enabled,
        )
    }
    if (draft.seedMode == PresetSeedMode.FIXED) {
        HarnessNumberField(
            value = draft.fixedSeed,
            onValueChange = { onDraftChanged(draft.copy(fixedSeed = it)) },
            label = "Fixed seed",
            enabled = enabled,
            isError = draft.fixedSeed.isBlank() || draft.fixedSeed.toLongOrNull() == null,
            supportingText = "Required for Fixed seed policy",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun String.parseOptionalInt(label: String): Int? =
    takeIf(String::isNotBlank)?.toIntOrNull() ?: takeIf(String::isNotBlank)?.let { error("$label is too large.") }

private fun String.parseOptionalLong(label: String): Long? =
    takeIf(String::isNotBlank)?.toLongOrNull() ?: takeIf(String::isNotBlank)?.let { error("$label is too large.") }

private fun String.parseOptionalFloat(label: String): Float? =
    takeIf(String::isNotBlank)?.toFloatOrNull() ?: takeIf(String::isNotBlank)?.let { error("$label is invalid.") }

private fun String.validInt(predicate: (Int) -> Boolean): Boolean = isBlank() || toIntOrNull()?.let(predicate) == true

private fun String.validFloat(predicate: (Float) -> Boolean): Boolean = isBlank() || toFloatOrNull()?.let(predicate) == true
