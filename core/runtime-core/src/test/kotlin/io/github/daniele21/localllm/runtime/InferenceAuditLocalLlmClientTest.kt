package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.audit.InferenceAuditOrigin
import io.github.daniele21.localllm.audit.InferenceAuditOriginKind
import io.github.daniele21.localllm.audit.InferenceAuditResult
import io.github.daniele21.localllm.audit.InferenceAuditStatus
import io.github.daniele21.localllm.audit.store.InMemoryInferenceAuditRepository
import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ChatTemplateSource
import io.github.daniele21.localllm.contracts.EffectiveGenerationMetadata
import io.github.daniele21.localllm.contracts.GenerationEvent
import io.github.daniele21.localllm.contracts.GenerationHandle
import io.github.daniele21.localllm.contracts.GenerationInput
import io.github.daniele21.localllm.contracts.GenerationListener
import io.github.daniele21.localllm.contracts.GenerationMetrics
import io.github.daniele21.localllm.contracts.GenerationRequest
import io.github.daniele21.localllm.contracts.LocalLlmClient
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.ModelLoadKind
import io.github.daniele21.localllm.contracts.PrepareResult
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.RuntimeSnapshot
import io.github.daniele21.localllm.contracts.RuntimeState
import io.github.daniele21.localllm.contracts.SeedPolicyType
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.SessionOptions
import io.github.daniele21.localllm.contracts.StopReason
import io.github.daniele21.localllm.contracts.ThinkingMode
import io.github.daniele21.localllm.contracts.UseCaseId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InferenceAuditLocalLlmClientTest {
    private val applicationId = ApplicationId("consumer")
    private val useCaseId = UseCaseId("redaction")
    private val requestId = RequestId("request-1")
    private val sessionId = SessionId("session-1")
    private val digest = ModelDigest("a".repeat(64))

    @Test
    fun `completed generation is durably captured before completion is forwarded`() {
        val repository = InMemoryInferenceAuditRepository()
        val delegate = AsyncGenerationClient(::completedEvents)
        val clock = AtomicLong(1_000L)
        val client = client(repository, delegate, clock)
        val terminal = CountDownLatch(1)
        val forwarded = AtomicReference<GenerationEvent?>()

        client.generate(request(), GenerationListener { event ->
            if (event is GenerationEvent.Completed || event is GenerationEvent.Failed) {
                forwarded.set(event)
                terminal.countDown()
            }
        })
        delegate.startEvents.countDown()

        assertTrue(terminal.await(2, TimeUnit.SECONDS))
        assertTrue(forwarded.get() is GenerationEvent.Completed)
        val record = (repository.find(requestId) as InferenceAuditResult.Success).value
        assertNotNull(record)
        requireNotNull(record)
        assertEquals(InferenceAuditStatus.COMPLETED, record.status)
        assertEquals("io.redactguard", record.admission.origin.verifiedPackageName)
        assertEquals("secret input", (record.admission.input as io.github.daniele21.localllm.audit.InferenceAuditInput.Text).value)
        assertEquals("final answer", record.terminal?.content?.answerOutput)
        assertEquals("private reasoning", record.terminal?.content?.reasoningOutput)
        assertEquals(24L, record.terminal?.metrics?.totalMs)
        assertEquals(12.5, record.terminal?.metrics?.decodeTokensPerSecond ?: 0.0, 0.0)
    }

    @Test
    fun `closed audit repository rejects admission before delegate generation`() {
        val repository = InMemoryInferenceAuditRepository().also { it.close() }
        val delegate = AsyncGenerationClient(::completedEvents)
        val client = client(repository, delegate, AtomicLong(1_000L))

        val failure = runCatching {
            client.generate(request(), GenerationListener {})
        }.exceptionOrNull()

        assertTrue(failure is InferenceAuditClientException)
        assertEquals(InferenceAuditWritePhase.ADMISSION, (failure as InferenceAuditClientException).phase)
        assertFalse(delegate.generateCalled)
    }

    private fun client(
        repository: InMemoryInferenceAuditRepository,
        delegate: AsyncGenerationClient,
        clock: AtomicLong,
    ) = InferenceAuditLocalLlmClient(
        delegate = delegate,
        auditRepository = repository,
        originResolver = InferenceAuditOriginResolver { request ->
            InferenceAuditOrigin(
                kind = InferenceAuditOriginKind.EXTERNAL_CONSUMER,
                applicationId = request.applicationId,
                useCaseId = request.useCaseId,
                verifiedPackageName = "io.redactguard",
            )
        },
        epochClock = EpochClock { clock.getAndIncrement() },
    )

    private fun request() = GenerationRequest(
        requestId = requestId,
        sessionId = sessionId,
        applicationId = applicationId,
        useCaseId = useCaseId,
        input = GenerationInput.Text("secret input"),
    )

    private fun completedEvents(listener: GenerationListener) {
        listener.onEvent(GenerationEvent.Queued(requestId, 1))
        listener.onEvent(
            GenerationEvent.Prepared(
                requestId = requestId,
                modelDigest = digest,
                configuration = EffectiveGenerationMetadata(
                    preset = null,
                    temperature = 0f,
                    topP = 0.95f,
                    topK = 40,
                    repeatPenalty = 1f,
                    repeatLastN = 64,
                    requestedSeedPolicy = SeedPolicyType.RANDOM,
                    effectiveSeed = 1L,
                    maxOutputTokens = 32,
                    contextSize = 4_096,
                    promptTokenCount = 5,
                    chatTemplateId = "chatml",
                    chatTemplateSource = ChatTemplateSource.GGUF,
                    systemPromptVersion = null,
                    thinkingMode = ThinkingMode.DISABLED,
                ),
            ),
        )
        listener.onEvent(GenerationEvent.Started(requestId, digest))
        listener.onEvent(
            GenerationEvent.Completed(
                requestId = requestId,
                output = "private reasoningfinal answer",
                reasoningOutput = "private reasoning",
                answerOutput = "final answer",
                metrics = GenerationMetrics(
                    queueMs = 2,
                    modelLoadMs = 3,
                    timeToFirstTokenMs = 4,
                    totalMs = 24,
                    inputTokens = 5,
                    outputTokens = 6,
                    decodeTokensPerSecond = 12.5,
                    prefillMs = 7,
                    decodeMs = 8,
                    modelLoadKind = ModelLoadKind.WARM,
                    stopReason = StopReason.END_OF_GENERATION,
                    promptPlanningMs = 9,
                    contextCreationMs = 10,
                    timeToFirstAnswerMs = 11,
                    reasoningTokens = 2,
                    answerTokens = 4,
                ),
            ),
        )
    }

    private class AsyncGenerationClient(private val emit: (GenerationListener) -> Unit) : LocalLlmClient {
        val startEvents = CountDownLatch(1)
        var generateCalled: Boolean = false
            private set

        override fun runtimeSnapshot(): RuntimeSnapshot = RuntimeSnapshot(RuntimeState.READY, null, 1, 0)

        override fun prepare(applicationId: ApplicationId, useCaseId: UseCaseId): PrepareResult = PrepareResult(true, null, "ready")

        override fun createSession(applicationId: ApplicationId, useCaseId: UseCaseId): SessionId = SessionId("session-1")

        override fun createSession(applicationId: ApplicationId, useCaseId: UseCaseId, options: SessionOptions): SessionId =
            createSession(applicationId, useCaseId)

        override fun generate(request: GenerationRequest, listener: GenerationListener): GenerationHandle {
            generateCalled = true
            val handle = object : GenerationHandle {
                override val requestId: RequestId = request.requestId
                override fun cancel() = Unit
            }
            Thread {
                if (startEvents.await(2, TimeUnit.SECONDS)) emit(listener)
            }.apply { isDaemon = true }.start()
            return handle
        }

        override fun closeSession(sessionId: SessionId) = Unit
    }
}
