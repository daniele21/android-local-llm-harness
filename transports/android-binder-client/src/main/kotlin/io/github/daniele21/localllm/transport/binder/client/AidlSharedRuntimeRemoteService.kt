package io.github.daniele21.localllm.transport.binder.client

import io.github.daniele21.localllm.transport.binder.contract.ClientHelloParcel
import io.github.daniele21.localllm.transport.binder.contract.ClientTokenParcel
import io.github.daniele21.localllm.transport.binder.contract.CloseSessionRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.IClientLifecycle
import io.github.daniele21.localllm.transport.binder.contract.ILocalLlmService
import io.github.daniele21.localllm.transport.binder.contract.IPrepareCallback
import io.github.daniele21.localllm.transport.binder.contract.IRegistrationCallback
import io.github.daniele21.localllm.transport.binder.contract.ISessionCallback
import io.github.daniele21.localllm.transport.binder.contract.OpenSessionRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.PrepareRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.PrepareResultParcel
import io.github.daniele21.localllm.transport.binder.contract.ProtocolInfoParcel
import io.github.daniele21.localllm.transport.binder.contract.RegistrationResultParcel
import io.github.daniele21.localllm.transport.binder.contract.SessionResultParcel

internal class AidlSharedRuntimeRemoteService(private val delegate: ILocalLlmService) : SharedRuntimeRemoteService {
    override fun protocolInfo(): ProtocolInfoParcel = delegate.protocolInfo

    override fun registerClient(
        hello: ClientHelloParcel,
        hostDisconnectingCallback: () -> Unit,
        callback: (RegistrationResultParcel) -> Unit,
    ) {
        delegate.registerClient(
            hello,
            object : IClientLifecycle.Stub() {
                override fun onHostDisconnecting() = hostDisconnectingCallback()
            },
            object : IRegistrationCallback.Stub() {
                override fun onResult(result: RegistrationResultParcel) = callback(result)
            },
        )
    }

    override fun prepare(request: PrepareRequestParcel, callback: (PrepareResultParcel) -> Unit) {
        delegate.prepare(
            request,
            object : IPrepareCallback.Stub() {
                override fun onResult(result: PrepareResultParcel) = callback(result)
            },
        )
    }

    override fun openSession(request: OpenSessionRequestParcel, callback: (SessionResultParcel) -> Unit) {
        delegate.openSession(
            request,
            object : ISessionCallback.Stub() {
                override fun onResult(result: SessionResultParcel) = callback(result)
            },
        )
    }

    override fun closeSession(request: CloseSessionRequestParcel) = delegate.closeSession(request)

    override fun unregisterClient(clientToken: ClientTokenParcel) = delegate.unregisterClient(clientToken)
}
