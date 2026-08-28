package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ConsumerRuntimePhase
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

class HarnessApplicationsRuntimeGatewayTest {
    @Test
    fun `snapshot enriches assignment from runtime source without changing control plane`() {
        val state = state()
        val store = InMemoryHostControlPlaneStore(state)
        val activePreset = InferencePresetRef(InferencePresetId("balanced"), 1)
        val runtimeSource = HarnessApplicationsRuntimeSource { applicationId, useCaseId ->
            assertEquals(APPLICATION_ID, applicationId)
            assertEquals(USE_CASE_ID, useCaseId)
            HarnessAssignmentRuntimeSummary(
                activationActive = true,
                activePreset = activePreset,
                effectiveModelProfileId = "qwen35-2b-q4",
                phase = ConsumerRuntimePhase.READY,
            )
        }
        val gateway = StoreHarnessApplicationsGateway(store, runtimeSource)

        val snapshot = gateway.snapshot()

        val runtime = snapshot.applications.single().assignments.single().runtime
        assertTrue(runtime.activationActive)
        assertEquals(activePreset, runtime.activePreset)
        assertEquals("qwen35-2b-q4", runtime.effectiveModelProfileId)
        assertEquals(ConsumerRuntimePhase.READY, runtime.phase)
        assertEquals(state.canonical(), store.snapshot())
    }

    private fun state(): HostControlPlaneState {
        val binding = ApplicationUseCaseBinding(
            bindingId = "binding-redactguard-pii",
            applicationId = APPLICATION_ID,
            useCaseId = USE_CASE_ID,
            revision = 1,
            enabled = true,
        )
        val preset = UseCasePresetDefinition(
            useCaseId = USE_CASE_ID,
            metadata = PresetConsumerMetadata("balanced", 1, "Balanced", "Balanced local PII configuration"),
            creationSource = PresetCreationSource.SUGGESTED,
            state = PresetLifecycleState.PUBLISHED,
            execution = PresetExecutionPolicy(
                modelProfileId = "qwen35-2b-q4",
                inferencePreset = InferencePresetRef(InferencePresetId("qwen35-json"), 1),
                contextTokens = 4_096,
                cachePolicy = UseCaseCachePolicy(
                    retainModelWarmMs = 60_000,
                    reuseStatelessContext = false,
                    enablePrefixSnapshot = false,
                    enableDeterministicResultCache = false,
                ),
            ),
        )
        return HostControlPlaneState(
            applications = listOf(
                RegisteredApplication(
                    applicationId = APPLICATION_ID,
                    packageName = "io.github.daniele21.redactguard",
                    signerSha256 = "a".repeat(64),
                    displayName = "RedactGuard",
                    state = ApplicationRegistrationState.AUTHORIZED,
                    firstSeenAtEpochMs = 1,
                    lastSeenAtEpochMs = 2,
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
                    revision = 1,
                ),
            ),
            presets = listOf(preset),
            bindings = listOf(binding),
            exposures = listOf(
                StoredPresetExposure(
                    bindingId = binding.bindingId,
                    bindingRevision = binding.revision,
                    presetId = preset.metadata.presetId,
                    presetRevision = preset.metadata.revision,
                    isDefault = true,
                ),
            ),
        )
    }

    private companion object {
        val APPLICATION_ID = ApplicationId("redactguard")
        val USE_CASE_ID = UseCaseId("document-pii-detection")
    }
}
