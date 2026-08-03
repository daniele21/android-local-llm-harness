package io.github.daniele21.localllm.observability.health

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.GenerationEvent
import io.github.daniele21.localllm.contracts.GenerationHandle
import io.github.daniele21.localllm.contracts.GenerationListener
import io.github.daniele21.localllm.contracts.GenerationMetrics
import io.github.daniele21.localllm.contracts.GenerationRequest
import io.github.daniele21.localllm.contracts.LocalLlmClient
import io.github.daniele21.localllm.contracts.LocalLlmError
import io.github.daniele21.localllm.contracts.PrepareResult
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.RuntimeSnapshot
import io.github.daniele21.localllm.contracts.RuntimeState
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.observability.HealthStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationSanityHealthCheckTest {
    private val applicationId = ApplicationId("app")
    private val useCaseId = UseCaseId("sanity")
    private val requestId = RequestId("sanity-request")

    @Test
    fun `passes when generation contains expected output and closes session`() {
        val client = FakeClient { request ->
            listOf(completed(request, "prefix LOCAL_LLM_OK suffix"))
        }
        val check = check(client)

        val result = check.evaluate()

        assertEquals(HealthStatus.PASS, result.status)
        assertEquals("generation-sanity:app:sanity", check.id)
        assertEquals("health prompt", client.lastRequest?.input)
        assertEquals(16, client.lastRequest?.overrides?.maxOutputTokens)
        assertEquals(0f, client.lastRequest?.overrides?.temperature)
        assertEquals(0L, client.lastRequest?.overrides?.seed)
        assertTrue(client.sessionClosed)
    }

    @Test
    fun `supports non empty forbidden marker and regex assertions`() {
        val cases = listOf(
            AssertionCase(
                output = "generated response",
                expectedOutput = "",
                outputMatch = SanityOutputMatch.NON_EMPTY,
            ),
            AssertionCase(
                output = "safe generated response",
                expectedOutput = "FORBIDDEN",
                outputMatch = SanityOutputMatch.NOT_CONTAINS,
            ),
            AssertionCase(
                output = "local_llm_ok:42",
                expectedOutput = "LOCAL_LLM_OK:[0-9]+",
                outputMatch = SanityOutputMatch.MATCHES_REGEX,
            ),
        )

        cases.forEach { case ->
            val client = FakeClient { request ->
                listOf(completed(request, case.output))
            }
            val result = check(
                client = client,
                expectedOutput = case.expectedOutput,
                outputMatch = case.outputMatch,
            ).evaluate()

            assertEquals(HealthStatus.PASS, result.status)
            assertTrue(client.sessionClosed)
        }
    }

    @Test
    fun `fails forbidden marker assertion without exposing generated output`() {
        val client = FakeClient { request ->
            listOf(completed(request, "private FORBIDDEN generated text"))
        }

        val result = check(
            client = client,
            expectedOutput = "FORBIDDEN",
            outputMatch = SanityOutputMatch.NOT_CONTAINS,
        ).evaluate()

        assertEquals(HealthStatus.FAIL, result.status)
        assertFalse("private" in result.detail)
        assertFalse("FORBIDDEN" in result.detail)
    }

    @Test
    fun `rejects invalid regex configuration`() {
        val failure = runCatching {
            GenerationSanitySpec(
                applicationId = applicationId,
                useCaseId = useCaseId,
                prompt = "health prompt",
                expectedOutput = "[",
                outputMatch = SanityOutputMatch.MATCHES_REGEX,
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun `fails without exposing generated output when output does not match`() {
        val client = FakeClient { request ->
            listOf(completed(request, "private generated text"))
        }

        val result = check(client).evaluate()

        assertEquals(HealthStatus.FAIL, result.status)
        assertFalse("private generated text" in result.detail)
        assertTrue(client.sessionClosed)
    }

    @Test
    fun `fails with error code only when runtime generation fails`() {
        val client = FakeClient { request ->
            listOf(
                GenerationEvent.Failed(
                    requestId = request.requestId,
                    error = LocalLlmError.NativeRuntime("secret prompt and path"),
                ),
            )
        }

        val result = check(client).evaluate()

        assertEquals(HealthStatus.FAIL, result.status)
        assertTrue("NATIVE_RUNTIME" in result.detail)
        assertFalse("secret" in result.detail)
        assertTrue(client.sessionClosed)
    }

    @Test
    fun `cancels timed out generation and closes session`() {
        val client = FakeClient { emptyList() }
        val result = check(client, timeoutMs = 1L).evaluate()

        assertEquals(HealthStatus.FAIL, result.status)
        assertTrue(client.lastHandle?.cancelled == true)
        assertTrue(client.sessionClosed)
    }

    @Test
    fun `stops before session creation when model preparation fails`() {
        val client = FakeClient(
            prepared = false,
            events = { error("Generation must not run") },
        )

        val result = check(client).evaluate()

        assertEquals(HealthStatus.FAIL, result.status)
        assertFalse(client.sessionCreated)
        assertFalse(client.sessionClosed)
    }

    @Test
    fun `reports cleanup failure after a successful generation`() {
        val client = FakeClient(
            closeFailure = true,
            events = { request -> listOf(completed(request, "LOCAL_LLM_OK")) },
        )

        val result = check(client).evaluate()

        assertEquals(HealthStatus.FAIL, result.status)
        assertEquals("Generation sanity session cleanup failed", result.detail)
    }

    private fun check(
        client: LocalLlmClient,
        timeoutMs: Long = 100L,
        expectedOutput: String = "LOCAL_LLM_OK",
        outputMatch: SanityOutputMatch = SanityOutputMatch.CONTAINS,
    ): GenerationSanityHealthCheck = GenerationSanityHealthCheck(
        client = client,
        spec = GenerationSanitySpec(
            applicationId = applicationId,
            useCaseId = useCaseId,
            prompt = "health prompt",
            expectedOutput = expectedOutput,
            outputMatch = outputMatch,
            timeoutMs = timeoutMs,
        ),
        requestIdFactory = SanityRequestIdFactory { requestId },
    )

    private fun completed(request: GenerationRequest, output: String): GenerationEvent.Completed = GenerationEvent.Completed(
        requestId = request.requestId,
        output = output,
        metrics = GenerationMetrics(
            queueMs = 0L,
            modelLoadMs = 0L,
            timeToFirstTokenMs = 0L,
            totalMs = 1L,
            inputTokens = 2,
            outputTokens = 1,
            decodeTokensPerSecond = 1.0,
        ),
    )

    private data class AssertionCase(val output: String, val expectedOutput: String, val outputMatch: SanityOutputMatch)

    private class FakeClient(
        private val prepared: Boolean = true,
        private val closeFailure: Boolean = false,
        private val events: (GenerationRequest) -> List<GenerationEvent>,
    ) : LocalLlmClient {
        private val sessionId = SessionId("sanity-session")
        var sessionCreated = false
        var sessionClosed = false
        var lastRequest: GenerationRequest? = null
        var lastHandle: RecordingHandle? = null

        override fun runtimeSnapshot(): RuntimeSnapshot = RuntimeSnapshot(
            state = RuntimeState.READY,
            loadedModel = null,
            activeSessions = 0,
            queuedRequests = 0,
        )

        override fun prepare(applicationId: ApplicationId, useCaseId: UseCaseId): PrepareResult = PrepareResult(
            ready = prepared,
            modelDigest = null,
            detail = if (prepared) "ready" else "private preparation failure",
        )

        override fun createSession(applicationId: ApplicationId, useCaseId: UseCaseId): SessionId {
            sessionCreated = true
            return sessionId
        }

        override fun generate(request: GenerationRequest, listener: GenerationListener): GenerationHandle {
            lastRequest = request
            val handle = RecordingHandle(request.requestId)
            lastHandle = handle
            events(request).forEach(listener::onEvent)
            return handle
        }

        override fun closeSession(sessionId: SessionId) {
            if (closeFailure) {
                error("private cleanup failure")
            }
            sessionClosed = true
        }
    }

    private class RecordingHandle(override val requestId: RequestId) : GenerationHandle {
        var cancelled = false

        override fun cancel() {
            cancelled = true
        }
    }
}
