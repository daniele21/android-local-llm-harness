package io.github.daniele21.localllm.models

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.UseCaseId

data class PublishedPresetMetadata(
    val presetId: String,
    val revision: Int,
    val displayName: String,
    val description: String,
    val isDefault: Boolean,
)

enum class PublishedPresetDiscoveryFailure {
    UNKNOWN_APPLICATION,
    APPLICATION_NOT_AUTHORIZED,
    USE_CASE_NOT_ASSIGNED,
}

sealed interface PublishedPresetDiscoveryResult {
    data class Success(
        val applicationId: ApplicationId,
        val useCaseId: UseCaseId,
        val bindingRevision: Int,
        val presets: List<PublishedPresetMetadata>,
    ) : PublishedPresetDiscoveryResult

    data class Failure(val reason: PublishedPresetDiscoveryFailure) : PublishedPresetDiscoveryResult
}

/** Consumer-safe projection of presets exposed through the latest enabled binding revision. */
class PublishedPresetDiscovery(private val store: HostControlPlaneStore) {
    fun discover(applicationId: ApplicationId, useCaseId: UseCaseId): PublishedPresetDiscoveryResult {
        val state = store.snapshot()
        val application = state.applications.firstOrNull { it.applicationId == applicationId }
            ?: return PublishedPresetDiscoveryResult.Failure(PublishedPresetDiscoveryFailure.UNKNOWN_APPLICATION)
        if (application.state != ApplicationRegistrationState.AUTHORIZED) {
            return PublishedPresetDiscoveryResult.Failure(PublishedPresetDiscoveryFailure.APPLICATION_NOT_AUTHORIZED)
        }
        val binding = state.latestBinding(applicationId, useCaseId)
        if (binding == null || !binding.enabled) {
            return PublishedPresetDiscoveryResult.Failure(PublishedPresetDiscoveryFailure.USE_CASE_NOT_ASSIGNED)
        }

        val exposures = state.exposures
            .filter { it.bindingId == binding.bindingId && it.bindingRevision == binding.revision }
            .sortedWith(compareBy({ !it.isDefault }, StoredPresetExposure::presetId, StoredPresetExposure::presetRevision))
        val projected = exposures.mapNotNull { exposure ->
            state.preset(useCaseId, exposure.presetId, exposure.presetRevision)
                ?.takeIf(UseCasePresetDefinition::isConsumerVisible)
                ?.metadata
                ?.let { metadata ->
                    PublishedPresetMetadata(
                        presetId = metadata.presetId,
                        revision = metadata.revision,
                        displayName = metadata.displayName,
                        description = metadata.description,
                        isDefault = exposure.isDefault,
                    )
                }
        }
        return PublishedPresetDiscoveryResult.Success(
            applicationId = applicationId,
            useCaseId = useCaseId,
            bindingRevision = binding.revision,
            presets = projected,
        )
    }
}
