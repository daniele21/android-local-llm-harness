package io.github.daniele21.localllm.transport.binder.client

import android.os.RemoteException
import io.github.daniele21.localllm.contracts.ConsumerErrorCode
import io.github.daniele21.localllm.contracts.ConsumerFailure
import io.github.daniele21.localllm.contracts.ConsumerGenerationEvent
import io.github.daniele21.localllm.contracts.ConsumerGenerationHandle
import io.github.daniele21.localllm.contracts.ConsumerGenerationListener
import io.github.daniele21.localllm.contracts.ConsumerGenerationRequest
import io.github.daniele21.localllm.contracts.ConsumerGenerationStartResult
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.transport.binder.contract.CancelRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerGenerationEventParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerGenerationEventReconstructor
import io.github.daniele21.localllm.transport.binder.contract.ConsumerRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.toConsumerWire
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class BinderConsumerGenerationAdapter(
    private val endpointProvider: () -> RegisteredSharedRuntimeEndpoint?,
    endpointInvalidations: SharedRuntimeEndpointInvalidationSource? = null,
    private val externalRequestIds: CorrelationIdSource = CorrelationIdSource { UUID.randomUUID().toString() },
    private val callbackExecutor: ExecutorService = consumerSerialExecutor(DEFAULT_CALLBACK_QUEUE_CAPACITY),
    private val maxAggregateCharacters: Int = DEFAULT_MAX_AGGREGATE_CHARACTERS,
) : AutoCloseable {
    private val lifecycleLock = Any()
    private val active = ConcurrentHashMap<String, ActiveConsumerGeneration>()
    private val closed = AtomicBoolean(false)
    private val invalidationSubscription = endpointInvalidations?.addListener { epoch, detail ->
        active.forEach { _, generation ->
            if (generation.endpoint.connectionEpoch == epoch) generation.markDisconnected(detail)
        }
        scheduleDrain()
    }

    fun generate(
        request: ConsumerGenerationRequest,
        listener: ConsumerGenerationListener,
    ): ConsumerGenerationStartResult {
        val endpoint = endpointProvider() ?: return rejectedTransport()
        val externalRequestId = externalRequestIds.nextId()
        val generation =
            ActiveConsumerGeneration(
                requestId = request.requestId,
                externalRequestId = externalRequestId,
                endpoint = endpoint,
                listener = listener,
                reconstructor = ConsumerGenerationEventReconstructor(externalRequestId, request.requestId),
            )
        synchronized(lifecycleLock) {
            if (closed.get()) return rejectedTransport()
            if (active.putIfAbsent(request.requestId.value, generation) != null) {
                return ConsumerGenerationStartResult.Rejected(
                    ConsumerFailure(ConsumerErrorCode.INVALID_INPUT, "Request ID is already active"),
                )
            }
            val wire =
                ConsumerRequestParcel(
                    clientToken = endpoint.clientToken,
                    operationId = externalRequestId,
                    externalSessionId = request.sessionId.value,
                    externalRequestId = externalRequestId,
                    input = request.input.toConsumerWire(),
                    outputConstraint = request.outputConstraint.toConsumerWire(),
                )
            try {
                endpoint.service.consumerGenerate(wire) { event -> enqueue(generation, event) }
            } catch (_: RemoteException) {
                active.remove(request.requestId.value, generation)
                generation.finish()
                return rejectedTransport()
            }
        }
        return ConsumerGenerationStartResult.Accepted(
            BinderConsumerGenerationHandle(request.requestId) { cancel(generation) },
        )
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        synchronized(lifecycleLock) {
            invalidationSubscription?.close()
            active.forEach { _, generation -> cancel(generation) }
            active.clear()
            callbackExecutor.shutdownNow()
        }
    }

    private fun enqueue(generation: ActiveConsumerGeneration, event: ConsumerGenerationEventParcel) {
        if (closed.get() || generation.terminal) return
        if (endpointProvider()?.connectionEpoch != generation.endpoint.connectionEpoch) {
            generation.markDisconnected(STALE_CONNECTION_DETAIL)
        }
        try {
            callbackExecutor.execute { process(generation, event) }
        } catch (_: RejectedExecutionException) {
            if (!closed.get() && generation.markOverflow(CALLBACK_QUEUE_OVERFLOW_DETAIL)) {
                requestRemoteCancel(generation)
            }
        }
    }

    private fun process(generation: ActiveConsumerGeneration, event: ConsumerGenerationEventParcel) {
        when {
            generation.terminal -> Unit
            generation.disconnectionDetail != null -> failDisconnected(generation)
            generation.overflowDetail != null -> failProtocol(generation, requireNotNull(generation.overflowDetail))
            else -> {
                val mapped = generation.accept(event, maxAggregateCharacters)
                if (mapped == null) {
                    failProtocol(generation, "Invalid or oversized consumer generation stream")
                } else if (!generation.deliver(mapped)) {
                    failProtocol(generation, "Consumer generation listener failed")
                } else if (mapped is ConsumerGenerationEvent.Completed || mapped is ConsumerGenerationEvent.Failed) {
                    finish(generation)
                }
            }
        }
        drainDisconnected()
    }

    private fun cancel(generation: ActiveConsumerGeneration) {
        if (generation.terminal || !generation.markCancelSent()) return
        try {
            generation.endpoint.service.consumerCancel(
                CancelRequestParcel(generation.endpoint.clientToken, generation.externalRequestId),
            )
        } catch (_: RemoteException) {
            generation.markDisconnected(CANCEL_TRANSPORT_FAILURE_DETAIL)
            scheduleDrain()
        }
    }

    private fun requestRemoteCancel(generation: ActiveConsumerGeneration) {
        if (!generation.markCancelSent()) return
        try {
            generation.endpoint.service.consumerCancel(
                CancelRequestParcel(generation.endpoint.clientToken, generation.externalRequestId),
            )
        } catch (_: RemoteException) {
            generation.markDisconnected(CANCEL_TRANSPORT_FAILURE_DETAIL)
        }
    }

    private fun failDisconnected(generation: ActiveConsumerGeneration) {
        if (!generation.finish()) return
        active.remove(generation.requestId.value, generation)
        generation.deliverTerminal(runtimeFailure("Shared runtime disconnected"))
    }

    private fun failProtocol(generation: ActiveConsumerGeneration, detail: String) {
        if (!generation.finish()) return
        active.remove(generation.requestId.value, generation)
        requestRemoteCancel(generation)
        generation.deliverTerminal(runtimeFailure("Binder protocol failure: $detail"))
    }

    private fun finish(generation: ActiveConsumerGeneration) {
        if (generation.finish()) active.remove(generation.requestId.value, generation)
    }

    private fun drainDisconnected() {
        active.forEach { _, generation ->
            if (generation.disconnectionDetail != null) failDisconnected(generation)
        }
    }

    private fun scheduleDrain() {
        if (closed.get()) return
        try {
            callbackExecutor.execute(::drainDisconnected)
        } catch (_: RejectedExecutionException) {
            // A queued callback will drain invalidations after it runs.
        }
    }

    private fun runtimeFailure(message: String): ConsumerGenerationEvent.Failed =
        ConsumerGenerationEvent.Failed(
            RequestId("transport-failure"),
            ConsumerFailure(ConsumerErrorCode.RUNTIME_FAILURE, message),
        )

    private companion object {
        const val DEFAULT_CALLBACK_QUEUE_CAPACITY = 256
        const val DEFAULT_MAX_AGGREGATE_CHARACTERS = 1_048_576
        const val CALLBACK_QUEUE_OVERFLOW_DETAIL = "Client callback queue capacity exceeded"
        const val STALE_CONNECTION_DETAIL = "Callback arrived from a stale shared-runtime registration"
        const val CANCEL_TRANSPORT_FAILURE_DETAIL = "Host Binder connection failed during cancellation"
    }
}

private class ActiveConsumerGeneration(
    val requestId: RequestId,
    val externalRequestId: String,
    val endpoint: RegisteredSharedRuntimeEndpoint,
    private val listener: ConsumerGenerationListener,
    private val reconstructor: ConsumerGenerationEventReconstructor,
) {
    private val terminalFlag = AtomicBoolean(false)
    private val cancelSent = AtomicBoolean(false)
    private val overflow = AtomicReference<String?>(null)
    private val disconnected = AtomicReference<String?>(null)
    private var aggregateCharacters = 0

    val terminal: Boolean
        get() = terminalFlag.get()
    val overflowDetail: String?
        get() = overflow.get()
    val disconnectionDetail: String?
        get() = disconnected.get()

    fun finish(): Boolean = terminalFlag.compareAndSet(false, true)
    fun markCancelSent(): Boolean = cancelSent.compareAndSet(false, true)
    fun markOverflow(detail: String): Boolean = overflow.compareAndSet(null, detail)
    fun markDisconnected(detail: String): Boolean = disconnected.compareAndSet(null, detail)

    fun accept(event: ConsumerGenerationEventParcel, maxAggregateCharacters: Int): ConsumerGenerationEvent? {
        if (event.deltaText != null) {
            aggregateCharacters += event.deltaText.length
            if (aggregateCharacters > maxAggregateCharacters) return null
        }
        return runCatching { reconstructor.accept(event) }.getOrNull()
    }

    fun deliver(event: ConsumerGenerationEvent): Boolean = runCatching { listener.onEvent(event) }.isSuccess

    fun deliverTerminal(event: ConsumerGenerationEvent.Failed) {
        val corrected = event.copy(requestId = requestId)
        runCatching { listener.onEvent(corrected) }
    }
}

private class BinderConsumerGenerationHandle(
    override val requestId: RequestId,
    private val cancelAction: () -> Unit,
) : ConsumerGenerationHandle {
    private val cancelled = AtomicBoolean(false)

    override fun cancel() {
        if (cancelled.compareAndSet(false, true)) cancelAction()
    }
}

private fun rejectedTransport() =
    ConsumerGenerationStartResult.Rejected(
        ConsumerFailure(ConsumerErrorCode.RUNTIME_FAILURE, "Shared runtime transport is unavailable"),
    )

private fun consumerSerialExecutor(queueCapacity: Int): ExecutorService =
    ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(queueCapacity),
        { runnable -> Thread(runnable, "local-llm-consumer-callback").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy(),
    )
