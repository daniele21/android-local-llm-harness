package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.catalog.CuratedModelCatalog
import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ConsumerSetupResolutionRequest
import io.github.daniele21.localllm.contracts.ConsumerSetupResolutionResult
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.models.HostControlPlaneState
import io.github.daniele21.localllm.models.InMemoryHostControlPlaneStore
import io.github.daniele21.localllm.models.PresetConsumerMetadata
import io.github.daniele21.localllm.models.PresetCreationSource
import io.github.daniele21.localllm.models.PresetGenerationOverrides
import io.github.daniele21.localllm.models.PresetLifecycleState
import io.github.daniele21.localllm.models.ResolvedUseCase
import io.github.daniele21.localllm.models.StoredPresetExposure
import io.github.daniele21.localllm.models.UseCasePresetDefinition
import io.github.daniele21.localllm.runtime.ActivationIdFactory
import io.github.daniele21.localllm.runtime.ActivationResidencyCoordinator
import io.github.daniele21.localllm.runtime.UseCaseActivationId
import io.github.daniele21.localllm.runtime.UseCaseActivationLeaseRegistry
import io.github.daniele21.localllm.store.ModelStore
import io.github.daniele21.localllm.store.ModelStoreSnapshot
import io.github.daniele21.localllm.store.StoredModel
import io.github.daniele21.localllm.store.VerificationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HarnessConsumerSetupResolutionTest {
    @Test
    fun `setup resolution exposes effective host configuration without activation residency or runtime binding`() {
        val applicationId = HarnessSharedRuntimeBindings.redactGuardApplicationId
        val fixture = fixture(applicationId)
        val runtimeControl = SetupRecordingRuntimeControl()
        val host = HarnessConsumerControlPlaneHost(
            store = InMemoryHostControlPlaneStore(fixture.state),
            modelStore = SetupReadOnlyModelStore(fixture.storedModel),
            runtimeControl = runtimeControl,
        )

        val result = host.resolveSetup(
            applicationId = applicationId,
            request = fixture.setupRequest(),
        )

        assertTrue(result is ConsumerSetupResolutionResult.Resolved)
        val setup = (result as ConsumerSetupResolutionResult.Resolved).setup
        assertEquals(fixture.publicPreset, setup.preset)
        assertTrue(setup.modelProfileId.isNotBlank())
        assertEquals(321, setup.generation.maxOutputTokens)
        assertEquals(0.2f, setup.generation.temperature)
        assertEquals(0, runtimeControl.installCalls)
        assertEquals(0, runtimeControl.activationResidency.activeLeaseCount(fixture.storedModel.digest))
    }

    @Test
    fun `warm retention host forwards setup resolution to its delegate`() {
        val applicationId = HarnessSharedRuntimeBindings.redactGuardApplicationId
        val fixture = fixture(applicationId)
        val runtimeControl = SetupRecordingRuntimeControl()
        val delegate = HarnessConsumerControlPlaneHost(
            store = InMemoryHostControlPlaneStore(fixture.state),
            modelStore = SetupReadOnlyModelStore(fixture.storedModel),
            runtimeControl = runtimeControl,
        )
        var cancelCalls = 0
        val host = HarnessWarmRetentionAwareControlPlaneHost(
            delegate = delegate,
            cancelWarmRetention = { cancelCalls += 1 },
        )

        val result = host.resolveSetup(
            applicationId = applicationId,
            request = fixture.setupRequest(),
        )

        assertTrue(result is ConsumerSetupResolutionResult.Resolved)
        val setup = (result as ConsumerSetupResolutionResult.Resolved).setup
        assertEquals(fixture.publicPreset, setup.preset)
        assertEquals(321, setup.generation.maxOutputTokens)
        assertEquals(0, cancelCalls)
        assertEquals(0, runtimeControl.installCalls)
    }

    @Test
    fun `setup resolution fails closed when observed revisions are stale without acquiring residency`() {
        val applicationId = HarnessSharedRuntimeBindings.redactGuardApplicationId
        val fixture = fixture(applicationId)
        val runtimeControl = SetupRecordingRuntimeControl()
        val host = HarnessConsumerControlPlaneHost(
            store = InMemoryHostControlPlaneStore(fixture.state),
            modelStore = SetupReadOnlyModelStore(fixture.storedModel),
            runtimeControl = runtimeControl,
        )

        val result = host.resolveSetup(
            applicationId = applicationId,
            request = fixture.setupRequest(bindingRevision = fixture.bindingRevision + 1),
        )

        assertTrue(result is ConsumerSetupResolutionResult.Rejected)
        assertEquals(0, runtimeControl.installCalls)
        assertEquals(0, runtimeControl.activationResidency.activeLeaseCount(fixture.storedModel.digest))
    }

    private fun fixture(applicationId: ApplicationId): SetupFixture {
        val requirement = HarnessBuiltInApplicationRequirement(
            applicationId = applicationId,
            acceptedPackageNames = setOf(HarnessSharedRuntimeBindings.REDACTGUARD_RELEASE_PACKAGE),
            acceptedSignerSha256 = setOf("0".repeat(64)),
            displayName = "RedactGuard",
        )
        val spec = HarnessBuiltInControlPlaneSpec.ombra(listOf(requirement))
        val binding = spec.bindingFor(applicationId)
        val publicPreset = InferencePresetRef(InferencePresetId("custom-las-preset"), 9)
        val customPreset = UseCasePresetDefinition(
            useCaseId = spec.useCase.useCaseId,
            metadata = PresetConsumerMetadata(
                presetId = publicPreset.id.value,
                revision = publicPreset.version,
                displayName = "LAS preset",
                description = "Setup-resolution regression preset",
            ),
            creationSource = PresetCreationSource.CUSTOM,
            state = PresetLifecycleState.PUBLISHED,
            execution = spec.preset.execution.copy(
                inferencePreset = HarnessSharedRuntimeBindings.ombraDefaultPreset,
                generationOverrides = PresetGenerationOverrides(
                    maxOutputTokens = 321,
                    temperature = 0.2f,
                ),
            ),
        )
        val artifact = CuratedModelCatalog.releases.first().artifact
        val storedModel = StoredModel(
            digest = artifact.digest,
            file = File("unused-las-model.gguf"),
            sizeBytes = artifact.sizeBytes,
            verified = false,
        )
        return SetupFixture(
            spec = spec,
            state = HostControlPlaneState(
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
            ),
            publicPreset = publicPreset,
            bindingRevision = binding.revision,
            storedModel = storedModel,
        )
    }
}

private data class SetupFixture(
    val spec: HarnessBuiltInControlPlaneSpec,
    val state: HostControlPlaneState,
    val publicPreset: InferencePresetRef,
    val bindingRevision: Int,
    val storedModel: StoredModel,
) {
    fun setupRequest(bindingRevision: Int = this.bindingRevision): ConsumerSetupResolutionRequest = ConsumerSetupResolutionRequest(
        useCaseId = spec.useCase.useCaseId,
        useCaseRevision = spec.useCase.revision,
        bindingRevision = bindingRevision,
        preset = publicPreset,
    )
}

private class SetupRecordingRuntimeControl : HarnessConsumerRuntimeControl {
    override val activationResidency = ActivationResidencyCoordinator(
        UseCaseActivationLeaseRegistry(
            ActivationIdFactory { error("Setup inspection must not allocate activation IDs") },
        ),
    )
    var installCalls = 0
        private set

    override fun installActivationBinding(
        activationId: UseCaseActivationId,
        applicationId: ApplicationId,
        useCaseId: UseCaseId,
        resolved: ResolvedUseCase,
    ) {
        installCalls += 1
    }

    override fun removeActivationBinding(activationId: UseCaseActivationId) = Unit
}

private class SetupReadOnlyModelStore(private val storedModel: StoredModel) : ModelStore {
    override fun find(digest: ModelDigest): StoredModel? = storedModel.takeIf { it.digest == digest }

    override fun snapshot(): ModelStoreSnapshot = ModelStoreSnapshot(
        modelCount = 1,
        totalBytes = storedModel.sizeBytes,
        entries = listOf(storedModel),
    )

    override fun import(source: File, artifact: io.github.daniele21.localllm.models.GgufArtifact): StoredModel =
        error("Setup inspection must not import model bytes")

    override fun verify(digest: ModelDigest): VerificationResult = error("Setup inspection must not verify or hash model bytes")

    override fun remove(digest: ModelDigest): Boolean = error("Setup inspection must not remove model bytes")
}
