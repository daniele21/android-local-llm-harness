package io.github.daniele21.localllm.transport.binder.contract

import android.os.Parcelable
import io.github.daniele21.localllm.contracts.ConsumerActivationId
import io.github.daniele21.localllm.contracts.ConsumerControlPlaneErrorCode
import io.github.daniele21.localllm.contracts.ConsumerControlPlaneFailure
import io.github.daniele21.localllm.contracts.ConsumerPreparationAction
import io.github.daniele21.localllm.contracts.ConsumerRuntimeIssue
import io.github.daniele21.localllm.contracts.ConsumerRuntimePhase
import io.github.daniele21.localllm.contracts.ConsumerRuntimeReadiness
import io.github.daniele21.localllm.contracts.ConsumerRuntimeReadinessResult
import kotlinx.parcelize.Parcelize

@Parcelize
data class ConsumerRuntimeReadinessResultParcel(
    val operationId: String,
    val activationId: String? = null,
    val phaseTag: String? = null,
    val preparationActionTag: String? = null,
    val issueTag: String? = null,
    val retryable: Boolean = false,
    val error: WireErrorParcel? = null,
) : Parcelable

fun ConsumerRuntimeReadinessResult.toConsumerRuntimeReadinessWire(operationId: String): ConsumerRuntimeReadinessResultParcel = when (this) {
    is ConsumerRuntimeReadinessResult.Available ->
        ConsumerRuntimeReadinessResultParcel(
            operationId = operationId,
            activationId = readiness.activationId.value,
            phaseTag = readiness.phase.name,
            preparationActionTag = readiness.preparationAction.name,
            issueTag = readiness.issue?.name,
            retryable = readiness.retryable,
        )

    is ConsumerRuntimeReadinessResult.Rejected ->
        ConsumerRuntimeReadinessResultParcel(
            operationId = operationId,
            error = failure.toRuntimeReadinessWireError(),
        )
}

fun ConsumerRuntimeReadinessResultParcel.toCoreRuntimeReadinessResult(): ConsumerRuntimeReadinessResult = error?.let {
    ConsumerRuntimeReadinessResult.Rejected(it.toConsumerControlPlaneFailure())
} ?: ConsumerRuntimeReadinessResult.Available(
    ConsumerRuntimeReadiness(
        activationId = ConsumerActivationId(requireNotNull(activationId)),
        phase = requireNotNull(enumTagOrNull<ConsumerRuntimePhase>(requireNotNull(phaseTag))),
        preparationAction = requireNotNull(enumTagOrNull<ConsumerPreparationAction>(requireNotNull(preparationActionTag))),
        issue = issueTag?.let { requireNotNull(enumTagOrNull<ConsumerRuntimeIssue>(it)) },
        retryable = retryable,
    ),
)

private fun ConsumerControlPlaneFailure.toRuntimeReadinessWireError(): WireErrorParcel = WireErrorParcel(
    code = code.name,
    safeMessage = "Consumer runtime readiness is unavailable",
    retryable = code == ConsumerControlPlaneErrorCode.MODEL_UNAVAILABLE || code == ConsumerControlPlaneErrorCode.TRANSPORT_FAILURE,
)
