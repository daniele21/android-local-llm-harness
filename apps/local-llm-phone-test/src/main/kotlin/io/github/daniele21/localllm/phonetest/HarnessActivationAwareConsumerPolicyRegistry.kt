package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.runtime.ConsumerUseCasePolicy
import io.github.daniele21.localllm.runtime.ConsumerUseCasePolicyRegistry

/**
 * Keeps static consumer capability policy as the fallback while projecting the public preset identity of the
 * current control-plane activation for document PII inference.
 */
internal class HarnessActivationAwareConsumerPolicyRegistry(
    private val applicationId: ApplicationId,
    private val bindings: HarnessPhoneBindingRegistry,
    private val fallback: ConsumerUseCasePolicyRegistry,
) : ConsumerUseCasePolicyRegistry {
    override fun find(applicationId: ApplicationId, useCaseId: UseCaseId): ConsumerUseCasePolicy? {
        val fallbackPolicy = fallback.find(applicationId, useCaseId)
        if (applicationId != this.applicationId || useCaseId != HarnessSharedRuntimeBindings.ombraUseCaseId) {
            return fallbackPolicy
        }
        val activatedPreset = bindings.activeResolved(applicationId, useCaseId)?.useCase?.defaultPreset
            ?: return fallbackPolicy
        return HarnessOmbraConsumerPolicy.create(applicationId, activatedPreset)
    }
}
