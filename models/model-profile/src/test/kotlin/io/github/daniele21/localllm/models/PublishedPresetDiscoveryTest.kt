package io.github.daniele21.localllm.models

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.UseCaseId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublishedPresetDiscoveryTest {
    @Test
    fun `consumer sees only presets exposed by latest binding revision without execution identity`() {
        val discovery = PublishedPresetDiscovery(InMemoryHostControlPlaneStore(state()))

        val result = discovery.discover(APP_ID, USE_CASE_ID) as PublishedPresetDiscoveryResult.Success

        assertEquals(2, result.presets.size)
        assertEquals(2, result.bindingRevision)
        assertEquals("balanced", result.presets.first().presetId)
        assertTrue(result.presets.first().isDefault)
        assertEquals("quality", result.presets.last().presetId)
        assertFalse(result.presets.last().isDefault)
    }

    @Test
    fun `disabled latest binding does not fall back to older exposures`() {
        val source = state().copy(
            bindings = listOf(binding(1, true), binding(2, false)),
            exposures = listOf(exposure(1, "balanced", 1, true)),
        )
        val result = PublishedPresetDiscovery(InMemoryHostControlPlaneStore(source)).discover(APP_ID, USE_CASE_ID)

        result as PublishedPresetDiscoveryResult.Failure
        assertEquals(PublishedPresetDiscoveryFailure.USE_CASE_NOT_ASSIGNED, result.reason)
    }

    private fun state(): HostControlPlaneState = HostControlPlaneState(
        applications = listOf(
            RegisteredApplication(
                APP_ID,
                "io.github.redactguard",
                "a".repeat(64),
                "RedactGuard",
                ApplicationRegistrationState.AUTHORIZED,
                1,
                2,
            ),
        ),
        useCases = listOf(
            UseCaseDefinition(
                USE_CASE_ID,
                "Document PII detection",
                "Detect configured PII",
                UseCaseRequirements(OutputMode.JSON_SCHEMA, SessionKind.STATELESS, false, 4_096),
                UseCaseDefinitionState.ACTIVE,
                1,
            ),
        ),
        presets = listOf(
            preset("balanced", 1, PresetLifecycleState.PUBLISHED, "model-a"),
            preset("quality", 3, PresetLifecycleState.PUBLISHED, "model-b"),
            preset("draft", 1, PresetLifecycleState.DRAFT, "model-secret"),
        ),
        bindings = listOf(binding(1, true), binding(2, true)),
        exposures = listOf(
            exposure(2, "balanced", 1, true),
            exposure(2, "quality", 3, false),
        ),
    )

    private fun binding(revision: Int, enabled: Boolean) = ApplicationUseCaseBinding(
        bindingId = BINDING_ID,
        applicationId = APP_ID,
        useCaseId = USE_CASE_ID,
        revision = revision,
        enabled = enabled,
    )

    private fun exposure(bindingRevision: Int, presetId: String, revision: Int, isDefault: Boolean) = StoredPresetExposure(
        bindingId = BINDING_ID,
        bindingRevision = bindingRevision,
        presetId = presetId,
        presetRevision = revision,
        isDefault = isDefault,
    )

    private fun preset(id: String, revision: Int, state: PresetLifecycleState, modelId: String) = UseCasePresetDefinition(
        useCaseId = USE_CASE_ID,
        metadata = PresetConsumerMetadata(id, revision, id.replaceFirstChar(Char::uppercase), "Preset $id"),
        creationSource = PresetCreationSource.CUSTOM,
        state = state,
        execution = PresetExecutionPolicy(
            modelProfileId = modelId,
            inferencePreset = InferencePresetRef(InferencePresetId("$id-generation"), revision),
            contextTokens = 4_096,
            cachePolicy = UseCaseCachePolicy(),
        ),
    )

    private companion object {
        val APP_ID = ApplicationId("redactguard")
        val USE_CASE_ID = UseCaseId("document-pii-detection")
        const val BINDING_ID = "binding-redactguard"
    }
}
