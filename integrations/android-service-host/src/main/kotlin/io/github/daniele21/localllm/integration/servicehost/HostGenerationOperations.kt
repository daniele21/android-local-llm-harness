package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.contracts.LocalLlmClient
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.transport.binder.contract.GenerationRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.WireErrorCodes
import io.github.daniele21.localllm.transport.binder.contract.WireProtocolException
import io.github.daniele21.localllm.transport.binder.contract.toCore

internal class HostGenerationOperations(
    private val client: LocalLlmClient,
    private val ledger: ClientConnectionLedger,
    private val resources: HostRuntimeResources,
    private val controlExecutor: HostControlExecutor,
) {
    fun generate(caller: AuthorizedCaller, request: GenerationRequestParcel, callback: HostEventCallback) {
        val validationError = validateGeneration(caller, request)
        if (validationError != null) {
            callback.onEvent(generationFailure(request.externalRequestId, validationError))
            return
        }
        controlExecutor.submitOrReject(
            onRejected = { callback.onEvent(generationFailure(request.externalRequestId, wireError(WireErrorCodes.TRANSPORT_FAILURE))) },
        ) {
            runGeneration(caller, request, callback)
        }
    }

    private fun runGeneration(caller: AuthorizedCaller, request: GenerationRequestParcel, callback: HostEventCallback) {
        val token = HostClientToken(request.clientToken.value)
        val dispatcher = resources.callbackDispatcher(token)
        if (dispatcher == null) {
            callback.onEvent(generationFailure(request.externalRequestId, wireError(WireErrorCodes.CLIENT_DISCONNECTED)))
            return
        }
        val sessionId = ledger.sessionId(token, caller, request.externalSessionId).successOrNull()
        if (sessionId == null) {
            callback.onEvent(generationFailure(request.externalRequestId, wireError(WireErrorCodes.SESSION_UNAVAILABLE)))
            return
        }
        val requestId = ledger.allocateRequest(token, caller, request.externalRequestId).successOrNull()
        if (requestId == null) {
            callback.onEvent(generationFailure(request.externalRequestId, wireError(WireErrorCodes.CLIENT_BACKPRESSURE)))
            return
        }
        val coreRequest =
            try {
                request.toCore(caller.applicationId, sessionId, requestId)
            } catch (error: WireProtocolException) {
                ledger.removeRequest(token, caller, request.externalRequestId)
                callback.onEvent(generationFailure(request.externalRequestId, error.toHostWireError()))
                return
            }
        val forwarder = generationForwarder(token, caller, request.externalRequestId, requestId, callback, dispatcher)
        try {
            val handle = client.generate(coreRequest, forwarder::onEvent)
            resources.attachHandle(requestId, handle)
            reconcileSynchronousTerminal(token, caller, request.externalRequestId, requestId, forwarder)
        } catch (_: RuntimeException) {
            ledger.removeRequest(token, caller, request.externalRequestId)
            callback.onEvent(generationFailure(request.externalRequestId, wireError(WireErrorCodes.RUNTIME_FAILURE)))
        }
    }

    private fun generationForwarder(
        token: HostClientToken,
        caller: AuthorizedCaller,
        externalRequestId: String,
        requestId: RequestId,
        callback: HostEventCallback,
        dispatcher: HostCallbackDispatcher,
    ): GenerationEventForwarder = GenerationEventForwarder(
        externalRequestId = externalRequestId,
        callback = callback,
        dispatcher = dispatcher,
        onTerminal = { submitRequestCleanup(token, caller, externalRequestId, requestId) },
        onCallbackFailure = { submitCallbackFailureCleanup(token, caller, externalRequestId, requestId) },
        onBackpressure = { resources.handle(requestId)?.cancelSafely() },
    )

    private fun submitCallbackFailureCleanup(
        token: HostClientToken,
        caller: AuthorizedCaller,
        externalRequestId: String,
        requestId: RequestId,
    ) {
        resources.handle(requestId)?.cancelSafely()
        submitRequestCleanup(token, caller, externalRequestId, requestId)
    }

    private fun submitRequestCleanup(token: HostClientToken, caller: AuthorizedCaller, externalRequestId: String, requestId: RequestId) {
        controlExecutor.submitOrReject(onRejected = { resources.removeHandle(requestId) }) {
            ledger.removeRequest(token, caller, externalRequestId)
            resources.removeHandle(requestId)
        }
    }

    private fun reconcileSynchronousTerminal(
        token: HostClientToken,
        caller: AuthorizedCaller,
        externalRequestId: String,
        requestId: RequestId,
        forwarder: GenerationEventForwarder,
    ) {
        if (forwarder.callbackFailed) {
            resources.removeHandle(requestId)?.cancelSafely()
            return
        }
        if (ledger.requestId(token, caller, externalRequestId).successOrNull() != null) return
        resources.removeHandle(requestId)
    }
}
