package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.catalog.CuratedModelCatalog
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.runtime.UseCaseActivationId
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
    fun `release host external clients include console redactguard and packaged evidence consumer`() {
        assertEquals(
            setOf(
                HarnessSharedRuntimeBindings.CONSOLE_RELEASE_PACKAGE,
                HarnessSharedRuntimeBindings.REDACTGUARD_RELEASE_PACKAGE,
                HarnessSharedRuntimeBindings.SR6_RELEASE_CONSUMER_PACKAGE,
            ),
            HarnessSharedRuntimeBindings.externalClientPackages(debugHost = false),
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
    fun `debug host external clients include exact redactguard debug identity without release evidence consumer`() {
        val packages = HarnessSharedRuntimeBindings.externalClientPackages(debugHost = true)

        assertEquals(
            HarnessSharedRuntimeBindings.consolePackages(debugHost = true) +
                HarnessSharedRuntimeBindings.redactGuardPackages(debugHost = true),
            packages,
        )
        assertFalse(HarnessSharedRuntimeBindings.SR6_RELEASE_CONSUMER_PACKAGE in packages)
        assertFalse(HarnessSharedRuntimeBindings.REDACTGUARD_RELEASE_PACKAGE in packages)
    }

    @Test
    fun `external binding requires explicit control-plane activation even with selected model`() {
        val registry = HarnessPhoneBindingRegistry()
        registry.selectedModel = curatedModel()

        val failure = assertThrows(IllegalStateException::class.java) {
            registry.resolve(
                HarnessSharedRuntimeBindings.consoleApplicationId,
                HarnessSharedRuntimeBindings.consoleUseCaseId,
            )
        }

        assertTrue(failure.message?.contains("control-plane activation") == true)
    }

    @Test
    fun `external binding uses activation-owned model and fixed console identity`() {
        val registry = HarnessPhoneBindingRegistry()
        val activationModel = curatedModel(index = 0)
        val selectedModel = curatedModel(index = 1)
        val resolvedActivation = HarnessSharedRuntimeBindings.resolveConsole(activationModel)
        registry.selectedModel = selectedModel
        registry.installActivationBinding(
            activationId = UseCaseActivationId("console-activation"),
            applicationId = HarnessSharedRuntimeBindings.consoleApplicationId,
            useCaseId = HarnessSharedRuntimeBindings.consoleUseCaseId,
            resolved = resolvedActivation,
        )

        val resolved = registry.resolve(
            HarnessSharedRuntimeBindings.consoleApplicationId,
            HarnessSharedRuntimeBindings.consoleUseCaseId,
        )

        assertEquals(HarnessSharedRuntimeBindings.consoleApplicationId, resolved.binding.applicationId)
        assertEquals(HarnessSharedRuntimeBindings.consoleUseCaseId, resolved.binding.useCaseId)
        assertEquals(activationModel.digest, resolved.model.artifact.digest)
        assertFalse(selectedModel.digest == resolved.model.artifact.digest)
    }

    @Test
    fun `external binding rejects use case without matching activation`() {
        val registry = HarnessPhoneBindingRegistry()
        registry.selectedModel = curatedModel()

        val failure = assertThrows(IllegalStateException::class.java) {
            registry.resolve(
                HarnessSharedRuntimeBindings.consoleApplicationId,
                UseCaseId("client-selected-model-profile"),
            )
        }

        assertTrue(failure.message?.contains("control-plane activation") == true)
    }

    private fun curatedModel(index: Int = 0): ImportedPhoneModel {
        val artifact = CuratedModelCatalog.releases[index].artifact
        return ImportedPhoneModel(
            digest = artifact.digest,
            fileName = artifact.fileName,
            sizeBytes = artifact.sizeBytes,
            architecture = artifact.architecture,
            quantization = artifact.quantization,
        )
    }
}
