package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ModelDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneTestModelsTest {
    @Test
    fun resolvedUseCaseKeepsTheExplicitModelIdentityAndCpuOnlyProfile() {
        val model = testModel()

        val resolved = resolvedPhoneUseCase(model, maxOutputTokens = 32)

        assertEquals(model.digest, resolved.model.artifact.digest)
        assertEquals("qwen3", resolved.model.artifact.architecture)
        assertEquals("Q4_K_M", resolved.model.artifact.quantization)
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
    }

    @Test
    fun playgroundOptionsParseSupportedOverrides() {
        val options = PlaygroundRequestOptions.parse(
            maxOutputTokens = "256",
            temperature = "0.35",
            seed = "123456789",
        )

        assertEquals(256, options.maxOutputTokens)
        assertEquals(0.35f, options.temperature)
        assertEquals(123456789L, options.seed)
    }

    @Test
    fun playgroundOptionsRejectUnsafeOutputBounds() {
        val error = runCatching {
            PlaygroundRequestOptions.parse(
                maxOutputTokens = "513",
                temperature = "0.2",
                seed = "42",
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    private fun testModel(): ImportedPhoneModel = ImportedPhoneModel(
        digest = ModelDigest("0".repeat(64)),
        fileName = "test.gguf",
        sizeBytes = 1234,
        architecture = "qwen3",
        quantization = "Q4_K_M",
    )
}
