package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ConsumerActivationId
import io.github.daniele21.localllm.contracts.ConsumerControlPlaneErrorCode
import io.github.daniele21.localllm.contracts.ConsumerControlPlaneFailure
import io.github.daniele21.localllm.contracts.ConsumerPreparationAction
import io.github.daniele21.localllm.contracts.ConsumerRuntimeIssue
import io.github.daniele21.localllm.contracts.ConsumerRuntimePhase
import io.github.daniele21.localllm.contracts.ConsumerRuntimeReadiness
import io.github.daniele21.localllm.contracts.ConsumerRuntimeReadinessResult
import io.github.daniele21.localllm.contracts.RuntimeSnapshot
import io.github.daniele21.localllm.contracts.RuntimeState
import io.github.daniele21.localllm.integration.servicehost.ConsumerRuntimeReadinessHost
import io.github.daniele21.localllm.runtime.ActivationOwnerId
import io.github.daniele21.localllm.runtime.ActivationResidencyCoordinator
import io.github.daniele21.localllm.runtime.UseCaseActivationId

/** Side-effect-free projection from canonical activation leases and the process-scoped runtime. */
internal class HarnessConsumerRuntimeReadinessHost(
    private val activationResidency: ActivationResidencyCoordinator,
    private val snapshot: () -> RuntimeSnapshot?,
) : ConsumerRuntimeReadinessHost {
    override fun runtimeReadiness(
        ownerId: String,
        applicationId: ApplicationId,
        activationId: ConsumerActivationId,
    ): ConsumerRuntimeReadinessResult {
        val internalId = runCatching { UseCaseActivationId(activationId.value) }.getOrNull()
            ?: return invalidActivation()
        val lease = activationResidency.find(internalId) ?: return invalidActivation()
        if (lease.ownerId != ActivationOwnerId(ownerId) || lease.applicationId != applicationId) {
            return invalidActivation()
        }
        val runtime = snapshot()
        val readiness = when (runtime?.state ?: RuntimeState.IDLE) {
            RuntimeState.IDLE -> idle(activationId)
            RuntimeState.PREPARING -> preparing(activationId, runtime, lease.modelDigest)
            RuntimeState.READY -> if (runtime.loadedModel == lease.modelDigest) ready(activationId) else idle(activationId)
            RuntimeState.GENERATING -> if (runtime.loadedModel == lease.modelDigest) {
                ConsumerRuntimeReadiness(
                    activationId = activationId,
                    phase = ConsumerRuntimePhase.GENERATING,
                )
            } else {
                idle(activationId)
            }

            RuntimeState.DEGRADED,
            RuntimeState.FAILED,
            -> ConsumerRuntimeReadiness(
                activationId = activationId,
                phase = ConsumerRuntimePhase.FAILED,
                issue = ConsumerRuntimeIssue.RUNTIME_FAILED,
                retryable = true,
            )
        }
        return ConsumerRuntimeReadinessResult.Available(readiness)
    }

    private fun preparing(
        activationId: ConsumerActivationId,
        runtime: RuntimeSnapshot,
        target: io.github.daniele21.localllm.contracts.ModelDigest,
    ): ConsumerRuntimeReadiness {
        val action = when (runtime.loadedModel) {
            null -> ConsumerPreparationAction.LOADING
            target -> ConsumerPreparationAction.REUSING
            else -> ConsumerPreparationAction.SWITCHING
        }
        return ConsumerRuntimeReadiness(
            activationId = activationId,
            phase = ConsumerRuntimePhase.PREPARING,
            preparationAction = action,
        )
    }

    private fun idle(activationId: ConsumerActivationId) = ConsumerRuntimeReadiness(
        activationId = activationId,
        phase = ConsumerRuntimePhase.IDLE,
    )

    private fun ready(activationId: ConsumerActivationId) = ConsumerRuntimeReadiness(
        activationId = activationId,
        phase = ConsumerRuntimePhase.READY,
    )

    private fun invalidActivation(): ConsumerRuntimeReadinessResult.Rejected =
        ConsumerRuntimeReadinessResult.Rejected(
            ConsumerControlPlaneFailure(
                ConsumerControlPlaneErrorCode.INVALID_REQUEST,
                "Consumer activation is unavailable",
            ),
        )
}
