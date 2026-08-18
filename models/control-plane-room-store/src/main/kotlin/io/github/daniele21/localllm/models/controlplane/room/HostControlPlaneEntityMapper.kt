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
import io.github.daniele21.localllm.models.PresetLifecycleState
import io.github.daniele21.localllm.models.RegisteredApplication
import io.github.daniele21.localllm.models.StoredPresetExposure
import io.github.daniele21.localllm.models.UseCaseCachePolicy
import io.github.daniele21.localllm.models.UseCaseDefinition
import io.github.daniele21.localllm.models.UseCaseDefinitionState
import io.github.daniele21.localllm.models.UseCasePresetDefinition
import io.github.daniele21.localllm.models.UseCaseRequirements

object HostControlPlaneEntityMapper {
    fun toEntities(state: HostControlPlaneState): HostControlPlaneEntitySet {
        val canonical = state.canonical()
        return HostControlPlaneEntitySet(
            applications = canonical.applications.map(::toEntity),
            useCases = canonical.useCases.map(::toEntity),
            presets = canonical.presets.map(::toEntity),
            bindings = canonical.bindings.map(::toEntity),
            exposures = canonical.exposures.map(::toEntity),
        )
    }

    fun fromEntities(entities: HostControlPlaneEntitySet): HostControlPlaneState = HostControlPlaneState(
        applications = entities.applications.map(::fromEntity),
        useCases = entities.useCases.map(::fromEntity),
        presets = entities.presets.map(::fromEntity),
        bindings = entities.bindings.map(::fromEntity),
        exposures = entities.exposures.map(::fromEntity),
    ).canonical()
}

private fun toEntity(value: RegisteredApplication) = HostControlPlaneEntities.ApplicationEntity(
    value.applicationId.value,
    value.packageName,
    value.signerSha256,
    value.displayName,
    value.state.name,
    value.firstSeenAtEpochMs,
    value.lastSeenAtEpochMs,
)

private fun fromEntity(value: HostControlPlaneEntities.ApplicationEntity) = RegisteredApplication(
    applicationId = ApplicationId(value.applicationId),
    packageName = value.packageName,
    signerSha256 = value.signerSha256,
    displayName = value.displayName,
    state = enumValueOf<ApplicationRegistrationState>(value.state),
    firstSeenAtEpochMs = value.firstSeenAtEpochMs,
    lastSeenAtEpochMs = value.lastSeenAtEpochMs,
)

private fun toEntity(value: UseCaseDefinition) = HostControlPlaneEntities.UseCaseEntity(
    value.useCaseId.value,
    value.revision,
    value.displayName,
    value.description,
    value.requirements.outputMode.name,
    value.requirements.sessionKind.name,
    value.requirements.reasoningSupported,
    value.requirements.minimumContextTokens,
    value.requirements.maxInputCharacters,
    value.requirements.maxJsonSchemaCharacters,
    value.state.name,
)

private fun fromEntity(value: HostControlPlaneEntities.UseCaseEntity) = UseCaseDefinition(
    useCaseId = UseCaseId(value.useCaseId),
    displayName = value.displayName,
    description = value.description,
    requirements = UseCaseRequirements(
        outputMode = enumValueOf<OutputMode>(value.outputMode),
        sessionKind = enumValueOf<SessionKind>(value.sessionKind),
        reasoningSupported = value.reasoningSupported,
        minimumContextTokens = value.minimumContextTokens,
        maxInputCharacters = value.maxInputCharacters,
        maxJsonSchemaCharacters = value.maxJsonSchemaCharacters,
    ),
    state = enumValueOf<UseCaseDefinitionState>(value.state),
    revision = value.revision,
)

private fun toEntity(value: UseCasePresetDefinition): HostControlPlaneEntities.PresetEntity {
    val cache = value.execution.cachePolicy
    return HostControlPlaneEntities.PresetEntity(
        value.useCaseId.value,
        value.metadata.presetId,
        value.metadata.revision,
        value.metadata.displayName,
        value.metadata.description,
        value.creationSource.name,
        value.state.name,
        value.execution.modelProfileId,
        value.execution.inferencePreset.id.value,
        value.execution.inferencePreset.version,
        value.execution.contextTokens,
        cache.retainModelWarmMs,
        cache.reuseStatelessContext,
        cache.enablePrefixSnapshot,
        cache.enableDeterministicResultCache,
    )
}

private fun fromEntity(value: HostControlPlaneEntities.PresetEntity) = UseCasePresetDefinition(
    useCaseId = UseCaseId(value.useCaseId),
    metadata = PresetConsumerMetadata(
        presetId = value.presetId,
        revision = value.revision,
        displayName = value.displayName,
        description = value.description,
    ),
    creationSource = enumValueOf<PresetCreationSource>(value.creationSource),
    state = enumValueOf<PresetLifecycleState>(value.state),
    execution = PresetExecutionPolicy(
        modelProfileId = value.modelProfileId,
        inferencePreset = InferencePresetRef(
            id = InferencePresetId(value.inferencePresetId),
            version = value.inferencePresetVersion,
        ),
        contextTokens = value.contextTokens,
        cachePolicy = UseCaseCachePolicy(
            retainModelWarmMs = value.retainModelWarmMs,
            reuseStatelessContext = value.reuseStatelessContext,
            enablePrefixSnapshot = value.enablePrefixSnapshot,
            enableDeterministicResultCache = value.enableDeterministicResultCache,
        ),
    ),
)

private fun toEntity(value: ApplicationUseCaseBinding) = HostControlPlaneEntities.BindingEntity(
    value.bindingId,
    value.revision,
    value.applicationId.value,
    value.useCaseId.value,
    value.enabled,
)

private fun fromEntity(value: HostControlPlaneEntities.BindingEntity) = ApplicationUseCaseBinding(
    bindingId = value.bindingId,
    applicationId = ApplicationId(value.applicationId),
    useCaseId = UseCaseId(value.useCaseId),
    revision = value.revision,
    enabled = value.enabled,
)

private fun toEntity(value: StoredPresetExposure) = HostControlPlaneEntities.ExposureEntity(
    value.bindingId,
    value.bindingRevision,
    value.presetId,
    value.presetRevision,
    value.isDefault,
)

private fun fromEntity(value: HostControlPlaneEntities.ExposureEntity) = StoredPresetExposure(
    bindingId = value.bindingId,
    bindingRevision = value.bindingRevision,
    presetId = value.presetId,
    presetRevision = value.presetRevision,
    isDefault = value.isDefault,
)

data class HostControlPlaneEntitySet(
    val applications: List<HostControlPlaneEntities.ApplicationEntity>,
    val useCases: List<HostControlPlaneEntities.UseCaseEntity>,
    val presets: List<HostControlPlaneEntities.PresetEntity>,
    val bindings: List<HostControlPlaneEntities.BindingEntity>,
    val exposures: List<HostControlPlaneEntities.ExposureEntity>,
)
