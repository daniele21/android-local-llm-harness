package io.github.daniele21.localllm.observability

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.ModelLoadKind
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.UseCaseId
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BenchmarkExecutionIdentityTest {
    @Test
    fun `backend execution material participates in benchmark identity`() {
        val baseline = runRecord().copy(
            backendId = "llama.cpp",
            backendRevision = "revision-a",
            backendExecutionFingerprint = "1".repeat(64),
            effectivePlacement = ExecutionPlacementStatus.UNAVAILABLE,
        )
        val changedMaterial = baseline.copy(backendExecutionFingerprint = "2".repeat(64))

        assertNotEquals(
            BenchmarkExecutionIdentity.fromRun(baseline),
            BenchmarkExecutionIdentity.fromRun(changedMaterial),
        )
    }

    @Test
    fun `effective placement availability participates in benchmark identity`() {
        val unavailable = runRecord().copy(
            backendId = "llama.cpp",
            backendRevision = "revision-a",
            backendExecutionFingerprint = "3".repeat(64),
            effectivePlacement = ExecutionPlacementStatus.UNAVAILABLE,
        )
        val known = unavailable.copy(effectivePlacement = ExecutionPlacementStatus.KNOWN)

        assertNotEquals(
            BenchmarkExecutionIdentity.fromRun(unavailable),
            BenchmarkExecutionIdentity.fromRun(known),
        )
    }

    private fun runRecord(): GenerationRunRecord = GenerationRunRecord(
        requestId = RequestId("request-1"),
        applicationId = ApplicationId("app-1"),
        useCaseId = UseCaseId("use-case-1"),
        modelDigest = ModelDigest("a".repeat(64)),
        startedAtEpochMs = 1L,
        completedAtEpochMs = 2L,
        status = RunStatus.COMPLETED,
        queueMs = 1L,
        modelLoadMs = 1L,
        timeToFirstTokenMs = 1L,
        totalMs = 2L,
        inputTokens = 1,
        outputTokens = 1,
        decodeTokensPerSecond = 1.0,
        errorCode = null,
        modelLoadKind = ModelLoadKind.WARM,
        contextSize = 2_048,
        promptTokenCount = 32,
    )
}
