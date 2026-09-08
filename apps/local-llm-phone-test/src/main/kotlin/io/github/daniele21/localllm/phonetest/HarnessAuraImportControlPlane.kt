package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.SessionKind
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

private const val AURA_MINIMUM_CONTEXT_TOKENS = 4_096
private const val AURA_MAX_INPUT_CHARACTERS = 12_000
private const val AURA_MAX_SCHEMA_CHARACTERS = 4_096
private const val AURA_WARM_RETENTION_MS = 60_000L

/** Aura schema-inference seed graph. Package/signer authorization stays in HarnessSharedRuntimePolicy. */
internal fun HarnessBuiltInControlPlaneSpec.Companion.auraSchema(
    applications: List<HarnessBuiltInApplicationRequirement>,
): HarnessBuiltInControlPlaneSpec = HarnessBuiltInControlPlaneSpec(
    applications = applications.sortedBy { it.applicationId.value },
    useCase = auraUseCase(
        useCaseId = HarnessSharedRuntimeBindings.auraSchemaInferenceUseCaseId,
        displayName = "Aura transaction schema inference",
        description = "Select reviewed Aura spreadsheet schema candidates locally",
    ),
    preset = auraPreset(
        useCaseId = HarnessSharedRuntimeBindings.auraSchemaInferenceUseCaseId,
        displayName = "Aura local schema inference",
        description = "Structured local inference for Aura spreadsheet schema selection",
    ),
)

/** Aura category-classification seed graph. */
internal fun HarnessBuiltInControlPlaneSpec.Companion.auraCategory(
    applications: List<HarnessBuiltInApplicationRequirement>,
): HarnessBuiltInControlPlaneSpec = HarnessBuiltInControlPlaneSpec(
    applications = applications.sortedBy { it.applicationId.value },
    useCase = auraUseCase(
        useCaseId = HarnessSharedRuntimeBindings.auraCategoryClassificationUseCaseId,
        displayName = "Aura transaction category classification",
        description = "Select one supplied Aura category for unresolved transaction descriptions locally",
    ),
    preset = auraPreset(
        useCaseId = HarnessSharedRuntimeBindings.auraCategoryClassificationUseCaseId,
        displayName = "Aura local category classification",
        description = "Structured local inference for Aura transaction category selection",
    ),
    isDefaultBinding = false,
)

private fun auraUseCase(useCaseId: io.github.daniele21.localllm.contracts.UseCaseId, displayName: String, description: String) =
    UseCaseDefinition(
        useCaseId = useCaseId,
        displayName = displayName,
        description = description,
        requirements = UseCaseRequirements(
            outputMode = OutputMode.JSON_SCHEMA,
            sessionKind = SessionKind.STATELESS,
            reasoningSupported = false,
            minimumContextTokens = AURA_MINIMUM_CONTEXT_TOKENS,
            maxInputCharacters = AURA_MAX_INPUT_CHARACTERS,
            maxJsonSchemaCharacters = AURA_MAX_SCHEMA_CHARACTERS,
        ),
        state = UseCaseDefinitionState.ACTIVE,
        revision = 1,
    )

private fun auraPreset(useCaseId: io.github.daniele21.localllm.contracts.UseCaseId, displayName: String, description: String) =
    UseCasePresetDefinition(
        useCaseId = useCaseId,
        metadata = PresetConsumerMetadata(
            presetId = HarnessSharedRuntimeBindings.auraDefaultPreset.id.value,
            revision = HarnessSharedRuntimeBindings.auraDefaultPreset.version,
            displayName = displayName,
            description = description,
        ),
        creationSource = PresetCreationSource.SUGGESTED,
        state = PresetLifecycleState.PUBLISHED,
        execution = PresetExecutionPolicy(
            modelProfileId = null,
            inferencePreset = HarnessSharedRuntimeBindings.auraDefaultPreset,
            contextTokens = AURA_MINIMUM_CONTEXT_TOKENS,
            cachePolicy = UseCaseCachePolicy(
                retainModelWarmMs = AURA_WARM_RETENTION_MS,
                reuseStatelessContext = false,
                enablePrefixSnapshot = false,
                enableDeterministicResultCache = false,
            ),
        ),
    )
