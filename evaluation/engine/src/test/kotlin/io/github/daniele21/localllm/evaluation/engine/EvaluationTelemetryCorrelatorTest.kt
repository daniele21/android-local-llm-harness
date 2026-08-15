package io.github.daniele21.localllm.evaluation.engine

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.observability.GenerationRunRecord
import io.github.daniele21.localllm.observability.NoOpTelemetryRepository
import io.github.daniele21.localllm.observability.RunStatus
import io.github.daniele21.localllm.observability.TelemetryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EvaluationTelemetryCorrelatorTest {
    @Test
    fun `maps only telemetry for the exact request identity`() {
        val expectedRequestId = RequestId("request-1")
        val repository = repositoryWith(
            run(
                requestId = expectedRequestId,
                ttftMs = 31L,
                totalMs = 120L,
                inputTokens = 40,
                outputTokens = 12,
                prefillMs = 20L,
                decodeMs = 70L,
                decodeTokensPerSecond = 18.5,
            ),
        )

        val metrics = TelemetryRepositoryEvaluationCorrelator(repository).metrics(expectedRequestId)

        assertEquals(31L, metrics.timeToFirstTokenMs)
        assertEquals(120L, metrics.totalMs)
        assertEquals(20L, metrics.prefillMs)
        assertEquals(70L, metrics.decodeMs)
        assertEquals(40, metrics.inputTokens)
        assertEquals(12, metrics.outputTokens)
        assertEquals(18.5, metrics.decodeTokensPerSecond ?: -1.0, 0.0)
        assertNull(metrics.processPssBytes)
        assertNull(metrics.availableMemoryBytes)
        assertNull(metrics.thermalStatus)
    }

    @Test
    fun `missing request telemetry remains unavailable instead of fabricated`() {
        val metrics = TelemetryRepositoryEvaluationCorrelator(repositoryWith(null)).metrics(RequestId("missing"))

        assertNull(metrics.timeToFirstTokenMs)
        assertNull(metrics.totalMs)
        assertNull(metrics.prefillMs)
        assertNull(metrics.decodeMs)
        assertNull(metrics.inputTokens)
        assertNull(metrics.outputTokens)
        assertNull(metrics.decodeTokensPerSecond)
        assertNull(metrics.processPssBytes)
        assertNull(metrics.availableMemoryBytes)
        assertNull(metrics.thermalStatus)
    }

    @Test
    fun `zero measured durations are preserved exactly`() {
        val requestId = RequestId("request-2")
        val metrics = TelemetryRepositoryEvaluationCorrelator(
            repositoryWith(run(requestId = requestId, prefillMs = 0L, decodeMs = 0L)),
        ).metrics(requestId)

        assertEquals(0L, metrics.prefillMs)
        assertEquals(0L, metrics.decodeMs)
    }

    private fun repositoryWith(run: GenerationRunRecord?): TelemetryRepository = object : TelemetryRepository by NoOpTelemetryRepository {
        override fun findRun(requestId: RequestId): GenerationRunRecord? = run?.takeIf { it.requestId == requestId }
    }

    private fun run(
        requestId: RequestId,
        ttftMs: Long? = null,
        totalMs: Long? = null,
        inputTokens: Int? = null,
        outputTokens: Int? = null,
        prefillMs: Long? = null,
        decodeMs: Long? = null,
        decodeTokensPerSecond: Double? = null,
    ) = GenerationRunRecord(
        requestId = requestId,
        applicationId = ApplicationId("evaluation"),
        useCaseId = UseCaseId("general-purpose"),
        modelDigest = ModelDigest("a".repeat(64)),
        startedAtEpochMs = 1L,
        completedAtEpochMs = 2L,
        status = RunStatus.COMPLETED,
        queueMs = 0L,
        modelLoadMs = 0L,
        timeToFirstTokenMs = ttftMs,
        totalMs = totalMs,
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        decodeTokensPerSecond = decodeTokensPerSecond,
        errorCode = null,
        prefillMs = prefillMs,
        decodeMs = decodeMs,
    )
}
