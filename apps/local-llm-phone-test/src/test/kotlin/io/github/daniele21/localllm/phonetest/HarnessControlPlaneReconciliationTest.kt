package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.models.ApplicationRegistrationState
import io.github.daniele21.localllm.models.ApplicationUseCaseBinding
import io.github.daniele21.localllm.models.HostControlPlaneState
import io.github.daniele21.localllm.models.PresetConsumerMetadata
import io.github.daniele21.localllm.models.PresetCreationSource
import io.github.daniele21.localllm.models.PresetExecutionPolicy
import io.github.daniele21.localllm.models.PresetLifecycleState
import io.github.daniele21.localllm.models.RegisteredApplication
import io.github.daniele21.localllm.models.StoredPresetExposure
import io.github.daniele21.localllm.models.UseCaseCachePolicy
import io.github.daniele21.localllm.models.UseCasePresetDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessControlPlaneReconciliationTest {
    private val requirement = HarnessBuiltInApplicationRequirement(
        applicationId = APP_ID,
        acceptedPackageNames = setOf("io.github.daniele21.redactguard"),
        acceptedSignerSha256 = setOf(SIGNER),
        displayName = "RedactGuard",
    )
    private val spec = HarnessBuiltInControlPlaneSpec.ombra(listOf(requirement))
    private val reconciler = HarnessControlPlaneReconciler(spec)

    @Test
    fun emptyStateConvergesToCompleteBuiltInGraphAndSecondPassIsNoOp() {
        val first = success(reconciler.reconcile(HostControlPlaneState(), observedAtEpochMs = 100))

        assertTrue(first.changed)
        assertEquals(listOf(APP_ID), first.state.applications.map { it.applicationId })
        assertEquals(spec.useCase, first.state.useCases.single())
        assertEquals(spec.preset, first.state.presets.single())
        assertEquals(spec.bindingFor(APP_ID), first.state.bindings.single())
        assertTrue(first.state.exposures.single().isDefault)

        val second = success(reconciler.reconcile(first.state, observedAtEpochMs = 999))
        assertFalse(second.changed)
        assertEquals(first.state, second.state)
        assertEquals(100, second.state.applications.single().firstSeenAtEpochMs)
        assertEquals(100, second.state.applications.single().lastSeenAtEpochMs)
    }

    @Test
    fun partialStateWithOnlyUseCaseAndPresetRepairsMissingApplicationBindingAndExposure() {
        val partial = HostControlPlaneState(
            useCases = listOf(spec.useCase),
            presets = listOf(spec.preset),
        )

        val repaired = success(reconciler.reconcile(partial, observedAtEpochMs = 200)).state

        assertEquals(APP_ID, repaired.applications.single().applicationId)
        assertEquals(spec.bindingFor(APP_ID), repaired.bindings.single())
        assertEquals(spec.preset.metadata.presetId, repaired.exposures.single().presetId)
        assertTrue(repaired.exposures.single().isDefault)
    }

    @Test
    fun unrelatedApplicationAndCustomPresetDefaultArePreserved() {
        val base = success(reconciler.reconcile(HostControlPlaneState(), observedAtEpochMs = 100)).state
        val currentBinding = base.bindings.single()
        val evolvedBinding = currentBinding.copy(revision = 2)
        val customPreset = customPreset()
        val customDefault = StoredPresetExposure(
            bindingId = evolvedBinding.bindingId,
            bindingRevision = evolvedBinding.revision,
            presetId = customPreset.metadata.presetId,
            presetRevision = customPreset.metadata.revision,
            isDefault = true,
        )
        val builtInNonDefault = StoredPresetExposure(
            bindingId = evolvedBinding.bindingId,
            bindingRevision = evolvedBinding.revision,
            presetId = spec.preset.metadata.presetId,
            presetRevision = spec.preset.metadata.revision,
            isDefault = false,
        )
        val unrelated = RegisteredApplication(
            applicationId = ApplicationId("other-app"),
            packageName = "io.github.example.other",
            signerSha256 = "b".repeat(64),
            displayName = "Other app",
            state = ApplicationRegistrationState.AUTHORIZED,
            firstSeenAtEpochMs = 5,
            lastSeenAtEpochMs = 10,
        )
        val state = base.copy(
            applications = base.applications + unrelated,
            presets = base.presets + customPreset,
            bindings = base.bindings + evolvedBinding,
            exposures = base.exposures + listOf(customDefault, builtInNonDefault),
        )

        val repaired = success(reconciler.reconcile(state, observedAtEpochMs = 300)).state

        assertTrue(repaired.applications.contains(unrelated))
        assertTrue(repaired.presets.contains(customPreset))
        assertEquals(evolvedBinding, repaired.latestBinding(APP_ID, spec.useCase.useCaseId))
        val latestExposures = repaired.exposures.filter { it.bindingRevision == evolvedBinding.revision }
        assertEquals(1, latestExposures.count(StoredPresetExposure::isDefault))
        assertEquals(customPreset.metadata.presetId, latestExposures.single(StoredPresetExposure::isDefault).presetId)
    }

    @Test
    fun explicitDisabledBindingRevisionIsPreservedAndNotReEnabled() {
        val base = success(reconciler.reconcile(HostControlPlaneState(), observedAtEpochMs = 100)).state
        val disabled = base.bindings.single().copy(revision = 2, enabled = false, isDefault = false)
        val state = base.copy(bindings = base.bindings + disabled)

        val repaired = success(reconciler.reconcile(state, observedAtEpochMs = 400)).state
        val latest = repaired.latestBinding(APP_ID, spec.useCase.useCaseId)

        assertEquals(disabled, latest)
        assertFalse(requireNotNull(latest).enabled)
        val latestExposure = repaired.exposures.single { it.bindingRevision == disabled.revision }
        assertFalse(latestExposure.isDefault)
    }

    @Test
    fun compatibleExistingApplicationKeepsStateAndTimestamps() {
        val existing = requirement.newRegistration(10).copy(
            state = ApplicationRegistrationState.DISABLED,
            lastSeenAtEpochMs = 20,
        )
        val current = HostControlPlaneState(applications = listOf(existing))

        val repaired = success(reconciler.reconcile(current, observedAtEpochMs = 500)).state

        assertEquals(existing, repaired.applications.single())
    }

    @Test
    fun incompatibleApplicationSignerFailsClosedWithoutReplacement() {
        val incompatible = requirement.newRegistration(10).copy(signerSha256 = "c".repeat(64))

        val conflict = conflict(
            reconciler.reconcile(
                HostControlPlaneState(applications = listOf(incompatible)),
                observedAtEpochMs = 500,
            ),
        )

        assertEquals(HarnessControlPlaneConflictCode.APPLICATION_IDENTITY, conflict.code)
        assertEquals(APP_ID.value, conflict.identity)
    }

    @Test
    fun conflictingBuiltInUseCaseRevisionFailsClosed() {
        val conflicting = spec.useCase.copy(description = "Unexpected built-in definition")

        val conflict = conflict(
            reconciler.reconcile(
                HostControlPlaneState(useCases = listOf(conflicting)),
                observedAtEpochMs = 500,
            ),
        )

        assertEquals(HarnessControlPlaneConflictCode.USE_CASE_DEFINITION, conflict.code)
    }

    @Test
    fun futureBuiltInPresetRevisionFailsClosedInsteadOfDowngrading() {
        val future = spec.preset.copy(metadata = spec.preset.metadata.copy(revision = spec.preset.metadata.revision + 1))
        val current = HostControlPlaneState(
            useCases = listOf(spec.useCase),
            presets = listOf(future),
        )

        val conflict = conflict(reconciler.reconcile(current, observedAtEpochMs = 500))

        assertEquals(HarnessControlPlaneConflictCode.PRESET_REVISION_AHEAD, conflict.code)
    }

    private fun customPreset() = UseCasePresetDefinition(
        useCaseId = spec.useCase.useCaseId,
        metadata = PresetConsumerMetadata(
            presetId = "custom-pii",
            revision = 1,
            displayName = "Custom PII",
            description = "User-selected custom preset",
        ),
        creationSource = PresetCreationSource.CUSTOM,
        state = PresetLifecycleState.PUBLISHED,
        execution = PresetExecutionPolicy(
            modelProfileId = null,
            inferencePreset = InferencePresetRef(InferencePresetId("custom-pii-runtime"), 1),
            contextTokens = 4_096,
            cachePolicy = UseCaseCachePolicy(
                retainModelWarmMs = 10_000,
                reuseStatelessContext = false,
                enablePrefixSnapshot = false,
                enableDeterministicResultCache = false,
            ),
        ),
    )

    private fun success(result: HarnessControlPlaneReconciliationResult): HarnessControlPlaneReconciliationResult.Success {
        assertTrue(result is HarnessControlPlaneReconciliationResult.Success)
        return result as HarnessControlPlaneReconciliationResult.Success
    }

    private fun conflict(result: HarnessControlPlaneReconciliationResult): HarnessControlPlaneReconciliationResult.Conflict {
        assertTrue(result is HarnessControlPlaneReconciliationResult.Conflict)
        return result as HarnessControlPlaneReconciliationResult.Conflict
    }

    private companion object {
        val APP_ID = ApplicationId("redactguard")
        const val SIGNER = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2"
    }
}
