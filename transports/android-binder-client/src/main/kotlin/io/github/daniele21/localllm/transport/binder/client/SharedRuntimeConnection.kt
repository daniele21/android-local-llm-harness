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

class SharedRuntimeConnection internal constructor(
    private val hostConfig: SharedRuntimeHostConfig,
    private val clientHello: ClientHelloParcel,
    private val binding: SharedRuntimeBinding,
    private val observer: SharedRuntimeConnectionObserver = SharedRuntimeConnectionObserver {},
) : AutoCloseable {
    private val lock = Any()
    private var current = SharedRuntimeConnectionSnapshot(SharedRuntimeConnectionState.DISCONNECTED)
    private var registeredEndpoint: RegisteredSharedRuntimeEndpoint? = null

    val snapshot: SharedRuntimeConnectionSnapshot
        get() = synchronized(lock) { current }

    internal val endpoint: RegisteredSharedRuntimeEndpoint?
        get() = synchronized(lock) { registeredEndpoint }

    fun connect() {
        synchronized(lock) {
            if (current.state in ACTIVE_STATES || current.state == SharedRuntimeConnectionState.CLOSED) return
        }
        if (!binding.hostExists(hostConfig)) {
            transition(SharedRuntimeConnectionState.HOST_NOT_INSTALLED, detail = "Configured host service is not installed")
            return
        }

        transition(SharedRuntimeConnectionState.BINDING)
        when (
            binding.bind(
                hostConfig,
                object : SharedRuntimeBindingCallbacks {
                    override fun onConnected(service: SharedRuntimeRemoteService) = negotiate(service)
                    override fun onDisconnected() = connectionLost("Host Binder connection was lost")
                },
            )
        ) {
            SharedRuntimeBindResult.STARTED -> Unit

            SharedRuntimeBindResult.PERMISSION_DENIED -> {
                transition(SharedRuntimeConnectionState.PERMISSION_DENIED, detail = "Host rejected the configured caller")
            }

            SharedRuntimeBindResult.REJECTED -> connectionLost("Android rejected the explicit host bind")
        }
    }

    override fun close() {
        val registered = synchronized(lock) {
            if (current.state == SharedRuntimeConnectionState.CLOSED) return
            registeredEndpoint.also { registeredEndpoint = null }
        }
        registered?.let { endpoint ->
            runCatching { endpoint.service.unregisterClient(endpoint.clientToken) }
        }
        binding.unbind()
        transition(SharedRuntimeConnectionState.CLOSED)
    }

    private fun negotiate(service: SharedRuntimeRemoteService) {
        synchronized(lock) {
            if (current.state == SharedRuntimeConnectionState.CLOSED) return
        }
        transition(SharedRuntimeConnectionState.NEGOTIATING)
        val negotiated = try {
            negotiateProtocol(service.protocolInfo(), clientHello)
        } catch (error: SecurityException) {
            binding.unbind()
            transition(SharedRuntimeConnectionState.PERMISSION_DENIED, detail = error.message)
            return
        } catch (error: WireProtocolException) {
            binding.unbind()
            transition(SharedRuntimeConnectionState.INCOMPATIBLE, detail = error.message)
            return
        } catch (error: RemoteException) {
            connectionLost(error.message ?: "Host Binder call failed")
            return
        }
        register(service, negotiated)
    }

    private fun register(service: SharedRuntimeRemoteService, negotiated: NegotiatedProtocol) {
        try {
            service.registerClient(
                hello = clientHello,
                onHostDisconnecting = { connectionLost("Host is disconnecting") },
                callback = { result -> handleRegistration(service, negotiated, result) },
            )
        } catch (error: SecurityException) {
            binding.unbind()
            transition(SharedRuntimeConnectionState.PERMISSION_DENIED, detail = error.message)
        } catch (error: RemoteException) {
            connectionLost(error.message ?: "Client registration failed")
        }
    }

    private fun handleRegistration(
        service: SharedRuntimeRemoteService,
        negotiated: NegotiatedProtocol,
        result: RegistrationResultParcel,
    ) {
        synchronized(lock) {
            if (current.state != SharedRuntimeConnectionState.NEGOTIATING) return
        }
        result.error?.let { error ->
            when (error.code) {
                WireErrorCodes.PROTOCOL_INCOMPATIBLE,
                WireErrorCodes.FEATURE_UNAVAILABLE,
                -> incompatible(error.safeMessage)

                WireErrorCodes.CLIENT_NOT_REGISTERED -> permissionDenied(error.safeMessage)
                else -> connectionLost(error.safeMessage)
            }
            return
        }

        val token = result.clientToken
        val resultMinor = result.negotiatedMinor
        val resultFeatures = result.enabledFeatures.toSet()
        if (
            token == null ||
            token.value.isBlank() ||
            token.value.length > BinderProtocolV1.MAX_IDENTIFIER_CHARACTERS ||
            resultMinor != negotiated.minor ||
            resultFeatures.size != result.enabledFeatures.size ||
            resultFeatures != negotiated.enabledFeatures
        ) {
            incompatible("Host returned an invalid registration result")
            return
        }

        synchronized(lock) {
            if (current.state != SharedRuntimeConnectionState.NEGOTIATING) return
            registeredEndpoint = RegisteredSharedRuntimeEndpoint(service, token)
        }
        connected(negotiated)
    }

    private fun connected(negotiated: NegotiatedProtocol) {
        transition(
            state = SharedRuntimeConnectionState.CONNECTED,
            negotiatedMinor = negotiated.minor,
            enabledFeatures = negotiated.enabledFeatures,
        )
    }

    private fun permissionDenied(detail: String) {
        binding.unbind()
        transition(SharedRuntimeConnectionState.PERMISSION_DENIED, detail = detail)
    }

    private fun incompatible(detail: String) {
        binding.unbind()
        transition(SharedRuntimeConnectionState.INCOMPATIBLE, detail = detail)
    }

    private fun connectionLost(detail: String) {
        synchronized(lock) { registeredEndpoint = null }
        binding.unbind()
        transition(SharedRuntimeConnectionState.CONNECTION_LOST, detail = detail)
    }

    private fun transition(
        state: SharedRuntimeConnectionState,
        negotiatedMinor: Int? = null,
        enabledFeatures: Set<String> = emptySet(),
        detail: String? = null,
    ) {
        val snapshot = synchronized(lock) {
            if (current.state == SharedRuntimeConnectionState.CLOSED && state != SharedRuntimeConnectionState.CLOSED) {
                return
            }
            SharedRuntimeConnectionSnapshot(state, negotiatedMinor, enabledFeatures, detail).also { current = it }
        }
        runCatching { observer.onStateChanged(snapshot) }
    }

    companion object {
        fun create(
            context: Context,
            hostConfig: SharedRuntimeHostConfig,
            clientBuildId: String,
            requiredFeatures: Set<String> = emptySet(),
            observer: SharedRuntimeConnectionObserver = SharedRuntimeConnectionObserver {},
        ): SharedRuntimeConnection {
            val hello = ClientHelloParcel(
                protocolMajor = BinderProtocolV1.MAJOR,
                protocolMinor = BinderProtocolV1.MINOR,
                minSupportedMinor = BinderProtocolV1.MIN_SUPPORTED_MINOR,
                requiredFeatures = requiredFeatures.sorted(),
                clientBuildId = clientBuildId,
            )
            validateClientHello(hello)
            return SharedRuntimeConnection(
                hostConfig = hostConfig,
                clientHello = hello,
                binding = AndroidSharedRuntimeBinding(context.applicationContext),
                observer = observer,
            )
        }

        private val ACTIVE_STATES = setOf(
            SharedRuntimeConnectionState.BINDING,
            SharedRuntimeConnectionState.NEGOTIATING,
            SharedRuntimeConnectionState.CONNECTED,
        )
    }
}

internal interface SharedRuntimeBindingCallbacks {
    fun onConnected(service: SharedRuntimeRemoteService)
    fun onDisconnected()
}

internal enum class SharedRuntimeBindResult {
    STARTED,
    PERMISSION_DENIED,
    REJECTED,
}

internal interface SharedRuntimeBinding {
    fun hostExists(hostConfig: SharedRuntimeHostConfig): Boolean
    fun bind(hostConfig: SharedRuntimeHostConfig, callbacks: SharedRuntimeBindingCallbacks): SharedRuntimeBindResult
    fun unbind()
}

private class AndroidSharedRuntimeBinding(private val context: Context) : SharedRuntimeBinding {
    private val lock = Any()
    private var connection: ServiceConnection? = null

    override fun hostExists(hostConfig: SharedRuntimeHostConfig): Boolean = try {
        @Suppress("DEPRECATION")
        context.packageManager.getServiceInfo(hostConfig.componentName(), 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    override fun bind(hostConfig: SharedRuntimeHostConfig, callbacks: SharedRuntimeBindingCallbacks): SharedRuntimeBindResult {
        val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val proxy = service?.let(ILocalLlmService.Stub::asInterface)
                if (proxy == null) {
                    callbacks.onDisconnected()
                } else {
                    callbacks.onConnected(AidlSharedRuntimeRemoteService(proxy))
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) = callbacks.onDisconnected()
            override fun onBindingDied(name: ComponentName?) = callbacks.onDisconnected()
            override fun onNullBinding(name: ComponentName?) = callbacks.onDisconnected()
        }
        synchronized(lock) {
            if (connection != null) return SharedRuntimeBindResult.STARTED
            connection = serviceConnection
        }
        return try {
            val started = context.bindService(
                Intent().setComponent(hostConfig.componentName()),
                serviceConnection,
                Context.BIND_AUTO_CREATE,
            )
            if (started) {
                SharedRuntimeBindResult.STARTED
            } else {
                synchronized(lock) { connection = null }
                SharedRuntimeBindResult.REJECTED
            }
        } catch (_: SecurityException) {
            synchronized(lock) { connection = null }
            SharedRuntimeBindResult.PERMISSION_DENIED
        }
    }

    override fun unbind() {
        val active = synchronized(lock) {
            connection.also { connection = null }
        } ?: return
        runCatching { context.unbindService(active) }
    }
}

internal fun SharedRuntimeHostConfig.componentName(): ComponentName = ComponentName(packageName, serviceClassName)
