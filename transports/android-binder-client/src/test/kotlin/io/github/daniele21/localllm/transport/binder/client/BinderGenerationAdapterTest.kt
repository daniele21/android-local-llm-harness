package io.github.daniele21.localllm.transport.binder.client

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.GenerationEvent
import io.github.daniele21.localllm.contracts.GenerationMetrics
import io.github.daniele21.localllm.contracts.GenerationRequest
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.transport.binder.contract.ClientTokenParcel
import io.github.daniele21.localllm.transport.binder.contract.toWire
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BinderGenerationAdapterTest {
    @Test
    fun `callbacks are reconstructed in order and small deltas are coalesced`() {
        val service = FakeSharedRuntimeRemoteService()
        val events = Collections.synchronizedList(mutableListOf<GenerationEvent>())
        val terminal = CountDownLatch(1)
        service.generationHandler = { request, callback ->
            callback(GenerationEvent.Queued(RequestId("host"), 1).toWire(request.externalRequestId, 0))
            callback(GenerationEvent.TextDelta(RequestId("host"), "hello ", 1).toWire(request.externalRequestId, 1))
            callback(GenerationEvent.TextDelta(RequestId("host"), "world", 2).toWire(request.externalRequestId, 2))
            callback(completed("host", "hello world").toWire(request.externalRequestId, 3))
        }
        val adapter = adapter(service)

        adapter.generate(request("caller-request")) { event ->
            events += event
            if (event is GenerationEvent.Completed) terminal.countDown()
        }

        assertTrue(terminal.await(2, TimeUnit.SECONDS))
        assertEquals(3, events.size)
        assertTrue(events.all { it.requestId == RequestId("caller-request") })
        assertTrue(events[1] is GenerationEvent.TextDelta)
        assertEquals("hello world", (events[1] as GenerationEvent.TextDelta).text)
        assertTrue(events.last() is GenerationEvent.Completed)
        assertEquals("hello world", (events.last() as GenerationEvent.Completed).output)
        assertNotEquals("caller-request", service.lastGenerationRequest?.externalRequestId)
        adapter.close()
    }

    @Test
    fun `sequence gap produces one local protocol failure`() {
        val service = FakeSharedRuntimeRemoteService()
        val events = Collections.synchronizedList(mutableListOf<GenerationEvent>())
        val terminal = CountDownLatch(1)
        service.generationHandler = { request, callback ->
            callback(GenerationEvent.Queued(RequestId("host"), 1).toWire(request.externalRequestId, 1))
        }
        val adapter = adapter(service)

        adapter.generate(request("gap-request")) { event ->
            events += event
            if (event is GenerationEvent.Failed) terminal.countDown()
        }

        assertTrue(terminal.await(2, TimeUnit.SECONDS))
        assertEquals(1, events.size)
        assertTrue(events.single() is GenerationEvent.Failed)
        val failure = events.single() as GenerationEvent.Failed
        assertTrue(failure.error.message.contains("Binder protocol failure"))
        adapter.close()
    }

    @Test
    fun `aggregate output bound terminates the affected request`() {
        val service = FakeSharedRuntimeRemoteService()
        val events = Collections.synchronizedList(mutableListOf<GenerationEvent>())
        val terminal = CountDownLatch(1)
        service.generationHandler = { request, callback ->
            callback(GenerationEvent.TextDelta(RequestId("host"), "12345", 1).toWire(request.externalRequestId, 0))
        }
        val adapter = adapter(service, maxAggregateCharacters = 4)

        adapter.generate(request("bounded-request")) { event ->
            events += event
            if (event is GenerationEvent.Failed) terminal.countDown()
        }

        assertTrue(terminal.await(2, TimeUnit.SECONDS))
        assertEquals(1, events.size)
        assertTrue(events.single() is GenerationEvent.Failed)
        val failure = events.single() as GenerationEvent.Failed
        assertTrue(failure.error.message.contains("aggregate bound"))
        adapter.close()
    }

    @Test
    fun `generation handle sends cancel at most once while active`() {
        val service = FakeSharedRuntimeRemoteService()
        service.generationHandler = { _, _ -> }
        val adapter = adapter(service)

        val handle = adapter.generate(request("cancel-request")) { }
        handle.cancel()
        handle.cancel()

        assertEquals(1, service.cancelCalls)
        assertEquals(service.lastGenerationRequest?.externalRequestId, service.lastCancelRequest?.externalRequestId)
        adapter.close()
    }

    private fun adapter(service: FakeSharedRuntimeRemoteService, maxAggregateCharacters: Int = 1_048_576): BinderGenerationAdapter =
        BinderGenerationAdapter(
            endpointProvider = { RegisteredSharedRuntimeEndpoint(service, ClientTokenParcel("client-token")) },
            externalRequestIds = CorrelationIdSource { "remote-${remoteCounter++}" },
            maxAggregateCharacters = maxAggregateCharacters,
        )

    private fun request(requestId: String): GenerationRequest = GenerationRequest(
        requestId = RequestId(requestId),
        sessionId = SessionId("session-1"),
        applicationId = ApplicationId("test-app"),
        useCaseId = UseCaseId("test-use-case"),
        input = "hello",
    )

    private fun completed(requestId: String, output: String) = GenerationEvent.Completed(
        requestId = RequestId(requestId),
        output = output,
        metrics = GenerationMetrics(
            queueMs = 0,
            modelLoadMs = null,
            timeToFirstTokenMs = 1,
            totalMs = 2,
            inputTokens = 1,
            outputTokens = 2,
            decodeTokensPerSecond = 10.0,
        ),
    )

    private companion object {
        var remoteCounter = 0
    }
}
