package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.catalog.CuratedModelCatalog
import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.runtime.UseCaseActivationId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessConsumerCapabilityProfileRegistryTest {
    private val applicationId = ApplicationId("external-consumer")
    private val useCaseId = UseCaseId("configured-use-case")
    private val configured = externalResolved(curatedModel(0))

    @Test
    fun `capability discovery resolves persisted configuration before activation`() {
        val runtimeBindings = HarnessPhoneBindingRegistry()
        var fallbackCalled = false
        val registry = HarnessConsumerCapabilityProfileRegistry(runtimeBindings) { applicationId, useCaseId ->
            fallbackCalled = true
            assertEquals(this.applicationId, applicationId)
            assertEquals(this.useCaseId, useCaseId)
            configured
        }

        assertEquals(configured, registry.resolve(applicationId, useCaseId))
        assertTrue(fallbackCalled)
    }

    @Test
    fun `active runtime binding wins over persisted capability projection`() {
        val runtimeBindings = HarnessPhoneBindingRegistry()
        val active = externalResolved(curatedModel(1))
        runtimeBindings.installActivationBinding(
            activationId = UseCaseActivationId("active-capability"),
            applicationId = applicationId,
            useCaseId = useCaseId,
            resolved = active,
        )
        var fallbackCalled = false
        val registry = HarnessConsumerCapabilityProfileRegistry(runtimeBindings) { _, _ ->
            fallbackCalled = true
            configured
        }

        assertEquals(active, registry.resolve(applicationId, useCaseId))
        assertFalse(fallbackCalled)
    }

    private fun externalResolved(model: ImportedPhoneModel) = resolvedPhonePlaygroundUseCase(model).let { resolved ->
        resolved.copy(
            binding = resolved.binding.copy(
                applicationId = applicationId,
                useCaseId = useCaseId,
            ),
        )
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
}
