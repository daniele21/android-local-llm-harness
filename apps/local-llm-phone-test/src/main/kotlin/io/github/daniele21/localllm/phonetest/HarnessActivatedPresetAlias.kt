package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.models.PresetGenerationOverrides
import io.github.daniele21.localllm.models.ResolvedUseCase
import io.github.daniele21.localllm.models.withPresetOverrides

/**
 * Builds the ephemeral runtime view for one activated control-plane preset.
 *
 * The public preset identity remains consumer-safe while generation/system-prompt policy is copied from the
 * canonical internal inference preset selected by HostExecutionResolver. Custom generation overrides are
 * layered on that immutable profile only for the activated public preset.
 */
internal fun ResolvedUseCase.withActivatedPresetAlias(
    publicPreset: InferencePresetRef,
    canonicalInferencePreset: InferencePresetRef,
    generationOverrides: PresetGenerationOverrides? = null,
): ResolvedUseCase {
    val canonicalPreset = checkNotNull(useCase.presets.singleOrNull { it.ref == canonicalInferencePreset }) {
        "Resolved runtime does not expose inference preset ${canonicalInferencePreset.id.value} v${canonicalInferencePreset.version}"
    }
    val publicAlias = canonicalPreset.copy(
        ref = publicPreset,
        generation = canonicalPreset.generation.withPresetOverrides(generationOverrides),
    )
    return copy(
        useCase = useCase.copy(
            generationDefaults = publicAlias.generation,
            systemPromptVersion = publicAlias.systemPromptVersion,
            systemPrompt = publicAlias.systemPrompt,
            presets = listOf(publicAlias),
            defaultPreset = publicPreset,
        ),
    )
}
