package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.models.ResolvedUseCase

/**
 * Builds the ephemeral runtime view for one activated control-plane preset.
 *
 * The public preset identity remains consumer-safe while generation/system-prompt policy is copied from the
 * canonical internal inference preset selected by HostExecutionResolver.
 */
internal fun ResolvedUseCase.withActivatedPresetAlias(
    publicPreset: InferencePresetRef,
    canonicalInferencePreset: InferencePresetRef,
): ResolvedUseCase {
    val canonicalPreset = requireNotNull(useCase.presets.singleOrNull { it.ref == canonicalInferencePreset }) {
        "Resolved runtime does not expose inference preset ${canonicalInferencePreset.id.value} v${canonicalInferencePreset.version}"
    }
    val publicAlias = canonicalPreset.copy(ref = publicPreset)
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
