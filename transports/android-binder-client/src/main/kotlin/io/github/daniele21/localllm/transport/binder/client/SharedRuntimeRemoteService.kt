package io.github.daniele21.localllm.transport.binder.client

import android.os.RemoteException
import io.github.daniele21.localllm.transport.binder.contract.CancelRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.ClientHelloParcel
import io.github.daniele21.localllm.transport.binder.contract.ClientTokenParcel
import io.github.daniele21.localllm.transport.binder.contract.CloseSessionRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerControlPlaneRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerControlPlaneResultParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerGenerationEventParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerResultParcel
import io.github.daniele21.localllm.transport.binder.contract.GenerationEventParcel
import io.github.daniele21.localllm.transport.binder.contract.GenerationRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.OpenSessionRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.PrepareRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.PrepareResultParcel
import io.github.daniele21.localllm.transport.binder.contract.ProtocolInfoParcel
import io.github.daniele21.localllm.transport.binder.contract.RegistrationResultParcel
import io.github.daniele21.localllm.transport.binder.contract.SessionResultParcel

internal interface ConsumerSharedRuntimeRemoteService {
    @Throws(RemoteException::class)
    fun capabilities(request: ConsumerRequestParcel, callback: (ConsumerResultParcel) -> Unit)

    @Throws(RemoteException::class)
    fun prepare(request: ConsumerRequestParcel, callback: (ConsumerResultParcel) -> Unit)

    @Throws(RemoteException::class)
    fun openSession(request: ConsumerRequestParcel, callback: (ConsumerResultParcel) -> Unit)

    @Throws(RemoteException::class)
    fun generate(request: ConsumerRequestParcel, callback: (ConsumerGenerationEventParcel) -> Unit)

    @Throws(RemoteException::class)
    fun cancel(request: CancelRequestParcel)

    @Throws(RemoteException::class)
    fun closeSession(request: CloseSessionRequestParcel)

    @Throws(RemoteException::class)
    fun discoverUseCases(request: ConsumerControlPlaneRequestParcel, callback: (ConsumerControlPlaneResultParcel) -> Unit)

    @Throws(RemoteException::class)
    fun discoverPresets(request: ConsumerControlPlaneRequestParcel, callback: (ConsumerControlPlaneResultParcel) -> Unit)

    @Throws(RemoteException::class)
    fun activate(request: ConsumerControlPlaneRequestParcel, callback: (ConsumerControlPlaneResultParcel) -> Unit)

    @Throws(RemoteException::class)
    fun deactivate(request: ConsumerControlPlaneRequestParcel, callback: (ConsumerControlPlaneResultParcel) -> Unit)
}

internal interface SharedRuntimeRemoteService {
    val consumer: ConsumerSharedRuntimeRemoteService

    @Throws(RemoteException::class)
    fun protocolInfo(): ProtocolInfoParcel

    @Throws(RemoteException::class)
    fun registerClient(hello: ClientHelloParcel, hostDisconnectingCallback: () -> Unit, callback: (RegistrationResultParcel) -> Unit)

    @Throws(RemoteException::class)
    fun prepare(request: PrepareRequestParcel, callback: (PrepareResultParcel) -> Unit)

    @Throws(RemoteException::class)
    fun openSession(request: OpenSessionRequestParcel, callback: (SessionResultParcel) -> Unit)

    @Throws(RemoteException::class)
    fun closeSession(request: CloseSessionRequestParcel)

    @Throws(RemoteException::class)
    fun generate(request: GenerationRequestParcel, callback: (GenerationEventParcel) -> Unit)

    @Throws(RemoteException::class)
    fun cancel(request: CancelRequestParcel)

    @Throws(RemoteException::class)
    fun unregisterClient(clientToken: ClientTokenParcel)
}

internal data class RegisteredSharedRuntimeEndpoint(
    val service: SharedRuntimeRemoteService,
    val clientToken: ClientTokenParcel,
    val connectionEpoch: Long = 0L,
    val negotiatedMinor: Int? = null,
    val enabledFeatures: Set<String> = emptySet(),
)

internal fun interface SharedRuntimeEndpointInvalidationListener {
    fun onEndpointInvalidated(connectionEpoch: Long, detail: String)
}

internal fun interface SharedRuntimeEndpointInvalidationSource {
    fun addListener(listener: SharedRuntimeEndpointInvalidationListener): AutoCloseable
}

internal class EndpointInvalidationRegistry {
    private val lock = Any()
    private val listeners = mutableSetOf<SharedRuntimeEndpointInvalidationListener>()

    val source = SharedRuntimeEndpointInvalidationSource { listener ->
        synchronized(lock) { listeners += listener }
        object : AutoCloseable {
            override fun close() {
                synchronized(lock) { listeners -= listener }
            }
        }
    }

    fun notify(connectionEpoch: Long, detail: String) {
        val snapshot = synchronized(lock) { listeners.toList() }
        snapshot.forEach { listener -> runCatching { listener.onEndpointInvalidated(connectionEpoch, detail) } }
    }
}
