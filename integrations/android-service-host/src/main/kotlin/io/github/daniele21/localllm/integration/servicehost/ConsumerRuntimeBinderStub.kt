package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.transport.binder.contract.CancelRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.CloseSessionRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerControlPlaneRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerControlPlaneResultParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerGenerationEventParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerGenerationRequestV2Parcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerLogicalJobQueryParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerLogicalJobResultParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerLogicalJobSubmitParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerResultParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerRuntimeReadinessResultParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerWireTags
import io.github.daniele21.localllm.transport.binder.contract.IConsumerControlPlaneResultCallback
import io.github.daniele21.localllm.transport.binder.contract.IConsumerGenerationCallback
import io.github.daniele21.localllm.transport.binder.contract.IConsumerLocalLlmService
import io.github.daniele21.localllm.transport.binder.contract.IConsumerLogicalJobResultCallback
import io.github.daniele21.localllm.transport.binder.contract.IConsumerResultCallback
import io.github.daniele21.localllm.transport.binder.contract.IConsumerRuntimeReadinessResultCallback
import io.github.daniele21.localllm.transport.binder.contract.WireErrorCodes

/** Mirrors the public Consumer AIDL transaction surface; keep transaction ownership in one auditable stub. */
@Suppress("TooManyFunctions")
internal class ConsumerRuntimeBinderStub(
    private val authorizer: CallerAuthorizer,
    private val delegate: SharedRuntimeHostDelegate,
    private val callingProcessSource: CallingProcessSource,
) : IConsumerLocalLlmService.Stub() {
    override fun capabilities(request: ConsumerRequestParcel, callback: IConsumerResultCallback) =
        withResultCaller(authorizer, callingProcessSource, request, callback) { caller ->
            delegate.consumerOperations.capabilities(
                caller,
                request,
                remoteConsumerResultCallback(delegate, caller, request.clientToken, callback),
            )
        }

    override fun prepare(request: ConsumerRequestParcel, callback: IConsumerResultCallback) =
        withResultCaller(authorizer, callingProcessSource, request, callback) { caller ->
            delegate.consumerOperations.prepare(
                caller,
                request,
                remoteConsumerResultCallback(delegate, caller, request.clientToken, callback),
            )
        }

    override fun openSession(request: ConsumerRequestParcel, callback: IConsumerResultCallback) =
        withResultCaller(authorizer, callingProcessSource, request, callback) { caller ->
            delegate.consumerOperations.openSession(
                caller,
                request,
                remoteConsumerResultCallback(delegate, caller, request.clientToken, callback),
            )
        }

    override fun generate(request: ConsumerRequestParcel, callback: IConsumerGenerationCallback) {
        val caller = authorizedCallerOrNull(authorizer, callingProcessSource)
        if (caller == null) {
            deliverConsumerUnauthorized(request.externalRequestId, callback)
            return
        }
        delegate.consumerOperations.generate(
            caller,
            request,
            remoteConsumerGenerationCallback(delegate, caller, request.clientToken, callback),
        )
    }

    override fun generateV2(request: ConsumerGenerationRequestV2Parcel, callback: IConsumerGenerationCallback) {
        val caller = authorizedCallerOrNull(authorizer, callingProcessSource)
        if (caller == null) {
            deliverConsumerUnauthorized(request.request.externalRequestId, callback)
            return
        }
        delegate.consumerOperations.generateV2(
            caller,
            request,
            remoteConsumerGenerationCallback(delegate, caller, request.request.clientToken, callback),
        )
    }

    override fun cancel(request: CancelRequestParcel) {
        authorizedCallerOrNull(authorizer, callingProcessSource)?.let { delegate.consumerOperations.cancel(it, request) }
    }

    override fun closeSession(request: CloseSessionRequestParcel) {
        authorizedCallerOrNull(authorizer, callingProcessSource)?.let { delegate.consumerOperations.closeSession(it, request) }
    }

    override fun discoverUseCases(request: ConsumerControlPlaneRequestParcel, callback: IConsumerControlPlaneResultCallback) =
        withControlPlaneCaller(authorizer, callingProcessSource, request, callback) { caller ->
            delegate.controlPlaneOperations.discoverUseCases(
                caller,
                request,
                remoteConsumerControlPlaneResultCallback(delegate, caller, request.clientToken, callback),
            )
        }

    override fun discoverPresets(request: ConsumerControlPlaneRequestParcel, callback: IConsumerControlPlaneResultCallback) =
        withControlPlaneCaller(authorizer, callingProcessSource, request, callback) { caller ->
            delegate.controlPlaneOperations.discoverPresets(
                caller,
                request,
                remoteConsumerControlPlaneResultCallback(delegate, caller, request.clientToken, callback),
            )
        }

    override fun activate(request: ConsumerControlPlaneRequestParcel, callback: IConsumerControlPlaneResultCallback) =
        withControlPlaneCaller(authorizer, callingProcessSource, request, callback) { caller ->
            delegate.controlPlaneOperations.activate(
                caller,
                request,
                remoteConsumerControlPlaneResultCallback(delegate, caller, request.clientToken, callback),
            )
        }

    override fun deactivate(request: ConsumerControlPlaneRequestParcel, callback: IConsumerControlPlaneResultCallback) =
        withControlPlaneCaller(authorizer, callingProcessSource, request, callback) { caller ->
            delegate.controlPlaneOperations.deactivate(
                caller,
                request,
                remoteConsumerControlPlaneResultCallback(delegate, caller, request.clientToken, callback),
            )
        }

    override fun runtimeReadiness(request: ConsumerControlPlaneRequestParcel, callback: IConsumerRuntimeReadinessResultCallback) =
        withRuntimeReadinessCaller(authorizer, callingProcessSource, request, callback) { caller ->
            delegate.readinessOperations.runtimeReadiness(
                caller,
                request,
                remoteConsumerRuntimeReadinessResultCallback(delegate, caller, request.clientToken, callback),
            )
        }

    override fun submitLogicalGeneration(
        request: ConsumerLogicalJobSubmitParcel,
        callback: IConsumerLogicalJobResultCallback,
    ) = withLogicalJobCaller(authorizer, callingProcessSource, request.operationId, callback) { caller ->
        delegate.logicalJobOperations.submit(
            caller,
            request,
            remoteConsumerLogicalJobResultCallback(delegate, caller, request.clientToken, callback),
        )
    }

    override fun getLogicalJob(
        request: ConsumerLogicalJobQueryParcel,
        callback: IConsumerLogicalJobResultCallback,
    ) = withLogicalJobCaller(authorizer, callingProcessSource, request.operationId, callback) { caller ->
        delegate.logicalJobOperations.query(
            caller,
            request,
            includeResult = false,
            remoteConsumerLogicalJobResultCallback(delegate, caller, request.clientToken, callback),
        )
    }

    override fun getLogicalJobResult(
        request: ConsumerLogicalJobQueryParcel,
        callback: IConsumerLogicalJobResultCallback,
    ) = withLogicalJobCaller(authorizer, callingProcessSource, request.operationId, callback) { caller ->
        delegate.logicalJobOperations.query(
            caller,
            request,
            includeResult = true,
            remoteConsumerLogicalJobResultCallback(delegate, caller, request.clientToken, callback),
        )
    }

    override fun cancelLogicalJob(request: ConsumerLogicalJobQueryParcel) {
        authorizedCallerOrNull(authorizer, callingProcessSource)?.let { delegate.logicalJobOperations.cancel(it, request) }
    }
}

private fun deliverConsumerUnauthorized(externalRequestId: String?, callback: IConsumerGenerationCallback) {
    deliverRemote {
        callback.onEvent(
            ConsumerGenerationEventParcel(
                externalRequestId = externalRequestId.orEmpty(),
                sequence = 0L,
                eventTag = ConsumerWireTags.EVENT_FAILED,
                error = wireError(WireErrorCodes.CLIENT_NOT_REGISTERED),
            ),
        )
    }
}

private inline fun withResultCaller(
    authorizer: CallerAuthorizer,
    callingProcessSource: CallingProcessSource,
    request: ConsumerRequestParcel,
    callback: IConsumerResultCallback,
    block: (AuthorizedCaller) -> Unit,
) {
    val caller = authorizedCallerOrNull(authorizer, callingProcessSource)
    if (caller == null) {
        deliverRemote {
            callback.onResult(
                ConsumerResultParcel(
                    operationId = request.operationId,
                    error = wireError(WireErrorCodes.CLIENT_NOT_REGISTERED),
                ),
            )
        }
    } else {
        block(caller)
    }
}

private inline fun withControlPlaneCaller(
    authorizer: CallerAuthorizer,
    callingProcessSource: CallingProcessSource,
    request: ConsumerControlPlaneRequestParcel,
    callback: IConsumerControlPlaneResultCallback,
    block: (AuthorizedCaller) -> Unit,
) {
    val caller = authorizedCallerOrNull(authorizer, callingProcessSource)
    if (caller == null) {
        deliverRemote {
            callback.onResult(
                ConsumerControlPlaneResultParcel(
                    operationId = request.operationId,
                    error = wireError(WireErrorCodes.CLIENT_NOT_REGISTERED),
                ),
            )
        }
    } else {
        block(caller)
    }
}

private inline fun withRuntimeReadinessCaller(
    authorizer: CallerAuthorizer,
    callingProcessSource: CallingProcessSource,
    request: ConsumerControlPlaneRequestParcel,
    callback: IConsumerRuntimeReadinessResultCallback,
    block: (AuthorizedCaller) -> Unit,
) {
    val caller = authorizedCallerOrNull(authorizer, callingProcessSource)
    if (caller == null) {
        deliverRemote {
            callback.onResult(
                ConsumerRuntimeReadinessResultParcel(
                    operationId = request.operationId,
                    error = wireError(WireErrorCodes.CLIENT_NOT_REGISTERED),
                ),
            )
        }
    } else {
        block(caller)
    }
}

private inline fun withLogicalJobCaller(
    authorizer: CallerAuthorizer,
    callingProcessSource: CallingProcessSource,
    operationId: String,
    callback: IConsumerLogicalJobResultCallback,
    block: (AuthorizedCaller) -> Unit,
) {
    val caller = authorizedCallerOrNull(authorizer, callingProcessSource)
    if (caller == null) {
        deliverRemote {
            callback.onResult(
                ConsumerLogicalJobResultParcel(
                    operationId = operationId,
                    error = wireError(WireErrorCodes.CLIENT_NOT_REGISTERED),
                ),
            )
        }
    } else {
        block(caller)
    }
}

private fun authorizedCallerOrNull(authorizer: CallerAuthorizer, callingProcessSource: CallingProcessSource): AuthorizedCaller? =
    when (val result = authorizer.authorize(callingProcessSource.current())) {
        is AuthorizationResult.Allowed -> result.caller
        is AuthorizationResult.Denied -> null
    }
