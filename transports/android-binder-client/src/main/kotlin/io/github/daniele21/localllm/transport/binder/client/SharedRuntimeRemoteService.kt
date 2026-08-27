package io.github.daniele21.localllm.transport.binder.client

import android.os.RemoteException
import io.github.daniele21.localllm.transport.binder.contract.CancelRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.ClientHelloParcel
import io.github.daniele21.localllm.transport.binder.contract.ClientTokenParcel
import io.github.daniele21.localllm.transport.binder.contract.CloseSessionRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerControlPlaneRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerControlPlaneResultParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerGenerationEventParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerGenerationRequestV2Parcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerResultParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerRuntimeReadinessResultParcel
import io.github.daniele21.localllm.transport.binder.contract.GenerationEventParcel
import io.github.daniele21.localllm.transport.binder.contract.GenerationRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.OpenSessionRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.PrepareRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.PrepareResultParcel
import io.github.daniele21.localllm.transport.binder.contract.ProtocolInfoParcel
import io.github.daniele21.localllm.transport.binder.contract.RegistrationResultParcel
import io.github.daniele21.localllm.transport.binder.contract.SessionResultParcel
import io.github.daniele21.localllm.transport.binder.contract.WireErrorCodes
import io.github.daniele21.localllm.transport.binder.contract.WireErrorParcel

/** Mirrors the negotiated Consumer Binder surface; splitting it would obscure transaction ownership. */
@Suppress("TooManyFunctions")
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
    fun generateV2(request: ConsumerGenerationRequestV2Parcel, callback: (ConsumerGenerationEventParcel) -> Unit): Unit =
        throw RemoteException("Consumer generation v2 is unavailable")

    @Throws(RemoteException::class)
    fun cancel(request: CancelRequestParcel)

    @Throws(RemoteException::class)
    fun closeSession(request: CloseSessionRequestParcel)

    @Throws(RemoteException::class)
    fun discoverUseCases(request: ConsumerControlPlaneRequestParcel, callback: (ConsumerControlPlaneResultParcel) -> Unit) {
        callback(controlPlaneUnavailable(request.operationId))
    }

    @Throws(RemoteException::class)
    fun discoverPresets(request: ConsumerControlPlaneRequestParcel, callback: (ConsumerControlPlaneResultParcel) -> Unit) {
        callback(controlPlaneUnavailable(request.operationId))
    }

    @Throws(RemoteException::class)
    fun activate(request: ConsumerControlPlaneRequestParcel, callback: (ConsumerControlPlaneResultParcel) -> Unit) {
        callback(controlPlaneUnavailable(request.operationId))
    }

    @Throws(RemoteException::class)
    fun deactivate(request: ConsumerControlPlaneRequestParcel, callback: (ConsumerControlPlaneResultParcel) -> Unit) {
        callback(controlPlaneUnavailable(request.operationId))
    }

    @Throws(RemoteException::class)
    fun runtimeReadiness(request: ConsumerControlPlaneRequestParcel, callback: (ConsumerRuntimeReadinessResultParcel) -> Unit) {
        callback(runtimeReadinessUnavailable(request.operationId))
    }
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

private fun controlPlaneUnavailable(operationId: String) = ConsumerControlPlaneResultParcel(
    operationId = operationId,
    error = WireErrorParcel(
        code = WireErrorCodes.FEATURE_UNAVAILABLE,
        safeMessage = "Consumer control plane is unavailable",
        retryable = false,
    ),
)

private fun runtimeReadinessUnavailable(operationId: String) = ConsumerRuntimeReadinessResultParcel(
    operationId = operationId,
    error = WireErrorParcel(
        code = WireErrorCodes.FEATURE_UNAVAILABLE,
        safeMessage = "Consumer runtime readiness is unavailable",
        retryable = false,
    ),
)
