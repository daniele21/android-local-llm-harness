package io.github.daniele21.localllm.audit.store

import io.github.daniele21.localllm.audit.InferenceAuditAdmission
import io.github.daniele21.localllm.audit.InferenceAuditExecutionIdentity
import io.github.daniele21.localllm.audit.InferenceAuditFailureCode
import io.github.daniele21.localllm.audit.InferenceAuditInput
import io.github.daniele21.localllm.audit.InferenceAuditMetrics
import io.github.daniele21.localllm.audit.InferenceAuditOrigin
import io.github.daniele21.localllm.audit.InferenceAuditOriginKind
import io.github.daniele21.localllm.audit.InferenceAuditPrepared
import io.github.daniele21.localllm.audit.InferenceAuditQuery
import io.github.daniele21.localllm.audit.InferenceAuditResult
import io.github.daniele21.localllm.audit.InferenceAuditRetentionPolicy
import io.github.daniele21.localllm.audit.InferenceAuditStatus
import io.github.daniele21.localllm.audit.InferenceAuditTerminal
import io.github.daniele21.localllm.audit.InferenceAuditTerminalCode
import io.github.daniele21.localllm.audit.InferenceAuditTerminalContent
import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.ModelLoadKind
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.StopReason
import io.github.daniele21.localllm.contracts.UseCaseId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryInferenceAuditRepositoryTest {
    @Test
    fun `lifecycle is durable within repository and queryable as summary and detail`() {
        val repository = InMemoryInferenceAuditRepository()
        val requestId = RequestId("request-1")

        assertSuccess(repository.admit(admission(requestId, 100)))
        assertSuccess(repository.markPrepared(prepared(requestId, 110)))
        assertSuccess(repository.markRunning(requestId, 120))
        assertSuccess(repository.recordTerminal(completed(requestId, 200)))

        val detail = successValue(repository.find(requestId))
        assertNotNull(detail)
        assertEquals(InferenceAuditStatus.COMPLETED, detail?.status)
        assertEquals("answer", detail?.terminal?.content?.answerOutput)

        val summaries = successValue(repository.recent(InferenceAuditQuery(limit = 10)))
        assertEquals(1, summaries.size)
        assertEquals(requestId, summaries.single().requestId)
        assertEquals(100L, summaries.single().totalMs)
    }

    @Test
    fun `exact lifecycle retries are idempotent but conflicting transitions fail`() {
        val repository = InMemoryInferenceAuditRepository()
        val requestId = RequestId("request-1")
        val admission = admission(requestId, 100)
        val prepared = prepared(requestId, 110)

        assertSuccess(repository.admit(admission))
        assertSuccess(repository.admit(admission))
        assertSuccess(repository.markPrepared(prepared))
        assertSuccess(repository.markPrepared(prepared))
        assertSuccess(repository.markRunning(requestId, 120))
        assertSuccess(repository.markRunning(requestId, 120))

        val failure = repository.markRunning(requestId, 121)
        assertEquals(InferenceAuditFailureCode.INVALID_STATE, (failure as InferenceAuditResult.Failure).code)
    }

    @Test
    fun `completed transition requires running while failure may terminate admitted work`() {
        val repository = InMemoryInferenceAuditRepository()
        val completedId = RequestId("completed")
        val failedId = RequestId("failed")
        assertSuccess(repository.admit(admission(completedId, 100)))
        assertSuccess(repository.admit(admission(failedId, 101)))

        val invalidCompletion = repository.recordTerminal(completed(completedId, 200))
        assertEquals(InferenceAuditFailureCode.INVALID_STATE, (invalidCompletion as InferenceAuditResult.Failure).code)

        assertSuccess(
            repository.recordTerminal(
                InferenceAuditTerminal(
                    requestId = failedId,
                    status = InferenceAuditStatus.FAILED,
                    completedAtEpochMs = 150,
                    terminalCode = InferenceAuditTerminalCode("MODEL_UNAVAILABLE"),
                ),
            ),
        )
        assertEquals(InferenceAuditStatus.FAILED, successValue(repository.find(failedId))?.status)
    }

    @Test
    fun `retention evicts oldest terminal history but protects active records`() {
        val repository = InMemoryInferenceAuditRepository(
            InferenceAuditRetentionPolicy(
                maxRecords = 2,
                maxAgeMs = 10_000,
                maxEncryptedContentBytes = 1_000_000,
            ),
        )
        val first = RequestId("first")
        val second = RequestId("second")
        val active = RequestId("active")

        complete(repository, first, 100, 200)
        complete(repository, second, 300, 400)
        assertSuccess(repository.admit(admission(active, 500)))

        assertEquals(null, successValue(repository.find(first)))
        assertNotNull(successValue(repository.find(second)))
        assertNotNull(successValue(repository.find(active)))
        assertEquals(listOf(active), successValue(repository.nonTerminal()).map { it.requestId })
    }

    @Test
    fun `clear history removes terminal rows only`() {
        val repository = InMemoryInferenceAuditRepository()
        val terminal = RequestId("terminal")
        val active = RequestId("active")
        complete(repository, terminal, 100, 200)
        assertSuccess(repository.admit(admission(active, 300)))

        assertEquals(1, successValue(repository.clearTerminalHistory()))
        assertEquals(null, successValue(repository.find(terminal)))
        assertNotNull(successValue(repository.find(active)))
    }

    private fun complete(repository: InMemoryInferenceAuditRepository, requestId: RequestId, admittedAt: Long, completedAt: Long) {
        assertSuccess(repository.admit(admission(requestId, admittedAt)))
        assertSuccess(repository.markPrepared(prepared(requestId, admittedAt + 10)))
        assertSuccess(repository.markRunning(requestId, admittedAt + 20))
        assertSuccess(repository.recordTerminal(completed(requestId, completedAt)))
    }

    private fun admission(requestId: RequestId, timestamp: Long) = InferenceAuditAdmission(
        requestId = requestId,
        origin = InferenceAuditOrigin(
            kind = InferenceAuditOriginKind.HARNEX_INTERNAL,
            applicationId = ApplicationId("harnex"),
            useCaseId = UseCaseId("playground"),
        ),
        receivedAtEpochMs = timestamp,
        input = InferenceAuditInput.Text("prompt-$requestId"),
    )

    private fun prepared(requestId: RequestId, timestamp: Long) = InferenceAuditPrepared(
        requestId = requestId,
        preparedAtEpochMs = timestamp,
        effectivePrompt = "effective prompt",
        execution = InferenceAuditExecutionIdentity(
            modelDigest = ModelDigest("a".repeat(64)),
            modelLoadKind = ModelLoadKind.WARM,
        ),
    )

    private fun completed(requestId: RequestId, timestamp: Long) = InferenceAuditTerminal(
        requestId = requestId,
        status = InferenceAuditStatus.COMPLETED,
        completedAtEpochMs = timestamp,
        content = InferenceAuditTerminalContent(answerOutput = "answer"),
        metrics = InferenceAuditMetrics(
            queueMs = 1,
            modelLoadMs = null,
            timeToFirstTokenMs = 10,
            totalMs = 100,
            inputTokens = 4,
            outputTokens = 2,
            decodeTokensPerSecond = 20.0,
            modelLoadKind = ModelLoadKind.WARM,
            stopReason = StopReason.END_OF_GENERATION,
        ),
    )

    private fun assertSuccess(result: InferenceAuditResult<Unit>) {
        assertTrue(result is InferenceAuditResult.Success)
    }

    private fun <T> successValue(result: InferenceAuditResult<T>): T = (result as InferenceAuditResult.Success).value
}
