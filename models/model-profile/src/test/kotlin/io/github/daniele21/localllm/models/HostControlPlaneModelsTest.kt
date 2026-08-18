package io.github.daniele21.localllm.models

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.UseCaseId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HostControlPlaneModelsTest {
    @Test
    fun `published custom preset is exposed without concrete model metadata`() {
        val configuration = configuration(
            presets = listOf(
                preset("quality", 3, PresetCreationSource.CUSTOM, PresetLifecycleState.PUBLISHED),
            ),
            exposures = listOf(PresetExposure(BINDING_ID, "quality", 3, isDefault = true)),
        )

        assertEquals(listOf("quality"), configuration.consumerPresets().map { it.presetId })
        assertEquals(3, configuration.defaultConsumerPreset()?.revision)
        assertEquals("Quality", configuration.defaultConsumerPreset()?.displayName)
    }

    @Test
    fun `draft preset cannot be exposed to a consumer`() {
        val result = runCatching {
            configuration(
                presets = listOf(
                    preset("draft", 1, PresetCreationSource.CUSTOM, PresetLifecycleState.DRAFT),
                ),
                exposures = listOf(PresetExposure(BINDING_ID, "draft", 1, isDefault = true)),
            )
        }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `at most one exposed preset can be default`() {
        val result = runCatching {
            configuration(
                presets = listOf(
                    preset("fast", 1, PresetCreationSource.SUGGESTED, PresetLifecycleState.PUBLISHED),
                    preset("quality", 1, PresetCreationSource.SUGGESTED, PresetLifecycleState.PUBLISHED),
                ),
                exposures = listOf(
                    PresetExposure(BINDING_ID, "fast", 1, isDefault = true),
                    PresetExposure(BINDING_ID, "quality", 1, isDefault = true),
                ),
            )
        }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `configuration may intentionally expose no preset`() {
        val configuration = configuration(
            presets = listOf(
                preset("quality", 1, PresetCreationSource.CUSTOM, PresetLifecycleState.PUBLISHED),
            ),
            exposures = emptyList(),
        )

        assertTrue(configuration.consumerPresets().isEmpty())
        assertNull(configuration.defaultConsumerPreset())
    }

    @Test
    fun `application signer identity must be canonical sha256`() {
        val result = runCatching {
            application(signerSha256 = "not-a-digest")
        }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `json schema size is rejected for non schema use case`() {
        val result = runCatching {
            UseCaseRequirements(
                outputMode = OutputMode.JSON,
                sessionKind = SessionKind.STATELESS,
                reasoningSupported = false,
                minimumContextTokens = 4096,
                maxJsonSchemaCharacters = 1024,
            )
        }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    private fun configuration(presets: List<UseCasePresetDefinition>, exposures: List<PresetExposure>): HostControlPlaneConfiguration =
        HostControlPlaneConfiguration(
            application = application(),
            useCase = useCase(),
            binding = ApplicationUseCaseBinding(
                bindingId = BINDING_ID,
                applicationId = APP_ID,
                useCaseId = USE_CASE_ID,
                revision = 7,
            ),
            presets = presets,
            exposures = exposures,
        )

    private fun application(signerSha256: String = "a".repeat(64)): RegisteredApplication =
        RegisteredApplication(
            applicationId = APP_ID,
            packageName = "io.github.example.consumer",
            signerSha256 = signerSha256,
            displayName = "Example consumer",
            state = ApplicationRegistrationState.AUTHORIZED,
            firstSeenAtEpochMs = 10,
            lastSeenAtEpochMs = 20,
        )

    private fun useCase(): UseCaseDefinition =
        UseCaseDefinition(
            useCaseId = USE_CASE_ID,
            displayName = "Document PII detection",
            description = "Detect configured PII in text documents",
            requirements = UseCaseRequirements(
                outputMode = OutputMode.JSON_SCHEMA,
                sessionKind = SessionKind.STATELESS,
                reasoningSupported = false,
                minimumContextTokens = 4096,
                maxInputCharacters = 32_000,
                maxJsonSchemaCharacters = 16_000,
            ),
            state = UseCaseDefinitionState.ACTIVE,
            revision = 2,
        )

    private fun preset(
        id: String,
        revision: Int,
        source: PresetCreationSource,
        state: PresetLifecycleState,
    ): UseCasePresetDefinition =
        UseCasePresetDefinition(
            useCaseId = USE_CASE_ID,
            metadata = PresetConsumerMetadata(
                presetId = id,
                revision = revision,
                displayName = id.replaceFirstChar { it.uppercase() },
                description = "Preset $id",
            ),
            creationSource = source,
            state = state,
            execution = PresetExecutionPolicy(
                modelProfileId = "qwen35-2b-q4",
                inferencePreset = InferencePresetRef(InferencePresetId("$id-generation"), revision),
                contextTokens = 4096,
                cachePolicy = UseCaseCachePolicy(
                    retainModelWarmMs = 60_000,
                    reuseStatelessContext = false,
                    enablePrefixSnapshot = false,
                    enableDeterministicResultCache = false,
                ),
            ),
        )

    private companion object {
        val APP_ID = ApplicationId("example-consumer")
        val USE_CASE_ID = UseCaseId("document-pii-detection")
        const val BINDING_ID = "binding-example-pii"
    }
}
