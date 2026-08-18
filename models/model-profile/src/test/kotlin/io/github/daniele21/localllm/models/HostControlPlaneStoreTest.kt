package io.github.daniele21.localllm.models

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.UseCaseId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HostControlPlaneStoreTest {
    @Test
    fun `store canonicalizes deterministic snapshot ordering`() {
        val store = InMemoryHostControlPlaneStore(
            state(
                applications = listOf(application("z-app"), application("a-app")),
                useCases = listOf(useCase(2), useCase(1)),
                bindings = listOf(binding("z-binding", 2, "z-app"), binding("a-binding", 1, "a-app")),
            ),
        )

        val snapshot = store.snapshot()

        assertEquals(listOf("a-app", "z-app"), snapshot.applications.map { it.applicationId.value })
        assertEquals(listOf(1, 2), snapshot.useCases.map { it.revision })
        assertEquals(listOf("a-binding", "z-binding"), snapshot.bindings.map { it.bindingId })
    }

    @Test
    fun `failed transaction does not replace current state`() {
        val initial = state(applications = listOf(application("redactguard")))
        val store = InMemoryHostControlPlaneStore(initial)

        val result = runCatching {
            store.transact {
                HostControlPlaneState(
                    applications = emptyList(),
                    useCases = listOf(useCase(1)),
                    bindings = listOf(binding("binding-1", 1, "redactguard")),
                )
            }
        }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals(initial.canonical(), store.snapshot())
    }

    @Test
    fun `exposure is pinned to exact binding revision and published preset`() {
        val store = InMemoryHostControlPlaneStore(
            state(
                applications = listOf(application("redactguard")),
                useCases = listOf(useCase(1)),
                presets = listOf(preset("balanced", 3, PresetLifecycleState.PUBLISHED)),
                bindings = listOf(binding("binding-1", 7, "redactguard")),
                exposures = listOf(
                    StoredPresetExposure(
                        bindingId = "binding-1",
                        bindingRevision = 7,
                        presetId = "balanced",
                        presetRevision = 3,
                        isDefault = true,
                    ),
                ),
            ),
        )

        val snapshot = store.snapshot()
        val exposure = snapshot.exposures.single()

        assertEquals(7, exposure.bindingRevision)
        assertTrue(exposure.isDefault)
        assertEquals("balanced", snapshot.preset(USE_CASE_ID, exposure.presetId, exposure.presetRevision)?.metadata?.presetId)
    }

    @Test
    fun `dangling exposure is rejected`() {
        val result = runCatching {
            state(
                applications = listOf(application("redactguard")),
                useCases = listOf(useCase(1)),
                presets = listOf(preset("balanced", 3, PresetLifecycleState.PUBLISHED)),
                bindings = listOf(binding("binding-1", 7, "redactguard")),
                exposures = listOf(
                    StoredPresetExposure(
                        bindingId = "binding-1",
                        bindingRevision = 8,
                        presetId = "balanced",
                        presetRevision = 3,
                    ),
                ),
            )
        }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `draft preset cannot be persisted as exposed`() {
        val result = runCatching {
            state(
                applications = listOf(application("redactguard")),
                useCases = listOf(useCase(1)),
                presets = listOf(preset("draft", 1, PresetLifecycleState.DRAFT)),
                bindings = listOf(binding("binding-1", 1, "redactguard")),
                exposures = listOf(
                    StoredPresetExposure(
                        bindingId = "binding-1",
                        bindingRevision = 1,
                        presetId = "draft",
                        presetRevision = 1,
                    ),
                ),
            )
        }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    private fun state(
        applications: List<RegisteredApplication> = emptyList(),
        useCases: List<UseCaseDefinition> = emptyList(),
        presets: List<UseCasePresetDefinition> = emptyList(),
        bindings: List<ApplicationUseCaseBinding> = emptyList(),
        exposures: List<StoredPresetExposure> = emptyList(),
    ): HostControlPlaneState = HostControlPlaneState(applications, useCases, presets, bindings, exposures)

    private fun application(id: String): RegisteredApplication = RegisteredApplication(
        applicationId = ApplicationId(id),
        packageName = "io.github.$id",
        signerSha256 = "a".repeat(64),
        displayName = id,
        state = ApplicationRegistrationState.AUTHORIZED,
        firstSeenAtEpochMs = 10,
        lastSeenAtEpochMs = 20,
    )

    private fun useCase(revision: Int): UseCaseDefinition = UseCaseDefinition(
        useCaseId = USE_CASE_ID,
        displayName = "Document PII detection",
        description = "Detect PII",
        requirements = UseCaseRequirements(
            outputMode = OutputMode.JSON_SCHEMA,
            sessionKind = SessionKind.STATELESS,
            reasoningSupported = false,
            minimumContextTokens = 4_096,
        ),
        state = UseCaseDefinitionState.ACTIVE,
        revision = revision,
    )

    private fun binding(id: String, revision: Int, applicationId: String): ApplicationUseCaseBinding =
        ApplicationUseCaseBinding(
            bindingId = id,
            applicationId = ApplicationId(applicationId),
            useCaseId = USE_CASE_ID,
            revision = revision,
        )

    private fun preset(id: String, revision: Int, state: PresetLifecycleState): UseCasePresetDefinition =
        UseCasePresetDefinition(
            useCaseId = USE_CASE_ID,
            metadata = PresetConsumerMetadata(id, revision, id, "Preset $id"),
            creationSource = PresetCreationSource.CUSTOM,
            state = state,
            execution = PresetExecutionPolicy(
                modelProfileId = "qwen35-2b-q4",
                inferencePreset = InferencePresetRef(InferencePresetId("$id-generation"), revision),
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
        val USE_CASE_ID = UseCaseId("document-pii-detection")
    }
}
