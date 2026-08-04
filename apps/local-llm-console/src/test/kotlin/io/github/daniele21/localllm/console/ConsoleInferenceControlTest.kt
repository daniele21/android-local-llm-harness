package io.github.daniele21.localllm.console

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.GenerationEvent
import io.github.daniele21.localllm.contracts.GenerationHandle
import io.github.daniele21.localllm.contracts.GenerationListener
import io.github.daniele21.localllm.contracts.GenerationMetrics
import io.github.daniele21.localllm.contracts.GenerationRequest
import io.github.daniele21.localllm.contracts.LocalLlmClient
import io.github.daniele21.localllm.contracts.LocalLlmError
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.ModelLoadKind
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

class ConsoleInferenceControlTest {
    private val applicationId = ApplicationId("app")
    private val useCaseId = UseCaseId("chat")
    private val target = ConsoleInferenceTarget(applicationId, useCaseId, "Local chat")
    private val requestId = RequestId("playground-request")
    private val modelDigest = ModelDigest("a".repeat(64))

    @Test
    fun `streams output completes metrics and closes the session`() {
        val client = FakeClient { request ->
            listOf(
                GenerationEvent.Queued(request.requestId, 1),
                GenerationEvent.Started(request.requestId, modelDigest),
                GenerationEvent.TextDelta(request.requestId, "hello ", 1),
                GenerationEvent.TextDelta(request.requestId, "world", 2),
                completed(request, "hello world"),
            )
        }
        val states = mutableListOf<ConsoleInferenceState>()
        val control = control(client)

        val outcome = control.start(
            request("private playground prompt"),
            ConsoleInferenceListener(states::add),
        )

        assertTrue(outcome.success)
        assertEquals(ConsoleInferencePhase.COMPLETED, control.snapshot().phase)
        assertEquals("hello world", control.snapshot().output)
        assertEquals(2, control.snapshot().generatedTokens)
        assertEquals(20.0, control.snapshot().metrics?.decodeTokensPerSecond ?: 0.0, 0.001)
        assertFalse(control.snapshot().sessionActive)
        assertTrue(client.sessionClosed)
        assertTrue(states.any { it.phase == ConsoleInferencePhase.GENERATING && it.output == "hello world" })
        assertFalse(control.snapshot().toString().contains("private playground prompt"))
    }

    @Test
    fun `cancels an active generation and closes after the cancelled terminal event`() {
        val client = FakeClient { emptyList() }
        val control = control(client)
        control.start(request("prompt"), ConsoleInferenceListener {})
        client.emit(GenerationEvent.Queued(requestId, 2))
        client.emit(GenerationEvent.Started(requestId, modelDigest))
        client.emit(GenerationEvent.TextDelta(requestId, "partial", 1))

        val cancellation = control.cancel()

        assertTrue(cancellation.success)
        assertTrue(client.handle.cancelled)
        assertTrue(control.snapshot().cancellationRequested)
        client.emit(
            GenerationEvent.Failed(
                requestId = requestId,
                error = LocalLlmError.Cancelled("private cancellation detail"),
            ),
        )
        assertEquals(ConsoleInferencePhase.CANCELLED, control.snapshot().phase)
        assertFalse(control.snapshot().sessionActive)
        assertTrue(client.sessionClosed)
        assertFalse(control.snapshot().toString().contains("private cancellation detail"))
    }

    @Test
    fun `preparation failure is fixed privacy safe and reaches the listener`() {
        val client = FakeClient(
            prepareReady = false,
            prepareDetail = "private model path",
            events = { error("Generation must not start") },
        )
        val states = mutableListOf<ConsoleInferenceState>()
        val control = control(client)

        val outcome = control.start(request("prompt"), ConsoleInferenceListener(states::add))

        assertFalse(outcome.success)
        assertEquals(ConsoleInferencePhase.FAILED, outcome.state.phase)
        assertEquals("Model preparation failed", outcome.state.detail)
        assertFalse(outcome.state.toString().contains("private model path"))
        assertTrue(states.any { it.phase == ConsoleInferencePhase.FAILED })
        assertFalse(client.sessionCreated)
    }

    @Test
    fun `cleanup failure overrides a successful terminal result without exposing the exception`() {
        val client = FakeClient(
            closeFailure = true,
            events = { request -> listOf(completed(request, "result")) },
        )
        val control = control(client)

        control.start(request("prompt"), ConsoleInferenceListener {})

        val state = control.snapshot()
        assertEquals(ConsoleInferencePhase.FAILED, state.phase)
        assertEquals("SESSION_CLEANUP_FAILED", state.errorCode)
        assertEquals("Inference session cleanup failed", state.detail)
        assertTrue(state.sessionActive)
        assertFalse(state.toString().contains("private cleanup failure"))
    }

    @Test
    fun `completed output is bounded and marks truncation`() {
        val output = "x".repeat(131_073)
        val client = FakeClient { request -> listOf(completed(request, output)) }
        val control = control(client)

        control.start(request("prompt"), ConsoleInferenceListener {})

        assertEquals(131_072, control.snapshot().output.length)
        assertTrue(control.snapshot().outputTruncated)
    }

    private fun control(client: LocalLlmClient) = LocalLlmConsoleInferenceControl(
        client = client,
        targets = listOf(target),
        source = "Embedded runtime",
        requestIdFactory = ConsoleInferenceRequestIdFactory { requestId },
    )

    private fun request(prompt: String) = ConsoleInferenceRequest(
        targetId = target.id,
        prompt = prompt,
        maxOutputTokens = 16,
        temperature = 0f,
        seed = 7L,
    )

    private fun completed(request: GenerationRequest, output: String): GenerationEvent.Completed = GenerationEvent.Completed(
        requestId = request.requestId,
        output = output,
        metrics = GenerationMetrics(
            queueMs = 1L,
            modelLoadMs = null,
            timeToFirstTokenMs = 2L,
            totalMs = 5L,
            inputTokens = 3,
            outputTokens = 2,
            decodeTokensPerSecond = 20.0,
            prefillMs = 1L,
            decodeMs = 2L,
            modelLoadKind = ModelLoadKind.WARM,
        ),
    )

    private class FakeClient(
        private val prepareReady: Boolean = true,
        private val prepareDetail: String = "ready",
        private val closeFailure: Boolean = false,
        private val events: (GenerationRequest) -> List<GenerationEvent>,
    ) : LocalLlmClient {
        private val sessionId = SessionId("playground-session")
        private var listener: GenerationListener? = null
        val handle = RecordingHandle(RequestId("playground-request"))
        var sessionCreated = false
        var sessionClosed = false

        override fun runtimeSnapshot(): RuntimeSnapshot = RuntimeSnapshot(
            state = RuntimeState.READY,
            loadedModel = null,
            activeSessions = if (sessionCreated && !sessionClosed) 1 else 0,
            queuedRequests = 0,
        )

        override fun prepare(applicationId: ApplicationId, useCaseId: UseCaseId): PrepareResult = PrepareResult(
            ready = prepareReady,
            modelDigest = null,
            detail = prepareDetail,
        )

        override fun createSession(applicationId: ApplicationId, useCaseId: UseCaseId): SessionId {
            sessionCreated = true
            return sessionId
        }

        override fun generate(request: GenerationRequest, listener: GenerationListener): GenerationHandle {
            this.listener = listener
            events(request).forEach(listener::onEvent)
            return handle
        }

        override fun closeSession(sessionId: SessionId) {
            if (closeFailure) error("private cleanup failure")
            sessionClosed = true
        }

        fun emit(event: GenerationEvent) {
            requireNotNull(listener).onEvent(event)
        }
    }

    private class RecordingHandle(override val requestId: RequestId) : GenerationHandle {
        var cancelled = false

        override fun cancel() {
            cancelled = true
        }
    }
}
