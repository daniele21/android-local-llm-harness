package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.models.ApplicationUseCaseBinding
import io.github.daniele21.localllm.models.HostControlPlaneState
import io.github.daniele21.localllm.models.HostControlPlaneStore
import io.github.daniele21.localllm.models.PresetConsumerMetadata
import io.github.daniele21.localllm.models.PresetCreationSource
import io.github.daniele21.localllm.models.PresetLifecycleState
import io.github.daniele21.localllm.models.StoredPresetExposure
import io.github.daniele21.localllm.models.UseCaseDefinition
import io.github.daniele21.localllm.models.UseCaseDefinitionState
import io.github.daniele21.localllm.models.UseCasePresetDefinition

internal data class HarnessCreateCustomPresetCommand(
    val applicationId: String,
    val useCaseId: String,
    val expectedBindingRevision: Int,
    val presetId: String,
    val basePresetId: String,
    val basePresetRevision: Int,
    val displayName: String,
    val modelProfileId: String?,
    val contextTokens: Int?,
)

internal sealed interface HarnessCustomPresetMutationResult {
    data class Success(val presetId: String, val presetRevision: Int) : HarnessCustomPresetMutationResult

    data class StaleRevision(val expectedRevision: Int, val actualRevision: Int) : HarnessCustomPresetMutationResult

    data class Rejected(val message: String) : HarnessCustomPresetMutationResult
}

internal interface HarnessCustomPresetGateway : HarnessApplicationsGateway {
    fun createCustomPreset(command: HarnessCreateCustomPresetCommand): HarnessCustomPresetMutationResult
}

internal class StoreHarnessCustomPresetGateway(private val store: HostControlPlaneStore) : HarnessCustomPresetGateway {
    private val delegate = StoreHarnessApplicationsGateway(store)

    override fun snapshot(): HarnessApplicationsSnapshot = delegate.snapshot()

    override fun setDefaultPreset(command: HarnessSetDefaultPresetCommand): HarnessControlPlaneMutationResult =
        delegate.setDefaultPreset(command)

    override fun createCustomPreset(command: HarnessCreateCustomPresetCommand): HarnessCustomPresetMutationResult {
        val identity = command.identity()
        if (identity is CustomPresetIdentity.Rejected) {
            return HarnessCustomPresetMutationResult.Rejected(identity.message)
        }
        identity as CustomPresetIdentity.Valid
        return try {
            store.transact { current -> current.withCustomPreset(command, identity) }
            HarnessCustomPresetMutationResult.Success(command.presetId, FIRST_CUSTOM_PRESET_REVISION)
        } catch (stale: CustomPresetStaleBindingRevision) {
            HarnessCustomPresetMutationResult.StaleRevision(stale.expectedRevision, stale.actualRevision)
        } catch (rejected: CustomPresetRejected) {
            HarnessCustomPresetMutationResult.Rejected(rejected.message.orEmpty())
        } catch (_: IllegalArgumentException) {
            HarnessCustomPresetMutationResult.Rejected("Custom preset could not be saved")
        }
    }
}

private sealed interface CustomPresetIdentity {
    data class Valid(val applicationId: ApplicationId, val useCaseId: UseCaseId) : CustomPresetIdentity

    data class Rejected(val message: String) : CustomPresetIdentity
}

private data class CustomPresetAssignment(val binding: ApplicationUseCaseBinding, val useCase: UseCaseDefinition)

private data class CustomPresetFields(val presetId: String, val displayName: String, val modelProfileId: String?)

private fun HarnessCreateCustomPresetCommand.identity(): CustomPresetIdentity {
    val parsedApplicationId = runCatching { ApplicationId(applicationId) }.getOrElse {
        return CustomPresetIdentity.Rejected("Application identity is invalid")
    }
    val parsedUseCaseId = runCatching { UseCaseId(useCaseId) }.getOrElse {
        return CustomPresetIdentity.Rejected("Use-case identity is invalid")
    }
    return CustomPresetIdentity.Valid(parsedApplicationId, parsedUseCaseId)
}

private fun HostControlPlaneState.withCustomPreset(
    command: HarnessCreateCustomPresetCommand,
    identity: CustomPresetIdentity.Valid,
): HostControlPlaneState {
    val assignment = resolveCustomPresetAssignment(command, identity)
    val basePreset = resolveBasePreset(command, identity.useCaseId, assignment.binding)
    val fields = resolveCustomPresetFields(command, identity.useCaseId)
    CustomPresetValidation.context(command.contextTokens, assignment.useCase)
    val customPreset = buildCustomPreset(
        command = command,
        useCaseId = identity.useCaseId,
        basePreset = basePreset,
        fields = fields,
    )
    return addCustomPreset(assignment.binding, customPreset)
}

private fun HostControlPlaneState.resolveCustomPresetAssignment(
    command: HarnessCreateCustomPresetCommand,
    identity: CustomPresetIdentity.Valid,
): CustomPresetAssignment {
    val binding = latestBinding(identity.applicationId, identity.useCaseId)
        ?: throw CustomPresetRejected("Assignment is no longer available")
    CustomPresetValidation.binding(command.expectedBindingRevision, binding)
    val useCase = latestUseCase(identity.useCaseId)
        ?: throw CustomPresetRejected("Use case is no longer available")
    CustomPresetValidation.useCase(useCase)
    return CustomPresetAssignment(binding, useCase)
}

private fun HostControlPlaneState.resolveBasePreset(
    command: HarnessCreateCustomPresetCommand,
    useCaseId: UseCaseId,
    binding: ApplicationUseCaseBinding,
): UseCasePresetDefinition {
    val baseExposure = exposures.firstOrNull { exposure ->
        exposure.bindingId == binding.bindingId &&
            exposure.bindingRevision == binding.revision &&
            exposure.presetId == command.basePresetId &&
            exposure.presetRevision == command.basePresetRevision
    } ?: throw CustomPresetRejected("Base preset is no longer available for this assignment")
    return preset(useCaseId, baseExposure.presetId, baseExposure.presetRevision)
        ?.takeIf(UseCasePresetDefinition::isConsumerVisible)
        ?: throw CustomPresetRejected("Base preset is not published for consumer use")
}

private fun HostControlPlaneState.resolveCustomPresetFields(
    command: HarnessCreateCustomPresetCommand,
    useCaseId: UseCaseId,
): CustomPresetFields = CustomPresetFields(
    presetId = validatePresetId(command.presetId, useCaseId),
    displayName = CustomPresetValidation.displayName(command.displayName),
    modelProfileId = CustomPresetValidation.modelProfileId(command.modelProfileId),
)

private fun HostControlPlaneState.validatePresetId(rawPresetId: String, useCaseId: UseCaseId): String {
    val presetId = rawPresetId.trim()
    if (presetId.isBlank()) {
        throw CustomPresetRejected("Preset identity is invalid")
    }
    if (presets.any { it.useCaseId == useCaseId && it.metadata.presetId == presetId }) {
        throw CustomPresetRejected("Preset identity is already in use")
    }
    return presetId
}

private fun buildCustomPreset(
    command: HarnessCreateCustomPresetCommand,
    useCaseId: UseCaseId,
    basePreset: UseCasePresetDefinition,
    fields: CustomPresetFields,
): UseCasePresetDefinition = UseCasePresetDefinition(
    useCaseId = useCaseId,
    metadata = PresetConsumerMetadata(
        presetId = fields.presetId,
        revision = FIRST_CUSTOM_PRESET_REVISION,
        displayName = fields.displayName,
        description = "Custom configuration based on ${basePreset.metadata.displayName}",
    ),
    creationSource = PresetCreationSource.CUSTOM,
    state = PresetLifecycleState.PUBLISHED,
    execution = basePreset.execution.copy(
        modelProfileId = fields.modelProfileId,
        contextTokens = command.contextTokens,
    ),
)

private fun HostControlPlaneState.addCustomPreset(
    binding: ApplicationUseCaseBinding,
    customPreset: UseCasePresetDefinition,
): HostControlPlaneState = copy(
    presets = presets + customPreset,
    exposures = exposures + StoredPresetExposure(
        bindingId = binding.bindingId,
        bindingRevision = binding.revision,
        presetId = customPreset.metadata.presetId,
        presetRevision = customPreset.metadata.revision,
        isDefault = false,
    ),
)

private object CustomPresetValidation {
    fun binding(expectedRevision: Int, binding: ApplicationUseCaseBinding) {
        if (binding.revision != expectedRevision) {
            throw CustomPresetStaleBindingRevision(expectedRevision, binding.revision)
        }
        if (!binding.enabled) {
            throw CustomPresetRejected("Assignment is disabled")
        }
    }

    fun useCase(useCase: UseCaseDefinition) {
        if (useCase.state != UseCaseDefinitionState.ACTIVE) {
            throw CustomPresetRejected("Use case is not active")
        }
    }

    fun displayName(rawDisplayName: String): String {
        val displayName = rawDisplayName.trim()
        if (displayName.isBlank()) {
            throw CustomPresetRejected("Preset name is required")
        }
        return displayName
    }

    fun modelProfileId(rawModelProfileId: String?): String? {
        val modelProfileId = rawModelProfileId?.trim()
        if (modelProfileId != null && modelProfileId.isBlank()) {
            throw CustomPresetRejected("Model profile identity is invalid")
        }
        return modelProfileId
    }

    fun context(contextTokens: Int?, useCase: UseCaseDefinition) {
        if (contextTokens != null && contextTokens < useCase.requirements.minimumContextTokens) {
            throw CustomPresetRejected(
                "Context must be at least ${useCase.requirements.minimumContextTokens} tokens for this use case",
            )
        }
    }
}

private class CustomPresetStaleBindingRevision(val expectedRevision: Int, val actualRevision: Int) : RuntimeException()

private class CustomPresetRejected(message: String) : RuntimeException(message)

private const val FIRST_CUSTOM_PRESET_REVISION = 1
