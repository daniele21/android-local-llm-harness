package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.contracts.LocalLlmClient
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.transport.binder.contract.CancelRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.CloseSessionRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.GenerationRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.OpenSessionRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.PrepareRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.PrepareResultParcel
import io.github.daniele21.localllm.transport.binder.contract.SessionResultParcel
import io.github.daniele21.localllm.transport.binder.contract.WireErrorCodes
import io.github.daniele21.localllm.transport.binder.contract.WireProtocolException
import io.github.daniele21.localllm.transport.binder.contract.toCore

internal class HostRuntimeOperations(
    private val client: LocalLlmClient,
    private val ledger: ClientConnectionLedger,
    private val resources: HostRuntimeResources,
    private val controlExecutor: HostControlExecutor,
) {
    fun prepare(
        caller: AuthorizedCaller,
        request: PrepareRequestParcel,
        callback: HostResultCallback<PrepareResultParcel>,
    ) {
        val validationError = validatePrepare(caller, request)
        if (validationError != null) {
            callback.onResult(prepareFailure(request.operationId, validationError))
            return
        }
        controlExecutor.submitOrReject(
            onRejected = { callback.onResult(prepareFailure(request.operationId, wireError(WireErrorCodes.TRANSPORT_FAILURE))) },
        ) {
            runPrepare(caller, request, callback)
        }
    }

    fun openSession(
        caller: AuthorizedCaller,
        request: OpenSessionRequestParcel,
        callback: HostResultCallback<SessionResultParcel>,
    ) {
        val validationError = validateOpenSession(caller, request)
        if (validationError != null) {
            callback.onResult(sessionFailure(request.operationId, validationError))
            return
        }
        controlExecutor.submitOrReject(
            onRejected = { callback.onResult(sessionFailure(request.operationId, wireError(WireErrorCodes.TRANSPORT_FAILURE))) },
        ) {
            runOpenSession(caller, request, callback)
        }
    }

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

    fun cancel(caller: AuthorizedCaller, request: CancelRequestParcel) {
        if (validateCancel(request) != null) return
        controlExecutor.submitOrReject(onRejected = {}) {
            val token = HostClientToken(request.clientToken.value)
            val requestId = ledger.requestId(token, caller, request.externalRequestId).successOrNull() ?: return@submitOrReject
            resources.handle(requestId)?.cancelSafely()
        }
    }

    fun closeSession(caller: AuthorizedCaller, request: CloseSessionRequestParcel) {
        if (validateClose(request) != null) return
        controlExecutor.submitOrReject(onRejected = {}) {
            val token = HostClientToken(request.clientToken.value)
            val sessionId = ledger.sessionId(token, caller, request.externalSessionId).successOrNull() ?: return@submitOrReject
            try {
                client.closeSession(sessionId)
                ledger.removeSession(token, caller, request.externalSessionId)
            } catch (_: RuntimeException) {
                // Preserve ownership so explicit close or death cleanup can retry.
            }
        }
    }

    private fun runPrepare(
        caller: AuthorizedCaller,
        request: PrepareRequestParcel,
        callback: HostResultCallback<PrepareResultParcel>,
    ) {
        val token = HostClientToken(request.clientToken.value)
        val connectionError = ledger.validateConnection(token, caller).failureOrNull()?.toHostWireError()
        if (connectionError != null) {
            callback.onResult(prepareFailure(request.operationId, connectionError))
            return
        }
        val result =
            try {
                client.prepare(caller.applicationId, UseCaseId(request.useCaseId))
            } catch (_: RuntimeException) {
                callback.onResult(prepareFailure(request.operationId, wireError(WireErrorCodes.RUNTIME_FAILURE)))
                return
            }
        callback.onResult(prepareResult(request.operationId, result))
    }

    private fun runOpenSession(
        caller: AuthorizedCaller,
        request: OpenSessionRequestParcel,
        callback: HostResultCallback<SessionResultParcel>,
    ) {
        val token = HostClientToken(request.clientToken.value)
        val connectionError = ledger.validateConnection(token, caller).failureOrNull()?.toHostWireError()
        if (connectionError != null) {
            callback.onResult(sessionFailure(request.operationId, connectionError))
            return
        }
        val sessionId =
            try {
                client.createSession(caller.applicationId, UseCaseId(request.useCaseId), request.options.toCore())
            } catch (_: RuntimeException) {
                callback.onResult(sessionFailure(request.operationId, wireError(WireErrorCodes.SESSION_UNAVAILABLE)))
                return
            }
        when (val registered = ledger.registerSession(token, caller, request.externalSessionId, sessionId)) {
            is LedgerResult.Success -> callback.onResult(sessionSuccess(request.operationId, request.externalSessionId))
            is LedgerResult.Failure -> {
                runCatching { client.closeSession(sessionId) }
                callback.onResult(sessionFailure(request.operationId, registered.reason.toHostWireError()))
            }
        }
    }

    private fun runGeneration(
        caller: AuthorizedCaller,
        request: GenerationRequestParcel,
        callback: HostEventCallback,
    ) {
        val token = HostClientToken(request.clientToken.value)
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
        val forwarder = generationForwarder(token, caller, request.externalRequestId, requestId, callback)
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
    ): GenerationEventForwarder =
        GenerationEventForwarder(
            externalRequestId = externalRequestId,
            callback = callback,
            onTerminal = { submitRequestCleanup(token, caller, externalRequestId, requestId) },
            onCallbackFailure = { submitCallbackFailureCleanup(token, caller, externalRequestId, requestId) },
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

    private fun submitRequestCleanup(
        token: HostClientToken,
        caller: AuthorizedCaller,
        externalRequestId: String,
        requestId: RequestId,
    ) {
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
        if (ledger.requestId(token, caller, externalRequestId).successOrNull() != null) return
        val handle = resources.removeHandle(requestId) ?: return
        if (forwarder.callbackFailed) handle.cancelSafely()
    }
}
