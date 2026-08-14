package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.contracts.ConsumerGenerationEvent
import io.github.daniele21.localllm.contracts.ConsumerGenerationListener
import io.github.daniele21.localllm.contracts.ConsumerGenerationRequest
import io.github.daniele21.localllm.contracts.ConsumerGenerationStartResult
import io.github.daniele21.localllm.contracts.ConsumerPrepareRequest
import io.github.daniele21.localllm.contracts.ConsumerPreparedId
import io.github.daniele21.localllm.contracts.ConsumerSessionResult
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.transport.binder.contract.CancelRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.CloseSessionRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerGenerationEventParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerResultParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerWireTags
import io.github.daniele21.localllm.transport.binder.contract.WireErrorCodes
import io.github.daniele21.localllm.transport.binder.contract.toConsumerWire
import io.github.daniele21.localllm.transport.binder.contract.toCoreConsumerInput
import io.github.daniele21.localllm.transport.binder.contract.toCoreConsumerOutput
import io.github.daniele21.localllm.transport.binder.contract.toCoreSelection
import java.util.concurrent.atomic.AtomicBoolean

internal class ConsumerHostOperations(
    private val ledger: ClientConnectionLedger,
    private val resources: HostRuntimeResources,
    private val consumerResources: ConsumerHostResources,
    private val controlExecutor: HostControlExecutor,
) {
    fun capabilities(caller: AuthorizedCaller, request: ConsumerRequestParcel, callback: HostResultCallback<ConsumerResultParcel>) {
        controlExecutor.submitOrReject(
            onRejected = { callback.onResult(failureResult(request.operationId, WireErrorCodes.TRANSPORT_FAILURE)) },
        ) {
            val context = resolveContext(caller, request)
            if (context == null) {
                callback.onResult(failureResult(request.operationId, WireErrorCodes.CLIENT_TOKEN_INVALID))
                return@submitOrReject
            }
            val useCaseId = request.useCaseId.toAuthorizedUseCase(caller)
            if (useCaseId == null) {
                callback.onResult(failureResult(request.operationId, WireErrorCodes.UNAUTHORIZED_USE_CASE))
                return@submitOrReject
            }
            val result = runCatching { context.client.capabilities(useCaseId) }.getOrNull()
            callback.onResult(
                result?.toConsumerWire(request.operationId)
                    ?: failureResult(request.operationId, WireErrorCodes.RUNTIME_FAILURE),
            )
        }
    }

    fun prepare(caller: AuthorizedCaller, request: ConsumerRequestParcel, callback: HostResultCallback<ConsumerResultParcel>) {
        controlExecutor.submitOrReject(
            onRejected = { callback.onResult(failureResult(request.operationId, WireErrorCodes.TRANSPORT_FAILURE)) },
        ) {
            val context = resolveContext(caller, request)
            if (context == null) {
                callback.onResult(failureResult(request.operationId, WireErrorCodes.CLIENT_TOKEN_INVALID))
                return@submitOrReject
            }
            val useCaseId = request.useCaseId.toAuthorizedUseCase(caller)
            val selection = runCatching { requireNotNull(request.selection).toCoreSelection() }.getOrNull()
            if (useCaseId == null || selection == null) {
                val code = if (useCaseId == null) WireErrorCodes.UNAUTHORIZED_USE_CASE else WireErrorCodes.INVALID_WIRE_REQUEST
                callback.onResult(failureResult(request.operationId, code))
                return@submitOrReject
            }
            val result =
                runCatching {
                    context.client.prepare(ConsumerPrepareRequest(useCaseId, selection))
                }.getOrNull()
            callback.onResult(
                result?.toConsumerWire(request.operationId)
                    ?: failureResult(request.operationId, WireErrorCodes.RUNTIME_FAILURE),
            )
        }
    }

    fun openSession(caller: AuthorizedCaller, request: ConsumerRequestParcel, callback: HostResultCallback<ConsumerResultParcel>) {
        controlExecutor.submitOrReject(
            onRejected = { callback.onResult(failureResult(request.operationId, WireErrorCodes.TRANSPORT_FAILURE)) },
        ) {
            val context = resolveContext(caller, request)
            val preparedId = request.preparedId?.takeIf(String::isNotBlank)
            val externalSessionId = request.externalSessionId?.takeIf(String::isNotBlank)
            if (context == null || preparedId == null || externalSessionId == null) {
                callback.onResult(failureResult(request.operationId, WireErrorCodes.INVALID_WIRE_REQUEST))
                return@submitOrReject
            }
            val result =
                runCatching { context.client.createSession(ConsumerPreparedId(preparedId)) }.getOrNull()
                    ?: run {
                        callback.onResult(failureResult(request.operationId, WireErrorCodes.RUNTIME_FAILURE))
                        return@submitOrReject
                    }
            if (result is ConsumerSessionResult.Created) {
                when (
                    val registered =
                        ledger.registerSession(
                            context.token,
                            caller,
                            externalSessionId,
                            result.sessionId,
                        )
                ) {
                    is LedgerResult.Success -> consumerResources.markSession(context.token, result.sessionId)

                    is LedgerResult.Failure -> {
                        runCatching { context.client.closeSession(result.sessionId) }
                        callback.onResult(failureResult(request.operationId, registered.reason.toWireCode()))
                        return@submitOrReject
                    }
                }
            }
            callback.onResult(result.toConsumerWire(request.operationId, externalSessionId))
        }
    }

    fun generate(caller: AuthorizedCaller, request: ConsumerRequestParcel, callback: ConsumerHostEventCallback) {
        controlExecutor.submitOrReject(
            onRejected = { callback.onEvent(failureEvent(request.externalRequestId, WireErrorCodes.TRANSPORT_FAILURE)) },
        ) {
            runGeneration(caller, request, callback)
        }
    }

    fun cancel(caller: AuthorizedCaller, request: CancelRequestParcel) {
        controlExecutor.submitOrReject(onRejected = {}) {
            val token = request.clientToken.toHostTokenOrNull() ?: return@submitOrReject
            val requestId =
                ledger.requestId(token, caller, request.externalRequestId).successOrNull()
                    ?: return@submitOrReject
            resources.handle(requestId)?.cancelSafely()
        }
    }

    fun closeSession(caller: AuthorizedCaller, request: CloseSessionRequestParcel) {
        controlExecutor.submitOrReject(onRejected = {}) {
            val token = request.clientToken.toHostTokenOrNull() ?: return@submitOrReject
            val sessionId =
                ledger.sessionId(token, caller, request.externalSessionId).successOrNull()
                    ?: return@submitOrReject
            if (!consumerResources.ownsSession(token, sessionId)) return@submitOrReject
            val client = consumerResources.client(token) ?: return@submitOrReject
            try {
                client.closeSession(sessionId)
                consumerResources.removeSession(token, sessionId)
                ledger.removeSession(token, caller, request.externalSessionId)
            } catch (_: RuntimeException) {
                // Preserve ownership so death/disconnect cleanup can retry.
            }
        }
    }

    private fun runGeneration(caller: AuthorizedCaller, request: ConsumerRequestParcel, callback: ConsumerHostEventCallback) {
        val context = resolveContext(caller, request)
        val externalRequestId = request.externalRequestId?.takeIf(String::isNotBlank)
        val externalSessionId = request.externalSessionId?.takeIf(String::isNotBlank)
        if (context == null || externalRequestId == null || externalSessionId == null) {
            callback.onEvent(failureEvent(externalRequestId, WireErrorCodes.INVALID_WIRE_REQUEST))
            return
        }
        val dispatcher = resources.callbackDispatcher(context.token)
        val sessionId = ledger.sessionId(context.token, caller, externalSessionId).successOrNull()
        if (dispatcher == null || sessionId == null || !consumerResources.ownsSession(context.token, sessionId)) {
            callback.onEvent(failureEvent(externalRequestId, WireErrorCodes.SESSION_UNAVAILABLE))
            return
        }
        val requestId = ledger.allocateRequest(context.token, caller, externalRequestId).successOrNull()
        if (requestId == null) {
            callback.onEvent(failureEvent(externalRequestId, WireErrorCodes.CLIENT_BACKPRESSURE))
            return
        }
        val coreRequest =
            runCatching {
                ConsumerGenerationRequest(
                    requestId = requestId,
                    sessionId = sessionId,
                    input = requireNotNull(request.input).toCoreConsumerInput(),
                    outputConstraint = requireNotNull(request.outputConstraint).toCoreConsumerOutput(),
                )
            }.getOrNull()
        if (coreRequest == null) {
            ledger.removeRequest(context.token, caller, externalRequestId)
            callback.onEvent(failureEvent(externalRequestId, WireErrorCodes.INVALID_WIRE_REQUEST))
            return
        }
        val forwarder =
            ConsumerEventForwarder(
                externalRequestId = externalRequestId,
                callback = callback,
                dispatcher = dispatcher,
                onTerminal = { submitRequestCleanup(context.token, caller, externalRequestId, requestId) },
                onBackpressure = { resources.handle(requestId)?.cancelSafely() },
            )
        when (
            val start =
                runCatching {
                    context.client.generate(
                        coreRequest,
                        ConsumerGenerationListener(forwarder::onEvent),
                    )
                }.getOrNull()
        ) {
            is ConsumerGenerationStartResult.Accepted -> {
                resources.attachHandle(start.handle.requestId, ConsumerGenerationHandleBridge(start.handle))
                if (ledger.requestId(context.token, caller, externalRequestId).successOrNull() == null) {
                    resources.removeHandle(requestId)
                }
            }

            is ConsumerGenerationStartResult.Rejected -> {
                ledger.removeRequest(context.token, caller, externalRequestId)
                callback.onEvent(
                    ConsumerGenerationEventParcel(
                        externalRequestId = externalRequestId,
                        sequence = 0L,
                        eventTag = ConsumerWireTags.EVENT_FAILED,
                        error = start.failure.toConsumerWireError(),
                    ),
                )
            }

            null -> {
                ledger.removeRequest(context.token, caller, externalRequestId)
                callback.onEvent(failureEvent(externalRequestId, WireErrorCodes.RUNTIME_FAILURE))
            }
        }
    }

    private fun resolveContext(caller: AuthorizedCaller, request: ConsumerRequestParcel): ConsumerContext? {
        if (request.operationId.isBlank()) return null
        val token = request.clientToken.toHostTokenOrNull() ?: return null
        if (ledger.validateConnection(token, caller).failureOrNull() != null) return null
        val client = consumerResources.client(token) ?: return null
        return ConsumerContext(token, client)
    }

    private fun submitRequestCleanup(
        token: HostClientToken,
        caller: AuthorizedCaller,
        externalRequestId: String,
        requestId: io.github.daniele21.localllm.contracts.RequestId,
    ) {
        controlExecutor.submitOrReject(onRejected = { resources.removeHandle(requestId) }) {
            ledger.removeRequest(token, caller, externalRequestId)
            resources.removeHandle(requestId)
        }
    }

    private data class ConsumerContext(
        val token: HostClientToken,
        val client: io.github.daniele21.localllm.contracts.ConsumerLocalLlmClient,
    )
}

private class ConsumerEventForwarder(
    private val externalRequestId: String,
    private val callback: ConsumerHostEventCallback,
    private val dispatcher: HostCallbackDispatcher,
    private val onTerminal: () -> Unit,
    private val onBackpressure: () -> Unit,
) {
    private val lock = Any()
    private val terminal = AtomicBoolean(false)
    private var sequence = 0L

    fun onEvent(event: ConsumerGenerationEvent) {
        synchronized(lock) {
            if (terminal.get()) return
            val parcels = event.toConsumerWire(externalRequestId, sequence)
            sequence += parcels.size
            val isTerminal =
                event is ConsumerGenerationEvent.Completed ||
                    event is ConsumerGenerationEvent.Failed
            if (isTerminal) terminal.set(true)
            if (!dispatcher.dispatch { parcels.forEach(callback::onEvent) }) {
                terminal.set(true)
                onBackpressure()
                onTerminal()
                return
            }
            if (isTerminal) onTerminal()
        }
    }
}

private fun io.github.daniele21.localllm.transport.binder.contract.ClientTokenParcel.toHostTokenOrNull(): HostClientToken? =
    value.takeIf(String::isNotBlank)?.let(::HostClientToken)

private fun String?.toAuthorizedUseCase(caller: AuthorizedCaller): UseCaseId? =
    this?.takeIf(String::isNotBlank)?.let(::UseCaseId)?.takeIf(caller::allows)

private fun LedgerFailure.toWireCode(): String = when (this) {
    LedgerFailure.CLIENT_TOKEN_INVALID -> WireErrorCodes.CLIENT_TOKEN_INVALID
    LedgerFailure.SESSION_NOT_OWNED -> WireErrorCodes.SESSION_UNAVAILABLE
    else -> WireErrorCodes.CLIENT_BACKPRESSURE
}

private fun failureResult(operationId: String, code: String): ConsumerResultParcel = ConsumerResultParcel(
    operationId = operationId,
    error = wireError(code),
)

private fun failureEvent(externalRequestId: String?, code: String): ConsumerGenerationEventParcel = ConsumerGenerationEventParcel(
    externalRequestId = externalRequestId.orEmpty(),
    sequence = 0L,
    eventTag = ConsumerWireTags.EVENT_FAILED,
    error = wireError(code),
)

private fun io.github.daniele21.localllm.contracts.ConsumerFailure.toConsumerWireError() =
    io.github.daniele21.localllm.transport.binder.contract.WireErrorParcel(
        code = code.name,
        safeMessage = "Consumer request failed",
        retryable = false,
    )
