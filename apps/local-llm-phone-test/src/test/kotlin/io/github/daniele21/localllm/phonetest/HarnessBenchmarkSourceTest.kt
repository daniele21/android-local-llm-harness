package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.ModelLoadKind
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.observability.BenchmarkBaseline
import io.github.daniele21.localllm.observability.BenchmarkExecutionIdentity
import io.github.daniele21.localllm.observability.BenchmarkKey
import io.github.daniele21.localllm.observability.GenerationRunRecord
import io.github.daniele21.localllm.observability.RunStatus
import io.github.daniele21.localllm.observability.store.InMemoryTelemetryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessBenchmarkSourceTest {
    private val digest = ModelDigest("a".repeat(64))
    private val model = ImportedPhoneModel(digest, "private-model.gguf", 100, "qwen35", "Q4_K_M")

    @Test
    fun `captures separate cold and warm baselines after enough samples`() {
        val repository = InMemoryTelemetryRepository(maxRuns = 100, maxLogs = 10)
        repeat(5) { index ->
            repository.recordRun(run(index, ModelLoadKind.COLD, completedAt = 100L + index))
            repository.recordRun(run(index + 10, ModelLoadKind.WARM, completedAt = 200L + index))
        }
        val source = HarnessBenchmarkSource(repository) { model }

        val state = source.captureEligible()

        assertEquals(2, state.baselines.size)
        assertEquals(setOf("COLD", "WARM"), state.baselines.map { it.loadKind }.toSet())
        assertTrue(state.captureDetail.orEmpty().contains("Captured 2"))
    }

    @Test
    fun `reports insufficient samples without fabricating a baseline`() {
        val repository = InMemoryTelemetryRepository(maxRuns = 100, maxLogs = 10)
        repository.recordRun(run(1, ModelLoadKind.WARM, completedAt = 100))
        val source = HarnessBenchmarkSource(repository) { model }

        val state = source.captureEligible()

        assertTrue(state.baselines.isEmpty())
        assertTrue(state.captureDetail.orEmpty().contains("at least 5"))
    }

    @Test
    fun `baseline presentation excludes model file name and full digest`() {
        val repository = InMemoryTelemetryRepository(maxRuns = 100, maxLogs = 10)
        repeat(5) { repository.recordRun(run(it, ModelLoadKind.COLD, completedAt = 100L + it)) }
        val source = HarnessBenchmarkSource(repository) { model }

        val visible = source.captureEligible().baselines.joinToString()

        assertTrue(!visible.contains(model.fileName))
        assertTrue(!visible.contains(model.digest.sha256))
    }

    private fun run(index: Int, loadKind: ModelLoadKind, completedAt: Long) = GenerationRunRecord(
        requestId = RequestId("request-$index-${loadKind.name}"),
        applicationId = ApplicationId("play-internal-phone-test"),
        useCaseId = UseCaseId("manual-inference-playground"),
        modelDigest = digest,
        startedAtEpochMs = completedAt - 50,
        completedAtEpochMs = completedAt,
        status = RunStatus.COMPLETED,
        queueMs = 1,
        modelLoadMs = if (loadKind == ModelLoadKind.COLD) 10 else null,
        timeToFirstTokenMs = 20L + index,
        totalMs = 50L + index,
        inputTokens = 4,
        outputTokens = 8,
        decodeTokensPerSecond = 10.0 + index,
        errorCode = null,
        modelLoadKind = loadKind,
    )

    @Test
    fun `exposes retained history separately from active baselines`() {
        val repository = InMemoryTelemetryRepository(maxRuns = 100, maxLogs = 10)
        val key = BenchmarkKey(
            applicationId = ApplicationId("play-internal-phone-test"),
            useCaseId = UseCaseId("manual-inference-playground"),
            modelDigest = digest,
            modelLoadKind = ModelLoadKind.WARM,
            executionIdentity = BenchmarkExecutionIdentity.fromFingerprint("f".repeat(64)),
        )
        repository.saveBenchmarkBaseline(baseline(key, 10L, 5))
        repository.saveBenchmarkBaseline(baseline(key, 20L, 6))

        val state = HarnessBenchmarkSource(repository) { model }.snapshot()

        assertEquals(1, state.baselines.size)
        assertEquals(2, state.history.size)
        assertTrue(state.history.first().active)
        assertFalse(state.history.last().active)
    }

    private fun baseline(key: BenchmarkKey, capturedAt: Long, samples: Int) = BenchmarkBaseline(
        key = key,
        capturedAtEpochMs = capturedAt,
        sampleCount = samples,
        medianTimeToFirstTokenMs = 20.0,
        p95TimeToFirstTokenMs = 25.0,
        medianTotalMs = 50.0,
        p95TotalMs = 60.0,
        medianDecodeTokensPerSecond = 12.0,
    )
}
