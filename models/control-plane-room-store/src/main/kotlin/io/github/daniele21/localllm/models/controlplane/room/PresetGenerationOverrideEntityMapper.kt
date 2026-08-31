package io.github.daniele21.localllm.models.controlplane.room

import io.github.daniele21.localllm.contracts.ThinkingMode
import io.github.daniele21.localllm.models.PresetGenerationOverrides
import io.github.daniele21.localllm.models.PresetSeedMode

internal fun HostControlPlaneEntities.PresetEntity.toGenerationOverrides(): PresetGenerationOverrides? {
    if (!hasGenerationOverrides()) return null
    return PresetGenerationOverrides(
        maxOutputTokens = generationMaxOutputTokens,
        temperature = generationTemperature,
        topP = generationTopP,
        topK = generationTopK,
        minP = generationMinP,
        presencePenalty = generationPresencePenalty,
        repeatPenalty = generationRepeatPenalty,
        repeatLastN = generationRepeatLastN,
        thinkingMode = generationThinkingMode?.let(ThinkingMode::valueOf),
        seedMode = generationSeedMode?.let(PresetSeedMode::valueOf) ?: PresetSeedMode.INHERIT,
        fixedSeed = generationFixedSeed,
    )
}

private fun HostControlPlaneEntities.PresetEntity.hasGenerationOverrides(): Boolean = listOf(
    generationMaxOutputTokens,
    generationTemperature,
    generationTopP,
    generationTopK,
    generationMinP,
    generationPresencePenalty,
    generationRepeatPenalty,
    generationRepeatLastN,
    generationThinkingMode,
    generationSeedMode,
    generationFixedSeed,
).any { it != null }
