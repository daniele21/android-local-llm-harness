package io.github.daniele21.localllm.transport.binder.client

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.RemoteException
import io.github.daniele21.localllm.transport.binder.contract.BinderProtocolV1
import io.github.daniele21.localllm.transport.binder.contract.ClientHelloParcel
import io.github.daniele21.localllm.transport.binder.contract.ClientTokenParcel
import io.github.daniele21.localllm.transport.binder.contract.ILocalLlmService
import io.github.daniele21.localllm.transport.binder.contract.NegotiatedProtocol
import io.github.daniele21.localllm.transport.binder.contract.RegistrationResultParcel
import io.github.daniele21.localllm.transport.binder.contract.WireErrorCodes
import io.github.daniele21.localllm.transport.binder.contract.WireProtocolException
import io.github.daniele21.localllm.transport.binder.contract.negotiateProtocol
import io.github.daniele21.localllm.transport.binder.contract.validateClientHello

/** Connection lifecycle exposed by the Binder client before a LocalLlmClient is available. */
enum class SharedRuntimeConnectionState {
    DISCONNECTED,
    BINDING,
    NEGOTIATING,
    CONNECTED,
    HOST_NOT_INSTALLED,
    PERMISSION_DENIED,
    INCOMPATIBLE,
    CONNECTION_LOST,
    CLOSED,
}

data class SharedRuntimeConnectionSnapshot(
    val state: SharedRuntimeConnectionState,
    val negotiatedMinor: Int? = null,
    val enabledFeatures: Set<String> = emptySet(),
    val detail: String? = null,
)

fun interface SharedRuntimeConnectionObserver {
    fun onStateChanged(snapshot: SharedRuntimeConnectionSnapshot)
}

internal class SharedRuntimeConnection(
    private val hostConfig: SharedRuntimeHostConfig,
    private val clientHello: ClientHelloParcel,
    private val binding: SharedRuntimeBinding,
    private val observer: SharedRuntimeConnectionObserver = SharedRuntimeConnectionObserver {},
) : AutoCloseable {
    private val lock = Any()
    private val invalidations = EndpointInvalidationRegistry()
    private var current = SharedRuntimeConnectionSnapshot(SharedRuntimeConnectionState.DISCONNECTED)
    private var registeredEndpoint: RegisteredSharedRuntimeEndpoint? = null
    private var connectionEpoch = 0L

    val snapshot: SharedRuntimeConnectionSnapshot
        get() = synchronized(lock) { current }

    internal val endpoint: RegisteredSharedRuntimeEndpoint?
        get() = synchronized(lock) { registeredEndpoint }

    internal val endpointInvalidations: SharedRuntimeEndpointInvalidationSource = invalidations.source

    fun connect() {
        synchronized(lock) {
            if (current.state in ACTIVE_STATES || current.state == SharedRuntimeConnectionState.CLOSED) return
        }
        if (!binding.hostExists(hostConfig)) {
            transition(SharedRuntimeConnectionState.HOST_NOT_INSTALLED, detail = "Configured host service is not installed")
            return
        }
        val epoch = synchronized(lock) {
            if (current.state in ACTIVE_STATES || current.state == SharedRuntimeConnectionState.CLOSED) return
            connectionEpoch += 1
            connectionEpoch
        }
        transitionForEpoch(epoch, SharedRuntimeConnectionState.BINDING)
        when (
            binding.bind(
                hostConfig,
                SharedRuntimeBindingCallbacks(
                    onConnected = { service -> onServiceConnected(epoch, service) },
                    onDisconnected = { onConnectionLost(epoch, "Host service disconnected") },
                ),
            )
        ) {
            SharedRuntimeBindResult.STARTED -> Unit
            SharedRuntimeBindResult.HOST_NOT_FOUND -> failBinding(epoch, SharedRuntimeConnectionState.HOST_NOT_INSTALLED)
            SharedRuntimeBindResult.PERMISSION_DENIED -> failBinding(epoch, SharedRuntimeConnectionState.PERMISSION_DENIED)
            SharedRuntimeBindResult.FAILED -> failBinding(epoch, SharedRuntimeConnectionState.CONNECTION_LOST)
        }
    }

    override fun close() {
        val endpoint = synchronized(lock) {
            if (current.state == SharedRuntimeConnectionState.CLOSED) return
            val existing = registeredEndpoint
            registeredEndpoint = null
            current = SharedRuntimeConnectionSnapshot(SharedRuntimeConnectionState.CLOSED)
            existing
        }
        endpoint?.let { invalidations.invalidate(it.connectionEpoch, ENDPOINT_INVALIDATED_DETAIL) }
        endpoint?.let { safeUnregister(it.service, it.clientToken) }
        binding.unbind()
        invalidations.close()
        observer.onStateChanged(snapshot)
    }

    private fun onServiceConnected(epoch: Long, service: SharedRuntimeRemoteService) {
        if (!isCurrentEpoch(epoch)) return
        transitionForEpoch(epoch, SharedRuntimeConnectionState.NEGOTIATING)
        val negotiated = try {
            val hostInfo = service.protocolInfo()
            validateClientHello(clientHello)
            negotiateProtocol(hostInfo, clientHello)
        } catch (_: RemoteException) {
            onConnectionLost(epoch, "Host protocol negotiation failed")
            return
        } catch (failure: WireProtocolException) {
            failBinding(epoch, SharedRuntimeConnectionState.INCOMPATIBLE, failure.safeMessage)
            return
        }
        register(epoch, service, negotiated)
    }

    private fun register(epoch: Long, service: SharedRuntimeRemoteService, negotiated: NegotiatedProtocol) {
        try {
            service.registerClient(
                clientHello,
                hostDisconnectingCallback = { onConnectionLost(epoch, "Host process is disconnecting") },
            ) { result -> onRegistered(epoch, service, negotiated, result) }
        } catch (_: RemoteException) {
            onConnectionLost(epoch, "Host registration failed")
        }
    }

    private fun onRegistered(
        epoch: Long,
        service: SharedRuntimeRemoteService,
        negotiated: NegotiatedProtocol,
        result: RegistrationResultParcel,
    ) {
        if (!isCurrentEpoch(epoch)) return
        val registrationError = result.error
        val token = result.clientToken
        val negotiatedMinor = result.negotiatedMinor
        if (registrationError != null || token == null || negotiatedMinor == null) {
            val state = when (registrationError?.code) {
                WireErrorCodes.PROTOCOL_INCOMPATIBLE,
                WireErrorCodes.FEATURE_UNAVAILABLE,
                -> SharedRuntimeConnectionState.INCOMPATIBLE

                else -> SharedRuntimeConnectionState.PERMISSION_DENIED
            }
            failBinding(epoch, state, registrationError?.safeMessage ?: "Host registration rejected")
            return
        }
        if (negotiatedMinor != negotiated.minor) {
            failBinding(epoch, SharedRuntimeConnectionState.INCOMPATIBLE, "Host registration changed negotiated protocol")
            return
        }
        val endpoint = RegisteredSharedRuntimeEndpoint(
            service = service,
            clientToken = token,
            connectionEpoch = epoch,
        )
        val accepted = synchronized(lock) {
            if (connectionEpoch != epoch || current.state != SharedRuntimeConnectionState.NEGOTIATING) {
                false
            } else {
                registeredEndpoint = endpoint
                current = SharedRuntimeConnectionSnapshot(
                    state = SharedRuntimeConnectionState.CONNECTED,
                    negotiatedMinor = negotiated.minor,
                    enabledFeatures = negotiated.enabledFeatures,
                )
                true
            }
        }
        if (accepted) {
            observer.onStateChanged(snapshot)
        } else {
            safeUnregister(service, token)
        }
    }

    private fun failBinding(epoch: Long, state: SharedRuntimeConnectionState, detail: String? = null) {
        val shouldNotify = synchronized(lock) {
            if (connectionEpoch != epoch || current.state == SharedRuntimeConnectionState.CLOSED) {
                false
            } else {
                registeredEndpoint = null
                current = SharedRuntimeConnectionSnapshot(state, detail = detail)
                true
            }
        }
        if (shouldNotify) {
            binding.unbind()
            observer.onStateChanged(snapshot)
        }
    }

    private fun onConnectionLost(epoch: Long, detail: String) {
        val endpoint = synchronized(lock) {
            if (connectionEpoch != epoch || current.state == SharedRuntimeConnectionState.CLOSED) return
            val existing = registeredEndpoint
            registeredEndpoint = null
            current = SharedRuntimeConnectionSnapshot(
                state = SharedRuntimeConnectionState.CONNECTION_LOST,
                detail = detail,
            )
            existing
        }
        endpoint?.let { invalidations.invalidate(it.connectionEpoch, ENDPOINT_INVALIDATED_DETAIL) }
        binding.unbind()
        observer.onStateChanged(snapshot)
    }

    private fun transition(state: SharedRuntimeConnectionState, detail: String? = null) {
        synchronized(lock) {
            if (current.state == SharedRuntimeConnectionState.CLOSED) return
            current = SharedRuntimeConnectionSnapshot(state, detail = detail)
        }
        observer.onStateChanged(snapshot)
    }

    private fun transitionForEpoch(epoch: Long, state: SharedRuntimeConnectionState) {
        val shouldNotify = synchronized(lock) {
            if (connectionEpoch != epoch || current.state == SharedRuntimeConnectionState.CLOSED) {
                false
            } else {
                current = SharedRuntimeConnectionSnapshot(state)
                true
            }
        }
        if (shouldNotify) observer.onStateChanged(snapshot)
    }

    private fun isCurrentEpoch(epoch: Long): Boolean = synchronized(lock) {
        connectionEpoch == epoch && current.state != SharedRuntimeConnectionState.CLOSED
    }

    companion object {
        private const val ENDPOINT_INVALIDATED_DETAIL = "Shared-runtime connection is no longer valid"
        private val ACTIVE_STATES = setOf(
            SharedRuntimeConnectionState.BINDING,
            SharedRuntimeConnectionState.NEGOTIATING,
            SharedRuntimeConnectionState.CONNECTED,
        )

        fun create(
            context: Context,
            hostConfig: SharedRuntimeHostConfig,
            clientBuildId: String,
            requiredFeatures: Set<String> = emptySet(),
            observer: SharedRuntimeConnectionObserver = SharedRuntimeConnectionObserver {},
        ): SharedRuntimeConnection = SharedRuntimeConnection(
            hostConfig = hostConfig,
            clientHello = ClientHelloParcel(
                protocolMajor = BinderProtocolV1.MAJOR,
                protocolMinor = BinderProtocolV1.MINOR,
                minSupportedMinor = BinderProtocolV1.MIN_SUPPORTED_MINOR,
                requiredFeatures = requiredFeatures.sorted(),
                clientBuildId = clientBuildId,
            ),
            binding = AndroidSharedRuntimeBinding(context.applicationContext),
            observer = observer,
        )
    }
}

internal data class RegisteredSharedRuntimeEndpoint(
    val service: SharedRuntimeRemoteService,
    val clientToken: ClientTokenParcel,
    val connectionEpoch: Long = 0L,
)

internal fun interface SharedRuntimeEndpointInvalidationListener {
    fun onEndpointInvalidated(connectionEpoch: Long, detail: String)
}

internal interface SharedRuntimeEndpointInvalidationSource {
    fun addListener(listener: SharedRuntimeEndpointInvalidationListener): AutoCloseable
}

private fun safeUnregister(service: SharedRuntimeRemoteService, token: ClientTokenParcel) {
    try {
        service.unregisterClient(token)
    } catch (_: RemoteException) {
        // Best-effort cleanup. The host also owns Binder-death cleanup.
    }
}

private class EndpointInvalidationRegistry : AutoCloseable {
    private val lock = Any()
    private var closed = false
    private val listeners = LinkedHashSet<SharedRuntimeEndpointInvalidationListener>()

    val source = object : SharedRuntimeEndpointInvalidationSource {
        override fun addListener(listener: SharedRuntimeEndpointInvalidationListener): AutoCloseable {
            synchronized(lock) {
                if (closed) return AutoCloseable {}
                listeners += listener
            }
            return AutoCloseable { synchronized(lock) { listeners -= listener } }
        }
    }

    fun invalidate(connectionEpoch: Long, detail: String) {
        val snapshot = synchronized(lock) { listeners.toList() }
        snapshot.forEach { it.onEndpointInvalidated(connectionEpoch, detail) }
    }

    override fun close() {
        synchronized(lock) {
            closed = true
            listeners.clear()
        }
    }
}

private class AndroidSharedRuntimeBinding(private val context: Context) : SharedRuntimeBinding {
    private val lock = Any()
    private var connection: ServiceConnection? = null

    override fun hostExists(hostConfig: SharedRuntimeHostConfig): Boolean = try {
        @Suppress("DEPRECATION")
        context.packageManager.getServiceInfo(hostConfig.componentName, PackageManager.GET_META_DATA)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    override fun bind(hostConfig: SharedRuntimeHostConfig, callbacks: SharedRuntimeBindingCallbacks): SharedRuntimeBindResult {
        val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val aidl = ILocalLlmService.Stub.asInterface(service) ?: run {
                    callbacks.onDisconnected()
                    return
                }
                callbacks.onConnected(AidlSharedRuntimeRemoteService(aidl))
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                callbacks.onDisconnected()
            }

            override fun onBindingDied(name: ComponentName?) {
                callbacks.onDisconnected()
            }

            override fun onNullBinding(name: ComponentName?) {
                callbacks.onDisconnected()
            }
        }
        synchronized(lock) {
            if (connection != null) return SharedRuntimeBindResult.FAILED
            connection = serviceConnection
        }
        val intent = Intent().setComponent(hostConfig.componentName)
        return try {
            if (context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)) {
                SharedRuntimeBindResult.STARTED
            } else {
                clearConnection(serviceConnection)
                SharedRuntimeBindResult.FAILED
            }
        } catch (_: SecurityException) {
            clearConnection(serviceConnection)
            SharedRuntimeBindResult.PERMISSION_DENIED
        }
    }

    override fun unbind() {
        val active = synchronized(lock) {
            val existing = connection
            connection = null
            existing
        } ?: return
        try {
            context.unbindService(active)
        } catch (_: IllegalArgumentException) {
            // Best-effort idempotent teardown.
        }
    }

    private fun clearConnection(expected: ServiceConnection) {
        synchronized(lock) {
            if (connection === expected) connection = null
        }
    }
}
