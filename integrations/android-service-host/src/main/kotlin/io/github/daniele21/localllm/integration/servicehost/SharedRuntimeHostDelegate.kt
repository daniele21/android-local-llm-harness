package io.github.daniele21.localllm.integration.servicehost

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

class SharedRuntimeHostDelegate(
    private val client: LocalLlmClient,
    val protocolInfo: ProtocolInfoParcel,
    private val ledger: ClientConnectionLedger = ClientConnectionLedger(),
    private val controlExecutor: HostControlExecutor = BoundedSerialHostControlExecutor(),
    private val callbackDispatcherFactory: HostCallbackDispatcherFactory =
        HostCallbackDispatcherFactory { BoundedSerialHostCallbackDispatcher() },
) {
    private val resources = HostRuntimeResources()
    private val runtimeOperations =
        HostRuntimeOperations(
            client = client,
            ledger = ledger,
            resources = resources,
            controlExecutor = controlExecutor,
        )

    fun registerClient(
        caller: AuthorizedCaller,
        hello: ClientHelloParcel,
        lifecycle: ClientLifecycleLinker,
        callback: HostResultCallback<RegistrationResultParcel>,
    ) {
        val negotiated =
            try {
                negotiateProtocol(protocolInfo, hello)
            } catch (error: WireProtocolException) {
                callback.onResult(registrationFailure(error.toHostWireError()))
                return
            }
        controlExecutor.submitOrReject(
            onRejected = {
                callback.onResult(
                    registrationFailure(wireError(WireErrorCodes.TRANSPORT_FAILURE)),
                )
            },
        ) {
            completeRegistration(caller, lifecycle, callback, negotiated.minor, negotiated.enabledFeatures.sorted())
        }
    }

    fun prepare(caller: AuthorizedCaller, request: PrepareRequestParcel, callback: HostResultCallback<PrepareResultParcel>) =
        runtimeOperations.prepare(caller, request, callback)

    fun openSession(caller: AuthorizedCaller, request: OpenSessionRequestParcel, callback: HostResultCallback<SessionResultParcel>) =
        runtimeOperations.openSession(caller, request, callback)

    fun generate(caller: AuthorizedCaller, request: GenerationRequestParcel, callback: HostEventCallback) =
        runtimeOperations.generate(caller, request, callback)

    fun cancel(caller: AuthorizedCaller, request: CancelRequestParcel) = runtimeOperations.cancel(caller, request)

    fun closeSession(caller: AuthorizedCaller, request: CloseSessionRequestParcel) = runtimeOperations.closeSession(caller, request)

    fun unregisterClient(caller: AuthorizedCaller, clientToken: String) {
        val token = runCatching { HostClientToken(clientToken) }.getOrNull() ?: return
        controlExecutor.submitOrReject(onRejected = {}) { cleanupConnection(token, caller) }
    }

    private fun completeRegistration(
        caller: AuthorizedCaller,
        lifecycle: ClientLifecycleLinker,
        callback: HostResultCallback<RegistrationResultParcel>,
        negotiatedMinor: Int,
        enabledFeatures: List<String>,
    ) {
        when (val registration = ledger.register(caller)) {
            is LedgerResult.Failure -> callback.onResult(registrationFailure(registration.reason.toHostWireError()))

            is LedgerResult.Success -> {
                val token = registration.value
                val dispatcher = runCatching(callbackDispatcherFactory::create).getOrNull()
                if (dispatcher == null) {
                    cleanupConnection(token, caller)
                    callback.onResult(registrationFailure(wireError(WireErrorCodes.TRANSPORT_FAILURE)))
                    return
                }
                resources.attachCallbackDispatcher(token, dispatcher)
                val deathLink = lifecycle.link { submitDeathCleanup(token, caller) }
                if (deathLink == null) {
                    cleanupConnection(token, caller)
                    callback.onResult(
                        registrationFailure(
                            wireError(WireErrorCodes.CLIENT_DISCONNECTED),
                        ),
                    )
                } else {
                    resources.attachDeathLink(token, deathLink)
                    callback.onResult(registrationSuccess(token, negotiatedMinor, enabledFeatures))
                }
            }
        }
    }

    private fun submitDeathCleanup(token: HostClientToken, caller: AuthorizedCaller) {
        controlExecutor.submitOrReject(onRejected = {}) { cleanupConnection(token, caller) }
    }

    private fun cleanupConnection(token: HostClientToken, caller: AuthorizedCaller) {
        val closing = ledger.beginClose(token, caller).successOrNull() ?: return
        closing.requestIds.forEach { requestId -> resources.removeHandle(requestId)?.cancelSafely() }
        closing.sessionIds.forEach { sessionId -> runCatching { client.closeSession(sessionId) } }
        resources.removeDeathLink(token)?.unlinkSafely()
        resources.removeCallbackDispatcher(token)?.closeSafely()
        ledger.finishClose(token, caller)
    }
}
