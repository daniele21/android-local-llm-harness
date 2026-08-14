package io.github.daniele21.localllm.transport.binder.client

import io.github.daniele21.localllm.contracts.ConsumerContentType
import io.github.daniele21.localllm.contracts.ConsumerExecutionIdentity
import io.github.daniele21.localllm.contracts.ConsumerGenerationEvent
import io.github.daniele21.localllm.contracts.ConsumerGenerationInput
import io.github.daniele21.localllm.contracts.ConsumerGenerationRequest
import io.github.daniele21.localllm.contracts.ConsumerGenerationStartResult
import io.github.daniele21.localllm.contracts.ConsumerInferenceMetrics
import io.github.daniele21.localllm.contracts.ConsumerInferenceResult
import io.github.daniele21.localllm.contracts.ConsumerOutputConstraint
import io.github.daniele21.localllm.contracts.ConsumerOutputConstraintKind
import io.github.daniele21.localllm.contracts.ConsumerStopReason
import io.github.daniele21.localllm.contracts.EffectiveConsumerReasoningMode
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.transport.binder.contract.ConsumerGenerationEventParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerWireTags
import io.github.daniele21.localllm.transport.binder.contract.toConsumerWire
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BinderConsumerGenerationAdapterTest {
    private val token = successfulRegistration().clientToken!!

    @Test
    fun `consumer generation reconstructs ordered public result and metrics`() {
        val service = FakeSharedRuntimeRemoteService()
        service.consumerGenerationHandler = { request, callback ->
            val externalId = requireNotNull(request.externalRequestId)
            events(externalId).forEach(callback)
        }
        val executor = Executors.newSingleThreadExecutor()
        val adapter = BinderConsumerGenerationAdapter(
            endpointProvider = { RegisteredSharedRuntimeEndpoint(service, token, connectionEpoch = 1L) },
            externalRequestIds = deterministicIds(),
            callbackExecutor = executor,
        )
        val received = CopyOnWriteArrayList<ConsumerGenerationEvent>()
        val terminal = CountDownLatch(1)

        val start = adapter.generate(request()) { event ->
            received += event
            if (event is ConsumerGenerationEvent.Completed || event is ConsumerGenerationEvent.Failed) terminal.countDown()
        }

        assertTrue(start is ConsumerGenerationStartResult.Accepted)
        assertTrue(terminal.await(2, TimeUnit.SECONDS))
        val completed = received.filterIsInstance<ConsumerGenerationEvent.Completed>().single()
        assertEquals("ok", completed.answer)
        assertEquals(2, completed.metrics.outputTokens)
        assertEquals(25.0, requireNotNull(completed.metrics.decodeTokensPerSecond), 0.0)
        assertEquals(ConsumerStopReason.END_OF_GENERATION, completed.metrics.stopReason)
        assertEquals("cap-rev-1", completed.execution.capabilityRevision)
        assertEquals(
            listOf(
                ConsumerGenerationEvent.Queued::class,
                ConsumerGenerationEvent.Prepared::class,
                ConsumerGenerationEvent.Started::class,
                ConsumerGenerationEvent.ContentDelta::class,
                ConsumerGenerationEvent.Completed::class,
            ),
            received.map { it::class },
        )

        adapter.close()
    }

    @Test
    fun `consumer cancellation is idempotent and protocol gap fails closed once`() {
        val service = FakeSharedRuntimeRemoteService()
        service.consumerGenerationHandler = { request, callback ->
            callback(
                ConsumerGenerationEventParcel(
                    externalRequestId = requireNotNull(request.externalRequestId),
                    sequence = 1,
                    eventTag = ConsumerWireTags.EVENT_STARTED,
                ),
            )
        }
        val executor = Executors.newSingleThreadExecutor()
        val adapter = BinderConsumerGenerationAdapter(
            endpointProvider = { RegisteredSharedRuntimeEndpoint(service, token, connectionEpoch = 1L) },
            externalRequestIds = deterministicIds(),
            callbackExecutor = executor,
        )
        val terminal = CountDownLatch(1)
        val received = CopyOnWriteArrayList<ConsumerGenerationEvent>()

        val start = adapter.generate(request()) { event ->
            received += event
            if (event is ConsumerGenerationEvent.Failed) terminal.countDown()
        }
        assertTrue(start is ConsumerGenerationStartResult.Accepted)
        val handle = (start as ConsumerGenerationStartResult.Accepted).handle
        handle.cancel()
        handle.cancel()

        assertTrue(terminal.await(2, TimeUnit.SECONDS))
        assertEquals(1, received.filterIsInstance<ConsumerGenerationEvent.Failed>().size)
        assertEquals(1, service.consumerCancelCalls)

        adapter.close()
    }

    private fun request() = ConsumerGenerationRequest(
        requestId = RequestId("consumer-request-1"),
        sessionId = SessionId("consumer-session-1"),
        input = ConsumerGenerationInput.Text("hello"),
        outputConstraint = ConsumerOutputConstraint.Text,
    )

    private fun events(externalRequestId: String): List<ConsumerGenerationEventParcel> {
        val requestId = RequestId("consumer-request-1")
        val execution =
            ConsumerExecutionIdentity(
                useCaseId = UseCaseId("document-pii-detection"),
                capabilityRevision = "cap-rev-1",
                preset = null,
                reasoningMode = EffectiveConsumerReasoningMode.DISABLED,
                outputConstraint = ConsumerOutputConstraintKind.TEXT,
                sessionKind = SessionKind.STATELESS,
            )
        val metrics =
            ConsumerInferenceMetrics(
                outputTokens = 2,
                timeToFirstTokenMs = 4,
                totalMs = 12,
                decodeTokensPerSecond = 25.0,
                inputTokens = 1,
                reasoningTokens = null,
                answerTokens = 2,
                queueMs = 1,
                stopReason = ConsumerStopReason.END_OF_GENERATION,
            )
        val events =
            listOf<ConsumerGenerationEvent>(
                ConsumerGenerationEvent.Queued(requestId, 1),
                ConsumerGenerationEvent.Prepared(requestId, execution),
                ConsumerGenerationEvent.Started(requestId),
                ConsumerGenerationEvent.ContentDelta(requestId, "ok", ConsumerContentType.ANSWER),
                ConsumerGenerationEvent.Completed(
                    requestId,
                    ConsumerInferenceResult("ok", null, metrics, execution),
                ),
            )
        var sequence = 0L
        return buildList {
            events.forEach { event ->
                val parcels = event.toConsumerWire(externalRequestId, sequence)
                addAll(parcels)
                sequence += parcels.size
            }
        }
    }

    private fun deterministicIds(): CorrelationIdSource = CorrelationIdSource { "wire-request-1" }
}
