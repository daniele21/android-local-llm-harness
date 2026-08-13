package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ConsumerLocalLlmClient
import io.github.daniele21.localllm.contracts.LocalLlmClient
import io.github.daniele21.localllm.transport.binder.contract.CancelRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.ClientHelloParcel
import io.github.daniele21.localllm.transport.binder.contract.CloseSessionRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerResultParcel
import io.github.daniele21.localllm.transport.binder.contract.GenerationRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.OpenSessionRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.PrepareRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.PrepareResultParcel
import io.github.daniele21.localllm.transport.binder.contract.ProtocolInfoParcel
import io.github.daniele21.localllm.transport.binder.contract.RegistrationResultParcel
import io.github.daniele21.localllm.transport.binder.contract.SessionResultParcel
import io.github.daniele21.localllm.transport.binder.contract.WireErrorCodes
import io.github.daniele21.localllm.transport.binder.contract.WireProtocolException
import io.github.daniele21.localllm.transport.binder.contract.negotiateProtocol
import java.util.concurrent.atomic.AtomicBoolean

class SharedRuntimeHostDelegate(
    private val client: LocalLlmClient,
    val protocolInfo: ProtocolInfoParcel,
    private val consumerClientFactory: ((ApplicationId) -> ConsumerLocalLlmClient)? = null,
    private val ledger: ClientConnectionLedger = ClientConnectionLedger(),
    private val controlExecutor: HostControlExecutor = BoundedSerialHostControlExecutor(),
    private val callbackDispatcherFactory: HostCallbackDispatcherFactory =
        HostCallbackDispatcherFactory { BoundedSerialHostCallbackDispatcher() },
) : AutoCloseable {
    private val resources = HostRuntimeResources()
    private val closed = AtomicBoolean(false)
    private val lifecycleLock = Any()
    internal val runtimeOperations = HostRuntimeOperations(client, ledger, resources, controlExecutor)
    internal val consumerOperations = ConsumerHostOperations(ledger, resources, controlExecutor)

    fun registerClient(
        caller: AuthorizedCaller,
        hello: ClientHelloParcel,
        lifecycle: ClientLifecycleLinker,
        callback: HostResultCallback<RegistrationResultParcel>,
    ) {
        if (closed.get()) {
            callback.onResult(registrationFailure(wireError(WireErrorCodes.CLIENT_DISCONNECTED)))
            return
        }
        val negotiated = try {
            negotiateProtocol(protocolInfo, hello)
        } catch (error: WireProtocolException) {
            callback.onResult(registrationFailure(error.toHostWireError()))
            return
        }
        controlExecutor.submitOrReject(
            onRejected = { callback.onResult(registrationFailure(wireError(WireErrorCodes.TRANSPORT_FAILURE))) },
        ) {
            completeRegistration(caller, lifecycle, callback, negotiated.minor, negotiated.enabledFeatures.sorted())
        }
    }

    fun unregisterClient(caller: AuthorizedCaller, clientToken: String) {
        if (closed.get()) return
        val token = runCatching { HostClientToken(clientToken) }.getOrNull() ?: return
        controlExecutor.submitOrReject(onRejected = {}) { cleanupConnection(token, caller) }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        controlExecutor.closeSafely()
        synchronized(lifecycleLock) {
            ledger.activeConnections.forEach { connection -> cleanupConnection(connection.token, connection.caller) }
            resources.closeAll()
        }
    }

    private fun completeRegistration(
        caller: AuthorizedCaller,
        lifecycle: ClientLifecycleLinker,
        callback: HostResultCallback<RegistrationResultParcel>,
        negotiatedMinor: Int,
        enabledFeatures: List<String>,
    ) = synchronized(lifecycleLock) {
        if (closed.get()) {
            callback.onResult(registrationFailure(wireError(WireErrorCodes.CLIENT_DISCONNECTED)))
            return@synchronized
        }
        when (val registration = ledger.register(caller)) {
            is LedgerResult.Failure -> callback.onResult(registrationFailure(registration.reason.toHostWireError()))
            is LedgerResult.Success -> finishRegistration(
                caller,
                lifecycle,
                callback,
                registration.value,
                negotiatedMinor,
                enabledFeatures,
            )
        }
    }

    private fun finishRegistration(
        caller: AuthorizedCaller,
        lifecycle: ClientLifecycleLinker,
        callback: HostResultCallback<RegistrationResultParcel>,
        token: HostClientToken,
        negotiatedMinor: Int,
        enabledFeatures: List<String>,
    ) {
        val dispatcher = runCatching(callbackDispatcherFactory::create).getOrNull()
        if (dispatcher == null) {
            cleanupConnection(token, caller)
            callback.onResult(registrationFailure(wireError(WireErrorCodes.TRANSPORT_FAILURE)))
            return
        }
        resources.attachCallbackDispatcher(token, dispatcher)
        val consumer = consumerClientFactory?.let { factory -> runCatching { factory(caller.applicationId) }.getOrNull() }
        if (consumerClientFactory != null && consumer == null) {
            cleanupConnection(token, caller)
            callback.onResult(registrationFailure(wireError(WireErrorCodes.TRANSPORT_FAILURE)))
            return
        }
        consumer?.let { resources.attachConsumerClient(token, it) }
        val deathLink = lifecycle.link {
            controlExecutor.submitOrReject(onRejected = {}) { cleanupConnection(token, caller) }
        }
        if (deathLink == null) {
            cleanupConnection(token, caller)
            callback.onResult(registrationFailure(wireError(WireErrorCodes.CLIENT_DISCONNECTED)))
        } else {
            resources.attachDeathLink(token, deathLink)
            callback.onResult(registrationSuccess(token, negotiatedMinor, enabledFeatures))
        }
    }

    private fun cleanupConnection(token: HostClientToken, caller: AuthorizedCaller) {
        val closing = ledger.beginClose(token, caller).successOrNull() ?: return
        closing.requestIds.forEach { requestId -> resources.removeHandle(requestId)?.cancelSafely() }
        val consumer = resources.consumerClient(token)
        closing.sessionIds.forEach { sessionId ->
            if (resources.isConsumerSession(token, sessionId) && consumer != null) {
                runCatching { consumer.closeSession(sessionId) }
                resources.removeConsumerSession(token, sessionId)
            } else {
                runCatching { client.closeSession(sessionId) }
            }
        }
        resources.removeConsumerClient(token)
        resources.removeDeathLink(token)?.unlinkSafely()
        resources.removeCallbackDispatcher(token)?.closeSafely()
        ledger.finishClose(token, caller)
    }
}
