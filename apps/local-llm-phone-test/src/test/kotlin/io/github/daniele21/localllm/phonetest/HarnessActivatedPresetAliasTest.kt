package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.catalog.CuratedModelCatalog
import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ConsumerActivationRequest
import io.github.daniele21.localllm.contracts.ConsumerActivationResult
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.models.HostControlPlaneState
import io.github.daniele21.localllm.models.InMemoryHostControlPlaneStore
import io.github.daniele21.localllm.models.PresetConsumerMetadata
import io.github.daniele21.localllm.models.PresetCreationSource
import io.github.daniele21.localllm.models.PresetLifecycleState
import io.github.daniele21.localllm.models.ResolvedUseCase
import io.github.daniele21.localllm.models.StoredPresetExposure
import io.github.daniele21.localllm.models.UseCasePresetDefinition
import io.github.daniele21.localllm.runtime.ActivationIdFactory
import io.github.daniele21.localllm.runtime.ActivationResidencyCoordinator
import io.github.daniele21.localllm.runtime.InMemoryConsumerUseCasePolicyRegistry
import io.github.daniele21.localllm.runtime.UseCaseActivationId
import io.github.daniele21.localllm.runtime.UseCaseActivationLeaseRegistry
import io.github.daniele21.localllm.store.ModelStore
import io.github.daniele21.localllm.store.ModelStoreSnapshot
import io.github.daniele21.localllm.store.StoredModel
import io.github.daniele21.localllm.store.VerificationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HarnessActivatedPresetAliasTest {
    @Test
    fun `alias preserves canonical execution under public preset identity`() {
        val base = resolvedOmbra()
        val source = base.useCase.presets.single { it.ref == HarnessSharedRuntimeBindings.ombraDefaultPreset }
        val publicPreset = customPresetRef()

        val aliased = base.aliasActivatedPreset(publicPreset, HarnessSharedRuntimeBindings.ombraDefaultPreset)
        val actual = aliased.useCase.presets.single()

        assertEquals(base.binding, aliased.binding)
        assertEquals(base.model, aliased.model)
        assertEquals(publicPreset, aliased.useCase.defaultPreset)
        assertEquals(publicPreset, actual.ref)
        assertEquals(source.generation, actual.generation)
        assertEquals(source.contextPreference, actual.contextPreference)
        assertEquals(source.systemPromptVersion, actual.systemPromptVersion)
        assertEquals(source.systemPrompt, actual.systemPrompt)
        assertEquals(source.allowedOutputModes, actual.allowedOutputModes)
    }

    @Test
    fun `alias fails closed when canonical inference preset is missing`() {
        val missing = InferencePresetRef(InferencePresetId("missing-profile"), 1)

        assertThrows(IllegalStateException::class.java) {
            resolvedOmbra().aliasActivatedPreset(customPresetRef(), missing)
        }
    }

    @Test
    fun `consumer policy follows activation and falls back after release`() {
        val applicationId = HarnessSharedRuntimeBindings.redactGuardApplicationId
        val bindings = HarnessPhoneBindingRegistry()
        val baseline = HarnessOmbraConsumerPolicy.create(applicationId)
        val registry = HarnessActivationAwareConsumerPolicyRegistry(
            applicationId = applicationId,
            bindings = bindings,
            fallback = InMemoryConsumerUseCasePolicyRegistry(listOf(baseline)),
        )
        val publicPreset = customPresetRef()
        val activationId = UseCaseActivationId("policy-activation")

        assertEquals(HarnessSharedRuntimeBindings.ombraDefaultPreset, registry.find(applicationId, ombraUseCaseId())?.defaultPreset)

        bindings.installActivationBinding(
            activationId = activationId,
            applicationId = applicationId,
            useCaseId = ombraUseCaseId(),
            resolved = resolvedOmbra().aliasActivatedPreset(publicPreset, HarnessSharedRuntimeBindings.ombraDefaultPreset),
        )

        val active = registry.find(applicationId, ombraUseCaseId())
        assertEquals(publicPreset, active?.defaultPreset)
        assertEquals(setOf(publicPreset), active?.exposedPresets)

        bindings.removeActivationBinding(activationId)
        assertEquals(HarnessSharedRuntimeBindings.ombraDefaultPreset, registry.find(applicationId, ombraUseCaseId())?.defaultPreset)
    }

    @Test
    fun `host activation installs public preset alias over canonical profile`() {
        val fixture = controlPlaneFixture()
        val runtimeControl = RecordingRuntimeControl()
        val host = HarnessConsumerControlPlaneHost(
            store = InMemoryHostControlPlaneStore(fixture.state),
            modelStore = ReadOnlyVerifiedModelStore(fixture.storedModel),
            runtimeControl = runtimeControl,
            epochClock = { 100L },
        )

        val result = host.activate(
            ownerId = "redactguard-connection",
            applicationId = HarnessSharedRuntimeBindings.redactGuardApplicationId,
            request = ConsumerActivationRequest(
                useCaseId = fixture.spec.useCase.useCaseId,
                useCaseRevision = fixture.spec.useCase.revision,
                bindingRevision = fixture.bindingRevision,
                preset = fixture.publicPreset,
            ),
        )

        assertTrue(result is ConsumerActivationResult.Activated)
        val installed = assertNotNull(runtimeControl.installedResolved).let { runtimeControl.installedResolved!! }
        assertEquals(fixture.publicPreset, installed.useCase.defaultPreset)
        assertEquals(fixture.publicPreset, installed.useCase.presets.single().ref)
        assertEquals(fixture.storedModel.digest, installed.model.artifact.digest)
    }

    private fun resolvedOmbra(): ResolvedUseCase = HarnessSharedRuntimeBindings.resolveOmbra(
        importedModel(),
        HarnessSharedRuntimeBindings.redactGuardApplicationId,
    )

    private fun controlPlaneFixture(): ControlPlaneFixture {
        val applicationId = HarnessSharedRuntimeBindings.redactGuardApplicationId
        val signer = "0".repeat(64)
        val requirement = HarnessBuiltInApplicationRequirement(
            applicationId = applicationId,
            acceptedPackageNames = setOf(HarnessSharedRuntimeBindings.REDACTGUARD_RELEASE_PACKAGE),
            acceptedSignerSha256 = setOf(signer),
            displayName = "RedactGuard",
        )
        val spec = HarnessBuiltInControlPlaneSpec.ombra(listOf(requirement))
        val binding = spec.bindingFor(applicationId)
        val publicPreset = customPresetRef()
        val customPreset = UseCasePresetDefinition(
            useCaseId = spec.useCase.useCaseId,
            metadata = PresetConsumerMetadata(
                presetId = publicPreset.id.value,
                revision = publicPreset.version,
                displayName = "Custom public preset",
                description = "Custom public alias over the canonical local PII inference profile",
            ),
            creationSource = PresetCreationSource.CUSTOM,
            state = PresetLifecycleState.PUBLISHED,
            execution = spec.preset.execution.copy(
                inferencePreset = HarnessSharedRuntimeBindings.ombraDefaultPreset,
            ),
        )
        val artifact = CuratedModelCatalog.releases.first().artifact
        val storedModel = StoredModel(
            digest = artifact.digest,
            file = File("unused-test-model.gguf"),
            sizeBytes = artifact.sizeBytes,
            verified = true,
        )
        val state = HostControlPlaneState(
            applications = listOf(requirement.newRegistration(0L)),
            useCases = listOf(spec.useCase),
            presets = listOf(customPreset),
            bindings = listOf(binding),
            exposures = listOf(
                StoredPresetExposure(
                    bindingId = binding.bindingId,
                    bindingRevision = binding.revision,
                    presetId = publicPreset.id.value,
                    presetRevision = publicPreset.version,
                    isDefault = true,
                ),
            ),
        )
        return ControlPlaneFixture(
            spec = spec,
            state = state,
            publicPreset = publicPreset,
            bindingRevision = binding.revision,
            storedModel = storedModel,
        )
    }

    private fun importedModel(): ImportedPhoneModel {
        val artifact = CuratedModelCatalog.releases.first().artifact
        return ImportedPhoneModel(
            digest = artifact.digest,
            fileName = artifact.fileName,
            sizeBytes = artifact.sizeBytes,
            architecture = artifact.architecture,
            quantization = artifact.quantization,
        )
    }

    private fun customPresetRef() = InferencePresetRef(InferencePresetId("custom-public-preset"), 7)

    private fun ombraUseCaseId(): UseCaseId = HarnessSharedRuntimeBindings.ombraUseCaseId
}

private data class ControlPlaneFixture(
    val spec: HarnessBuiltInControlPlaneSpec,
    val state: HostControlPlaneState,
    val publicPreset: InferencePresetRef,
    val bindingRevision: Int,
    val storedModel: StoredModel,
)

private class RecordingRuntimeControl : HarnessConsumerRuntimeControl {
    override val activationResidency = ActivationResidencyCoordinator(
        UseCaseActivationLeaseRegistry(
            idFactory = ActivationIdFactory { UseCaseActivationId("host-activation") },
        ),
    )

    var installedResolved: ResolvedUseCase? = null
        private set

    override fun installActivationBinding(
        activationId: UseCaseActivationId,
        applicationId: ApplicationId,
        useCaseId: UseCaseId,
        resolved: ResolvedUseCase,
    ) {
        installedResolved = resolved
    }

    override fun removeActivationBinding(activationId: UseCaseActivationId) {
        installedResolved = null
    }
}

private class ReadOnlyVerifiedModelStore(private val storedModel: StoredModel) : ModelStore {
    override fun find(digest: ModelDigest): StoredModel? = storedModel.takeIf { it.digest == digest }

    override fun snapshot(): ModelStoreSnapshot = ModelStoreSnapshot(
        modelCount = 1,
        totalBytes = storedModel.sizeBytes,
        entries = listOf(storedModel),
    )

    override fun import(source: File, artifact: io.github.daniele21.localllm.models.GgufArtifact): StoredModel =
        error("Activation must not import model bytes")

    override fun verify(digest: ModelDigest): VerificationResult = error("Activation must not re-verify model bytes")

    override fun remove(digest: ModelDigest): Boolean = error("Activation must not remove model bytes")
}
