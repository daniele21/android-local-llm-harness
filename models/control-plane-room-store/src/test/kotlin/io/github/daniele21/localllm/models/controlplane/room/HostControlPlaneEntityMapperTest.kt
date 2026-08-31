package io.github.daniele21.localllm.models.controlplane.room

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.models.ApplicationRegistrationState
import io.github.daniele21.localllm.models.ApplicationUseCaseBinding
import io.github.daniele21.localllm.models.HostControlPlaneState
import io.github.daniele21.localllm.models.OutputMode
import io.github.daniele21.localllm.models.PresetConsumerMetadata
import io.github.daniele21.localllm.models.PresetCreationSource
import io.github.daniele21.localllm.models.PresetExecutionPolicy
import io.github.daniele21.localllm.models.PresetGenerationOverrides
import io.github.daniele21.localllm.models.PresetLifecycleState
import io.github.daniele21.localllm.models.PresetSeedMode
import io.github.daniele21.localllm.models.RegisteredApplication
import io.github.daniele21.localllm.models.StoredPresetExposure
import io.github.daniele21.localllm.models.ThinkingMode
import io.github.daniele21.localllm.models.UseCaseCachePolicy
import io.github.daniele21.localllm.models.UseCaseDefinition
import io.github.daniele21.localllm.models.UseCaseDefinitionState
import io.github.daniele21.localllm.models.UseCasePresetDefinition
import io.github.daniele21.localllm.models.UseCaseRequirements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HostControlPlaneEntityMapperTest {
    @Test
    fun `round trip preserves exact revision residency binding and generation configuration`() {
        val expected = state()

        val actual = HostControlPlaneEntityMapper.fromEntities(
            HostControlPlaneEntityMapper.toEntities(expected),
        )

        assertEquals(expected.canonical(), actual)
        assertEquals(90_000L, actual.presets.single().execution.cachePolicy.retainModelWarmMs)
        assertEquals(
            PresetGenerationOverrides(
                maxOutputTokens = 768,
                temperature = 0.45f,
                topP = 0.82f,
                topK = 32,
                minP = 0.04f,
                presencePenalty = 0.2f,
                repeatPenalty = 1.1f,
                repeatLastN = 128,
                thinkingMode = ThinkingMode.ENABLED,
                seedMode = PresetSeedMode.FIXED,
                fixedSeed = 84L,
            ),
            actual.presets.single().execution.generationOverrides,
        )
        assertEquals(7, actual.exposures.single().bindingRevision)
        assertTrue(actual.bindings.single().isDefault)
    }

    private fun state(): HostControlPlaneState = HostControlPlaneState(
        applications = listOf(
            RegisteredApplication(
                applicationId = APP_ID,
                packageName = "io.github.redactguard",
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
                description = "Detect configured PII",
                requirements = UseCaseRequirements(
                    outputMode = OutputMode.JSON_SCHEMA,
                    sessionKind = SessionKind.STATELESS,
                    reasoningSupported = false,
                    minimumContextTokens = 4_096,
                    maxInputCharacters = 120_000,
                    maxJsonSchemaCharacters = 16_000,
                ),
                state = UseCaseDefinitionState.ACTIVE,
                revision = 2,
            ),
        ),
        presets = listOf(
            UseCasePresetDefinition(
                useCaseId = USE_CASE_ID,
                metadata = PresetConsumerMetadata("balanced", 3, "Balanced", "Balanced local PII analysis"),
                creationSource = PresetCreationSource.CUSTOM,
                state = PresetLifecycleState.PUBLISHED,
                execution = PresetExecutionPolicy(
                    modelProfileId = "qwen35-2b-q4",
                    inferencePreset = InferencePresetRef(InferencePresetId("pii-json"), 5),
                    contextTokens = 8_192,
                    cachePolicy = UseCaseCachePolicy(
                        retainModelWarmMs = 90_000,
                        reuseStatelessContext = true,
                        enablePrefixSnapshot = false,
                        enableDeterministicResultCache = false,
                    ),
                    generationOverrides = PresetGenerationOverrides(
                        maxOutputTokens = 768,
                        temperature = 0.45f,
                        topP = 0.82f,
                        topK = 32,
                        minP = 0.04f,
                        presencePenalty = 0.2f,
                        repeatPenalty = 1.1f,
                        repeatLastN = 128,
                        thinkingMode = ThinkingMode.ENABLED,
                        seedMode = PresetSeedMode.FIXED,
                        fixedSeed = 84L,
                    ),
                ),
            ),
        ),
        bindings = listOf(
            ApplicationUseCaseBinding(
                bindingId = BINDING_ID,
                applicationId = APP_ID,
                useCaseId = USE_CASE_ID,
                revision = 7,
                isDefault = true,
            ),
        ),
        exposures = listOf(
            StoredPresetExposure(
                bindingId = BINDING_ID,
                bindingRevision = 7,
                presetId = "balanced",
                presetRevision = 3,
                isDefault = true,
            ),
        ),
    )

    private companion object {
        val APP_ID = ApplicationId("redactguard")
        val USE_CASE_ID = UseCaseId("document-pii-detection")
        const val BINDING_ID = "binding-redactguard-pii"
    }
}
