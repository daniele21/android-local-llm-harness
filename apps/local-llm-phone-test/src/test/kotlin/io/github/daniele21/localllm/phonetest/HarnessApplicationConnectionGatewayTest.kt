package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.models.ApplicationRegistrationState
import io.github.daniele21.localllm.models.HostControlPlaneState
import io.github.daniele21.localllm.models.InMemoryHostControlPlaneStore
import io.github.daniele21.localllm.models.OutputMode
import io.github.daniele21.localllm.models.PresetConsumerMetadata
import io.github.daniele21.localllm.models.PresetCreationSource
import io.github.daniele21.localllm.models.PresetExecutionPolicy
import io.github.daniele21.localllm.models.PresetLifecycleState
import io.github.daniele21.localllm.models.UseCaseCachePolicy
import io.github.daniele21.localllm.models.UseCaseDefinition
import io.github.daniele21.localllm.models.UseCaseDefinitionState
import io.github.daniele21.localllm.models.UseCasePresetDefinition
import io.github.daniele21.localllm.models.UseCaseRequirements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessApplicationConnectionGatewayTest {
    @Test
    fun `create connection persists authorized application assignment and default preset`() {
        val store = InMemoryHostControlPlaneStore(baseState())
        val gateway = StoreHarnessApplicationsGateway(store)

        val result = gateway.createApplicationConnection(createCommand())

        assertTrue(result is HarnessControlPlaneMutationResult.Success)
        val application = gateway.snapshot().applications.single()
        assertEquals("Example consumer", application.displayName)
        assertEquals(HarnessApplicationStatus.AUTHORIZED, application.status)
        val assignment = application.assignments.single()
        assertEquals(USE_CASE_ID.value, assignment.useCaseId)
        assertEquals("Balanced", assignment.defaultPreset?.displayName)
        assertEquals(1, store.snapshot().exposures.count { it.isDefault })
    }

    @Test
    fun `disable and re-enable connection retains assignment and preset configuration`() {
        val store = InMemoryHostControlPlaneStore(baseState())
        val gateway = StoreHarnessApplicationsGateway(store)
        gateway.createApplicationConnection(createCommand())
        val before = store.snapshot()

        assertTrue(
            gateway.setApplicationConnectionEnabled(
                HarnessSetApplicationConnectionEnabledCommand(APPLICATION_ID.value, enabled = false),
            ) is HarnessControlPlaneMutationResult.Success,
        )
        assertEquals(HarnessApplicationStatus.DISABLED, gateway.snapshot().applications.single().status)
        val disabled = store.snapshot()
        assertEquals(before.bindings, disabled.bindings)
        assertEquals(before.exposures, disabled.exposures)
        assertEquals(ApplicationRegistrationState.DISABLED, disabled.applications.single().state)

        assertTrue(
            gateway.setApplicationConnectionEnabled(
                HarnessSetApplicationConnectionEnabledCommand(APPLICATION_ID.value, enabled = true),
            ) is HarnessControlPlaneMutationResult.Success,
        )
        assertEquals(HarnessApplicationStatus.AUTHORIZED, gateway.snapshot().applications.single().status)
        assertEquals(before.bindings, store.snapshot().bindings)
        assertEquals(before.exposures, store.snapshot().exposures)
    }

    @Test
    fun `duplicate package is rejected without mutating state`() {
        val store = InMemoryHostControlPlaneStore(baseState())
        val gateway = StoreHarnessApplicationsGateway(store)
        gateway.createApplicationConnection(createCommand())
        val before = store.snapshot()

        val result = gateway.createApplicationConnection(
            createCommand().copy(applicationId = "other-app"),
        )

        assertEquals(HarnessControlPlaneMutationResult.Rejected("Package name is already connected"), result)
        assertEquals(before, store.snapshot())
    }

    @Test
    fun `snapshot exposes only active published connection options`() {
        val gateway = StoreHarnessApplicationsGateway(InMemoryHostControlPlaneStore(baseState()))

        val option = gateway.snapshot().connectionOptions.single()

        assertEquals(USE_CASE_ID.value, option.useCaseId)
        assertEquals(listOf("Balanced"), option.presets.map { it.displayName })
    }

    private fun createCommand() = HarnessCreateApplicationConnectionCommand(
        applicationId = APPLICATION_ID.value,
        displayName = "Example consumer",
        packageName = "com.example.consumer",
        signerSha256 = "a".repeat(64),
        useCaseId = USE_CASE_ID.value,
        presetId = "balanced",
        presetRevision = 1,
    )

    private fun baseState() = HostControlPlaneState(
        applications = emptyList(),
        useCases = listOf(
            UseCaseDefinition(
                useCaseId = USE_CASE_ID,
                displayName = "Document PII detection",
                description = "Detect PII locally",
                requirements = UseCaseRequirements(
                    outputMode = OutputMode.JSON_SCHEMA,
                    sessionKind = SessionKind.STATELESS,
                    reasoningSupported = false,
                    minimumContextTokens = 4_096,
                ),
                state = UseCaseDefinitionState.ACTIVE,
                revision = 1,
            ),
        ),
        presets = listOf(
            UseCasePresetDefinition(
                useCaseId = USE_CASE_ID,
                metadata = PresetConsumerMetadata("balanced", 1, "Balanced", "Balanced preset"),
                creationSource = PresetCreationSource.SUGGESTED,
                state = PresetLifecycleState.PUBLISHED,
                execution = PresetExecutionPolicy(
                    modelProfileId = null,
                    inferencePreset = InferencePresetRef(InferencePresetId("balanced"), 1),
                    contextTokens = 4_096,
                    cachePolicy = UseCaseCachePolicy(0, false, false, false),
                ),
            ),
        ),
        bindings = emptyList(),
        exposures = emptyList(),
    )

    private companion object {
        val APPLICATION_ID = ApplicationId("example-consumer")
        val USE_CASE_ID = UseCaseId("document-pii-detection")
    }
}
