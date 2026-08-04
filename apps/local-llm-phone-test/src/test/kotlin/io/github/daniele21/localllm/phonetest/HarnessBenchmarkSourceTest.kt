package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.ModelLoadKind
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.observability.GenerationRunRecord
import io.github.daniele21.localllm.observability.RunStatus
import io.github.daniele21.localllm.observability.store.InMemoryTelemetryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessBenchmarkSourceTest {
    private val digest = ModelDigest("a".repeat(64))
    private val model = ImportedPhoneModel(digest, "private-model.gguf", 100, "qwen3", "Q4_K_M")

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
}
