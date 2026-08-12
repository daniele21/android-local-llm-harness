package io.github.daniele21.localllm.transport.binder.client

import android.os.RemoteException
import io.github.daniele21.localllm.transport.binder.contract.ClientHelloParcel
import io.github.daniele21.localllm.transport.binder.contract.ClientTokenParcel
import io.github.daniele21.localllm.transport.binder.contract.CloseSessionRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.OpenSessionRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.PrepareRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.PrepareResultParcel
import io.github.daniele21.localllm.transport.binder.contract.ProtocolInfoParcel
import io.github.daniele21.localllm.transport.binder.contract.RegistrationResultParcel
import io.github.daniele21.localllm.transport.binder.contract.SessionResultParcel

internal interface SharedRuntimeRemoteService {
    @Throws(RemoteException::class)
    fun protocolInfo(): ProtocolInfoParcel

    @Throws(RemoteException::class)
    fun registerClient(
        hello: ClientHelloParcel,
        onHostDisconnecting: () -> Unit,
        callback: (RegistrationResultParcel) -> Unit,
    )

    @Throws(RemoteException::class)
    fun prepare(request: PrepareRequestParcel, callback: (PrepareResultParcel) -> Unit)

    @Throws(RemoteException::class)
    fun openSession(request: OpenSessionRequestParcel, callback: (SessionResultParcel) -> Unit)

    @Throws(RemoteException::class)
    fun closeSession(request: CloseSessionRequestParcel)

    @Throws(RemoteException::class)
    fun unregisterClient(clientToken: ClientTokenParcel)
}

internal data class RegisteredSharedRuntimeEndpoint(
    val service: SharedRuntimeRemoteService,
    val clientToken: ClientTokenParcel,
)
