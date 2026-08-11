package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.contracts.GenerationEvent
import io.github.daniele21.localllm.transport.binder.contract.chunkDelta
import io.github.daniele21.localllm.transport.binder.contract.toWire
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal class GenerationEventForwarder(
    private val externalRequestId: String,
    private val callback: HostEventCallback,
    private val onTerminal: () -> Unit,
    private val onCallbackFailure: () -> Unit,
) {
    private val nextSequence = AtomicLong(0)
    private val terminalDelivered = AtomicBoolean(false)
    private val failed = AtomicBoolean(false)

    val callbackFailed: Boolean
        get() = failed.get()

    fun onEvent(event: GenerationEvent) {
        if (failed.get() || terminalDelivered.get()) return
        val events =
            if (event is GenerationEvent.TextDelta) {
                chunkDelta(event.text).map { chunk -> event.copy(text = chunk) }
            } else {
                listOf(event)
            }
        events.forEach(::deliver)
    }

    private fun deliver(event: GenerationEvent) {
        if (failed.get() || terminalDelivered.get()) return
        val terminal = event is GenerationEvent.Completed || event is GenerationEvent.Failed
        val wireEvent = event.toWire(externalRequestId, nextSequence.getAndIncrement())
        try {
            callback.onEvent(wireEvent)
            if (terminal && terminalDelivered.compareAndSet(false, true)) {
                onTerminal()
            }
        } catch (_: RuntimeException) {
            if (failed.compareAndSet(false, true)) {
                onCallbackFailure()
            }
        }
    }
}
