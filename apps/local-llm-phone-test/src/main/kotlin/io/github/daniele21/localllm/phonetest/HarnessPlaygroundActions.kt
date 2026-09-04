package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ThinkingMode

internal data class HarnessPlaygroundActions(
    val openModels: () -> Unit,
    val updatePrompt: (String) -> Unit,
    val updatePreset: (String) -> Unit,
    val updateThinkingMode: (ThinkingMode) -> Unit,
    val updateTemperature: (String) -> Unit,
    val updateTopP: (String) -> Unit,
    val updateMaxTokens: (String) -> Unit,
    val updateTopK: (String) -> Unit,
    val updateMinP: (String) -> Unit,
    val updatePresencePenalty: (String) -> Unit,
    val updateRepeatPenalty: (String) -> Unit,
    val updateRepeatLastN: (String) -> Unit,
    val updateSeed: (String) -> Unit,
    val updateContext: (String) -> Unit,
    val run: () -> Unit,
    val cancel: () -> Unit,
)
