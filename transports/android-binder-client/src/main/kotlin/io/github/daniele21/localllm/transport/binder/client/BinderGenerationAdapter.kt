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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal class BinderGenerationAdapter(
    private val endpointProvider: () -> RegisteredSharedRuntimeEndpoint?,
    private val callbackExecutor: ExecutorService = Executors.newSingleThreadExecutor(),
    private val externalRequestIds: CorrelationIdSource = CorrelationIdSource { UUID.randomUUID().toString() },
    private val maxAggregateCharacters: Int = DEFAULT_MAX_AGGREGATE_CHARACTERS,
    private val deliveryChunkCharacters: Int = DEFAULT_DELIVERY_CHUNK_CHARACTERS,
) : AutoCloseable {
    private val active = ConcurrentHashMap<String, ActiveGeneration>()
    private val closed = AtomicBoolean(false)

    init {
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
        active.values.toList().forEach(::cancel)
        active.clear()
        callbackExecutor.shutdownNow()
    }

    private fun enqueue(generation: ActiveGeneration, event: GenerationEventParcel) {
        if (closed.get() || generation.isTerminal()) return
        callbackExecutor.execute { process(generation, event) }
    }

    private fun process(generation: ActiveGeneration, wireEvent: GenerationEventParcel) {
        if (generation.isTerminal()) return
        val mapped = runCatching { generation.reconstructor.accept(wireEvent) }
            .getOrElse { error ->
                failProtocol(generation, error.message ?: "Invalid shared-runtime generation event")
                return
            }
        val deliveries = generation.coalesce(mapped, maxAggregateCharacters, deliveryChunkCharacters)
        if (deliveries == null) {
            failProtocol(generation, "Reconstructed generation output exceeded the client aggregate bound")
            return
        }
        if (!deliveries.all(generation::deliver)) {
            cancel(generation)
            return
        }
        if (mapped.isTerminal()) {
            finish(generation)
        }
    }

    private fun cancel(generation: ActiveGeneration) {
        if (!generation.markCancelSent() || generation.isTerminal()) return
        try {
            generation.endpoint.service.cancel(
                CancelRequestParcel(generation.endpoint.clientToken, generation.externalRequestId),
            )
        } catch (_: RemoteException) {
            finish(generation)
        }
    }

    private fun failProtocol(generation: ActiveGeneration, detail: String) {
        if (!generation.finish()) return
        active.remove(generation.requestId.value, generation)
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
        const val DEFAULT_MAX_AGGREGATE_CHARACTERS = 1_048_576
        const val DEFAULT_DELIVERY_CHUNK_CHARACTERS = 256
    }
}

private class ActiveGeneration(
    val requestId: RequestId,
    val externalRequestId: String,
    val endpoint: RegisteredSharedRuntimeEndpoint,
    val eventSink: (GenerationEvent) -> Unit,
    val reconstructor: GenerationEventReconstructor,
) {
    private val terminal = AtomicBoolean(false)
    private val cancelSent = AtomicBoolean(false)
    private val pendingText = StringBuilder()
    private var pendingContentType = GenerationContentType.ANSWER
    private var pendingGeneratedTokens = 0
    private var aggregateCharacters = 0

    fun isTerminal(): Boolean = terminal.get()

    fun finish(): Boolean = terminal.compareAndSet(false, true)

    fun markCancelSent(): Boolean = cancelSent.compareAndSet(false, true)

    fun deliver(event: GenerationEvent): Boolean = runCatching { eventSink(event) }.isSuccess

    fun deliverTerminal(event: GenerationEvent) {
        runCatching { eventSink(event) }
    }

    fun coalesce(event: GenerationEvent, maxAggregateCharacters: Int, deliveryChunkCharacters: Int): List<GenerationEvent>? {
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

private class BinderGenerationHandle(
    override val requestId: RequestId,
    private val cancelAction: () -> Unit,
) : GenerationHandle {
    private val cancelled = AtomicBoolean(false)

    override fun cancel() {
        if (cancelled.compareAndSet(false, true)) {
            cancelAction()
        }
    }
}

private fun GenerationEvent.isTerminal(): Boolean = this is GenerationEvent.Completed || this is GenerationEvent.Failed
