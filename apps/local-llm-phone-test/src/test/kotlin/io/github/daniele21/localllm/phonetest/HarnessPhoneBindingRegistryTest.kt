package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.UseCaseId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HarnessPhoneBindingRegistryTest {
    private val model = ImportedPhoneModel(
        digest = ModelDigest("a".repeat(64)),
        fileName = "model.gguf",
        sizeBytes = 1_024,
        architecture = "qwen3",
        quantization = "Q4_K_M",
    )

    @Test
    fun `resolves playground and validation from the selected model`() {
        val registry = HarnessPhoneBindingRegistry()
        registry.select(model)

        val playground = registry.resolve(APPLICATION_ID, HarnessRuntimePurpose.PLAYGROUND.useCaseId)
        val validation = registry.resolve(APPLICATION_ID, HarnessRuntimePurpose.PHYSICAL_VALIDATION.useCaseId)

        assertEquals(model.digest, playground.model.artifact.digest)
        assertEquals(model.digest, validation.model.artifact.digest)
        assertEquals("manual-inference-playground", playground.binding.useCaseId.value)
        assertEquals("physical-device-validation", validation.binding.useCaseId.value)
    }

    @Test
    fun `uses the latest selected model for both purposes`() {
        val registry = HarnessPhoneBindingRegistry()
        val replacement = model.copy(
            digest = ModelDigest("b".repeat(64)),
            fileName = "replacement.gguf",
        )

        registry.select(model)
        registry.select(replacement)

        val playground = registry.resolve(APPLICATION_ID, HarnessRuntimePurpose.PLAYGROUND.useCaseId)
        val validation = registry.resolve(APPLICATION_ID, HarnessRuntimePurpose.PHYSICAL_VALIDATION.useCaseId)

        assertEquals(replacement.digest, playground.model.artifact.digest)
        assertEquals(replacement.digest, validation.model.artifact.digest)
    }

    @Test
    fun `requires a selected model`() {
        val registry = HarnessPhoneBindingRegistry()

        assertThrows(IllegalArgumentException::class.java) {
            registry.resolve(APPLICATION_ID, HarnessRuntimePurpose.PLAYGROUND.useCaseId)
        }
    }

    @Test
    fun `clear removes the selected model`() {
        val registry = HarnessPhoneBindingRegistry()
        registry.select(model)

        registry.clear()

        assertThrows(IllegalArgumentException::class.java) {
            registry.resolve(APPLICATION_ID, HarnessRuntimePurpose.PLAYGROUND.useCaseId)
        }
    }

    @Test
    fun `rejects unknown application and use case ids`() {
        val registry = HarnessPhoneBindingRegistry()
        registry.select(model)

        assertThrows(IllegalArgumentException::class.java) {
            registry.resolve(ApplicationId("other-app"), HarnessRuntimePurpose.PLAYGROUND.useCaseId)
        }
        assertThrows(IllegalStateException::class.java) {
            registry.resolve(APPLICATION_ID, UseCaseId("unknown"))
        }
    }

    private companion object {
        val APPLICATION_ID = ApplicationId("play-internal-phone-test")
    }
}
