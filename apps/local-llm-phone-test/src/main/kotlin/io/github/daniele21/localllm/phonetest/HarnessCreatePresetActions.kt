package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.models.PresetGenerationOverrides

internal data class HarnessCreatePresetActions(
    val onSave: (HarnessPresetSummary, String, String?, Int?, PresetGenerationOverrides?) -> Unit,
    val onReload: () -> Unit,
    val onClearFeedback: () -> Unit,
    val onViewSavedPreset: (String, Int) -> Unit,
    val onDone: () -> Unit,
)
