package io.github.daniele21.localllm.observability.health

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.GenerationEvent
import io.github.daniele21.localllm.contracts.GenerationHandle
import io.github.daniele21.localllm.contracts.GenerationListener
import io.github.daniele21.localllm.contracts.GenerationMetrics
import io.github.daniele21.localllm.contracts.GenerationRequest
import io.github.daniele21.localllm.contracts.LocalLlmClient
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.PrepareResult
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.RuntimeSnapshot
import io.github.daniele21.localllm.contracts.RuntimeState
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.observability.SanityFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class LocalLlmSanityExecutorTest {
    @Test
    fun `executor prepares generates and closes its session`() {
        val client = FakeLocalLlmClient(
            terminalEvent = { request ->
                GenerationEvent.Completed(
                    requestId = request.requestId,
                    output = "deterministic-output",
                    metrics = metrics(outputTokens = 2),
                )
            },
        )
        val nanos = AtomicLong(0L)
        val executor = LocalLlmSanityExecutor(
            client = client,
            requestIdFactory = { RequestId("sanity-request") },
            monotonicClock = { nanos.addAndGet(1_000_000L) },
        )

        val result = executor.execute(fixture())

        assertTrue(result.successful)
        assertEquals("deterministic-output", result.output)
        assertEquals(2, result.outputTokens)
        assertEquals(1, client.prepareCalls)
        assertEquals(1, client.createSessionCalls)
        assertEquals(listOf(SessionId("sanity-session")), client.closedSessions)
    }

    @Test
    fun `prepare failure returns typed sanity failure without creating a session`() {
        val client = FakeLocalLlmClient(
            prepareResult = PrepareResult(false, null, "model missing"),
        )
        val executor = LocalLlmSanityExecutor(
            client = client,
            requestIdFactory = { RequestId("unused") },
        )

        val result = executor.execute(fixture())

        assertFalse(result.successful)
        assertEquals("SANITY_PREPARE_FAILED", result.errorCode)
        assertEquals(0, client.createSessionCalls)
        assertTrue(client.closedSessions.isEmpty())
    }

    @Test
    fun `timeout cancels generation and closes the session`() {
        val client = FakeLocalLlmClient(terminalEvent = null)
        val executor = LocalLlmSanityExecutor(
            client = client,
            requestIdFactory = { RequestId("timeout-request") },
        )

        val result = executor.execute(fixture(timeoutMs = 1L))

        assertEquals("SANITY_TIMEOUT", result.errorCode)
        assertTrue(client.cancelled.get())
        assertEquals(listOf(SessionId("sanity-session")), client.closedSessions)
    }

    private fun fixture(timeoutMs: Long = 100L): SanityFixture = SanityFixture(
        id = "fixture",
        applicationId = ApplicationId("app"),
        useCaseId = UseCaseId("assistant"),
        input = "Return a deterministic output.",
        timeoutMs = timeoutMs,
    )

    private fun metrics(outputTokens: Int): GenerationMetrics = GenerationMetrics(
        queueMs = 1L,
        modelLoadMs = 2L,
        timeToFirstTokenMs = 3L,
        totalMs = 4L,
        inputTokens = 5,
        outputTokens = outputTokens,
        decodeTokensPerSecond = 6.0,
        prefillMs = 1L,
        decodeMs = 2L,
    )
}

private class FakeLocalLlmClient(
    private val prepareResult: PrepareResult = PrepareResult(
        ready = true,
        modelDigest = ModelDigest("a".repeat(64)),
        detail = "ready",
    ),
    private val terminalEvent: ((GenerationRequest) -> GenerationEvent)? = { request ->
        GenerationEvent.Completed(
            requestId = request.requestId,
            output = "ok",
            metrics = GenerationMetrics(0L, 0L, 0L, 0L, 0, 1, 1.0),
        )
    },
) : LocalLlmClient {
    var prepareCalls: Int = 0
    var createSessionCalls: Int = 0
    val closedSessions = mutableListOf<SessionId>()
    val cancelled = AtomicBoolean(false)

    override fun runtimeSnapshot(): RuntimeSnapshot = RuntimeSnapshot(
        state = RuntimeState.READY,
        loadedModel = prepareResult.modelDigest,
        activeSessions = 0,
        queuedRequests = 0,
    )

    override fun prepare(applicationId: ApplicationId, useCaseId: UseCaseId): PrepareResult {
        prepareCalls += 1
        return prepareResult
    }

    override fun createSession(applicationId: ApplicationId, useCaseId: UseCaseId): SessionId {
        createSessionCalls += 1
        return SessionId("sanity-session")
    }

    override fun generate(request: GenerationRequest, listener: GenerationListener): GenerationHandle {
        terminalEvent?.invoke(request)?.let(listener::onEvent)
        return object : GenerationHandle {
            override val requestId: RequestId = request.requestId

            override fun cancel() {
                cancelled.set(true)
            }
        }
    }

    override fun closeSession(sessionId: SessionId) {
        closedSessions += sessionId
    }
}
