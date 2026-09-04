package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.contracts.GenerationEvent
import io.github.daniele21.localllm.contracts.GenerationMetrics
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.transport.binder.contract.WireErrorCodes
import io.github.daniele21.localllm.transport.binder.contract.WireTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class GenerationEventForwarderTest {
    private val requestId = RequestId("internal-request")

    @Test
    fun callbacksAreDeferredSerializedAndContiguous() {
        val dispatcher = ManualDispatcher()
        val events = mutableListOf<io.github.daniele21.localllm.transport.binder.contract.GenerationEventParcel>()
        var terminalCount = 0
        val forwarder =
            forwarder(
                dispatcher = dispatcher,
                callback = HostEventCallback(events::add),
                onTerminal = { terminalCount += 1 },
            )

        forwarder.onEvent(GenerationEvent.Queued(requestId, 1))
        forwarder.onEvent(GenerationEvent.TextDelta(requestId, "answer", 1))
        forwarder.onEvent(GenerationEvent.Completed(requestId, "answer", metrics()))

        assertTrue(events.isEmpty())
        dispatcher.runAll()

        assertEquals(listOf(0L, 1L, 2L), events.map { it.sequence })
        assertEquals(listOf(WireTags.EVENT_QUEUED, WireTags.EVENT_TEXT_DELTA, WireTags.EVENT_COMPLETED), events.map { it.eventTag })
        assertEquals(1, terminalCount)
        assertFalse(forwarder.callbackFailed)
    }

    @Test
    fun pendingCallbackOverflowCancelsAndDeliversTypedTerminalFailure() {
        val dispatcher = ManualDispatcher()
        val events = mutableListOf<io.github.daniele21.localllm.transport.binder.contract.GenerationEventParcel>()
        var terminalCount = 0
        var backpressureCount = 0
        var callbackFailureCount = 0
        val forwarder =
            forwarder(
                dispatcher = dispatcher,
                callback = HostEventCallback(events::add),
                onTerminal = { terminalCount += 1 },
                onCallbackFailure = { callbackFailureCount += 1 },
                onBackpressure = { backpressureCount += 1 },
            )

        repeat(16) { position -> forwarder.onEvent(GenerationEvent.Queued(requestId, position)) }
        dispatcher.runAll()

        assertEquals(1, events.size)
        assertEquals(WireTags.EVENT_FAILED, events.single().eventTag)
        assertEquals(WireErrorCodes.CLIENT_BACKPRESSURE, events.single().error?.code)
        assertEquals(0L, events.single().sequence)
        assertEquals(1, terminalCount)
        assertEquals(1, backpressureCount)
        assertEquals(0, callbackFailureCount)
        assertTrue(forwarder.callbackFailed)
    }

    @Test
    fun callbackFailureTriggersCleanupOnce() {
        val dispatcher = ManualDispatcher()
        var failureCount = 0
        val forwarder =
            forwarder(
                dispatcher = dispatcher,
                callback = HostEventCallback { throw IllegalStateException("dead callback") },
                onCallbackFailure = { failureCount += 1 },
            )

        forwarder.onEvent(GenerationEvent.Queued(requestId, 1))
        forwarder.onEvent(GenerationEvent.Queued(requestId, 2))
        dispatcher.runAll()

        assertEquals(1, failureCount)
        assertTrue(forwarder.callbackFailed)
    }

    @Test
    fun separateClientDispatchersDoNotBlockEachOther() {
        val first = BoundedSerialHostCallbackDispatcher(queueCapacity = 2)
        val second = BoundedSerialHostCallbackDispatcher(queueCapacity = 2)
        val releaseFirst = CountDownLatch(1)
        val firstStarted = CountDownLatch(1)
        val secondCompleted = CountDownLatch(1)
        try {
            assertTrue(
                first.dispatch {
                    firstStarted.countDown()
                    releaseFirst.await(2, TimeUnit.SECONDS)
                },
            )
            assertTrue(firstStarted.await(1, TimeUnit.SECONDS))
            assertTrue(second.dispatch { secondCompleted.countDown() })
            assertTrue(secondCompleted.await(1, TimeUnit.SECONDS))
        } finally {
            releaseFirst.countDown()
            first.close()
            second.close()
        }
    }

    private fun forwarder(
        dispatcher: HostCallbackDispatcher,
        callback: HostEventCallback,
        onTerminal: () -> Unit = {},
        onCallbackFailure: () -> Unit = {},
        onBackpressure: () -> Unit = {},
    ) = GenerationEventForwarder(
        externalRequestId = "external-request",
        callback = callback,
        dispatcher = dispatcher,
        onTerminal = onTerminal,
        onCallbackFailure = onCallbackFailure,
        onBackpressure = onBackpressure,
    )

    private fun metrics() = GenerationMetrics(
        queueMs = 0,
        modelLoadMs = 0,
        timeToFirstTokenMs = 1,
        totalMs = 2,
        inputTokens = 1,
        outputTokens = 1,
        decodeTokensPerSecond = 1.0,
    )

    private class ManualDispatcher : HostCallbackDispatcher {
        private val tasks = ArrayDeque<() -> Unit>()

        override fun dispatch(task: () -> Unit): Boolean {
            tasks.addLast(task)
            return true
        }

        fun runAll() {
            while (tasks.isNotEmpty()) tasks.removeFirst().invoke()
        }

        override fun close() {
            tasks.clear()
        }
    }
}
