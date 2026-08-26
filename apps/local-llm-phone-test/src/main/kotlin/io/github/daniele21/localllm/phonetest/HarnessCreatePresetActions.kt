package io.github.daniele21.localllm.phonetest

internal data class HarnessCreatePresetActions(
    val onSave: (HarnessPresetSummary, String, Boolean, Int?) -> Unit,
    val onReload: () -> Unit,
    val onClearFeedback: () -> Unit,
    val onViewSavedPreset: (String, Int) -> Unit,
    val onDone: () -> Unit,
)
