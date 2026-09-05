package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.audit.InferenceAuditAdmission
import io.github.daniele21.localllm.audit.InferenceAuditExecutionIdentity
import io.github.daniele21.localllm.audit.InferenceAuditInput
import io.github.daniele21.localllm.audit.InferenceAuditMetrics
import io.github.daniele21.localllm.audit.InferenceAuditOrigin
import io.github.daniele21.localllm.audit.InferenceAuditOriginKind
import io.github.daniele21.localllm.audit.InferenceAuditPrepared
import io.github.daniele21.localllm.audit.InferenceAuditResult
import io.github.daniele21.localllm.audit.InferenceAuditStatus
import io.github.daniele21.localllm.audit.InferenceAuditTerminal
import io.github.daniele21.localllm.audit.InferenceAuditTerminalContent
import io.github.daniele21.localllm.audit.store.InMemoryInferenceAuditRepository
import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.ModelLoadKind
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.StopReason
import io.github.daniele21.localllm.contracts.UseCaseId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessInferenceActivitySourceTest {
    private val repository = InMemoryInferenceAuditRepository()
    private val source = HarnessInferenceActivitySource(repository)
    private val requestId = RequestId("redactguard-request")

    @Test
    fun `completed RedactGuard inference is presented with sensitive detail only on explicit lookup`() {
        val admission = admission(requestId, 1_000L)
        assertSuccess(repository.admit(admission))
        assertSuccess(
            repository.markPrepared(
                InferenceAuditPrepared(
                    requestId = requestId,
                    preparedAtEpochMs = 1_010L,
                    effectivePrompt = "system prompt\nuser secret",
                    execution = InferenceAuditExecutionIdentity(
                        modelDigest = ModelDigest("a".repeat(64)),
                        modelLoadKind = ModelLoadKind.WARM,
                        presetId = "qwen35-json",
                        presetVersion = 1,
                        backendId = "llama.cpp",
                    ),
                ),
            ),
        )
        assertSuccess(repository.markRunning(requestId, 1_020L))
        assertSuccess(
            repository.recordTerminal(
                InferenceAuditTerminal(
                    requestId = requestId,
                    status = InferenceAuditStatus.COMPLETED,
                    completedAtEpochMs = 1_050L,
                    content = InferenceAuditTerminalContent(
                        answerOutput = "redacted answer",
                        reasoningOutput = "reasoning",
                    ),
                    metrics = InferenceAuditMetrics(
                        queueMs = 2,
                        modelLoadMs = 3,
                        timeToFirstTokenMs = 4,
                        totalMs = 50,
                        inputTokens = 10,
                        outputTokens = 5,
                        decodeTokensPerSecond = 14.5,
                        modelLoadKind = ModelLoadKind.WARM,
                        stopReason = StopReason.END_OF_GENERATION,
                    ),
                ),
            ),
        )

        val list = source.snapshot()
        assertNull(list.errorCode)
        assertEquals(1, list.items.size)
        assertEquals("RedactGuard", list.items.single().applicationLabel)
        assertEquals("io.github.daniele21.redactguard", list.items.single().verifiedPackageName)
        assertEquals(InferenceAuditStatus.COMPLETED, list.items.single().status)
        assertEquals(50L, list.items.single().totalMs)

        val detail = source.detail(requestId.value)
        assertTrue(detail is InferenceActivityDetailResult.Available)
        val value = (detail as InferenceActivityDetailResult.Available).detail
        assertEquals("user secret", value.input)
        assertEquals("system prompt\nuser secret", value.effectivePrompt)
        assertEquals("redacted answer", value.answerOutput)
        assertEquals("reasoning", value.reasoningOutput)
        assertEquals("qwen35-json", value.presetId)
        assertEquals(14.5, value.decodeTokensPerSecond ?: 0.0, 0.0)
    }

    @Test
    fun `startup reconciliation marks stale accepted inference interrupted`() {
        assertSuccess(repository.admit(admission(RequestId("orphan"), 2_000L)))

        val state = source.reconcileInterrupted(nowEpochMs = 3_000L)

        assertNull(state.errorCode)
        assertEquals(1, state.interruptedRecords)
        val record = (repository.find(RequestId("orphan")) as InferenceAuditResult.Success).value
        assertEquals(InferenceAuditStatus.INTERRUPTED, record?.status)
        assertEquals("HOST_PROCESS_LOSS", record?.terminal?.terminalCode?.value)
    }

    private fun admission(id: RequestId, receivedAt: Long) = InferenceAuditAdmission(
        requestId = id,
        origin = InferenceAuditOrigin(
            kind = InferenceAuditOriginKind.EXTERNAL_CONSUMER,
            applicationId = HarnessSharedRuntimeBindings.redactGuardApplicationId,
            useCaseId = HarnessSharedRuntimeBindings.ombraUseCaseId,
            verifiedPackageName = HarnessSharedRuntimeBindings.REDACTGUARD_RELEASE_PACKAGE,
        ),
        receivedAtEpochMs = receivedAt,
        input = InferenceAuditInput.Text("user secret"),
    )

    private fun assertSuccess(result: InferenceAuditResult<Unit>) {
        assertTrue(result is InferenceAuditResult.Success)
    }
}
