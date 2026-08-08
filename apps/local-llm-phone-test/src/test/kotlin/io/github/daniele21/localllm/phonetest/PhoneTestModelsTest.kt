package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.catalog.CuratedModelCatalog
import io.github.daniele21.localllm.contracts.SeedPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneTestModelsTest {
    @Test
    fun resolvedUseCaseKeepsTheExplicitModelIdentityAndCpuOnlyProfile() {
        val model = testModel()

        val resolved = resolvedPhoneUseCase(model, maxOutputTokens = 32)

        assertEquals(model.digest, resolved.model.artifact.digest)
        assertEquals("qwen35", resolved.model.artifact.architecture)
        assertEquals(model.quantization, resolved.model.artifact.quantization)
        assertEquals(32, resolved.useCase.generationDefaults.maxOutputTokens)
        assertEquals(0, resolved.model.gpuLayers)
    }

    @Test
    fun playgroundProfileUsesAnExplicitTargetAndLargerContext() {
        val resolved = resolvedPhonePlaygroundUseCase(testModel())

        assertEquals("play-internal-phone-test", resolved.binding.applicationId.value)
        assertEquals("manual-inference-playground", resolved.binding.useCaseId.value)
        assertEquals(128, resolved.useCase.generationDefaults.maxOutputTokens)
        assertEquals(2048, resolved.model.contextSize)
        assertEquals(0, resolved.model.gpuLayers)
        assertEquals(listOf("<|user|>", "<|system|>"), resolved.model.chatTemplatePolicy.stopSequences)
        val precise = resolved.useCase.presets.first { it.ref.id.value == "precise-structured" }
        assertEquals(2_048, precise.contextPreference.preferredTokens)
        assertEquals(4_096, precise.contextPreference.recommendedMaximumTokens)
        assertEquals(null, precise.contextPreference.maximumTokens)
        val balanced = resolved.useCase.presets.first { it.ref.id.value == "balanced-conversation" }
        assertEquals(PHONE_INFERENCE_PRESET_VERSION, balanced.ref.version)
        assertEquals(1.05f, balanced.generation.repeatPenalty)
        assertEquals(64, balanced.generation.repeatLastN)
        assertEquals(4_096, balanced.contextPreference.preferredTokens)
        assertEquals(8_192, balanced.contextPreference.recommendedMaximumTokens)
    }

    @Test
    fun playgroundOptionsParseSupportedOverrides() {
        val options = PlaygroundRequestOptions.parse(
            PlaygroundRequestFields(
                presetId = "",
                maxOutputTokens = "256",
                temperature = "0.35",
                topP = "0.9",
                topK = "40",
                repeatPenalty = "1.1",
                repeatLastN = "96",
                seed = "123456789",
                context = "4096",
            ),
        )

        assertEquals(256, options.maxOutputTokens)
        assertEquals(0.35f, options.temperature)
        assertEquals(1.1f, options.repeatPenalty)
        assertEquals(96, options.repeatLastN)
        assertEquals(SeedPolicy.Fixed(123456789), options.seedPolicy)
        assertEquals(4096, options.contextTokens)
    }

    @Test
    fun playgroundOptionsRejectUnsafeOutputBounds() {
        val error = runCatching {
            PlaygroundRequestOptions.parse(
                PlaygroundRequestFields(
                    presetId = "",
                    maxOutputTokens = "32769",
                    temperature = "0.2",
                    topP = "0.9",
                    topK = "40",
                    repeatPenalty = "1.05",
                    repeatLastN = "64",
                    seed = "42",
                    context = "",
                ),
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun playgroundOptionsRejectEnabledPenaltyWithoutARepeatWindow() {
        val error = runCatching {
            PlaygroundRequestOptions.parse(
                PlaygroundRequestFields(
                    presetId = "",
                    maxOutputTokens = "64",
                    temperature = "0.7",
                    topP = "0.8",
                    topK = "20",
                    repeatPenalty = "1.05",
                    repeatLastN = "0",
                    seed = "",
                    context = "",
                ),
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    private fun testModel(): ImportedPhoneModel {
        val artifact = CuratedModelCatalog.releases.first().artifact
        return ImportedPhoneModel(
            digest = artifact.digest,
            fileName = artifact.fileName,
            sizeBytes = artifact.sizeBytes,
            architecture = artifact.architecture,
            quantization = artifact.quantization,
        )
    }
}
