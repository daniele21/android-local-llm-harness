package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.catalog.CuratedModelCatalog
import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.models.ApplicationRegistrationState
import io.github.daniele21.localllm.models.ApplicationUseCaseBinding
import io.github.daniele21.localllm.models.HostControlPlaneState
import io.github.daniele21.localllm.models.HostExecutionEnvironment
import io.github.daniele21.localllm.models.HostExecutionRequest
import io.github.daniele21.localllm.models.HostExecutionResolution
import io.github.daniele21.localllm.models.HostExecutionResolver
import io.github.daniele21.localllm.models.InMemoryHostControlPlaneStore
import io.github.daniele21.localllm.models.OutputMode
import io.github.daniele21.localllm.models.PresetConsumerMetadata
import io.github.daniele21.localllm.models.PresetCreationSource
import io.github.daniele21.localllm.models.PresetExecutionPolicy
import io.github.daniele21.localllm.models.PresetLifecycleState
import io.github.daniele21.localllm.models.Qwen35RuntimeTuningProfiles
import io.github.daniele21.localllm.models.RegisteredApplication
import io.github.daniele21.localllm.models.StoredPresetExposure
import io.github.daniele21.localllm.models.UseCaseCachePolicy
import io.github.daniele21.localllm.models.UseCaseDefinition
import io.github.daniele21.localllm.models.UseCaseDefinitionState
import io.github.daniele21.localllm.models.UseCasePresetDefinition
import io.github.daniele21.localllm.models.UseCaseRequirements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessConsumerRuntimeConfigurationTest {
    @Test
    fun `consumer capability policy follows control-plane exposed and default presets`() {
        val state = state()
        val policy =
            HarnessControlPlaneConsumerPolicyRegistry(InMemoryHostControlPlaneStore(state))
                .find(APPLICATION_ID, USE_CASE_ID)

        requireNotNull(policy)
        assertEquals(setOf(BASE_REF, CUSTOM_REF), policy.exposedPresets)
        assertEquals(CUSTOM_REF, policy.defaultPreset)
        assertTrue(policy.revision.contains("binding=$BINDING_REVISION"))
        assertTrue(policy.revision.contains("custom-pii@1:default"))
        assertNotEquals(HarnessOmbraConsumerPolicy.REVISION, policy.revision)
    }

    @Test
    fun `activated custom preset materializes canonical generation and control-plane context`() {
        val model = curatedModel()
        val runtimeBase = HarnessSharedRuntimeBindings.resolveOmbra(model, APPLICATION_ID)
        val state = state(runtimeBase.model.id)
        val store = InMemoryHostControlPlaneStore(state)
        val resolution =
            HostExecutionResolver(store).resolve(
                HostExecutionRequest(
                    applicationId = APPLICATION_ID,
                    useCaseId = USE_CASE_ID,
                    presetId = CUSTOM_REF.id.value,
                    presetRevision = CUSTOM_REF.version,
                ),
                HostExecutionEnvironment(
                    modelProfiles = listOf(runtimeBase.model),
                    installedModelDigests = setOf(runtimeBase.model.artifact.digest),
                    backendId = "llama.cpp",
                    backendRevision = Qwen35RuntimeTuningProfiles.LLAMA_CPP_REVISION,
                ),
            )
        require(resolution is HostExecutionResolution.Success)

        val materialized =
            HarnessActivatedUseCaseMaterializer.materialize(
                model = model,
                applicationId = APPLICATION_ID,
                execution = resolution.execution,
                state = store.snapshot(),
            )

        val canonical = runtimeBase.useCase.presets.single { it.ref == BASE_REF }
        val custom = materialized.useCase.presets.single { it.ref == CUSTOM_REF }
        assertEquals(CUSTOM_REF, materialized.useCase.defaultPreset)
        assertEquals(canonical.generation, custom.generation)
        assertEquals(canonical.systemPromptVersion, custom.systemPromptVersion)
        assertEquals(CUSTOM_CONTEXT, custom.contextPreference.preferredTokens)
        assertEquals(CUSTOM_CONTEXT, custom.contextPreference.maximumTokens)
        assertEquals(CUSTOM_CACHE, materialized.useCase.cachePolicy)
        assertEquals(runtimeBase.model.artifact.digest, materialized.model.artifact.digest)
        assertEquals(APPLICATION_ID, materialized.binding.applicationId)
        assertEquals(USE_CASE_ID, materialized.binding.useCaseId)
    }

    private fun state(modelProfileId: String? = null): HostControlPlaneState =
        HostControlPlaneState(
            applications =
                listOf(
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
            useCases =
                listOf(
                    UseCaseDefinition(
                        useCaseId = USE_CASE_ID,
                        displayName = "Document PII detection",
                        description = "Detect PII in local documents",
                        requirements =
                            UseCaseRequirements(
                                outputMode = OutputMode.JSON_SCHEMA,
                                sessionKind = SessionKind.STATELESS,
                                reasoningSupported = false,
                                minimumContextTokens = 2_048,
                            ),
                        state = UseCaseDefinitionState.ACTIVE,
                        revision = USE_CASE_REVISION,
                    ),
                ),
            presets =
                listOf(
                    preset(
                        id = BASE_REF.id.value,
                        revision = BASE_REF.version,
                        displayName = "Balanced",
                        modelProfileId = null,
                        contextTokens = 4_096,
                        cachePolicy = BASE_CACHE,
                    ),
                    preset(
                        id = CUSTOM_REF.id.value,
                        revision = CUSTOM_REF.version,
                        displayName = "Private document accuracy",
                        modelProfileId = modelProfileId,
                        contextTokens = CUSTOM_CONTEXT,
                        cachePolicy = CUSTOM_CACHE,
                    ),
                ),
            bindings =
                listOf(
                    ApplicationUseCaseBinding(
                        bindingId = BINDING_ID,
                        applicationId = APPLICATION_ID,
                        useCaseId = USE_CASE_ID,
                        revision = BINDING_REVISION,
                        enabled = true,
                    ),
                ),
            exposures =
                listOf(
                    StoredPresetExposure(
                        bindingId = BINDING_ID,
                        bindingRevision = BINDING_REVISION,
                        presetId = BASE_REF.id.value,
                        presetRevision = BASE_REF.version,
                        isDefault = false,
                    ),
                    StoredPresetExposure(
                        bindingId = BINDING_ID,
                        bindingRevision = BINDING_REVISION,
                        presetId = CUSTOM_REF.id.value,
                        presetRevision = CUSTOM_REF.version,
                        isDefault = true,
                    ),
                ),
        )

    private fun preset(
        id: String,
        revision: Int,
        displayName: String,
        modelProfileId: String?,
        contextTokens: Int,
        cachePolicy: UseCaseCachePolicy,
    ): UseCasePresetDefinition =
        UseCasePresetDefinition(
            useCaseId = USE_CASE_ID,
            metadata = PresetConsumerMetadata(id, revision, displayName, "$displayName configuration"),
            creationSource = if (id == BASE_REF.id.value) PresetCreationSource.SUGGESTED else PresetCreationSource.CUSTOM,
            state = PresetLifecycleState.PUBLISHED,
            execution =
                PresetExecutionPolicy(
                    modelProfileId = modelProfileId,
                    inferencePreset = BASE_REF,
                    contextTokens = contextTokens,
                    cachePolicy = cachePolicy,
                ),
        )

    private fun curatedModel(): ImportedPhoneModel {
        val artifact = CuratedModelCatalog.releases.first().artifact
        return ImportedPhoneModel(
            digest = artifact.digest,
            fileName = artifact.fileName,
            sizeBytes = artifact.sizeBytes,
            architecture = artifact.architecture,
            quantization = artifact.quantization,
        )
    }

    private companion object {
        val APPLICATION_ID = ApplicationId("redactguard")
        val USE_CASE_ID = UseCaseId("document-pii-detection")
        val BASE_REF = HarnessSharedRuntimeBindings.ombraDefaultPreset
        val CUSTOM_REF = InferencePresetRef(InferencePresetId("custom-pii"), 1)
        const val BINDING_ID = "binding-redactguard-pii"
        const val BINDING_REVISION = 3
        const val USE_CASE_REVISION = 2
        const val CUSTOM_CONTEXT = 2_048
        val BASE_CACHE = UseCaseCachePolicy(
            retainModelWarmMs = 60_000,
            reuseStatelessContext = false,
            enablePrefixSnapshot = false,
            enableDeterministicResultCache = false,
        )
        val CUSTOM_CACHE = UseCaseCachePolicy(
            retainModelWarmMs = 15_000,
            reuseStatelessContext = false,
            enablePrefixSnapshot = false,
            enableDeterministicResultCache = false,
        )
    }
}
