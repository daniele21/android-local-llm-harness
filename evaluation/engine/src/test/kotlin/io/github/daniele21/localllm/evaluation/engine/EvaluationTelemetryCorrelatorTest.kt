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
                decodeTokensPerSecond = 18.5,
            ),
        )

        val metrics = TelemetryRepositoryEvaluationCorrelator(repository).metrics(expectedRequestId)

        assertEquals(31L, metrics.ttftMs)
        assertEquals(120L, metrics.totalDurationMs)
        assertEquals(40, metrics.promptTokens)
        assertEquals(12, metrics.completionTokens)
        assertEquals(2_000.0, metrics.prefillTokensPerSecond ?: -1.0, 0.0)
        assertEquals(18.5, metrics.decodeTokensPerSecond ?: -1.0, 0.0)
        assertNull(metrics.processPssBytes)
        assertNull(metrics.availableMemoryBytes)
        assertNull(metrics.thermalStatus)
    }

    @Test
    fun `missing request telemetry remains unavailable instead of fabricated`() {
        val metrics = TelemetryRepositoryEvaluationCorrelator(repositoryWith(null)).metrics(RequestId("missing"))

        assertNull(metrics.ttftMs)
        assertNull(metrics.totalDurationMs)
        assertNull(metrics.promptTokens)
        assertNull(metrics.completionTokens)
        assertNull(metrics.prefillTokensPerSecond)
        assertNull(metrics.decodeTokensPerSecond)
        assertNull(metrics.processPssBytes)
        assertNull(metrics.availableMemoryBytes)
        assertNull(metrics.thermalStatus)
    }

    @Test
    fun `zero prefill duration does not create synthetic throughput`() {
        val requestId = RequestId("request-2")
        val metrics = TelemetryRepositoryEvaluationCorrelator(
            repositoryWith(run(requestId = requestId, inputTokens = 10, prefillMs = 0L)),
        ).metrics(requestId)

        assertNull(metrics.prefillTokensPerSecond)
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
    )
}
