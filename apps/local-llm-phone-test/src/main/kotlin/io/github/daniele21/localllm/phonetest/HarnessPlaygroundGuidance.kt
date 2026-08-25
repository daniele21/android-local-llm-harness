package io.github.daniele21.localllm.phonetest

import java.util.Locale

internal fun playgroundSettingsValidationMessage(state: HarnessUiState): String? = runCatching {
    PlaygroundRequestOptions.parse(
        PlaygroundRequestFields(
            presetId = state.playgroundPreset,
            maxOutputTokens = state.playgroundMaxTokens,
            temperature = state.playgroundTemperature,
            topP = state.playgroundTopP,
            topK = state.playgroundTopK,
            minP = state.playgroundMinP,
            presencePenalty = state.playgroundPresencePenalty,
            thinkingMode = state.playgroundThinkingMode,
            repeatPenalty = state.playgroundRepeatPenalty,
            repeatLastN = state.playgroundRepeatLastN,
            seed = state.playgroundSeed,
            context = state.playgroundContext,
        ),
    )
}.exceptionOrNull()?.message

internal fun playgroundSamplingGuidance(state: HarnessUiState): String? = if (state.playgroundTemperature.toFloatOrNull() == 0f) {
    "Temperature 0 disables stochastic sampling; Top-p, Top-k and Min-p are inactive."
} else {
    null
}

internal fun playgroundTemperature(state: HarnessUiState): Float =
    state.playgroundTemperature.toFloatOrNull()?.coerceIn(0f, 2f) ?: 0f

internal fun formatPlaygroundControlValue(value: Float): String =
    "%.2f".format(Locale.ROOT, value).trimEnd('0').trimEnd('.')
