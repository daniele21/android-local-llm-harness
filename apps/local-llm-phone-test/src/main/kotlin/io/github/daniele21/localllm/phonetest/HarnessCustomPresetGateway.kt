package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.models.HostControlPlaneState
import io.github.daniele21.localllm.models.HostControlPlaneStore
import io.github.daniele21.localllm.models.PresetConsumerMetadata
import io.github.daniele21.localllm.models.PresetCreationSource
import io.github.daniele21.localllm.models.PresetLifecycleState
import io.github.daniele21.localllm.models.StoredPresetExposure
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
    val binding = latestBinding(identity.applicationId, identity.useCaseId)
        ?: throw CustomPresetRejected("Assignment is no longer available")
    if (binding.revision != command.expectedBindingRevision) {
        throw CustomPresetStaleBindingRevision(command.expectedBindingRevision, binding.revision)
    }
    if (!binding.enabled) throw CustomPresetRejected("Assignment is disabled")

    val useCase = latestUseCase(identity.useCaseId)
        ?: throw CustomPresetRejected("Use case is no longer available")
    if (useCase.state != UseCaseDefinitionState.ACTIVE) {
        throw CustomPresetRejected("Use case is not active")
    }

    val baseExposure = exposures.firstOrNull { exposure ->
        exposure.bindingId == binding.bindingId &&
            exposure.bindingRevision == binding.revision &&
            exposure.presetId == command.basePresetId &&
            exposure.presetRevision == command.basePresetRevision
    } ?: throw CustomPresetRejected("Base preset is no longer available for this assignment")
    val basePreset = preset(identity.useCaseId, baseExposure.presetId, baseExposure.presetRevision)
    if (basePreset?.isConsumerVisible != true) {
        throw CustomPresetRejected("Base preset is not published for consumer use")
    }

    val presetId = command.presetId.trim()
    if (presetId.isBlank()) throw CustomPresetRejected("Preset identity is invalid")
    if (presets.any { it.useCaseId == identity.useCaseId && it.metadata.presetId == presetId }) {
        throw CustomPresetRejected("Preset identity is already in use")
    }

    val displayName = command.displayName.trim()
    if (displayName.isBlank()) throw CustomPresetRejected("Preset name is required")
    val modelProfileId = command.modelProfileId?.trim()
    if (modelProfileId != null && modelProfileId.isBlank()) {
        throw CustomPresetRejected("Model profile identity is invalid")
    }
    if (command.contextTokens != null && command.contextTokens < useCase.requirements.minimumContextTokens) {
        throw CustomPresetRejected(
            "Context must be at least ${useCase.requirements.minimumContextTokens} tokens for this use case",
        )
    }

    val customPreset = UseCasePresetDefinition(
        useCaseId = identity.useCaseId,
        metadata = PresetConsumerMetadata(
            presetId = presetId,
            revision = FIRST_CUSTOM_PRESET_REVISION,
            displayName = displayName,
            description = "Custom configuration based on ${basePreset.metadata.displayName}",
        ),
        creationSource = PresetCreationSource.CUSTOM,
        state = PresetLifecycleState.PUBLISHED,
        execution = basePreset.execution.copy(
            modelProfileId = modelProfileId,
            contextTokens = command.contextTokens,
        ),
    )

    return copy(
        presets = presets + customPreset,
        exposures = exposures + StoredPresetExposure(
            bindingId = binding.bindingId,
            bindingRevision = binding.revision,
            presetId = customPreset.metadata.presetId,
            presetRevision = customPreset.metadata.revision,
            isDefault = false,
        ),
    )
}

private class CustomPresetStaleBindingRevision(val expectedRevision: Int, val actualRevision: Int) : RuntimeException()

private class CustomPresetRejected(message: String) : RuntimeException(message)

private const val FIRST_CUSTOM_PRESET_REVISION = 1
