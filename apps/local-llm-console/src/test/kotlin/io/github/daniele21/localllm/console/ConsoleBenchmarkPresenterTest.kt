package io.github.daniele21.localllm.console

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.ModelLoadKind
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.observability.BenchmarkBaseline
import io.github.daniele21.localllm.observability.BenchmarkKey
import io.github.daniele21.localllm.observability.GenerationRunRecord
import io.github.daniele21.localllm.observability.HealthStatus
import io.github.daniele21.localllm.observability.RunStatus
import io.github.daniele21.localllm.observability.benchmark.BenchmarkComparisonEvaluator
import io.github.daniele21.localllm.observability.benchmark.BenchmarkPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class ConsoleBenchmarkPresenterTest {
    private val presenter = ConsoleBenchmarkPresenter(ZoneOffset.UTC)
    private val key = BenchmarkKey(
        applicationId = ApplicationId("app"),
        useCaseId = UseCaseId("assistant"),
        modelDigest = ModelDigest("a".repeat(64)),
        modelLoadKind = ModelLoadKind.WARM,
    )
    private val policy = BenchmarkPolicy(
        baselineWindowSize = 5,
        comparisonWindowSize = 3,
        minimumBaselineSamples = 5,
        minimumComparisonSamples = 3,
    )

    @Test
    fun `renders regression details retained history and chronological metric charts`() {
        val previous = baseline(
            capturedAt = 50L,
            medianTtft = 90.0,
            p95Total = null,
            throughput = 110.0,
        )
        val active = baseline(
            capturedAt = 100L,
            medianTtft = 100.0,
            p95Total = 1_100.0,
            throughput = 100.0,
        )
        val currentRuns = listOf(
            run("current-1", 101L, 140L, 1_400L, 70.0),
            run("current-2", 102L, 150L, 1_500L, 69.0),
            run("current-3", 103L, 160L, 1_600L, 68.0),
        )
        val comparison = BenchmarkComparisonEvaluator(policy).compare(key, active, currentRuns)
        val snapshot = emptySnapshot().copy(
            benchmarkBaselines = listOf(active),
            benchmarkHistory = listOf(active, previous),
            benchmarkComparisons = listOf(comparison),
        )

        val screen = presenter.present(snapshot)

        assertEquals("Benchmark regressions", screen.title)
        assertEquals("Benchmark summary", screen.cards.first().title)
        assertTrue(screen.cards.first().lines.contains("Fail: 1"))
        val comparisonCard = screen.cards[1]
        assertEquals(ConsoleEmphasis.NEGATIVE, comparisonCard.emphasis)
        assertTrue(comparisonCard.lines.any { "Regression" in it })
        assertTrue(comparisonCard.lines.any { "1.50x" in it })
        assertEquals(3, screen.charts.size)
        val ttftChart = screen.charts.first { it.title == "Median TTFT history" }
        assertEquals(listOf(50L, 100L), ttftChart.series.single().points.map { it.timestampEpochMs })
        assertEquals(listOf(90.0, 100.0), ttftChart.series.single().points.map { it.value })
        val p95Chart = screen.charts.first { it.title == "p95 total latency history" }
        assertNull(p95Chart.series.single().points.first().value)
        assertEquals(1_100.0, p95Chart.series.single().points.last().value)
        val historyCards = screen.cards.filter { it.title.startsWith("Baseline ·") }
        assertEquals(2, historyCards.size)
        assertTrue(historyCards.first().lines.contains("Active: true"))
        assertTrue(historyCards.last().lines.contains("Active: false"))
    }

    @Test
    fun `keeps insufficient sample comparison as a warning preview`() {
        val active = baseline(100L, 100.0, 1_100.0, 100.0)
        val comparison = BenchmarkComparisonEvaluator(policy).compare(
            key = key,
            baseline = active,
            runs = listOf(run("current", 101L, 140L, 1_400L, 70.0)),
        )

        val screen = presenter.present(
            emptySnapshot().copy(
                benchmarkBaselines = listOf(active),
                benchmarkHistory = listOf(active),
                benchmarkComparisons = listOf(comparison),
            ),
        )

        assertEquals(HealthStatus.WARN, comparison.status)
        assertEquals(ConsoleEmphasis.WARNING, screen.cards[1].emphasis)
        assertTrue(screen.cards[1].lines.contains("Comparison state: Preview or unavailable"))
        assertTrue(screen.cards[1].lines.any { it.endsWith("Preview") })
    }

    private fun emptySnapshot(): ConsoleSnapshot = ConsoleSnapshot(
        capturedAtEpochMs = 0,
        runtime = DisconnectedRuntimeStateProvider.snapshot(),
        runs = emptyList(),
        logs = emptyList(),
        health = emptyList(),
        resources = emptyList(),
        benchmarkBaselines = emptyList(),
    )

    private fun baseline(
        capturedAt: Long,
        medianTtft: Double,
        p95Total: Double?,
        throughput: Double,
    ): BenchmarkBaseline = BenchmarkBaseline(
        key = key,
        capturedAtEpochMs = capturedAt,
        sampleCount = 5,
        medianTimeToFirstTokenMs = medianTtft,
        p95TimeToFirstTokenMs = medianTtft + 10,
        medianTotalMs = p95Total?.minus(100),
        p95TotalMs = p95Total,
        medianDecodeTokensPerSecond = throughput,
    )

    private fun run(
        id: String,
        completedAt: Long,
        ttft: Long,
        total: Long,
        throughput: Double,
    ): GenerationRunRecord = GenerationRunRecord(
        requestId = RequestId(id),
        applicationId = key.applicationId,
        useCaseId = key.useCaseId,
        modelDigest = key.modelDigest,
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
        modelLoadKind = key.modelLoadKind,
    )
}
