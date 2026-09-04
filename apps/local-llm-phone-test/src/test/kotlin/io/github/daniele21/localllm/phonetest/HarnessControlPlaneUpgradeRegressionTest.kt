package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.models.ApplicationRegistrationState
import io.github.daniele21.localllm.models.HostControlPlaneState
import io.github.daniele21.localllm.models.RegisteredApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessControlPlaneUpgradeRegressionTest {
    private val redactGuard = requirement(
        id = "redactguard",
        packageName = "io.github.daniele21.redactguard",
        displayName = "RedactGuard",
    )
    private val console = requirement(
        id = "console",
        packageName = "io.github.daniele21.localllm.console",
        displayName = "Local LLM Console",
    )
    private val spec = HarnessBuiltInControlPlaneSpec.ombra(listOf(redactGuard, console))
    private val reconciler = HarnessControlPlaneReconciler(spec)

    @Test
    fun applicationOnlyLegacyStateRepairsCompleteAssignmentGraph() {
        val legacyApplication = redactGuard.newRegistration(10).copy(lastSeenAtEpochMs = 20)
        val legacy = HostControlPlaneState(applications = listOf(legacyApplication))

        val repaired = success(reconciler.reconcile(legacy, observedAtEpochMs = 100)).state

        assertEquals(2, repaired.applications.size)
        assertEquals(legacyApplication, repaired.applications.single { it.applicationId == redactGuard.applicationId })
        assertEquals(spec.useCase, repaired.useCases.single())
        assertEquals(spec.preset, repaired.presets.single())
        assertEquals(2, repaired.currentBindings().size)
        assertEquals(2, repaired.exposures.size)
        assertTrue(repaired.exposures.all { it.isDefault })
    }

    @Test
    fun useCaseAndApplicationsWithoutBindingsRepairBothConsumersDeterministically() {
        val legacy = HostControlPlaneState(
            applications = listOf(redactGuard.newRegistration(10), console.newRegistration(10)),
            useCases = listOf(spec.useCase),
            presets = listOf(spec.preset),
        )

        val repaired = success(reconciler.reconcile(legacy, observedAtEpochMs = 100)).state

        assertEquals(
            setOf(redactGuard.applicationId, console.applicationId),
            repaired.currentBindings().map { it.applicationId }.toSet(),
        )
        assertEquals(
            repaired.currentBindings().map { it.bindingId }.toSet(),
            repaired.exposures.map { it.bindingId }.toSet(),
        )
    }

    @Test
    fun missingExposureOnExistingBindingIsRepairedWithoutRevisionChurn() {
        val binding = spec.bindingFor(redactGuard.applicationId)
        val legacy = HostControlPlaneState(
            applications = listOf(redactGuard.newRegistration(10), console.newRegistration(10)),
            useCases = listOf(spec.useCase),
            presets = listOf(spec.preset),
            bindings = listOf(binding, spec.bindingFor(console.applicationId)),
            exposures = listOf(
                exposureFor(spec.bindingFor(console.applicationId)),
            ),
        )

        val first = success(reconciler.reconcile(legacy, observedAtEpochMs = 100))
        val second = success(reconciler.reconcile(first.state, observedAtEpochMs = 200))

        assertEquals(1, first.state.bindings.count { it.applicationId == redactGuard.applicationId })
        assertEquals(binding.revision, first.state.latestBinding(redactGuard.applicationId, spec.useCase.useCaseId)?.revision)
        assertEquals(1, first.state.exposures.count { it.bindingId == binding.bindingId })
        assertFalse(second.changed)
        assertEquals(first.state, second.state)
    }

    @Test
    fun disabledCurrentRevisionRemainsDisabledAndReceivesNonDefaultBuiltInExposure() {
        val baseline = spec.bindingFor(redactGuard.applicationId)
        val disabled = baseline.copy(revision = 2, enabled = false, isDefault = false)
        val consoleBinding = spec.bindingFor(console.applicationId)
        val legacy = HostControlPlaneState(
            applications = listOf(redactGuard.newRegistration(10), console.newRegistration(10)),
            useCases = listOf(spec.useCase),
            presets = listOf(spec.preset),
            bindings = listOf(baseline, disabled, consoleBinding),
            exposures = listOf(exposureFor(baseline), exposureFor(consoleBinding)),
        )

        val repaired = success(reconciler.reconcile(legacy, observedAtEpochMs = 100)).state
        val current = repaired.latestBinding(redactGuard.applicationId, spec.useCase.useCaseId)
        val repairedExposure = repaired.exposures.single {
            it.bindingId == disabled.bindingId && it.bindingRevision == disabled.revision
        }

        assertEquals(disabled, current)
        assertFalse(requireNotNull(current).enabled)
        assertFalse(repairedExposure.isDefault)
    }

    @Test
    fun disabledApplicationAuthorizationStateIsNotSilentlyRestored() {
        val disabledApplication = redactGuard.newRegistration(10).copy(
            state = ApplicationRegistrationState.DISABLED,
            lastSeenAtEpochMs = 50,
        )
        val legacy = HostControlPlaneState(applications = listOf(disabledApplication))

        val repaired = success(reconciler.reconcile(legacy, observedAtEpochMs = 100)).state

        assertEquals(disabledApplication, repaired.applications.single { it.applicationId == redactGuard.applicationId })
    }

    @Test
    fun conflictingBindingIdentityFailsClosedWithoutReturningCandidateState() {
        val legacy = HostControlPlaneState(
            applications = listOf(redactGuard.newRegistration(10), console.newRegistration(10)),
            useCases = listOf(spec.useCase),
            presets = listOf(spec.preset),
            bindings = listOf(
                spec.bindingFor(redactGuard.applicationId).copy(bindingId = "legacy-conflicting-binding"),
            ),
        )

        val result = reconciler.reconcile(legacy, observedAtEpochMs = 100)

        assertTrue(result is HarnessControlPlaneReconciliationResult.Conflict)
        assertEquals(
            HarnessControlPlaneConflictCode.BINDING_IDENTITY,
            (result as HarnessControlPlaneReconciliationResult.Conflict).code,
        )
    }

    private fun requirement(id: String, packageName: String, displayName: String) = HarnessBuiltInApplicationRequirement(
        applicationId = ApplicationId(id),
        acceptedPackageNames = setOf(packageName),
        acceptedSignerSha256 = setOf(SIGNER),
        displayName = displayName,
    )

    private fun exposureFor(binding: io.github.daniele21.localllm.models.ApplicationUseCaseBinding) =
        io.github.daniele21.localllm.models.StoredPresetExposure(
            bindingId = binding.bindingId,
            bindingRevision = binding.revision,
            presetId = spec.preset.metadata.presetId,
            presetRevision = spec.preset.metadata.revision,
            isDefault = binding.enabled,
        )

    private fun success(result: HarnessControlPlaneReconciliationResult): HarnessControlPlaneReconciliationResult.Success {
        assertTrue(result is HarnessControlPlaneReconciliationResult.Success)
        return result as HarnessControlPlaneReconciliationResult.Success
    }

    private companion object {
        const val SIGNER = "e78eda815e24a5879d7d6b963bb0a6dde8fdb3221c01a9f50cc1182696acc110"
    }
}
