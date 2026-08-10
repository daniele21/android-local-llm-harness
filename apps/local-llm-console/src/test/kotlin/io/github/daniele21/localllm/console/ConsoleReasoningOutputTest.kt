package io.github.daniele21.localllm.console

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.GenerationContentType
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleReasoningOutputTest {
    @Test
    fun `console routes reasoning and answer deltas independently`() {
        val applicationId = ApplicationId("app")
        val useCaseId = UseCaseId("chat")
        val target = ConsoleInferenceTarget(applicationId, useCaseId, "Local chat")
        val requestId = RequestId("reasoning-console-request")
        val client = ReasoningConsoleClient(requestId)
        val control = LocalLlmConsoleInferenceControl(
            client = client,
            targets = listOf(target),
            requestIdFactory = ConsoleInferenceRequestIdFactory { requestId },
        )

        val result = control.start(
            ConsoleInferenceRequest(
                targetId = target.id,
                prompt = "Explain briefly",
                maxOutputTokens = 32,
                temperature = 0f,
                seed = 1,
            ),
            ConsoleInferenceListener {},
        )

        assertTrue(result.success)
        val state = control.snapshot()
        assertEquals(ConsoleInferencePhase.COMPLETED, state.phase)
        assertEquals("analysis", state.reasoningOutput)
        assertEquals("final answer", state.answerOutput)
        assertEquals("analysis</think>\n\nfinal answer", state.output)
        assertEquals(9L, state.metrics?.timeToFirstAnswerMs)
        assertFalse(state.outputTruncated)
        assertTrue(client.sessionClosed)
    }
}

private class ReasoningConsoleClient(private val requestId: RequestId) : LocalLlmClient {
    private val sessionId = SessionId("reasoning-console-session")
    private val digest = ModelDigest("d".repeat(64))
    var sessionClosed = false

    override fun runtimeSnapshot(): RuntimeSnapshot = RuntimeSnapshot(
        state = RuntimeState.READY,
        loadedModel = digest,
        activeSessions = if (sessionClosed) 0 else 1,
        queuedRequests = 0,
    )

    override fun prepare(applicationId: ApplicationId, useCaseId: UseCaseId): PrepareResult = PrepareResult(
        ready = true,
        modelDigest = digest,
        detail = "ready",
    )

    override fun createSession(applicationId: ApplicationId, useCaseId: UseCaseId): SessionId = sessionId

    override fun generate(request: GenerationRequest, listener: GenerationListener): GenerationHandle {
        listener.onEvent(GenerationEvent.Started(request.requestId, digest))
        listener.onEvent(
            GenerationEvent.TextDelta(
                requestId = request.requestId,
                text = "analysis",
                generatedTokens = 4,
                contentType = GenerationContentType.REASONING,
            ),
        )
        listener.onEvent(
            GenerationEvent.TextDelta(
                requestId = request.requestId,
                text = "final answer",
                generatedTokens = 7,
                contentType = GenerationContentType.ANSWER,
            ),
        )
        listener.onEvent(
            GenerationEvent.Completed(
                requestId = request.requestId,
                output = "analysis</think>\n\nfinal answer",
                reasoningOutput = "analysis",
                answerOutput = "final answer",
                metrics = GenerationMetrics(
                    queueMs = 1,
                    modelLoadMs = null,
                    timeToFirstTokenMs = 2,
                    totalMs = 12,
                    inputTokens = 4,
                    outputTokens = 7,
                    decodeTokensPerSecond = 700.0,
                    timeToFirstAnswerMs = 9,
                    reasoningTokens = 4,
                    answerTokens = 3,
                ),
            ),
        )
        return object : GenerationHandle {
            override val requestId: RequestId = this@ReasoningConsoleClient.requestId
            override fun cancel() = Unit
        }
    }

    override fun closeSession(sessionId: SessionId) {
        sessionClosed = true
    }
}
