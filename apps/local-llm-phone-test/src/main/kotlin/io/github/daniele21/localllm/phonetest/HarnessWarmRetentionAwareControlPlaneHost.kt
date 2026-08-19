package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ConsumerActivationId
import io.github.daniele21.localllm.contracts.ConsumerActivationRequest
import io.github.daniele21.localllm.contracts.ConsumerActivationResult
import io.github.daniele21.localllm.contracts.ConsumerAssignedUseCasesResult
import io.github.daniele21.localllm.contracts.ConsumerDeactivationResult
import io.github.daniele21.localllm.contracts.ConsumerPublishedPresetsResult
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.integration.servicehost.ConsumerControlPlaneHost

internal class HarnessWarmRetentionAwareControlPlaneHost(
    private val delegate: ConsumerControlPlaneHost,
    private val warmRetention: HarnessResolvedWarmRetentionCoordinator,
) : ConsumerControlPlaneHost {
    override fun assignedUseCases(applicationId: ApplicationId): ConsumerAssignedUseCasesResult =
        delegate.assignedUseCases(applicationId)

    override fun publishedPresets(applicationId: ApplicationId, useCaseId: UseCaseId): ConsumerPublishedPresetsResult =
        delegate.publishedPresets(applicationId, useCaseId)

    override fun activate(
        ownerId: String,
        applicationId: ApplicationId,
        request: ConsumerActivationRequest,
    ): ConsumerActivationResult {
        warmRetention.cancel()
        return delegate.activate(ownerId, applicationId, request)
    }

    override fun deactivate(
        ownerId: String,
        applicationId: ApplicationId,
        activationId: ConsumerActivationId,
    ): ConsumerDeactivationResult = delegate.deactivate(ownerId, applicationId, activationId)

    override fun releaseAll(ownerId: String, applicationId: ApplicationId) = delegate.releaseAll(ownerId, applicationId)
}
