package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.contracts.GenerationEvent
import io.github.daniele21.localllm.contracts.GenerationHandle
import io.github.daniele21.localllm.contracts.LocalLlmClient
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.transport.binder.contract.BinderProtocolV1
import io.github.daniele21.localllm.transport.binder.contract.CancelRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.ClientHelloParcel
import io.github.daniele21.localllm.transport.binder.contract.CloseSessionRequestParcel
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
import io.github.daniele21.localllm.transport.binder.contract.WireProtocolException
import io.github.daniele21.localllm.transport.binder.contract.negotiateProtocol
import io.github.daniele21.localllm.transport.binder.contract.toCore
import io.github.daniele21.localllm.transport.binder.contract.validateCancelRequest
import io.github.daniele21.localllm.transport.binder.contract.validateCloseSessionRequest
import io.github.daniele21.localllm.transport.binder.contract.validateGenerationRequest
import io.github.daniele21.localllm.transport.binder.contract.validateOpenSessionRequest
import io.github.daniele21.localllm.transport.binder.contract.validatePrepareRequest
import java.util.concurrent.ConcurrentHashMap

class SharedRuntimeHostDelegate(
    private val client: LocalLlmClient,
    val protocolInfo: ProtocolInfoParcel,
    private val ledger: ClientConnectionLedger = ClientConnectionLedger(),
    private val controlExecutor: HostControlExecutor = BoundedSerialHostControlExecutor(),
) {
    private val resources = HostRuntimeResources()

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
                callback.onResult(registrationFailure(error.toSafeWire()))
                return
            }
        submitOrReject(
            onRejected = { callback.onResult(registrationFailure(transportFailure())) },
        ) {
            when (val registration = ledger.register(caller)) {
                is LedgerResult.Failure -> callback.onResult(registrationFailure(registration.reason.toWireError()))
                is LedgerResult.Success -> {
                    val token = registration.value
                    val deathLink = lifecycle.link { submitDeathCleanup(token, caller) }
                    if (deathLink == null) {
                        cleanupConnection(token, caller)
                        callback.onResult(registrationFailure(disconnectedFailure()))
                    } else {
                        resources.attachDeathLink(token, deathLink)
                        callback.onResult(
                            RegistrationResultParcel(
                                clientToken = io.github.daniele21.localllm.transport.binder.contract.ClientTokenParcel(token.value),
                                negotiatedMinor = negotiated.minor,
                                enabledFeatures = negotiated.enabledFeatures.sorted(),
                                error = null,
                            ),
                        )
                    }
                }
            }
        }
    }

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
        submitOrReject(
            onRejected = { callback.onResult(prepareFailure(request.operationId, transportFailure())) },
        ) {
            val token = HostClientToken(request.clientToken.value)
            when (val connection = ledger.validateConnection(token, caller)) {
                is LedgerResult.Failure -> callback.onResult(prepareFailure(request.operationId, connection.reason.toWireError()))
                is LedgerResult.Success -> runPrepare(caller, request, callback)
            }
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
        submitOrReject(
            onRejected = { callback.onResult(sessionFailure(request.operationId, transportFailure())) },
        ) {
            runOpenSession(caller, request, callback)
        }
    }

    fun generate(
        caller: AuthorizedCaller,
        request: GenerationRequestParcel,
        callback: HostEventCallback,
    ) {
        val validationError = validateGeneration(caller, request)
        if (validationError != null) {
            callback.onEvent(generationFailure(request.externalRequestId, validationError))
            return
        }
        submitOrReject(
            onRejected = { callback.onEvent(generationFailure(request.externalRequestId, transportFailure())) },
        ) {
            runGeneration(caller, request, callback)
        }
    }

    fun cancel(caller: AuthorizedCaller, request: CancelRequestParcel) {
        if (validateCancel(request) != null) return
        submitOrReject(onRejected = {}) {
            val token = HostClientToken(request.clientToken.value)
            val requestId = ledger.requestId(token, caller, request.externalRequestId).successOrNull() ?: return@submitOrReject
            resources.handle(requestId)?.cancelSafely()
        }
    }

    fun closeSession(caller: AuthorizedCaller, request: CloseSessionRequestParcel) {
        if (validateClose(request) != null) return
        submitOrReject(onRejected = {}) {
            val token = HostClientToken(request.clientToken.value)
            val sessionId = ledger.sessionId(token, caller, request.externalSessionId).successOrNull() ?: return@submitOrReject
            try {
                client.closeSession(sessionId)
                ledger.removeSession(token, caller, request.externalSessionId)
            } catch (_: RuntimeException) {
                // Keep ownership so the caller or death cleanup can retry closing the session.
            }
        }
    }

    fun unregisterClient(caller: AuthorizedCaller, clientToken: String) {
        val token = runCatching { HostClientToken(clientToken) }.getOrNull() ?: return
        submitOrReject(onRejected = {}) { cleanupConnection(token, caller) }
    }

    private fun runPrepare(
        caller: AuthorizedCaller,
        request: PrepareRequestParcel,
        callback: HostResultCallback<PrepareResultParcel>,
    ) {
        val result =
            try {
                client.prepare(caller.applicationId, UseCaseId(request.useCaseId))
            } catch (_: RuntimeException) {
                callback.onResult(prepareFailure(request.operationId, runtimeFailure()))
                return
            }
        callback.onResult(
            PrepareResultParcel(
                operationId = request.operationId,
                ready = result.ready,
                modelDigestSha256 = result.modelDigest?.sha256,
                detail = if (result.ready) "Model ready" else "Preparation failed",
                error = if (result.ready) null else preparationFailure(),
            ),
        )
    }

    private fun runOpenSession(
        caller: AuthorizedCaller,
        request: OpenSessionRequestParcel,
        callback: HostResultCallback<SessionResultParcel>,
    ) {
        val token = HostClientToken(request.clientToken.value)
        val connectionError = ledger.validateConnection(token, caller).failureOrNull()?.toWireError()
        if (connectionError != null) {
            callback.onResult(sessionFailure(request.operationId, connectionError))
            return
        }
        val sessionId =
            try {
                client.createSession(caller.applicationId, UseCaseId(request.useCaseId), request.options.toCore())
            } catch (_: RuntimeException) {
                callback.onResult(sessionFailure(request.operationId, sessionUnavailable()))
                return
            }
        when (val registered = ledger.registerSession(token, caller, request.externalSessionId, sessionId)) {
            is LedgerResult.Success -> callback.onResult(SessionResultParcel(request.operationId, request.externalSessionId, null))
            is LedgerResult.Failure -> {
                runCatching { client.closeSession(sessionId) }
                callback.onResult(sessionFailure(request.operationId, registered.reason.toWireError()))
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
            callback.onEvent(generationFailure(request.externalRequestId, sessionUnavailable()))
            return
        }
        val requestId = ledger.allocateRequest(token, caller, request.externalRequestId).successOrNull()
        if (requestId == null) {
            callback.onEvent(generationFailure(request.externalRequestId, requestRejected()))
            return
        }
        val coreRequest =
            try {
                request.toCore(caller.applicationId, sessionId, requestId)
            } catch (error: WireProtocolException) {
                ledger.removeRequest(token, caller, request.externalRequestId)
                callback.onEvent(generationFailure(request.externalRequestId, error.toSafeWire()))
                return
            }
        val forwarder = generationForwarder(token, caller, request.externalRequestId, requestId, callback)
        try {
            val handle = client.generate(coreRequest, forwarder::onEvent)
            resources.attachHandle(requestId, handle)
        } catch (_: RuntimeException) {
            ledger.removeRequest(token, caller, request.externalRequestId)
            callback.onEvent(generationFailure(request.externalRequestId, runtimeFailure()))
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
            onCallbackFailure = {
                resources.handle(requestId)?.cancelSafely()
                submitRequestCleanup(token, caller, externalRequestId, requestId)
            },
        )

    private fun submitRequestCleanup(
        token: HostClientToken,
        caller: AuthorizedCaller,
        externalRequestId: String,
        requestId: RequestId,
    ) {
        submitOrReject(onRejected = { resources.removeHandle(requestId) }) {
            ledger.removeRequest(token, caller, externalRequestId)
            resources.removeHandle(requestId)
        }
    }

    private fun submitDeathCleanup(token: HostClientToken, caller: AuthorizedCaller) {
        submitOrReject(onRejected = {}) { cleanupConnection(token, caller) }
    }

    private fun cleanupConnection(token: HostClientToken, caller: AuthorizedCaller) {
        val closing = ledger.beginClose(token, caller).successOrNull() ?: return
        closing.requestIds.forEach { requestId -> resources.removeHandle(requestId)?.cancelSafely() }
        closing.sessionIds.forEach { sessionId -> runCatching { client.closeSession(sessionId) } }
        resources.removeDeathLink(token)?.unlinkSafely()
        ledger.finishClose(token, caller)
    }

    private fun submitOrReject(onRejected: () -> Unit, task: () -> Unit) {
        if (!controlExecutor.execute(task)) onRejected()
    }
}

private class HostRuntimeResources {
    private val handles = ConcurrentHashMap<RequestId, GenerationHandle>()
    private val deathLinks = ConcurrentHashMap<HostClientToken, ClientDeathLink>()

    fun attachHandle(requestId: RequestId, handle: GenerationHandle) {
        handles[requestId] = handle
    }

    fun handle(requestId: RequestId): GenerationHandle? = handles[requestId]

    fun removeHandle(requestId: RequestId): GenerationHandle? = handles.remove(requestId)

    fun attachDeathLink(token: HostClientToken, link: ClientDeathLink) {
        deathLinks[token] = link
    }

    fun removeDeathLink(token: HostClientToken): ClientDeathLink? = deathLinks.remove(token)
}

private fun validatePrepare(caller: AuthorizedCaller, request: PrepareRequestParcel): WireErrorParcel? =
    validateWireAndUseCase(caller, request.useCaseId) { validatePrepareRequest(request) }

private fun validateOpenSession(caller: AuthorizedCaller, request: OpenSessionRequestParcel): WireErrorParcel? =
    validateWireAndUseCase(caller, request.useCaseId) { validateOpenSessionRequest(request) }

private fun validateGeneration(caller: AuthorizedCaller, request: GenerationRequestParcel): WireErrorParcel? =
    validateWireAndUseCase(caller, request.useCaseId) { validateGenerationRequest(request) }

private fun validateCancel(request: CancelRequestParcel): WireErrorParcel? = validateWire { validateCancelRequest(request) }

private fun validateClose(request: CloseSessionRequestParcel): WireErrorParcel? = validateWire { validateCloseSessionRequest(request) }

private fun validateWireAndUseCase(caller: AuthorizedCaller, useCase: String, validation: () -> Unit): WireErrorParcel? {
    val wireError = validateWire(validation)
    if (wireError != null) return wireError
    return if (caller.allows(UseCaseId(useCase))) null else unauthorizedUseCase()
}

private fun validateWire(validation: () -> Unit): WireErrorParcel? =
    try {
        validation()
        null
    } catch (error: WireProtocolException) {
        error.toSafeWire()
    }

private fun registrationFailure(error: WireErrorParcel) = RegistrationResultParcel(null, null, emptyList(), error)

private fun prepareFailure(operationId: String, error: WireErrorParcel) =
    PrepareResultParcel(operationId, false, null, "Preparation failed", error)

private fun sessionFailure(operationId: String, error: WireErrorParcel) = SessionResultParcel(operationId, null, error)

private fun generationFailure(externalRequestId: String, error: WireErrorParcel) =
    GenerationEventParcel(
        externalRequestId = externalRequestId,
        sequence = 0,
        eventTag = io.github.daniele21.localllm.transport.binder.contract.WireTags.EVENT_FAILED,
        error = error,
    )

private fun LedgerFailure.toWireError(): WireErrorParcel =
    when (this) {
        LedgerFailure.CLIENT_TOKEN_INVALID -> clientTokenFailure()
        LedgerFailure.CLIENT_CLOSING -> disconnectedFailure()
        LedgerFailure.SESSION_LIMIT, LedgerFailure.REQUEST_LIMIT, LedgerFailure.CONNECTION_LIMIT -> backpressureFailure()
        LedgerFailure.DUPLICATE_EXTERNAL_SESSION_ID, LedgerFailure.DUPLICATE_EXTERNAL_REQUEST_ID -> invalidRequestFailure()
        LedgerFailure.SESSION_NOT_OWNED -> sessionUnavailable()
        LedgerFailure.REQUEST_NOT_OWNED -> invalidRequestFailure()
        LedgerFailure.TOKEN_GENERATION_FAILED -> transportFailure()
    }

private fun WireProtocolException.toSafeWire() = fixedWireError(wireCode)

private fun fixedWireError(code: String): WireErrorParcel =
    when (code) {
        WireErrorCodes.PROTOCOL_INCOMPATIBLE -> WireErrorParcel(code, "Protocol incompatible", false)
        WireErrorCodes.FEATURE_UNAVAILABLE -> WireErrorParcel(code, "Required feature unavailable", false)
        WireErrorCodes.PAYLOAD_TOO_LARGE -> WireErrorParcel(code, "Request payload too large", false)
        else -> WireErrorParcel(WireErrorCodes.INVALID_WIRE_REQUEST, "Invalid request", false)
    }

private fun unauthorizedUseCase() = WireErrorParcel(WireErrorCodes.UNAUTHORIZED_USE_CASE, "Use case is not authorized", false)

private fun clientTokenFailure() = WireErrorParcel(WireErrorCodes.CLIENT_TOKEN_INVALID, "Client token is invalid", false)

private fun disconnectedFailure() = WireErrorParcel(WireErrorCodes.CLIENT_DISCONNECTED, "Client is disconnected", true)

private fun backpressureFailure() = WireErrorParcel(WireErrorCodes.CLIENT_BACKPRESSURE, "Host capacity is exhausted", true)

private fun invalidRequestFailure() = WireErrorParcel(WireErrorCodes.INVALID_WIRE_REQUEST, "Invalid request", false)

private fun sessionUnavailable() = WireErrorParcel(WireErrorCodes.SESSION_UNAVAILABLE, "Session is unavailable", false)

private fun preparationFailure() = WireErrorParcel(WireErrorCodes.PREPARATION_FAILED, "Preparation failed", true)

private fun requestRejected() = WireErrorParcel(WireErrorCodes.CLIENT_BACKPRESSURE, "Request cannot be accepted", true)

private fun runtimeFailure() = WireErrorParcel(WireErrorCodes.RUNTIME_FAILURE, "Runtime operation failed", true)

private fun transportFailure() = WireErrorParcel(WireErrorCodes.TRANSPORT_FAILURE, "Host transport unavailable", true)

private fun GenerationHandle.cancelSafely() {
    runCatching(::cancel)
}

private fun ClientDeathLink.unlinkSafely() {
    runCatching(::unlink)
}

private fun <T> LedgerResult<T>.successOrNull(): T? = (this as? LedgerResult.Success)?.value

private fun LedgerResult<*>.failureOrNull(): LedgerFailure? = (this as? LedgerResult.Failure)?.reason
