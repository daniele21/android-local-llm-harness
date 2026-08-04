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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessBenchmarkReadinessTest {
    private val digest = ModelDigest("c".repeat(64))
    private val model = ImportedPhoneModel(digest, "model.gguf", 100, "qwen3", "Q4_K_M")

    @Test
    fun `reports missing samples for each cold or warm key`() {
        val repository = InMemoryTelemetryRepository(maxRuns = 100, maxLogs = 10)
        repeat(2) { repository.recordRun(run(it, ModelLoadKind.WARM)) }

        val readiness = HarnessBenchmarkSource(repository) { model }.snapshot().readiness.single()

        assertEquals(2, readiness.baselineSamples)
        assertEquals(5, readiness.baselineRequired)
        assertFalse(readiness.captureReady)
        assertTrue(readiness.detail.contains("3 more"))
    }

    @Test
    fun `captures only the selected ready key`() {
        val repository = InMemoryTelemetryRepository(maxRuns = 100, maxLogs = 10)
        repeat(5) { index ->
            repository.recordRun(run(index, ModelLoadKind.COLD))
            repository.recordRun(run(index + 10, ModelLoadKind.WARM))
        }
        val source = HarnessBenchmarkSource(repository) { model }
        val initial = source.snapshot()
        val cold = initial.readiness.single { it.loadKind == "COLD" }

        val captured = source.capture(cold.stableId)

        assertEquals(1, captured.baselines.size)
        assertEquals("COLD", captured.baselines.single().loadKind)
        assertTrue(captured.readiness.single { it.loadKind == "COLD" }.baselineCaptured)
        assertFalse(captured.readiness.single { it.loadKind == "WARM" }.baselineCaptured)
        assertFalse(captured.toString().contains(digest.sha256))
    }

    private fun run(index: Int, loadKind: ModelLoadKind): GenerationRunRecord = GenerationRunRecord(
        requestId = RequestId("readiness-$index-${loadKind.name}"),
        applicationId = ApplicationId("play-internal-phone-test"),
        useCaseId = UseCaseId("manual-inference-playground"),
        modelDigest = digest,
        startedAtEpochMs = index.toLong(),
        completedAtEpochMs = 100L + index,
        status = RunStatus.COMPLETED,
        queueMs = 1,
        modelLoadMs = if (loadKind == ModelLoadKind.COLD) 10 else null,
        timeToFirstTokenMs = 20,
        totalMs = 50,
        inputTokens = 4,
        outputTokens = 8,
        decodeTokensPerSecond = 10.0,
        errorCode = null,
        modelLoadKind = loadKind,
    )
}
