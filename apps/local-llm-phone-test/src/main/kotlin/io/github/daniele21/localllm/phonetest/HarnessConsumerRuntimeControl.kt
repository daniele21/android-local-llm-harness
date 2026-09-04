package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.models.ResolvedUseCase
import io.github.daniele21.localllm.runtime.ActivationResidencyCoordinator
import io.github.daniele21.localllm.runtime.UseCaseActivationId

/** Minimal activation capability consumed by the Binder-facing control-plane host. */
internal interface HarnessConsumerRuntimeControl {
    val activationResidency: ActivationResidencyCoordinator

    fun installActivationBinding(
        activationId: UseCaseActivationId,
        applicationId: ApplicationId,
        useCaseId: UseCaseId,
        resolved: ResolvedUseCase,
    )

    fun removeActivationBinding(activationId: UseCaseActivationId)
}

internal class HarnessRuntimeGraphConsumerControl(private val graph: HarnessRuntimeGraph) : HarnessConsumerRuntimeControl {
    override val activationResidency: ActivationResidencyCoordinator
        get() = graph.activationResidency

    override fun installActivationBinding(
        activationId: UseCaseActivationId,
        applicationId: ApplicationId,
        useCaseId: UseCaseId,
        resolved: ResolvedUseCase,
    ) {
        graph.installActivationBinding(activationId, applicationId, useCaseId, resolved)
    }

    override fun removeActivationBinding(activationId: UseCaseActivationId) {
        graph.removeActivationBinding(activationId)
    }
}
