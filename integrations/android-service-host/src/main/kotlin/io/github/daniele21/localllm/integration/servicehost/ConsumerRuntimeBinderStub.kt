package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.transport.binder.contract.CancelRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.CloseSessionRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerGenerationEventParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerResultParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerWireTags
import io.github.daniele21.localllm.transport.binder.contract.IConsumerGenerationCallback
import io.github.daniele21.localllm.transport.binder.contract.IConsumerLocalLlmService
import io.github.daniele21.localllm.transport.binder.contract.IConsumerResultCallback
import io.github.daniele21.localllm.transport.binder.contract.WireErrorCodes

internal class ConsumerRuntimeBinderStub(
    private val authorizer: CallerAuthorizer,
    private val delegate: SharedRuntimeHostDelegate,
    private val callingProcessSource: CallingProcessSource,
) : IConsumerLocalLlmService.Stub() {
    override fun capabilities(request: ConsumerRequestParcel, callback: IConsumerResultCallback) =
        withResultCaller(request, callback) { caller ->
            delegate.consumerOperations.capabilities(
                caller,
                request,
                remoteConsumerResultCallback(delegate, caller, request.clientToken, callback),
            )
        }

    override fun prepare(request: ConsumerRequestParcel, callback: IConsumerResultCallback) =
        withResultCaller(request, callback) { caller ->
            delegate.consumerOperations.prepare(
                caller,
                request,
                remoteConsumerResultCallback(delegate, caller, request.clientToken, callback),
            )
        }

    override fun openSession(request: ConsumerRequestParcel, callback: IConsumerResultCallback) =
        withResultCaller(request, callback) { caller ->
            delegate.consumerOperations.openSession(
                caller,
                request,
                remoteConsumerResultCallback(delegate, caller, request.clientToken, callback),
            )
        }

    override fun generate(request: ConsumerRequestParcel, callback: IConsumerGenerationCallback) {
        val caller = authorizedCallerOrNull()
        if (caller == null) {
            deliverRemote {
                callback.onEvent(
                    ConsumerGenerationEventParcel(
                        externalRequestId = request.externalRequestId.orEmpty(),
                        sequence = 0L,
                        eventTag = ConsumerWireTags.EVENT_FAILED,
                        error = wireError(WireErrorCodes.CLIENT_NOT_REGISTERED),
                    ),
                )
            }
            return
        }
        delegate.consumerOperations.generate(
            caller,
            request,
            remoteConsumerGenerationCallback(delegate, caller, request.clientToken, callback),
        )
    }

    override fun cancel(request: CancelRequestParcel) {
        authorizedCallerOrNull()?.let { delegate.consumerOperations.cancel(it, request) }
    }

    override fun closeSession(request: CloseSessionRequestParcel) {
        authorizedCallerOrNull()?.let { delegate.consumerOperations.closeSession(it, request) }
    }

    private inline fun withResultCaller(
        request: ConsumerRequestParcel,
        callback: IConsumerResultCallback,
        block: (AuthorizedCaller) -> Unit,
    ) {
        val caller = authorizedCallerOrNull()
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

    private fun authorizedCallerOrNull(): AuthorizedCaller? =
        when (val result = authorizer.authorize(callingProcessSource.current())) {
            is AuthorizationResult.Allowed -> result.caller
            is AuthorizationResult.Denied -> null
        }
}
