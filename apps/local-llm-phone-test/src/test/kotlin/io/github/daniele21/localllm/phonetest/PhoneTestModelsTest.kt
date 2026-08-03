package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ModelDigest
import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneTestModelsTest {
    @Test
    fun resolvedUseCaseKeepsTheExplicitModelIdentityAndCpuOnlyProfile() {
        val model = ImportedPhoneModel(
            digest = ModelDigest("0".repeat(64)),
            fileName = "test.gguf",
            sizeBytes = 1234,
            architecture = "qwen3",
            quantization = "Q4_K_M",
        )

        val resolved = resolvedPhoneUseCase(model, maxOutputTokens = 32)

        assertEquals(model.digest, resolved.model.artifact.digest)
        assertEquals("qwen3", resolved.model.artifact.architecture)
        assertEquals("Q4_K_M", resolved.model.artifact.quantization)
        assertEquals(32, resolved.useCase.generationDefaults.maxOutputTokens)
        assertEquals(0, resolved.model.gpuLayers)
    }
}
