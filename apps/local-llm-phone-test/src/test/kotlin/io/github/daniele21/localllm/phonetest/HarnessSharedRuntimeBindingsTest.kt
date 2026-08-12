package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.catalog.CuratedModelCatalog
import io.github.daniele21.localllm.contracts.UseCaseId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessSharedRuntimeBindingsTest {
    @Test
    fun `release host authorizes only exact release console package`() {
        assertEquals(
            setOf(HarnessSharedRuntimeBindings.CONSOLE_RELEASE_PACKAGE),
            HarnessSharedRuntimeBindings.consolePackages(debugHost = false),
        )
    }

    @Test
    fun `debug host authorizes only exact debug and internal console packages`() {
        val packages = HarnessSharedRuntimeBindings.consolePackages(debugHost = true)

        assertEquals(
            setOf(
                HarnessSharedRuntimeBindings.CONSOLE_DEBUG_PACKAGE,
                HarnessSharedRuntimeBindings.CONSOLE_INTERNAL_PACKAGE,
            ),
            packages,
        )
        assertFalse(HarnessSharedRuntimeBindings.CONSOLE_RELEASE_PACKAGE in packages)
        assertTrue(packages.none { it == "io.github.daniele21.localllm.console.debug.extra" })
    }

    @Test
    fun `external binding requires host-selected curated model`() {
        val registry = HarnessPhoneBindingRegistry()

        assertThrows(IllegalArgumentException::class.java) {
            registry.resolve(
                HarnessSharedRuntimeBindings.consoleApplicationId,
                HarnessSharedRuntimeBindings.consoleUseCaseId,
            )
        }
    }

    @Test
    fun `external binding uses host model and fixed console identity`() {
        val registry = HarnessPhoneBindingRegistry()
        val model = curatedModel()
        registry.selectedModel = model

        val resolved = registry.resolve(
            HarnessSharedRuntimeBindings.consoleApplicationId,
            HarnessSharedRuntimeBindings.consoleUseCaseId,
        )

        assertEquals(HarnessSharedRuntimeBindings.consoleApplicationId, resolved.binding.applicationId)
        assertEquals(HarnessSharedRuntimeBindings.consoleUseCaseId, resolved.binding.useCaseId)
        assertEquals(model.digest, resolved.model.artifact.digest)
    }

    @Test
    fun `external binding rejects unregistered use case`() {
        val registry = HarnessPhoneBindingRegistry()
        registry.selectedModel = curatedModel()

        assertThrows(IllegalArgumentException::class.java) {
            registry.resolve(
                HarnessSharedRuntimeBindings.consoleApplicationId,
                UseCaseId("client-selected-model-profile"),
            )
        }
    }

    private fun curatedModel(): ImportedPhoneModel {
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
