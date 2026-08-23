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
                object : SharedRuntimeBindingCallbacks {
                    override fun onConnected(service: SharedRuntimeRemoteService) = negotiate(service, epoch)
                    override fun onDisconnected() = connectionLost("Host Binder connection was lost", epoch)
                },
            )
        ) {
            SharedRuntimeBindResult.STARTED -> Unit

            SharedRuntimeBindResult.PERMISSION_DENIED -> {
                transitionForEpoch(
                    epoch,
                    SharedRuntimeConnectionState.PERMISSION_DENIED,
                    detail = "Host rejected the configured caller",
                )
            }

            SharedRuntimeBindResult.REJECTED -> connectionLost("Android rejected the explicit host bind", epoch)
        }
    }

    override fun close() {
        val registered = synchronized(lock) {
            if (current.state == SharedRuntimeConnectionState.CLOSED) return
            connectionEpoch += 1
            registeredEndpoint.also { registeredEndpoint = null }
        }
        registered?.let { endpoint ->
            runCatching { endpoint.service.unregisterClient(endpoint.clientToken) }
            invalidations.notify(endpoint.connectionEpoch, "Shared-runtime client connection closed")
        }
        binding.unbind()
        transition(SharedRuntimeConnectionState.CLOSED)
    }

    private fun negotiate(service: SharedRuntimeRemoteService, epoch: Long) {
        if (!isCurrentEpoch(epoch)) return
        transitionForEpoch(epoch, SharedRuntimeConnectionState.NEGOTIATING)
        val negotiated = try {
            negotiateProtocol(service.protocolInfo(), clientHello)
        } catch (error: SecurityException) {
            failConnection(epoch, SharedRuntimeConnectionState.PERMISSION_DENIED, error.message)
            return
        } catch (error: WireProtocolException) {
            failConnection(epoch, SharedRuntimeConnectionState.INCOMPATIBLE, error.message)
            return
        } catch (error: RemoteException) {
            connectionLost(error.message ?: "Host Binder call failed", epoch)
            return
        }
        register(service, negotiated, epoch)
    }

    private fun register(service: SharedRuntimeRemoteService, negotiated: NegotiatedProtocol, epoch: Long) {
        if (!isCurrentEpoch(epoch)) return
        try {
            service.registerClient(
                hello = clientHello,
                hostDisconnectingCallback = { connectionLost("Host is disconnecting", epoch) },
                callback = { result -> handleRegistration(service, negotiated, result, epoch) },
            )
        } catch (error: SecurityException) {
            failConnection(epoch, SharedRuntimeConnectionState.PERMISSION_DENIED, error.message)
        } catch (error: RemoteException) {
            connectionLost(error.message ?: "Client registration failed", epoch)
        }
    }

    private fun handleRegistration(
        service: SharedRuntimeRemoteService,
        negotiated: NegotiatedProtocol,
        result: RegistrationResultParcel,
        epoch: Long,
    ) {
        val callbackIsCurrent = synchronized(lock) {
            epoch == connectionEpoch && current.state == SharedRuntimeConnectionState.NEGOTIATING
        }
        if (!callbackIsCurrent) return

        when (val validation = validateRegisteredConsumer(service, negotiated, result)) {
            is RegisteredConsumerValidation.Ready -> {
                val published = synchronized(lock) {
                    if (epoch != connectionEpoch || current.state != SharedRuntimeConnectionState.NEGOTIATING) {
                        false
                    } else {
                        registeredEndpoint = RegisteredSharedRuntimeEndpoint(service, validation.token, epoch)
                        true
                    }
                }
                if (!published) return
                transitionForEpoch(
                    epoch = epoch,
                    state = SharedRuntimeConnectionState.CONNECTED,
                    negotiatedMinor = negotiated.minor,
                    enabledFeatures = negotiated.enabledFeatures,
                )
            }

            is RegisteredConsumerValidation.Rejected -> {
                failConnection(epoch, validation.state, validation.detail)
            }

            is RegisteredConsumerValidation.Lost -> connectionLost(validation.detail, epoch)
        }
    }

    private fun failConnection(epoch: Long, state: SharedRuntimeConnectionState, detail: String?) {
        if (!isCurrentEpoch(epoch)) return
        binding.unbind()
        transitionForEpoch(epoch, state, detail = detail)
    }

    private fun connectionLost(detail: String, epoch: Long) {
        val invalidated = synchronized(lock) {
            if (epoch != connectionEpoch || current.state == SharedRuntimeConnectionState.CLOSED) return
            registeredEndpoint.also { registeredEndpoint = null }
        }
        binding.unbind()
        transitionForEpoch(epoch, SharedRuntimeConnectionState.CONNECTION_LOST, detail = detail)
        invalidated?.let { endpoint -> invalidations.notify(endpoint.connectionEpoch, detail) }
    }

    private fun isCurrentEpoch(epoch: Long): Boolean = synchronized(lock) {
        epoch == connectionEpoch && current.state != SharedRuntimeConnectionState.CLOSED
    }

    private fun transitionForEpoch(
        epoch: Long,
        state: SharedRuntimeConnectionState,
        negotiatedMinor: Int? = null,
        enabledFeatures: Set<String> = emptySet(),
        detail: String? = null,
    ) {
        val snapshot = synchronized(lock) {
            if (epoch != connectionEpoch || current.state == SharedRuntimeConnectionState.CLOSED) return
            SharedRuntimeConnectionSnapshot(state, negotiatedMinor, enabledFeatures, detail).also { current = it }
        }
        runCatching { observer.onStateChanged(snapshot) }
    }

    private fun transition(
        state: SharedRuntimeConnectionState,
        negotiatedMinor: Int? = null,
        enabledFeatures: Set<String> = emptySet(),
        detail: String? = null,
    ) {
        val snapshot = synchronized(lock) {
            if (current.state == SharedRuntimeConnectionState.CLOSED && state != SharedRuntimeConnectionState.CLOSED) return
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

private sealed interface RegisteredConsumerValidation {
    data class Ready(val token: ClientTokenParcel) : RegisteredConsumerValidation

    data class Rejected(val state: SharedRuntimeConnectionState, val detail: String) : RegisteredConsumerValidation

    data class Lost(val detail: String) : RegisteredConsumerValidation
}

private fun validateRegisteredConsumer(
    service: SharedRuntimeRemoteService,
    negotiated: NegotiatedProtocol,
    result: RegistrationResultParcel,
): RegisteredConsumerValidation {
    result.error?.let { error ->
        return when (error.code) {
            WireErrorCodes.PROTOCOL_INCOMPATIBLE,
            WireErrorCodes.FEATURE_UNAVAILABLE,
            -> RegisteredConsumerValidation.Rejected(SharedRuntimeConnectionState.INCOMPATIBLE, error.safeMessage)

            WireErrorCodes.CLIENT_NOT_REGISTERED ->
                RegisteredConsumerValidation.Rejected(SharedRuntimeConnectionState.PERMISSION_DENIED, error.safeMessage)

            else -> RegisteredConsumerValidation.Lost(error.safeMessage)
        }
    }
    if (!isValidRegistration(result, negotiated)) {
        return RegisteredConsumerValidation.Rejected(
            SharedRuntimeConnectionState.INCOMPATIBLE,
            "Host returned an invalid registration result",
        )
    }
    val token = requireNotNull(result.clientToken)
    if (BinderProtocolV1.FEATURE_CONSUMER_API_V1 !in negotiated.enabledFeatures) {
        return RegisteredConsumerValidation.Ready(token)
    }
    return try {
        // Consumer API v1 requires an authorization probe before CONNECTED. Legacy clients that
        // did not negotiate the feature intentionally skip this newer endpoint.
        service.consumer
        RegisteredConsumerValidation.Ready(token)
    } catch (error: SecurityException) {
        RegisteredConsumerValidation.Rejected(
            SharedRuntimeConnectionState.PERMISSION_DENIED,
            error.message ?: "Host denied Consumer API access",
        )
    } catch (error: RemoteException) {
        RegisteredConsumerValidation.Lost(error.message ?: "Consumer API handshake failed")
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

private fun isValidRegistration(result: RegistrationResultParcel, negotiated: NegotiatedProtocol): Boolean {
    val token = result.clientToken ?: return false
    return isValidClientToken(token) && isValidNegotiationEcho(result, negotiated)
}

private fun isValidClientToken(token: ClientTokenParcel): Boolean =
    token.value.isNotBlank() && token.value.length <= BinderProtocolV1.MAX_IDENTIFIER_CHARACTERS

private fun isValidNegotiationEcho(result: RegistrationResultParcel, negotiated: NegotiatedProtocol): Boolean {
    val features = result.enabledFeatures.toSet()
    val uniqueFeatures = features.size == result.enabledFeatures.size
    val minorMatches = result.negotiatedMinor == negotiated.minor
    val featuresMatch = features == negotiated.enabledFeatures
    return uniqueFeatures && minorMatches && featuresMatch
}

internal fun SharedRuntimeHostConfig.componentName(): ComponentName = ComponentName(packageName, serviceClassName)
