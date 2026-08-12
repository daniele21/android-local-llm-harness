package io.github.daniele21.localllm.transport.binder.client

import android.os.RemoteException
import io.github.daniele21.localllm.contracts.GenerationContentType
import io.github.daniele21.localllm.contracts.GenerationEvent
import io.github.daniele21.localllm.contracts.GenerationHandle
import io.github.daniele21.localllm.contracts.GenerationRequest
import io.github.daniele21.localllm.contracts.LocalLlmError
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.transport.binder.contract.CancelRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.GenerationEventParcel
import io.github.daniele21.localllm.transport.binder.contract.GenerationEventReconstructor
import io.github.daniele21.localllm.transport.binder.contract.toWire
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class BinderGenerationAdapter(
    private val endpointProvider: () -> RegisteredSharedRuntimeEndpoint?,
    endpointInvalidations: SharedRuntimeEndpointInvalidationSource? = null,
    callbackQueueCapacity: Int = DEFAULT_CALLBACK_QUEUE_CAPACITY,
    private val callbackExecutor: ExecutorService = boundedSerialExecutor(callbackQueueCapacity),
    private val externalRequestIds: CorrelationIdSource = CorrelationIdSource { UUID.randomUUID().toString() },
    private val maxAggregateCharacters: Int = DEFAULT_MAX_AGGREGATE_CHARACTERS,
    private val deliveryChunkCharacters: Int = DEFAULT_DELIVERY_CHUNK_CHARACTERS,
) : AutoCloseable {
    private val active = ConcurrentHashMap<String, ActiveGeneration>()
    private val closed = AtomicBoolean(false)
    private val disconnects = GenerationDisconnectCoordinator(active, callbackExecutor, closed, ::failDisconnected)
    private val invalidationSubscription = endpointInvalidations?.addListener { epoch, detail ->
        disconnects.onEndpointInvalidated(epoch, detail)
    }

    init {
        require(callbackQueueCapacity > 0) { "callbackQueueCapacity must be positive" }
        require(maxAggregateCharacters > 0) { "maxAggregateCharacters must be positive" }
        require(deliveryChunkCharacters > 0) { "deliveryChunkCharacters must be positive" }
    }

    fun generate(request: GenerationRequest, eventSink: (GenerationEvent) -> Unit): GenerationHandle {
        check(!closed.get()) { "Shared-runtime generation adapter is closed" }
        val endpoint = requireNotNull(endpointProvider()) { "Shared runtime is not connected" }
        val externalRequestId = externalRequestIds.nextId()
        val generation = ActiveGeneration(
            requestId = request.requestId,
            externalRequestId = externalRequestId,
            endpoint = endpoint,
            eventSink = eventSink,
            reconstructor = GenerationEventReconstructor(externalRequestId, request.requestId),
        )
        check(active.putIfAbsent(request.requestId.value, generation) == null) {
            "Request ${request.requestId.value} is already active"
        }
        val wireRequest = request.toWire(endpoint.clientToken).copy(externalRequestId = externalRequestId)
        try {
            endpoint.service.generate(wireRequest) { event -> enqueue(generation, event) }
        } catch (error: RemoteException) {
            active.remove(request.requestId.value, generation)
            generation.finish()
            throw IllegalStateException("Shared runtime transport failed", error)
        }
        return BinderGenerationHandle(request.requestId) { cancel(generation) }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        invalidationSubscription?.close()
        active.values.toList().forEach(::cancel)
        active.clear()
        callbackExecutor.shutdownNow()
    }

    private fun enqueue(generation: ActiveGeneration, event: GenerationEventParcel) {
        if (closed.get() || generation.terminal || generation.overflowDetail != null) return
        if (endpointProvider()?.connectionEpoch != generation.endpoint.connectionEpoch) {
            disconnects.markStale(generation, STALE_CONNECTION_DETAIL)
            return
        }
        try {
            callbackExecutor.execute { process(generation, event) }
        } catch (_: RejectedExecutionException) {
            if (!closed.get() && generation.markOverflow(CALLBACK_QUEUE_OVERFLOW_DETAIL)) {
                requestRemoteCancel(generation)
            }
        }
    }

    private fun process(generation: ActiveGeneration, wireEvent: GenerationEventParcel) {
        if (!generation.terminal) {
            val disconnected = generation.disconnectionDetail
            if (disconnected != null) {
                failDisconnected(generation, disconnected)
            } else {
                when (val outcome = generation.accept(wireEvent, maxAggregateCharacters, deliveryChunkCharacters)) {
                    is GenerationProcessingOutcome.Failure -> failProtocol(generation, outcome.detail)

                    is GenerationProcessingOutcome.Ready -> {
                        val listenerAccepted = outcome.deliveries.all(generation::deliver)
                        when {
                            !listenerAccepted -> failProtocol(generation, "Client generation listener failed")

                            generation.disconnectionDetail != null -> {
                                failDisconnected(generation, requireNotNull(generation.disconnectionDetail))
                            }

                            generation.overflowDetail != null -> {
                                failProtocol(generation, requireNotNull(generation.overflowDetail))
                            }

                            outcome.event.isTerminal() -> finish(generation)
                        }
                    }
                }
            }
        }
        disconnects.drain()
    }

    private fun cancel(generation: ActiveGeneration) {
        if (generation.terminal || generation.disconnectionDetail != null || !generation.markCancelSent()) return
        try {
            generation.endpoint.service.cancel(
                CancelRequestParcel(generation.endpoint.clientToken, generation.externalRequestId),
            )
        } catch (_: RemoteException) {
            finish(generation)
        }
    }

    private fun requestRemoteCancel(generation: ActiveGeneration) {
        if (!generation.markCancelSent()) return
        try {
            generation.endpoint.service.cancel(
                CancelRequestParcel(generation.endpoint.clientToken, generation.externalRequestId),
            )
        } catch (_: RemoteException) {
            // The queued worker still emits the local terminal failure for this request.
        }
    }

    private fun failDisconnected(generation: ActiveGeneration, detail: String) {
        if (!generation.finish()) return
        active.remove(generation.requestId.value, generation)
        generation.deliverTerminal(
            GenerationEvent.Failed(
                generation.requestId,
                LocalLlmError.NativeRuntime("SERVICE_DISCONNECTED: $detail"),
            ),
        )
    }

    private fun failProtocol(generation: ActiveGeneration, detail: String) {
        if (!generation.finish()) return
        active.remove(generation.requestId.value, generation)
        requestRemoteCancel(generation)
        generation.deliverTerminal(
            GenerationEvent.Failed(
                generation.requestId,
                LocalLlmError.NativeRuntime("Binder protocol failure: $detail"),
            ),
        )
    }

    private fun finish(generation: ActiveGeneration) {
        if (generation.finish()) {
            active.remove(generation.requestId.value, generation)
        }
    }

    private companion object {
        const val DEFAULT_CALLBACK_QUEUE_CAPACITY = 256
        const val DEFAULT_MAX_AGGREGATE_CHARACTERS = 1_048_576
        const val DEFAULT_DELIVERY_CHUNK_CHARACTERS = 256
        const val CALLBACK_QUEUE_OVERFLOW_DETAIL = "Client callback queue capacity exceeded"
        const val STALE_CONNECTION_DETAIL = "Callback arrived from a stale shared-runtime registration"
    }
}

private class GenerationDisconnectCoordinator(
    private val active: ConcurrentHashMap<String, ActiveGeneration>,
    private val callbackExecutor: ExecutorService,
    private val closed: AtomicBoolean,
    private val failureSink: (ActiveGeneration, String) -> Unit,
) {
    fun markStale(generation: ActiveGeneration, detail: String) {
        if (generation.markDisconnected(detail)) scheduleDrain()
    }

    fun onEndpointInvalidated(connectionEpoch: Long, detail: String) {
        if (closed.get()) return
        active.values.forEach { generation ->
            if (generation.endpoint.connectionEpoch == connectionEpoch) {
                generation.markDisconnected(detail)
            }
        }
        scheduleDrain()
    }

    fun drain() {
        active.values.toList().forEach { generation ->
            generation.disconnectionDetail?.let { detail -> failureSink(generation, detail) }
        }
    }

    private fun scheduleDrain() {
        if (closed.get()) return
        try {
            callbackExecutor.execute(::drain)
        } catch (_: RejectedExecutionException) {
            // An already-running or queued callback drains invalidations after it completes.
        }
    }
}

private sealed interface GenerationProcessingOutcome {
    data class Failure(val detail: String) : GenerationProcessingOutcome
    data class Ready(val event: GenerationEvent, val deliveries: List<GenerationEvent>) : GenerationProcessingOutcome
}

private class ActiveGeneration(
    val requestId: RequestId,
    val externalRequestId: String,
    val endpoint: RegisteredSharedRuntimeEndpoint,
    val eventSink: (GenerationEvent) -> Unit,
    val reconstructor: GenerationEventReconstructor,
) {
    private val terminalFlag = AtomicBoolean(false)
    private val cancelSent = AtomicBoolean(false)
    private val overflow = AtomicReference<String?>(null)
    private val disconnected = AtomicReference<String?>(null)
    private val pendingText = StringBuilder()
    private var pendingContentType = GenerationContentType.ANSWER
    private var pendingGeneratedTokens = 0
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

    fun deliver(event: GenerationEvent): Boolean = runCatching { eventSink(event) }.isSuccess

    fun deliverTerminal(event: GenerationEvent) {
        runCatching { eventSink(event) }
    }

    fun accept(wireEvent: GenerationEventParcel, maxAggregateCharacters: Int, deliveryChunkCharacters: Int): GenerationProcessingOutcome {
        val overflowFailure = overflowDetail
        if (overflowFailure != null) return GenerationProcessingOutcome.Failure(overflowFailure)
        val mapped = runCatching { reconstructor.accept(wireEvent) }
            .getOrElse { error ->
                return GenerationProcessingOutcome.Failure(error.message ?: "Invalid shared-runtime generation event")
            }
        val deliveries = coalesce(mapped, maxAggregateCharacters, deliveryChunkCharacters)
            ?: return GenerationProcessingOutcome.Failure(
                "Reconstructed generation output exceeded the client aggregate bound",
            )
        return GenerationProcessingOutcome.Ready(mapped, deliveries)
    }

    private fun coalesce(event: GenerationEvent, maxAggregateCharacters: Int, deliveryChunkCharacters: Int): List<GenerationEvent>? {
        if (event !is GenerationEvent.TextDelta) {
            return buildList {
                flushPending()?.let(::add)
                add(event)
            }
        }
        aggregateCharacters += event.text.length
        if (aggregateCharacters > maxAggregateCharacters) return null
        return buildList {
            if (pendingText.isNotEmpty() && pendingContentType != event.contentType) {
                flushPending()?.let(::add)
            }
            if (pendingText.isEmpty()) {
                pendingContentType = event.contentType
            }
            pendingText.append(event.text)
            pendingGeneratedTokens = event.generatedTokens
            if (pendingText.length >= deliveryChunkCharacters) {
                flushPending()?.let(::add)
            }
        }
    }

    private fun flushPending(): GenerationEvent.TextDelta? {
        if (pendingText.isEmpty()) return null
        val result = GenerationEvent.TextDelta(
            requestId = requestId,
            text = pendingText.toString(),
            generatedTokens = pendingGeneratedTokens,
            contentType = pendingContentType,
        )
        pendingText.clear()
        return result
    }
}

private class BinderGenerationHandle(override val requestId: RequestId, private val cancelAction: () -> Unit) : GenerationHandle {
    private val cancelled = AtomicBoolean(false)

    override fun cancel() {
        if (cancelled.compareAndSet(false, true)) {
            cancelAction()
        }
    }
}

private fun boundedSerialExecutor(queueCapacity: Int): ExecutorService = ThreadPoolExecutor(
    1,
    1,
    0L,
    TimeUnit.MILLISECONDS,
    ArrayBlockingQueue(queueCapacity),
    Executors.defaultThreadFactory(),
    ThreadPoolExecutor.AbortPolicy(),
)

private fun GenerationEvent.isTerminal(): Boolean = this is GenerationEvent.Completed || this is GenerationEvent.Failed
