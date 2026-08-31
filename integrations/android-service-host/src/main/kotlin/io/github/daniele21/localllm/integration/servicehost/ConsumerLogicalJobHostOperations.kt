package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.contracts.ConsumerLocalLlmClient
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.transport.binder.contract.BinderProtocolV1
import io.github.daniele21.localllm.transport.binder.contract.ClientTokenParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerLogicalJobQueryParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerLogicalJobResultParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerLogicalJobSubmitParcel
import io.github.daniele21.localllm.transport.binder.contract.WireErrorCodes
import io.github.daniele21.localllm.transport.binder.contract.WireErrorParcel

/** Minor-v5 detached-job transport facade. Connection validation is separate from job ownership. */
internal class ConsumerLogicalJobHostOperations(
    private val ledger: ClientConnectionLedger,
    private val consumerResources: ConsumerHostResources,
    private val controlExecutor: HostControlExecutor,
    private val coordinator: HostLogicalJobCoordinator,
) {
    fun submit(
        caller: AuthorizedCaller,
        request: ConsumerLogicalJobSubmitParcel,
        callback: HostResultCallback<ConsumerLogicalJobResultParcel>,
    ) {
        controlExecutor.submitOrReject(
            onRejected = { callback.onResult(failure(request.operationId, WireErrorCodes.TRANSPORT_FAILURE)) },
        ) {
            val client = validatedClient(caller, request.clientToken, request.operationId)
            if (client == null) {
                callback.onResult(failure(request.operationId, WireErrorCodes.CLIENT_TOKEN_INVALID))
                return@submitOrReject
            }
            callback.onResult(coordinator.submit(caller, client, request))
        }
    }

    fun query(
        caller: AuthorizedCaller,
        request: ConsumerLogicalJobQueryParcel,
        includeResult: Boolean,
        callback: HostResultCallback<ConsumerLogicalJobResultParcel>,
    ) {
        controlExecutor.submitOrReject(
            onRejected = { callback.onResult(failure(request.operationId, WireErrorCodes.TRANSPORT_FAILURE)) },
        ) {
            if (!validatedConnection(caller, request.clientToken, request.operationId)) {
                callback.onResult(failure(request.operationId, WireErrorCodes.CLIENT_TOKEN_INVALID))
                return@submitOrReject
            }
            val scope = request.scopeOrNull(caller)
            val jobId = runCatching { HostLogicalJobId(request.jobId) }.getOrNull()
            if (scope == null || jobId == null) {
                callback.onResult(failure(request.operationId, WireErrorCodes.INVALID_WIRE_REQUEST))
                return@submitOrReject
            }
            callback.onResult(
                if (includeResult) coordinator.result(request.operationId, scope, jobId)
                else coordinator.query(request.operationId, scope, jobId),
            )
        }
    }

    fun cancel(caller: AuthorizedCaller, request: ConsumerLogicalJobQueryParcel) {
        controlExecutor.submitOrReject(onRejected = {}) {
            if (!validatedConnection(caller, request.clientToken, request.operationId)) return@submitOrReject
            val scope = request.scopeOrNull(caller) ?: return@submitOrReject
            val jobId = runCatching { HostLogicalJobId(request.jobId) }.getOrNull() ?: return@submitOrReject
            coordinator.cancel(scope, jobId)
        }
    }

    private fun validatedClient(
        caller: AuthorizedCaller,
        tokenParcel: ClientTokenParcel,
        operationId: String,
    ): ConsumerLocalLlmClient? {
        if (!validatedConnection(caller, tokenParcel, operationId)) return null
        return consumerResources.client(HostClientToken(tokenParcel.value))
    }

    private fun validatedConnection(
        caller: AuthorizedCaller,
        tokenParcel: ClientTokenParcel,
        operationId: String,
    ): Boolean {
        if (operationId.isBlank() || tokenParcel.value.isBlank()) return false
        val token = runCatching { HostClientToken(tokenParcel.value) }.getOrNull() ?: return false
        if (ledger.validateConnection(token, caller).failureOrNull() != null) return false
        return ledger.supportsFeature(token, caller, BinderProtocolV1.FEATURE_CONSUMER_LOGICAL_JOBS_V1).successOrNull() == true
    }
}

private fun ConsumerLogicalJobQueryParcel.scopeOrNull(caller: AuthorizedCaller): HostLogicalJobScope? {
    val useCase = useCaseId.takeIf(String::isNotBlank)?.let(::UseCaseId)?.takeIf(caller::allows) ?: return null
    return HostLogicalJobScope(caller.applicationId, useCase)
}

private fun failure(operationId: String, code: String): ConsumerLogicalJobResultParcel = ConsumerLogicalJobResultParcel(
    operationId = operationId,
    error = WireErrorParcel(code = code, safeMessage = "Logical job request failed", retryable = false),
)
