package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.models.ApplicationRegistrationState
import io.github.daniele21.localllm.models.ApplicationUseCaseBinding
import io.github.daniele21.localllm.models.HostControlPlaneState
import io.github.daniele21.localllm.models.InMemoryHostControlPlaneStore
import io.github.daniele21.localllm.models.OutputMode
import io.github.daniele21.localllm.models.PresetConsumerMetadata
import io.github.daniele21.localllm.models.PresetCreationSource
import io.github.daniele21.localllm.models.PresetExecutionPolicy
import io.github.daniele21.localllm.models.PresetLifecycleState
import io.github.daniele21.localllm.models.RegisteredApplication
import io.github.daniele21.localllm.models.StoredPresetExposure
import io.github.daniele21.localllm.models.UseCaseCachePolicy
import io.github.daniele21.localllm.models.UseCaseDefinition
import io.github.daniele21.localllm.models.UseCaseDefinitionState
import io.github.daniele21.localllm.models.UseCasePresetDefinition
import io.github.daniele21.localllm.models.UseCaseRequirements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessApplicationsGatewayTest {
    @Test
    fun `snapshot maps application assignment and default preset`() {
        val gateway = StoreHarnessApplicationsGateway(InMemoryHostControlPlaneStore(state()))

        val snapshot = gateway.snapshot()

        val application = snapshot.applications.single()
        assertEquals("RedactGuard", application.displayName)
        assertEquals("a".repeat(64), application.signerSha256)
        assertEquals(HarnessApplicationStatus.AUTHORIZED, application.status)
        val assignment = application.assignments.single()
        assertEquals("Document PII detection", assignment.displayName)
        assertEquals(2, assignment.useCaseRevision)
        assertEquals(3, assignment.bindingRevision)
        assertTrue(assignment.bindingEnabled)
        assertEquals(HarnessAssignmentStatus.ACTIVE, assignment.status)
        assertEquals("Balanced", assignment.defaultPreset?.displayName)
        assertEquals(listOf("Balanced", "Fast"), assignment.availablePresets.map { it.displayName })
    }

    @Test
    fun `set default preset updates only current binding exposure`() {
        val store = InMemoryHostControlPlaneStore(state())
        val gateway = StoreHarnessApplicationsGateway(store)

        val result = gateway.setDefaultPreset(
            HarnessSetDefaultPresetCommand(
                applicationId = APPLICATION_ID.value,
                useCaseId = USE_CASE_ID.value,
                expectedBindingRevision = 3,
                presetId = "fast",
                presetRevision = 1,
            ),
        )

        assertTrue(result is HarnessControlPlaneMutationResult.Success)
        val assignment = gateway.snapshot().applications.single().assignments.single()
        assertEquals("Fast", assignment.defaultPreset?.displayName)
        assertEquals(1, store.snapshot().exposures.count { it.isDefault })
    }

    @Test
    fun `stale revision fails closed without mutating state`() {
        val store = InMemoryHostControlPlaneStore(state())
        val before = store.snapshot()
        val gateway = StoreHarnessApplicationsGateway(store)

        val result = gateway.setDefaultPreset(
            HarnessSetDefaultPresetCommand(
                applicationId = APPLICATION_ID.value,
                useCaseId = USE_CASE_ID.value,
                expectedBindingRevision = 2,
                presetId = "fast",
                presetRevision = 1,
            ),
        )

        assertEquals(HarnessControlPlaneMutationResult.StaleRevision(2, 3), result)
        assertEquals(before, store.snapshot())
    }

    @Test
    fun `disabled latest binding remains visible and cannot be mutated`() {
        val base = state()
        val disabled = binding(revision = 4, enabled = false)
        val store = InMemoryHostControlPlaneStore(base.copy(bindings = base.bindings + disabled))
        val gateway = StoreHarnessApplicationsGateway(store)

        val assignment = gateway.snapshot().applications.single().assignments.single()
        assertEquals(HarnessAssignmentStatus.DISABLED, assignment.status)

        val result = gateway.setDefaultPreset(
            HarnessSetDefaultPresetCommand(
                applicationId = APPLICATION_ID.value,
                useCaseId = USE_CASE_ID.value,
                expectedBindingRevision = 4,
                presetId = "balanced",
                presetRevision = 1,
            ),
        )
        assertEquals(HarnessControlPlaneMutationResult.Rejected("Assignment is disabled"), result)
    }

    private fun state(): HostControlPlaneState = HostControlPlaneState(
        applications = listOf(
            RegisteredApplication(
                applicationId = APPLICATION_ID,
                packageName = "io.github.daniele21.redactguard",
                signerSha256 = "a".repeat(64),
                displayName = "RedactGuard",
                state = ApplicationRegistrationState.AUTHORIZED,
                firstSeenAtEpochMs = 10,
                lastSeenAtEpochMs = 20,
            ),
        ),
        useCases = listOf(
            UseCaseDefinition(
                useCaseId = USE_CASE_ID,
                displayName = "Document PII detection",
                description = "Detect PII in local documents",
                requirements = UseCaseRequirements(
                    outputMode = OutputMode.JSON_SCHEMA,
                    sessionKind = SessionKind.STATELESS,
                    reasoningSupported = false,
                    minimumContextTokens = 4_096,
                ),
                state = UseCaseDefinitionState.ACTIVE,
                revision = 2,
            ),
        ),
        presets = listOf(
            preset("balanced", "Balanced", PresetCreationSource.SUGGESTED),
            preset("fast", "Fast", PresetCreationSource.CUSTOM),
        ),
        bindings = listOf(binding(revision = 3)),
        exposures = listOf(
            StoredPresetExposure("binding-redactguard-pii", 3, "balanced", 1, isDefault = true),
            StoredPresetExposure("binding-redactguard-pii", 3, "fast", 1, isDefault = false),
        ),
    )

    private fun binding(revision: Int, enabled: Boolean = true): ApplicationUseCaseBinding = ApplicationUseCaseBinding(
        bindingId = "binding-redactguard-pii",
        applicationId = APPLICATION_ID,
        useCaseId = USE_CASE_ID,
        revision = revision,
        enabled = enabled,
    )

    private fun preset(id: String, name: String, source: PresetCreationSource): UseCasePresetDefinition = UseCasePresetDefinition(
        useCaseId = USE_CASE_ID,
        metadata = PresetConsumerMetadata(id, 1, name, "$name preset"),
        creationSource = source,
        state = PresetLifecycleState.PUBLISHED,
        execution = PresetExecutionPolicy(
            modelProfileId = "qwen35-2b-q4",
            inferencePreset = InferencePresetRef(InferencePresetId("$id-generation"), 1),
            contextTokens = 4_096,
            cachePolicy = UseCaseCachePolicy(
                retainModelWarmMs = 60_000,
                reuseStatelessContext = false,
                enablePrefixSnapshot = false,
                enableDeterministicResultCache = false,
            ),
        ),
    )

    private companion object {
        val APPLICATION_ID = ApplicationId("redactguard")
        val USE_CASE_ID = UseCaseId("document-pii-detection")
    }
}
