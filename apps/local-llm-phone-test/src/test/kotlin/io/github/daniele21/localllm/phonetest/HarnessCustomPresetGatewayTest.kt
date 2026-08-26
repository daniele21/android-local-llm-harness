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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessCustomPresetGatewayTest {
    @Test
    fun `create custom preset clones owned base policy and exposes new preset without changing default`() {
        val store = InMemoryHostControlPlaneStore(state())
        val gateway = StoreHarnessCustomPresetGateway(store)

        val result = gateway.createCustomPreset(
            command(
                presetId = "custom-high-accuracy",
                modelProfileId = "qwen35-4b-q4",
                contextTokens = 8_192,
            ),
        )

        assertEquals(HarnessCustomPresetMutationResult.Success("custom-high-accuracy", 1), result)
        val persisted = store.snapshot()
        val base = persisted.preset(USE_CASE_ID, "balanced", 1)!!
        val custom = persisted.preset(USE_CASE_ID, "custom-high-accuracy", 1)!!
        assertEquals(PresetCreationSource.CUSTOM, custom.creationSource)
        assertEquals(PresetLifecycleState.PUBLISHED, custom.state)
        assertEquals("High accuracy PII", custom.metadata.displayName)
        assertEquals("qwen35-4b-q4", custom.execution.modelProfileId)
        assertEquals(8_192, custom.execution.contextTokens)
        assertEquals(base.execution.inferencePreset, custom.execution.inferencePreset)
        assertEquals(base.execution.cachePolicy, custom.execution.cachePolicy)
        assertTrue(
            persisted.exposures.any {
                it.bindingId == BINDING_ID &&
                    it.bindingRevision == 3 &&
                    it.presetId == "custom-high-accuracy" &&
                    it.presetRevision == 1 &&
                    !it.isDefault
            },
        )
        assertEquals("balanced", persisted.exposures.single { it.isDefault }.presetId)
    }

    @Test
    fun `stale binding revision fails closed without creating preset`() {
        val store = InMemoryHostControlPlaneStore(state())
        val before = store.snapshot()
        val gateway = StoreHarnessCustomPresetGateway(store)

        val result = gateway.createCustomPreset(command(expectedBindingRevision = 2))

        assertEquals(HarnessCustomPresetMutationResult.StaleRevision(2, 3), result)
        assertEquals(before, store.snapshot())
    }

    @Test
    fun `base preset must be exposed through current binding`() {
        val base = state()
        val store = InMemoryHostControlPlaneStore(base.copy(exposures = emptyList()))
        val gateway = StoreHarnessCustomPresetGateway(store)

        val result = gateway.createCustomPreset(command())

        assertEquals(
            HarnessCustomPresetMutationResult.Rejected("Base preset is no longer available for this assignment"),
            result,
        )
        assertEquals(1, store.snapshot().presets.size)
    }

    @Test
    fun `context below use case minimum is rejected`() {
        val store = InMemoryHostControlPlaneStore(state())
        val gateway = StoreHarnessCustomPresetGateway(store)

        val result = gateway.createCustomPreset(command(contextTokens = 2_048))

        assertEquals(
            HarnessCustomPresetMutationResult.Rejected("Context must be at least 4096 tokens for this use case"),
            result,
        )
        assertFalse(store.snapshot().presets.any { it.metadata.presetId == "custom-pii" })
    }

    private fun command(
        presetId: String = "custom-pii",
        expectedBindingRevision: Int = 3,
        modelProfileId: String? = null,
        contextTokens: Int? = 4_096,
    ): HarnessCreateCustomPresetCommand = HarnessCreateCustomPresetCommand(
        applicationId = APPLICATION_ID.value,
        useCaseId = USE_CASE_ID.value,
        expectedBindingRevision = expectedBindingRevision,
        presetId = presetId,
        basePresetId = "balanced",
        basePresetRevision = 1,
        displayName = "High accuracy PII",
        modelProfileId = modelProfileId,
        contextTokens = contextTokens,
    )

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
        presets = listOf(preset()),
        bindings = listOf(
            ApplicationUseCaseBinding(
                bindingId = BINDING_ID,
                applicationId = APPLICATION_ID,
                useCaseId = USE_CASE_ID,
                revision = 3,
                enabled = true,
            ),
        ),
        exposures = listOf(
            StoredPresetExposure(
                bindingId = BINDING_ID,
                bindingRevision = 3,
                presetId = "balanced",
                presetRevision = 1,
                isDefault = true,
            ),
        ),
    )

    private fun preset(): UseCasePresetDefinition = UseCasePresetDefinition(
        useCaseId = USE_CASE_ID,
        metadata = PresetConsumerMetadata("balanced", 1, "Balanced", "Balanced local PII"),
        creationSource = PresetCreationSource.SUGGESTED,
        state = PresetLifecycleState.PUBLISHED,
        execution = PresetExecutionPolicy(
            modelProfileId = "qwen35-2b-q4",
            inferencePreset = InferencePresetRef(InferencePresetId("balanced-generation"), 1),
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
        const val BINDING_ID = "binding-redactguard-pii"
        val APPLICATION_ID = ApplicationId("redactguard")
        val USE_CASE_ID = UseCaseId("document-pii-detection")
    }
}
