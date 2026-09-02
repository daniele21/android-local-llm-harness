package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ConsumerLocalLlmClient
import io.github.daniele21.localllm.contracts.LocalLlmClient
import io.github.daniele21.localllm.transport.binder.contract.CancelRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.ClientHelloParcel
import io.github.daniele21.localllm.transport.binder.contract.CloseSessionRequestParcel
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
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

private class SharedRuntimeHostInfrastructure(
    val ledger: ClientConnectionLedger,
    val controlExecutor: HostControlExecutor,
    val readinessExecutor: HostControlExecutor,
    val callbackDispatcherFactory: HostCallbackDispatcherFactory,
)

class SharedRuntimeHostDelegate private constructor(
    private val client: LocalLlmClient,
    val protocolInfo: ProtocolInfoParcel,
    private val consumerClientFactory: ((ApplicationId) -> ConsumerLocalLlmClient)?,
    private val consumerControlPlaneHost: ConsumerControlPlaneHost?,
    private val consumerRuntimeReadinessHost: ConsumerRuntimeReadinessHost?,
    infrastructure: SharedRuntimeHostInfrastructure,
    logicalJobMetadataStore: HostLogicalJobMetadataStore,
) : AutoCloseable {
    constructor(
        client: LocalLlmClient,
        protocolInfo: ProtocolInfoParcel,
        consumerClientFactory: ((ApplicationId) -> ConsumerLocalLlmClient)? = null,
        consumerControlPlaneHost: ConsumerControlPlaneHost? = null,
        consumerRuntimeReadinessHost: ConsumerRuntimeReadinessHost? = null,
        ledger: ClientConnectionLedger = ClientConnectionLedger(),
        controlExecutor: HostControlExecutor = BoundedSerialHostControlExecutor(),
        readinessExecutor: HostControlExecutor = BoundedSerialHostControlExecutor(),
        callbackDispatcherFactory: HostCallbackDispatcherFactory =
            HostCallbackDispatcherFactory { BoundedSerialHostCallbackDispatcher() },
    ) : this(
        client = client,
        protocolInfo = protocolInfo,
        consumerClientFactory = consumerClientFactory,
        consumerControlPlaneHost = consumerControlPlaneHost,
        consumerRuntimeReadinessHost = consumerRuntimeReadinessHost,
        infrastructure =
        SharedRuntimeHostInfrastructure(
            ledger = ledger,
            controlExecutor = controlExecutor,
            readinessExecutor = readinessExecutor,
            callbackDispatcherFactory = callbackDispatcherFactory,
        ),
        logicalJobMetadataStore = NoOpHostLogicalJobMetadataStore,
    )

    internal constructor(
        client: LocalLlmClient,
        protocolInfo: ProtocolInfoParcel,
        consumerClientFactory: ((ApplicationId) -> ConsumerLocalLlmClient)?,
        consumerControlPlaneHost: ConsumerControlPlaneHost?,
        consumerRuntimeReadinessHost: ConsumerRuntimeReadinessHost?,
        logicalJobMetadataStore: HostLogicalJobMetadataStore,
    ) : this(
        client = client,
        protocolInfo = protocolInfo,
        consumerClientFactory = consumerClientFactory,
        consumerControlPlaneHost = consumerControlPlaneHost,
        consumerRuntimeReadinessHost = consumerRuntimeReadinessHost,
        infrastructure =
        SharedRuntimeHostInfrastructure(
            ledger = ClientConnectionLedger(),
            controlExecutor = BoundedSerialHostControlExecutor(),
            readinessExecutor = BoundedSerialHostControlExecutor(),
            callbackDispatcherFactory = HostCallbackDispatcherFactory { BoundedSerialHostCallbackDispatcher() },
        ),
        logicalJobMetadataStore = logicalJobMetadataStore,
    )

    private val ledger = infrastructure.ledger
    private val controlExecutor = infrastructure.controlExecutor
    private val readinessExecutor = infrastructure.readinessExecutor
    private val callbackDispatcherFactory = infrastructure.callbackDispatcherFactory
    private val resources = HostRuntimeResources()
    private val consumerResources = ConsumerHostResources()
    private val consumerActivity = ConsumerRuntimeActivityTracker()
    private val logicalJobExecutionDemand = HostLogicalJobExecutionDemand()
    private val logicalJobRegistry = HostLogicalJobRegistry(
        maxJobs = LOGICAL_JOB_CAPACITY,
        runtimeSessionId = HostRuntimeSessionId("runtime:${UUID.randomUUID()}"),
        idFactory = { HostLogicalJobId(UUID.randomUUID().toString()) },
        metadataStore = logicalJobMetadataStore,
    )
    private val logicalJobCoordinator = HostLogicalJobCoordinator(logicalJobRegistry, logicalJobExecutionDemand)
    private val closed = AtomicBoolean(false)
    private val lifecycleLock = Any()

    internal val runtimeOperations = HostRuntimeOperations(client, ledger, resources, controlExecutor)
    internal val consumerOperations =
        ConsumerHostOperations(ledger, resources, consumerResources, controlExecutor)
    internal val logicalJobOperations =
        ConsumerLogicalJobHostOperations(ledger, consumerResources, controlExecutor, logicalJobCoordinator)
    internal val controlPlaneOperations =
        ConsumerControlPlaneHostOperations(ledger, consumerControlPlaneHost, controlExecutor)
    internal val readinessOperations =
        ConsumerRuntimeReadinessHostOperations(ledger, consumerRuntimeReadinessHost, readinessExecutor, consumerActivity)

    fun setLogicalJobExecutionDemandListener(listener: (Boolean) -> Unit) {
        logicalJobExecutionDemand.setListener(listener)
    }

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
        val negotiated =
            try {
                negotiateProtocol(protocolInfo, hello)
            } catch (error: WireProtocolException) {
                callback.onResult(registrationFailure(error.toHostWireError()))
                return
            }
        controlExecutor.submitOrReject(
            onRejected = {
                callback.onResult(registrationFailure(wireError(WireErrorCodes.TRANSPORT_FAILURE)))
            },
        ) {
            completeRegistration(
                caller,
                lifecycle,
                callback,
                negotiated.minor,
                negotiated.enabledFeatures.sorted(),
            )
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
        readinessExecutor.closeSafely()
        synchronized(lifecycleLock) {
            ledger.activeConnections.forEach { connection ->
                cleanupConnection(connection.token, connection.caller)
            }
            logicalJobCoordinator.close()
            resources.closeAll()
            consumerResources.clear()
            consumerActivity.clear()
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
        when (val registration = ledger.register(caller, negotiatedMinor, enabledFeatures.toSet())) {
            is LedgerResult.Failure ->
                callback.onResult(registrationFailure(registration.reason.toHostWireError()))

            is LedgerResult.Success ->
                finishRegistration(
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
        val consumer =
            consumerClientFactory?.let { factory ->
                runCatching { factory(caller.applicationId) }.getOrNull()
            }
        if (consumerClientFactory != null && consumer == null) {
            cleanupConnection(token, caller)
            callback.onResult(registrationFailure(wireError(WireErrorCodes.TRANSPORT_FAILURE)))
            return
        }
        consumer?.let {
            consumerResources.attachClient(
                token,
                RuntimeActivityTrackingConsumerClient(token, it, consumerActivity),
            )
        }
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
        closing.requestIds.forEach { requestId ->
            resources.removeHandle(requestId)?.cancelSafely()
        }
        val consumer = consumerResources.client(token)
        closing.sessionIds.forEach { sessionId ->
            if (consumerResources.ownsSession(token, sessionId) && consumer != null) {
                runCatching { consumer.closeSession(sessionId) }
                consumerResources.removeSession(token, sessionId)
            } else {
                runCatching { client.closeSession(sessionId) }
            }
        }
        runCatching { consumerControlPlaneHost?.releaseAll(token.value, caller.applicationId) }
        consumerResources.removeClient(token)
        consumerActivity.clear(token)
        resources.removeDeathLink(token)?.unlinkSafely()
        resources.removeCallbackDispatcher(token)?.closeSafely()
        ledger.finishClose(token, caller)
    }

    private companion object {
        const val LOGICAL_JOB_CAPACITY = 64
    }
}

internal fun SharedRuntimeHostDelegate.prepare(
    caller: AuthorizedCaller,
    request: PrepareRequestParcel,
    callback: HostResultCallback<PrepareResultParcel>,
) = runtimeOperations.prepare(caller, request, callback)

internal fun SharedRuntimeHostDelegate.openSession(
    caller: AuthorizedCaller,
    request: OpenSessionRequestParcel,
    callback: HostResultCallback<SessionResultParcel>,
) = runtimeOperations.openSession(caller, request, callback)

internal fun SharedRuntimeHostDelegate.generate(caller: AuthorizedCaller, request: GenerationRequestParcel, callback: HostEventCallback) =
    runtimeOperations.generate(caller, request, callback)

internal fun SharedRuntimeHostDelegate.cancel(caller: AuthorizedCaller, request: CancelRequestParcel) =
    runtimeOperations.cancel(caller, request)

internal fun SharedRuntimeHostDelegate.closeSession(caller: AuthorizedCaller, request: CloseSessionRequestParcel) =
    runtimeOperations.closeSession(caller, request)
