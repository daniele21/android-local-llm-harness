package io.github.daniele21.localllm.phonetest

internal data class HarnessApplicationsGraphCallbacks(
    val onRefresh: () -> Unit,
    val onSetDefaultPreset: (String, HarnessAssignmentSummary, HarnessPresetSummary) -> Unit,
    val onCreateCustomPreset: (String, HarnessAssignmentSummary, HarnessPresetSummary, String, Boolean, Int?) -> Unit,
    val onClearMutationFeedback: () -> Unit,
)
