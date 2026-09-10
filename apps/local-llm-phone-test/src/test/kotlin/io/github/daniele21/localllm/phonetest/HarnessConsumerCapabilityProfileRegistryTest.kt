package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.catalog.CuratedModelCatalog
import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ConsumerCapabilityResult
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.contracts.UseCaseReadiness
import io.github.daniele21.localllm.models.GgufArtifact
import io.github.daniele21.localllm.models.HostControlPlaneState
import io.github.daniele21.localllm.models.InMemoryHostControlPlaneStore
import io.github.daniele21.localllm.runtime.ConsumerCapabilityPolicyService
import io.github.daniele21.localllm.runtime.InMemoryConsumerUseCasePolicyRegistry
import io.github.daniele21.localllm.runtime.UseCaseActivationId
import io.github.daniele21.localllm.store.ModelStore
import io.github.daniele21.localllm.store.ModelStoreSnapshot
import io.github.daniele21.localllm.store.StoredModel
import io.github.daniele21.localllm.store.VerificationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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

    @Test
    fun `missing model inventory remains a model readiness failure after configured capability resolution`() {
        val applicationId = HarnessSharedRuntimeBindings.redactGuardApplicationId
        val requirement = HarnessBuiltInApplicationRequirement(
            applicationId = applicationId,
            acceptedPackageNames = setOf(HarnessSharedRuntimeBindings.REDACTGUARD_RELEASE_PACKAGE),
            acceptedSignerSha256 = setOf("0".repeat(64)),
            displayName = "RedactGuard",
        )
        val spec = HarnessBuiltInControlPlaneSpec.ombra(listOf(requirement))
        val reconciled = HarnessControlPlaneReconciler(spec).reconcile(HostControlPlaneState(), observedAtEpochMs = 0L)
        assertTrue(reconciled is HarnessControlPlaneReconciliationResult.Success)
        val store = InMemoryHostControlPlaneStore((reconciled as HarnessControlPlaneReconciliationResult.Success).state)
        val configuredResolver = HarnessConfiguredConsumerProfileResolver(store, CapabilityEmptyModelStore)
        val service = ConsumerCapabilityPolicyService(
            profileRegistry = HarnessConsumerCapabilityProfileRegistry(
                activeBindings = HarnessPhoneBindingRegistry(),
                configuredResolver = configuredResolver::resolve,
            ),
            modelStore = CapabilityEmptyModelStore,
            policyRegistry = InMemoryConsumerUseCasePolicyRegistry(
                listOf(HarnessOmbraConsumerPolicy.create(applicationId)),
            ),
        )

        val result = service.discover(applicationId, spec.useCase.useCaseId)

        assertTrue(result is ConsumerCapabilityResult.Available)
        assertEquals(
            UseCaseReadiness.UNAVAILABLE_MODEL,
            (result as ConsumerCapabilityResult.Available).capabilities.readiness,
        )
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

private object CapabilityEmptyModelStore : ModelStore {
    override fun find(digest: ModelDigest): StoredModel? = null

    override fun snapshot(): ModelStoreSnapshot = ModelStoreSnapshot(
        modelCount = 0,
        totalBytes = 0L,
        entries = emptyList(),
    )

    override fun import(source: File, artifact: GgufArtifact): StoredModel =
        error("Capability discovery must not import model bytes")

    override fun verify(digest: ModelDigest): VerificationResult =
        error("Capability discovery must not verify absent model bytes")

    override fun remove(digest: ModelDigest): Boolean =
        error("Capability discovery must not remove model bytes")
}
