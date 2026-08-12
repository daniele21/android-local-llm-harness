package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.catalog.CuratedModelCatalog
import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.UseCaseId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HarnessPhoneBindingRegistryTest {
    private val model = curatedModel(0)

    @Test
    fun `resolves playground and validation from the selected curated Qwen35 model`() {
        val registry = HarnessPhoneBindingRegistry()
        registry.selectedModel = model

        val playground = registry.resolve(APPLICATION_ID, HarnessRuntimePurpose.PLAYGROUND.useCaseId)
        val validation = registry.resolve(APPLICATION_ID, HarnessRuntimePurpose.PHYSICAL_VALIDATION.useCaseId)

        assertEquals(model.digest, playground.model.artifact.digest)
        assertEquals(model.digest, validation.model.artifact.digest)
        assertEquals("qwen35", playground.model.artifact.architecture)
        assertEquals("manual-inference-playground", playground.binding.useCaseId.value)
        assertEquals("physical-device-validation", validation.binding.useCaseId.value)
    }

    @Test
    fun `uses the latest selected curated model for both purposes`() {
        val registry = HarnessPhoneBindingRegistry()
        val replacement = curatedModel(1)

        registry.selectedModel = model
        registry.selectedModel = replacement

        val playground = registry.resolve(APPLICATION_ID, HarnessRuntimePurpose.PLAYGROUND.useCaseId)
        val validation = registry.resolve(APPLICATION_ID, HarnessRuntimePurpose.PHYSICAL_VALIDATION.useCaseId)

        assertEquals(replacement.digest, playground.model.artifact.digest)
        assertEquals(replacement.digest, validation.model.artifact.digest)
    }

    @Test
    fun `rejects a model that is not an exact curated artifact`() {
        val registry = HarnessPhoneBindingRegistry()
        val unsupported = model.copy(
            digest = ModelDigest("f".repeat(64)),
            fileName = "arbitrary.gguf",
        )

        assertThrows(IllegalArgumentException::class.java) {
            registry.selectedModel = unsupported
        }
    }

    @Test
    fun `requires a selected model`() {
        val registry = HarnessPhoneBindingRegistry()

        assertThrows(IllegalArgumentException::class.java) {
            registry.resolve(APPLICATION_ID, HarnessRuntimePurpose.PLAYGROUND.useCaseId)
        }
    }

    @Test
    fun `clearing selected model removes the binding`() {
        val registry = HarnessPhoneBindingRegistry()
        registry.selectedModel = model

        registry.selectedModel = null

        assertThrows(IllegalArgumentException::class.java) {
            registry.resolve(APPLICATION_ID, HarnessRuntimePurpose.PLAYGROUND.useCaseId)
        }
    }

    @Test
    fun `rejects unknown application and use case ids`() {
        val registry = HarnessPhoneBindingRegistry()
        registry.selectedModel = model

        assertThrows(IllegalArgumentException::class.java) {
            registry.resolve(ApplicationId("other-app"), HarnessRuntimePurpose.PLAYGROUND.useCaseId)
        }
        assertThrows(IllegalStateException::class.java) {
            registry.resolve(APPLICATION_ID, UseCaseId("unknown"))
        }
    }

    private fun curatedModel(index: Int): ImportedPhoneModel {
        val artifact = CuratedModelCatalog.releases[index].artifact
        return ImportedPhoneModel(
            digest = artifact.digest,
            fileName = artifact.fileName,
            sizeBytes = artifact.sizeBytes,
            architecture = artifact.architecture,
            quantization = artifact.quantization,
        )
    }

    private companion object {
        val APPLICATION_ID = ApplicationId("play-internal-phone-test")
    }
}
