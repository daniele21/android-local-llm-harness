package io.github.daniele21.localllm.models

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.UseCaseId

data class AssignedUseCaseMetadata(
    val useCaseId: UseCaseId,
    val useCaseRevision: Int,
    val bindingRevision: Int,
    val displayName: String,
    val description: String,
    val isDefault: Boolean,
)

enum class AssignedUseCaseDiscoveryFailure {
    UNKNOWN_APPLICATION,
    APPLICATION_NOT_AUTHORIZED,
}

sealed interface AssignedUseCaseDiscoveryResult {
    data class Success(
        val applicationId: ApplicationId,
        val assignments: List<AssignedUseCaseMetadata>,
    ) : AssignedUseCaseDiscoveryResult

    data class Failure(val reason: AssignedUseCaseDiscoveryFailure) : AssignedUseCaseDiscoveryResult
}

/** Consumer-safe projection of the current use cases assigned to one authenticated application. */
class AssignedUseCaseDiscovery(private val store: HostControlPlaneStore) {
    fun discover(applicationId: ApplicationId): AssignedUseCaseDiscoveryResult {
        val state = store.snapshot()
        val application = state.applications.firstOrNull { it.applicationId == applicationId }
            ?: return AssignedUseCaseDiscoveryResult.Failure(AssignedUseCaseDiscoveryFailure.UNKNOWN_APPLICATION)
        if (application.state != ApplicationRegistrationState.AUTHORIZED) {
            return AssignedUseCaseDiscoveryResult.Failure(AssignedUseCaseDiscoveryFailure.APPLICATION_NOT_AUTHORIZED)
        }

        val assignments = state.currentBindings(applicationId).mapNotNull { binding ->
            state.latestUseCase(binding.useCaseId)
                ?.takeIf { it.state == UseCaseDefinitionState.ACTIVE }
                ?.let { useCase ->
                    AssignedUseCaseMetadata(
                        useCaseId = useCase.useCaseId,
                        useCaseRevision = useCase.revision,
                        bindingRevision = binding.revision,
                        displayName = useCase.displayName,
                        description = useCase.description,
                        isDefault = binding.isDefault,
                    )
                }
        }
        return AssignedUseCaseDiscoveryResult.Success(applicationId, assignments)
    }
}
