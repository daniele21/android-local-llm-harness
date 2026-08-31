package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.catalog.CuratedModelCatalog
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.models.PresetGenerationOverrides
import io.github.daniele21.localllm.models.PresetSeedMode
import io.github.daniele21.localllm.models.ThinkingMode
import org.junit.Assert.assertEquals
import org.junit.Test

class HarnessPresetGenerationOverrideActivationTest {
    @Test
    fun `activated public preset applies persisted generation overrides over canonical profile`() {
        val artifact = CuratedModelCatalog.releases.first().artifact
        val imported = ImportedPhoneModel(
            digest = artifact.digest,
            fileName = artifact.fileName,
            sizeBytes = artifact.sizeBytes,
            architecture = artifact.architecture,
            quantization = artifact.quantization,
        )
        val base = HarnessSharedRuntimeBindings.resolveOmbra(
            imported,
            HarnessSharedRuntimeBindings.redactGuardApplicationId,
        )
        val publicPreset = InferencePresetRef(InferencePresetId("custom-runtime-preset"), 1)

        val resolved = base.withActivatedPresetAlias(
            publicPreset = publicPreset,
            canonicalInferencePreset = HarnessSharedRuntimeBindings.ombraDefaultPreset,
            generationOverrides = PresetGenerationOverrides(
                maxOutputTokens = 321,
                temperature = 0.25f,
                topP = 0.75f,
                topK = 18,
                thinkingMode = ThinkingMode.ENABLED,
                seedMode = PresetSeedMode.FIXED,
                fixedSeed = 123L,
            ),
        )

        val generation = resolved.useCase.presets.single().generation
        assertEquals(321, generation.maxOutputTokens)
        assertEquals(0.25f, generation.temperature)
        assertEquals(0.75f, generation.topP)
        assertEquals(18, generation.topK)
        assertEquals(ThinkingMode.ENABLED, generation.thinkingMode)
        assertEquals(123L, generation.seed)
    }
}
