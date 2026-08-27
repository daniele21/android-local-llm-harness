package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ConsumerActivationId
import io.github.daniele21.localllm.contracts.ConsumerControlPlaneErrorCode
import io.github.daniele21.localllm.contracts.ConsumerControlPlaneFailure
import io.github.daniele21.localllm.contracts.ConsumerRuntimeReadinessResult
import io.github.daniele21.localllm.transport.binder.contract.BinderProtocolV1
import io.github.daniele21.localllm.transport.binder.contract.ConsumerControlPlaneRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerRuntimeReadinessResultParcel
import io.github.daniele21.localllm.transport.binder.contract.toConsumerRuntimeReadinessWire

/** Host-owned provider for the consumer-safe runtime lifecycle projection. */
interface ConsumerRuntimeReadinessHost {
    fun runtimeReadiness(ownerId: String, applicationId: ApplicationId, activationId: ConsumerActivationId): ConsumerRuntimeReadinessResult
}

internal class ConsumerRuntimeReadinessHostOperations(
    private val ledger: ClientConnectionLedger,
    private val host: ConsumerRuntimeReadinessHost?,
    private val controlExecutor: HostControlExecutor,
) {
    fun runtimeReadiness(
        caller: AuthorizedCaller,
        request: ConsumerControlPlaneRequestParcel,
        callback: HostResultCallback<ConsumerRuntimeReadinessResultParcel>,
    ) {
        val token = runCatching { HostClientToken(request.clientToken.value) }.getOrNull()
        val activationId = request.activationId?.takeIf(String::isNotBlank)?.let(::ConsumerActivationId)
        if (token == null || request.operationId.isBlank() || activationId == null) {
            callback.onResult(failure(request, ConsumerControlPlaneErrorCode.INVALID_REQUEST))
            return
        }
        controlExecutor.submitOrReject(
            onRejected = { callback.onResult(failure(request, ConsumerControlPlaneErrorCode.TRANSPORT_FAILURE)) },
        ) {
            when (
                val support = ledger.supportsFeature(
                    token,
                    caller,
                    BinderProtocolV1.FEATURE_CONSUMER_RUNTIME_READINESS_V1,
                )
            ) {
                is LedgerResult.Failure -> callback.onResult(failure(request, ConsumerControlPlaneErrorCode.TRANSPORT_FAILURE))

                is LedgerResult.Success -> {
                    val readinessHost = host
                    if (!support.value || readinessHost == null) {
                        callback.onResult(failure(request, ConsumerControlPlaneErrorCode.FEATURE_UNAVAILABLE))
                    } else {
                        val result = runCatching {
                            readinessHost.runtimeReadiness(token.value, caller.applicationId, activationId)
                        }.getOrElse {
                            ConsumerRuntimeReadinessResult.Rejected(
                                ConsumerControlPlaneFailure(
                                    ConsumerControlPlaneErrorCode.RUNTIME_FAILURE,
                                    "Consumer runtime readiness failed",
                                ),
                            )
                        }
                        callback.onResult(result.toConsumerRuntimeReadinessWire(request.operationId))
                    }
                }
            }
        }
    }
}

private fun failure(
    request: ConsumerControlPlaneRequestParcel,
    code: ConsumerControlPlaneErrorCode,
): ConsumerRuntimeReadinessResultParcel = ConsumerRuntimeReadinessResult.Rejected(
    ConsumerControlPlaneFailure(code, "Consumer runtime readiness is unavailable"),
).toConsumerRuntimeReadinessWire(request.operationId)
