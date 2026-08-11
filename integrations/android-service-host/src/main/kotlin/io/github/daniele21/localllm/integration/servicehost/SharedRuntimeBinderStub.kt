package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.transport.binder.contract.CancelRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.ClientHelloParcel
import io.github.daniele21.localllm.transport.binder.contract.ClientTokenParcel
import io.github.daniele21.localllm.transport.binder.contract.CloseSessionRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.GenerationRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.IClientLifecycle
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
    override fun getProtocolInfo(): ProtocolInfoParcel {
        requireAuthorizedCaller()
        return delegate.protocolInfo
    }

    override fun registerClient(hello: ClientHelloParcel, lifecycle: IClientLifecycle, callback: IRegistrationCallback) {
        val caller = authorizedCallerOrNull()
        if (caller == null) {
            deliverRemote { callback.onResult(registrationFailure(wireError(WireErrorCodes.CLIENT_NOT_REGISTERED))) }
            return
        }
        delegate.registerClient(
            caller = caller,
            hello = hello,
            lifecycle = BinderClientLifecycleLinker(lifecycle),
            callback = remoteRegistrationCallback(delegate, caller, callback),
        )
    }

    override fun prepare(request: PrepareRequestParcel, callback: IPrepareCallback) {
        val caller = authorizedCallerOrNull()
        if (caller == null) {
            deliverRemote { callback.onResult(prepareFailure(request.operationId, wireError(WireErrorCodes.CLIENT_NOT_REGISTERED))) }
            return
        }
        delegate.prepare(caller, request, remotePrepareCallback(delegate, caller, request.clientToken, callback))
    }

    override fun openSession(request: OpenSessionRequestParcel, callback: ISessionCallback) {
        val caller = authorizedCallerOrNull()
        if (caller == null) {
            deliverRemote { callback.onResult(sessionFailure(request.operationId, wireError(WireErrorCodes.CLIENT_NOT_REGISTERED))) }
            return
        }
        delegate.openSession(caller, request, remoteSessionCallback(delegate, caller, request.clientToken, callback))
    }

    override fun generate(request: GenerationRequestParcel, callback: IGenerationCallback) {
        val caller = authorizedCallerOrNull()
        if (caller == null) {
            deliverRemote {
                callback.onEvent(generationFailure(request.externalRequestId, wireError(WireErrorCodes.CLIENT_NOT_REGISTERED)))
            }
            return
        }
        delegate.generate(caller, request, remoteGenerationCallback(delegate, caller, request.clientToken, callback))
    }

    override fun cancel(request: CancelRequestParcel) {
        val caller = authorizedCallerOrNull() ?: return
        delegate.cancel(caller, request)
    }

    override fun closeSession(request: CloseSessionRequestParcel) {
        val caller = authorizedCallerOrNull() ?: return
        delegate.closeSession(caller, request)
    }

    override fun unregisterClient(clientToken: ClientTokenParcel) {
        val caller = authorizedCallerOrNull() ?: return
        delegate.unregisterClient(caller, clientToken.value)
    }

    private fun authorizedCallerOrNull(): AuthorizedCaller? = when (val result = authorizer.authorize(callingProcessSource.current())) {
        is AuthorizationResult.Allowed -> result.caller
        is AuthorizationResult.Denied -> null
    }

    private fun requireAuthorizedCaller(): AuthorizedCaller =
        authorizedCallerOrNull() ?: throw SecurityException("Caller is not authorized")
}
