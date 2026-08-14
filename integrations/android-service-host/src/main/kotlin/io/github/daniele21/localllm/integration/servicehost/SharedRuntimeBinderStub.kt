package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.transport.binder.contract.CancelRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.ClientHelloParcel
import io.github.daniele21.localllm.transport.binder.contract.ClientTokenParcel
import io.github.daniele21.localllm.transport.binder.contract.CloseSessionRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerGenerationEventParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerResultParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerWireTags
import io.github.daniele21.localllm.transport.binder.contract.GenerationRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.IClientLifecycle
import io.github.daniele21.localllm.transport.binder.contract.IConsumerGenerationCallback
import io.github.daniele21.localllm.transport.binder.contract.IConsumerLocalLlmService
import io.github.daniele21.localllm.transport.binder.contract.IConsumerResultCallback
import io.github.daniele21.localllm.transport.binder.contract.IGenerationCallback
import io.github.daniele21.localllm.transport.binder.contract.ILocalLlmService
import io.github.daniele21.localllm.transport.binder.contract.IPrepareCallback
import io.github.daniele21.localllm.transport.binder.contract.IRegistrationCallback
import io.github.daniele21.localllm.transport.binder.contract.ISessionCallback
import io.github.daniele21.localllm.transport.binder.contract.OpenSessionRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.PrepareRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.ProtocolInfoParcel
import io.github.daniele21.localllm.transport.binder.contract.WireErrorCodes

class SharedRuntimeBinderStub(
    private val authorizer: CallerAuthorizer,
    private val delegate: SharedRuntimeHostDelegate,
    private val callingProcessSource: CallingProcessSource = BinderCallingProcessSource(),
) : ILocalLlmService.Stub() {
    private val consumerStub = ConsumerRuntimeBinderStub(authorizer, delegate, callingProcessSource)

    override fun getProtocolInfo(): ProtocolInfoParcel {
        authorizedCallerOrNull() ?: throw SecurityException("Caller is not authorized")
        return delegate.protocolInfo
    }

    override fun registerClient(hello: ClientHelloParcel, lifecycle: IClientLifecycle, callback: IRegistrationCallback) {
        val caller = authorizedCallerOrNull()
        if (caller == null) {
            deliverRemote { callback.onResult(registrationFailure(wireError(WireErrorCodes.CLIENT_NOT_REGISTERED))) }
            return
        }
        delegate.registerClient(
            caller,
            hello,
            BinderClientLifecycleLinker(lifecycle),
            remoteRegistrationCallback(delegate, caller, callback),
        )
    }

    override fun prepare(request: PrepareRequestParcel, callback: IPrepareCallback) {
        val caller = authorizedCallerOrNull()
        if (caller == null) {
            deliverRemote { callback.onResult(prepareFailure(request.operationId, wireError(WireErrorCodes.CLIENT_NOT_REGISTERED))) }
            return
        }
        delegate.runtimeOperations.prepare(caller, request, remotePrepareCallback(delegate, caller, request.clientToken, callback))
    }

    override fun openSession(request: OpenSessionRequestParcel, callback: ISessionCallback) {
        val caller = authorizedCallerOrNull()
        if (caller == null) {
            deliverRemote { callback.onResult(sessionFailure(request.operationId, wireError(WireErrorCodes.CLIENT_NOT_REGISTERED))) }
            return
        }
        delegate.runtimeOperations.openSession(caller, request, remoteSessionCallback(delegate, caller, request.clientToken, callback))
    }

    override fun generate(request: GenerationRequestParcel, callback: IGenerationCallback) {
        val caller = authorizedCallerOrNull()
        if (caller == null) {
            deliverRemote {
                callback.onEvent(generationFailure(request.externalRequestId, wireError(WireErrorCodes.CLIENT_NOT_REGISTERED)))
            }
            return
        }
        delegate.runtimeOperations.generate(caller, request, remoteGenerationCallback(delegate, caller, request.clientToken, callback))
    }

    override fun cancel(request: CancelRequestParcel) {
        authorizedCallerOrNull()?.let { delegate.runtimeOperations.cancel(it, request) }
    }

    override fun closeSession(request: CloseSessionRequestParcel) {
        authorizedCallerOrNull()?.let { delegate.runtimeOperations.closeSession(it, request) }
    }

    override fun unregisterClient(clientToken: ClientTokenParcel) {
        authorizedCallerOrNull()?.let { delegate.unregisterClient(it, clientToken.value) }
    }

    override fun getConsumerApi(): IConsumerLocalLlmService {
        authorizedCallerOrNull() ?: throw SecurityException("Caller is not authorized")
        return consumerStub
    }

    private fun authorizedCallerOrNull(): AuthorizedCaller? = when (val result = authorizer.authorize(callingProcessSource.current())) {
        is AuthorizationResult.Allowed -> result.caller
        is AuthorizationResult.Denied -> null
    }

    private fun requireAuthorizedCaller(): AuthorizedCaller =
        authorizedCallerOrNull() ?: throw SecurityException("Caller is not authorized")
}
