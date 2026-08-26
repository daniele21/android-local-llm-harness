package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ConsumerActivationRequest
import io.github.daniele21.localllm.contracts.ConsumerActivationResult
import io.github.daniele21.localllm.contracts.ConsumerAssignedUseCasesResult
import io.github.daniele21.localllm.contracts.ConsumerControlPlaneErrorCode
import io.github.daniele21.localllm.contracts.ConsumerPublishedPresetsResult
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.models.GgufArtifact
import io.github.daniele21.localllm.models.InMemoryHostControlPlaneStore
import io.github.daniele21.localllm.models.ResolvedUseCase
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

class HarnessControlPlaneCrossSurfaceTest {
    @Test
    fun applicationsGatewayAndConsumerHostObserveSameReconciledGraph() {
        val store = reconciledStore()
        val gateway = StoreHarnessApplicationsGateway(store)
        val host = host(store)

        val application = gateway.snapshot().applications.single()
        val uiAssignment = application.assignments.single()
        val assigned = host.assignedUseCases(APPLICATION_ID)
        assertTrue(assigned is ConsumerAssignedUseCasesResult.Available)
        val consumerAssignment = (assigned as ConsumerAssignedUseCasesResult.Available).assignments.single()

        assertEquals(APPLICATION_ID.value, application.applicationId)
        assertEquals(uiAssignment.useCaseId, consumerAssignment.useCaseId.value)
        assertEquals(uiAssignment.useCaseRevision, consumerAssignment.useCaseRevision)
        assertEquals(uiAssignment.bindingRevision, consumerAssignment.bindingRevision)
        assertEquals(uiAssignment.displayName, consumerAssignment.displayName)
        assertEquals(uiAssignment.description, consumerAssignment.description)

        val presets = host.publishedPresets(APPLICATION_ID, consumerAssignment.useCaseId)
        assertTrue(presets is ConsumerPublishedPresetsResult.Available)
        val consumerPresets = presets as ConsumerPublishedPresetsResult.Available
        val uiPreset = requireNotNull(uiAssignment.defaultPreset)
        val consumerPreset = consumerPresets.presets.single()

        assertEquals(uiAssignment.bindingRevision, consumerPresets.bindingRevision)
        assertEquals(uiPreset.presetId, consumerPreset.preset.id.value)
        assertEquals(uiPreset.revision, consumerPreset.preset.version)
        assertEquals(uiPreset.displayName, consumerPreset.displayName)
        assertEquals(uiPreset.description, consumerPreset.description)
        assertEquals(uiPreset.isDefault, consumerPreset.isDefault)

        val canonicalBinding = requireNotNull(store.snapshot().latestBinding(APPLICATION_ID, USE_CASE_ID))
        assertEquals(canonicalBinding.bindingId, uiAssignment.bindingId)
        assertEquals(canonicalBinding.revision, consumerAssignment.bindingRevision)
    }

    @Test
    fun activationReadsSameReconciledExposureAndRejectsUnknownPresetBeforeRuntimeMutation() {
        val store = reconciledStore()
        val runtimeControl = RecordingRuntimeControl()
        val host = host(store, runtimeControl)
        val assignment = StoreHarnessApplicationsGateway(store).snapshot().applications.single().assignments.single()

        val result = host.activate(
            ownerId = "consumer-connection",
            applicationId = APPLICATION_ID,
            request = ConsumerActivationRequest(
                useCaseId = USE_CASE_ID,
                useCaseRevision = assignment.useCaseRevision,
                bindingRevision = assignment.bindingRevision,
                preset = InferencePresetRef(InferencePresetId("not-exposed"), 1),
            ),
        )

        assertTrue(result is ConsumerActivationResult.Rejected)
        assertEquals(
            ConsumerControlPlaneErrorCode.PRESET_NOT_EXPOSED,
            (result as ConsumerActivationResult.Rejected).failure.code,
        )
        assertTrue(runtimeControl.installedBindings.isEmpty())
    }

    private fun reconciledStore(): InMemoryHostControlPlaneStore {
        val requirement = HarnessBuiltInApplicationRequirement(
            applicationId = APPLICATION_ID,
            acceptedPackageNames = setOf("io.github.daniele21.redactguard"),
            acceptedSignerSha256 = setOf(SIGNER),
            displayName = "RedactGuard",
        )
        val spec = HarnessBuiltInControlPlaneSpec.ombra(listOf(requirement))
        val store = InMemoryHostControlPlaneStore()
        HarnessControlPlaneStartup(
            store = store,
            reconciler = HarnessControlPlaneReconciler(spec),
            epochClock = { 100 },
        ).reconcile()
        return store
    }

    private fun host(store: InMemoryHostControlPlaneStore, runtimeControl: RecordingRuntimeControl = RecordingRuntimeControl()) =
        HarnessConsumerControlPlaneHost(
            store = store,
            modelStore = EmptyModelStore,
            runtimeControl = runtimeControl,
            epochClock = { 200 },
        )

    private class RecordingRuntimeControl : HarnessConsumerRuntimeControl {
        override val activationResidency = ActivationResidencyCoordinator(
            UseCaseActivationLeaseRegistry(
                ActivationIdFactory { UseCaseActivationId("cprec-50-activation") },
            ),
        )
        val installedBindings = mutableListOf<UseCaseActivationId>()

        override fun installActivationBinding(
            activationId: UseCaseActivationId,
            applicationId: ApplicationId,
            useCaseId: UseCaseId,
            resolved: ResolvedUseCase,
        ) {
            installedBindings += activationId
        }

        override fun removeActivationBinding(activationId: UseCaseActivationId) {
            installedBindings -= activationId
        }
    }

    private object EmptyModelStore : ModelStore {
        override fun find(digest: ModelDigest): StoredModel? = null

        override fun import(source: File, artifact: GgufArtifact): StoredModel = error("Not used by CPREC-50")

        override fun verify(digest: ModelDigest): VerificationResult = VerificationResult(false, null, "Not used by CPREC-50")

        override fun remove(digest: ModelDigest): Boolean = false

        override fun snapshot(): ModelStoreSnapshot = ModelStoreSnapshot(0, 0, emptyList())
    }

    private companion object {
        val APPLICATION_ID = ApplicationId("redactguard")
        val USE_CASE_ID = HarnessSharedRuntimeBindings.ombraUseCaseId
        const val SIGNER = "e78eda815e24a5879d7d6b963bb0a6dde8fdb3221c01a9f50cc1182696acc110"
    }
}
