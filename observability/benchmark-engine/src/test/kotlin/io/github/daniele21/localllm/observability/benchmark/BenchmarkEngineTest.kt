@file:Suppress("LongParameterList")

package io.github.daniele21.localllm.observability.benchmark

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.ModelLoadKind
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.SeedPolicyType
import io.github.daniele21.localllm.contracts.ThinkingMode
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.observability.BenchmarkKey
import io.github.daniele21.localllm.observability.GenerationRunRecord
import io.github.daniele21.localllm.observability.HealthStatus
import io.github.daniele21.localllm.observability.RunStatus
import io.github.daniele21.localllm.observability.store.InMemoryTelemetryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchmarkEngineTest {
    private val applicationId = ApplicationId("app")
    private val useCaseId = UseCaseId("assistant")
    private val digest = ModelDigest("a".repeat(64))
    private val warmKey by lazy { BenchmarkKey.fromRun(run("identity", 10, 100, 50.0, 1L)) }
    private val policy = BenchmarkPolicy(
        baselineWindowSize = 5,
        comparisonWindowSize = 3,
        minimumBaselineSamples = 5,
        minimumComparisonSamples = 3,
        maxMedianTimeToFirstTokenRatio = 1.20,
        maxP95TotalRatio = 1.20,
        minMedianDecodeThroughputRatio = 0.85,
    )

    @Test
    fun `captures deterministic median and p95 baseline`() {
        val repository = InMemoryTelemetryRepository()
        listOf(10L, 20L, 30L, 40L, 50L).forEachIndexed { index, value ->
            repository.recordRun(run("baseline-$index", value, value * 10, 100.0 - index, index.toLong() + 1))
        }

        val result = BenchmarkBaselineRecorder(repository, policy, BenchmarkEpochClock { 100L }).capture(warmKey)
        val baseline = (result as BenchmarkCaptureResult.Captured).baseline

        assertEquals(5, baseline.sampleCount)
        assertEquals(30.0, baseline.medianTimeToFirstTokenMs)
        assertEquals(50.0, baseline.p95TimeToFirstTokenMs)
        assertEquals(300.0, baseline.medianTotalMs)
        assertEquals(500.0, baseline.p95TotalMs)
        assertEquals(98.0, baseline.medianDecodeTokensPerSecond)
        assertEquals(listOf(baseline), repository.benchmarkBaselines())
        assertEquals(listOf(baseline), repository.benchmarkBaselineHistory())
    }

    @Test
    fun `does not mix cold runs into a warm baseline`() {
        val repository = InMemoryTelemetryRepository()
        repeat(4) { index -> repository.recordRun(run("warm-$index", 10, 100, 50.0, index.toLong() + 1)) }
        repository.recordRun(
            run("cold", 999, 999, 1.0, 5L, loadKind = ModelLoadKind.COLD),
        )

        val result = BenchmarkBaselineRecorder(repository, policy).capture(warmKey)

        assertEquals(BenchmarkCaptureResult.InsufficientSamples(4, 5), result)
    }

    @Test
    fun `does not mix different execution identities`() {
        val repository = InMemoryTelemetryRepository()
        repeat(4) { index -> repository.recordRun(run("matching-$index", 10, 100, 50.0, index.toLong() + 1)) }
        repository.recordRun(
            run("other-context", 10, 100, 50.0, 5L, contextSize = 4_096),
        )
        repository.recordRun(
            run("other-thinking", 10, 100, 50.0, 6L, thinkingMode = ThinkingMode.ENABLED),
        )

        val result = BenchmarkBaselineRecorder(repository, policy).capture(warmKey)

        assertEquals(BenchmarkCaptureResult.InsufficientSamples(4, 5), result)
        assertFalse(warmKey.matches(run("mismatch", 10, 100, 50.0, 7L, contextSize = 4_096)))
        assertTrue(warmKey.stableId.endsWith(warmKey.executionIdentity.fingerprint))
    }

    @Test
    fun `passes when post baseline metrics stay within policy`() {
        val repository = baselineRepository()
        listOf(
            run("current-1", 105, 1_050, 92.0, 101),
            run("current-2", 110, 1_100, 90.0, 102),
            run("current-3", 115, 1_150, 88.0, 103),
        ).forEach(repository::recordRun)

        val result = BenchmarkRegressionHealthCheck(repository, warmKey, policy).evaluate()
        val comparison = BenchmarkComparisonEvaluator(policy).compare(
            key = warmKey,
            baseline = repository.benchmarkBaselines().single(),
            runs = repository.recentRuns(),
        )

        assertEquals(HealthStatus.PASS, result.status)
        assertTrue("3 comparable metric" in result.detail)
        assertEquals(HealthStatus.PASS, comparison.status)
        assertTrue(comparison.comparisonReady)
        assertEquals(3, comparison.availableSamples)
        assertEquals(3, comparison.metrics.size)
        assertEquals(
            1.1,
            comparison.metrics.single { it.metric == BenchmarkMetric.MEDIAN_TIME_TO_FIRST_TOKEN }.ratio!!,
            0.0001,
        )
        assertFalse(comparison.metrics.any(BenchmarkMetricComparison::regressed))
    }

    @Test
    fun `fails and names regressed metrics without exposing digest`() {
        val repository = baselineRepository()
        listOf(
            run("current-1", 140, 1_400, 70.0, 101),
            run("current-2", 150, 1_500, 69.0, 102),
            run("current-3", 160, 1_600, 68.0, 103),
        ).forEach(repository::recordRun)

        val result = BenchmarkRegressionHealthCheck(repository, warmKey, policy).evaluate()
        val comparison = BenchmarkComparisonEvaluator(policy).compare(
            key = warmKey,
            baseline = repository.benchmarkBaselines().single(),
            runs = repository.recentRuns(),
        )

        assertEquals(HealthStatus.FAIL, result.status)
        assertTrue("median TTFT" in result.detail)
        assertTrue("p95 total latency" in result.detail)
        assertTrue("median decode throughput" in result.detail)
        assertTrue(digest.sha256 !in result.detail)
        assertEquals(3, comparison.metrics.count(BenchmarkMetricComparison::regressed))
    }

    @Test
    fun `keeps partial comparison metrics as a non actionable preview`() {
        val repository = baselineRepository()
        repository.recordRun(run("current", 130, 1_300, 80.0, 101))

        val comparison = BenchmarkComparisonEvaluator(policy).compare(
            key = warmKey,
            baseline = repository.benchmarkBaselines().single(),
            runs = repository.recentRuns(),
        )

        assertEquals(HealthStatus.WARN, comparison.status)
        assertFalse(comparison.comparisonReady)
        assertEquals(1, comparison.availableSamples)
        assertNotNull(comparison.current)
        assertTrue(comparison.metrics.all(BenchmarkMetricComparison::comparable))
    }

    @Test
    fun `warns when a baseline or post baseline window is missing`() {
        val empty = InMemoryTelemetryRepository()
        assertEquals(
            HealthStatus.WARN,
            BenchmarkRegressionHealthCheck(empty, warmKey, policy).evaluate().status,
        )

        val baselineOnly = baselineRepository()
        assertEquals(
            HealthStatus.WARN,
            BenchmarkRegressionHealthCheck(baselineOnly, warmKey, policy).evaluate().status,
        )
    }

    private fun baselineRepository(): InMemoryTelemetryRepository = InMemoryTelemetryRepository().also { repository ->
        repeat(5) { index ->
            repository.recordRun(run("baseline-$index", 100, 1_000, 100.0, index.toLong() + 1))
        }
        BenchmarkBaselineRecorder(repository, policy, BenchmarkEpochClock { 100L }).capture(warmKey)
    }

    private fun run(
        id: String,
        ttft: Long,
        total: Long,
        throughput: Double,
        completedAt: Long,
        loadKind: ModelLoadKind = ModelLoadKind.WARM,
        contextSize: Int = 2_048,
        thinkingMode: ThinkingMode = ThinkingMode.DISABLED,
    ): GenerationRunRecord = GenerationRunRecord(
        requestId = RequestId(id),
        applicationId = applicationId,
        useCaseId = useCaseId,
        modelDigest = digest,
        startedAtEpochMs = completedAt - 1,
        completedAtEpochMs = completedAt,
        status = RunStatus.COMPLETED,
        queueMs = 1,
        modelLoadMs = null,
        timeToFirstTokenMs = ttft,
        totalMs = total,
        inputTokens = 10,
        outputTokens = 10,
        decodeTokensPerSecond = throughput,
        errorCode = null,
        modelLoadKind = loadKind,
        presetId = InferencePresetId("qwen35-text-quality"),
        presetVersion = 1,
        temperature = 1.0f,
        topP = 1.0f,
        topK = 20,
        minP = 0.0f,
        presencePenalty = 2.0f,
        thinkingMode = thinkingMode,
        repeatPenalty = 1.0f,
        repeatLastN = 64,
        seedPolicy = SeedPolicyType.FIXED,
        effectiveSeed = 20260307L,
        maxOutputTokens = 64,
        contextSize = contextSize,
        promptTokenCount = 10,
        chatTemplateId = "qwen35",
        systemPromptVersion = "benchmark-v1",
    )
}
