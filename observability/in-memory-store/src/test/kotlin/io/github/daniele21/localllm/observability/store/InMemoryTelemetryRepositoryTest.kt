package io.github.daniele21.localllm.observability.store

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.ModelLoadKind
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.observability.BenchmarkBaseline
import io.github.daniele21.localllm.observability.BenchmarkExecutionIdentity
import io.github.daniele21.localllm.observability.BenchmarkKey
import io.github.daniele21.localllm.observability.TelemetryRetentionPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class InMemoryTelemetryRepositoryTest {
    @Test
    fun `retains immutable benchmark history while replacing active baseline`() {
        val repository = InMemoryTelemetryRepository(
            TelemetryRetentionPolicy(maxBenchmarkBaselines = 2),
        )
        val first = baseline(10L, 5)
        val second = baseline(20L, 6)
        val third = baseline(30L, 7)

        repository.saveBenchmarkBaseline(first)
        repository.saveBenchmarkBaseline(second)
        repository.saveBenchmarkBaseline(third)

        assertEquals(listOf(third), repository.benchmarkBaselines())
        assertEquals(listOf(third, second), repository.benchmarkBaselineHistory())
    }

    private fun baseline(capturedAt: Long, samples: Int) = BenchmarkBaseline(
        key = BenchmarkKey(
            applicationId = ApplicationId("app"),
            useCaseId = UseCaseId("use-case"),
            modelDigest = ModelDigest("a".repeat(64)),
            modelLoadKind = ModelLoadKind.WARM,
            executionIdentity = BenchmarkExecutionIdentity.fromFingerprint("b".repeat(64)),
        ),
        capturedAtEpochMs = capturedAt,
        sampleCount = samples,
        medianTimeToFirstTokenMs = 1.0,
        p95TimeToFirstTokenMs = 2.0,
        medianTotalMs = 3.0,
        p95TotalMs = 4.0,
        medianDecodeTokensPerSecond = 5.0,
    )
}
