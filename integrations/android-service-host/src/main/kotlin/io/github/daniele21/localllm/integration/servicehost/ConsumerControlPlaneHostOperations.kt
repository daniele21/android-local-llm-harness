package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ConsumerActivationId
import io.github.daniele21.localllm.contracts.ConsumerActivationRequest
import io.github.daniele21.localllm.contracts.ConsumerActivationResult
import io.github.daniele21.localllm.contracts.ConsumerAssignedUseCasesResult
import io.github.daniele21.localllm.contracts.ConsumerControlPlaneErrorCode
import io.github.daniele21.localllm.contracts.ConsumerControlPlaneFailure
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
        val result = requireHost(host, request).assignedUseCases(caller.applicationId)
        val authorized = when (result) {
            is ConsumerAssignedUseCasesResult.Available ->
                ConsumerAssignedUseCasesResult.Available(
                    result.assignments.filter { caller.allows(it.useCaseId) },
                )

            is ConsumerAssignedUseCasesResult.Rejected -> result
        }
        authorized.toConsumerControlPlaneWire(request.operationId)
    }

    fun discoverPresets(
        caller: AuthorizedCaller,
        request: ConsumerControlPlaneRequestParcel,
        callback: HostResultCallback<ConsumerControlPlaneResultParcel>,
    ) = submit(caller, request, callback) {
        val useCaseId = request.useCaseId?.takeIf(String::isNotBlank)?.let(::UseCaseId)
            ?: return@submit invalidRequest(request)
        if (!caller.allows(useCaseId)) {
            return@submit unauthorizedPresetDiscovery(request)
        }
        requireHost(host, request).publishedPresets(caller.applicationId, useCaseId).toConsumerControlPlaneWire(request.operationId)
    }

    fun activate(
        caller: AuthorizedCaller,
        request: ConsumerControlPlaneRequestParcel,
        callback: HostResultCallback<ConsumerControlPlaneResultParcel>,
    ) = submit(caller, request, callback) { token ->
        val activationRequest = request.toCoreActivationRequestOrNull() ?: return@submit invalidRequest(request)
        if (!caller.allows(activationRequest.useCaseId)) {
            return@submit unauthorizedActivation(request)
        }
        requireHost(host, request)
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
        requireHost(host, request)
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
}

private fun requireHost(host: ConsumerControlPlaneHost?, request: ConsumerControlPlaneRequestParcel): ConsumerControlPlaneHost =
    host ?: error("Consumer control plane is unavailable for ${request.operationId}")

private fun ConsumerControlPlaneRequestParcel.toCoreActivationRequestOrNull(): ConsumerActivationRequest? {
    val useCase = useCaseId?.takeIf(String::isNotBlank)
    val useCaseRevisionValue = useCaseRevision?.takeIf { it > 0 }
    val bindingRevisionValue = bindingRevision?.takeIf { it > 0 }
    val presetValue = preset?.takeIf { it.id.isNotBlank() && it.version > 0 }
    val hasIdentity = useCase != null && presetValue != null
    val hasRevisions = useCaseRevisionValue != null && bindingRevisionValue != null
    if (!hasIdentity || !hasRevisions) return null
    return ConsumerActivationRequest(
        useCaseId = UseCaseId(requireNotNull(useCase)),
        useCaseRevision = requireNotNull(useCaseRevisionValue),
        bindingRevision = requireNotNull(bindingRevisionValue),
        preset = InferencePresetRef(InferencePresetId(requireNotNull(presetValue).id), presetValue.version),
    )
}

private fun unauthorizedPresetDiscovery(request: ConsumerControlPlaneRequestParcel): ConsumerControlPlaneResultParcel =
    ConsumerPublishedPresetsResult.Rejected(unauthorizedUseCaseFailure())
        .toConsumerControlPlaneWire(request.operationId)

private fun unauthorizedActivation(request: ConsumerControlPlaneRequestParcel): ConsumerControlPlaneResultParcel =
    ConsumerActivationResult.Rejected(unauthorizedUseCaseFailure())
        .toConsumerControlPlaneWire(request.operationId)

private fun unauthorizedUseCaseFailure() = ConsumerControlPlaneFailure(
    ConsumerControlPlaneErrorCode.USE_CASE_NOT_ASSIGNED,
    "Use case is not assigned",
)

private fun invalidRequest(request: ConsumerControlPlaneRequestParcel) = failure(request, WireErrorCodes.INVALID_WIRE_REQUEST)

private fun failure(request: ConsumerControlPlaneRequestParcel, code: String) = ConsumerControlPlaneResultParcel(
    operationId = request.operationId,
    error = wireError(code),
)
