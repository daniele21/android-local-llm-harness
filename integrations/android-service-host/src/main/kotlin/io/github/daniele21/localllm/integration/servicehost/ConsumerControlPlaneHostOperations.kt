package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ConsumerActivationId
import io.github.daniele21.localllm.contracts.ConsumerActivationRequest
import io.github.daniele21.localllm.contracts.ConsumerActivationResult
import io.github.daniele21.localllm.contracts.ConsumerAssignedUseCasesResult
import io.github.daniele21.localllm.contracts.ConsumerDeactivationResult
import io.github.daniele21.localllm.contracts.ConsumerPublishedPresetsResult
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.transport.binder.contract.BinderProtocolV1
import io.github.daniele21.localllm.transport.binder.contract.ConsumerControlPlaneRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerControlPlaneResultParcel
import io.github.daniele21.localllm.transport.binder.contract.WireErrorCodes
import io.github.daniele21.localllm.transport.binder.contract.toConsumerControlPlaneWire

interface ConsumerControlPlaneHost {
    fun assignedUseCases(applicationId: ApplicationId): ConsumerAssignedUseCasesResult

    fun publishedPresets(applicationId: ApplicationId, useCaseId: UseCaseId): ConsumerPublishedPresetsResult

    fun activate(ownerId: String, applicationId: ApplicationId, request: ConsumerActivationRequest): ConsumerActivationResult

    fun deactivate(ownerId: String, applicationId: ApplicationId, activationId: ConsumerActivationId): ConsumerDeactivationResult

    fun releaseAll(ownerId: String, applicationId: ApplicationId)
}

internal class ConsumerControlPlaneHostOperations(
    private val ledger: ClientConnectionLedger,
    private val host: ConsumerControlPlaneHost?,
    private val controlExecutor: HostControlExecutor,
) {
    fun discoverUseCases(
        caller: AuthorizedCaller,
        request: ConsumerControlPlaneRequestParcel,
        callback: HostResultCallback<ConsumerControlPlaneResultParcel>,
    ) = submit(caller, request, callback) {
        requireHost(request).assignedUseCases(caller.applicationId).toConsumerControlPlaneWire(request.operationId)
    }

    fun discoverPresets(
        caller: AuthorizedCaller,
        request: ConsumerControlPlaneRequestParcel,
        callback: HostResultCallback<ConsumerControlPlaneResultParcel>,
    ) = submit(caller, request, callback) {
        val useCaseId = request.useCaseId?.takeIf(String::isNotBlank)?.let(::UseCaseId)
            ?: return@submit invalidRequest(request)
        requireHost(request).publishedPresets(caller.applicationId, useCaseId).toConsumerControlPlaneWire(request.operationId)
    }

    fun activate(
        caller: AuthorizedCaller,
        request: ConsumerControlPlaneRequestParcel,
        callback: HostResultCallback<ConsumerControlPlaneResultParcel>,
    ) = submit(caller, request, callback) { token ->
        val activationRequest = request.toCoreActivationRequestOrNull() ?: return@submit invalidRequest(request)
        requireHost(request)
            .activate(token.value, caller.applicationId, activationRequest)
            .toConsumerControlPlaneWire(request.operationId)
    }

    fun deactivate(
        caller: AuthorizedCaller,
        request: ConsumerControlPlaneRequestParcel,
        callback: HostResultCallback<ConsumerControlPlaneResultParcel>,
    ) = submit(caller, request, callback) { token ->
        val activationId = request.activationId?.takeIf(String::isNotBlank)?.let(::ConsumerActivationId)
            ?: return@submit invalidRequest(request)
        requireHost(request)
            .deactivate(token.value, caller.applicationId, activationId)
            .toConsumerControlPlaneWire(request.operationId, activationId)
    }

    private fun submit(
        caller: AuthorizedCaller,
        request: ConsumerControlPlaneRequestParcel,
        callback: HostResultCallback<ConsumerControlPlaneResultParcel>,
        block: (HostClientToken) -> ConsumerControlPlaneResultParcel,
    ) {
        val token = runCatching { HostClientToken(request.clientToken.value) }.getOrNull()
        if (token == null || request.operationId.isBlank()) {
            callback.onResult(invalidRequest(request))
            return
        }
        controlExecutor.submitOrReject(
            onRejected = { callback.onResult(failure(request, WireErrorCodes.TRANSPORT_FAILURE)) },
        ) {
            when (
                val support = ledger.supportsFeature(
                    token,
                    caller,
                    BinderProtocolV1.FEATURE_CONSUMER_CONTROL_PLANE_V1,
                )
            ) {
                is LedgerResult.Failure -> callback.onResult(failure(request, WireErrorCodes.CLIENT_TOKEN_INVALID))

                is LedgerResult.Success -> {
                    if (!support.value || host == null) {
                        callback.onResult(failure(request, WireErrorCodes.FEATURE_UNAVAILABLE))
                    } else {
                        val result = runCatching { block(token) }
                            .getOrElse { failure(request, WireErrorCodes.RUNTIME_FAILURE) }
                        callback.onResult(result)
                    }
                }
            }
        }
    }

    private fun requireHost(request: ConsumerControlPlaneRequestParcel): ConsumerControlPlaneHost =
        host ?: error("Consumer control plane is unavailable for ${request.operationId}")

    private fun ConsumerControlPlaneRequestParcel.toCoreActivationRequestOrNull(): ConsumerActivationRequest? {
        val useCase = useCaseId?.takeIf(String::isNotBlank) ?: return null
        val useCaseRevisionValue = useCaseRevision?.takeIf { it > 0 } ?: return null
        val bindingRevisionValue = bindingRevision?.takeIf { it > 0 } ?: return null
        val presetValue = preset ?: return null
        if (presetValue.id.isBlank() || presetValue.version <= 0) return null
        return ConsumerActivationRequest(
            useCaseId = UseCaseId(useCase),
            useCaseRevision = useCaseRevisionValue,
            bindingRevision = bindingRevisionValue,
            preset = InferencePresetRef(InferencePresetId(presetValue.id), presetValue.version),
        )
    }

    private fun invalidRequest(request: ConsumerControlPlaneRequestParcel) = failure(request, WireErrorCodes.INVALID_WIRE_REQUEST)

    private fun failure(request: ConsumerControlPlaneRequestParcel, code: String) = ConsumerControlPlaneResultParcel(
        operationId = request.operationId,
        error = wireError(code),
    )
}
