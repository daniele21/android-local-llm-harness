package io.github.daniele21.localllm.console

import io.github.daniele21.localllm.observability.BenchmarkBaseline
import io.github.daniele21.localllm.observability.BenchmarkKey
import io.github.daniele21.localllm.observability.HealthStatus
import io.github.daniele21.localllm.observability.benchmark.BenchmarkComparison
import io.github.daniele21.localllm.observability.benchmark.BenchmarkMetricComparison
import io.github.daniele21.localllm.observability.benchmark.BenchmarkThresholdDirection
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class ConsoleBenchmarkPresenter(zoneId: ZoneId = ZoneId.systemDefault()) {
    private val timestampFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)
        .withZone(zoneId)

    fun present(snapshot: ConsoleSnapshot): ConsoleScreen {
        val comparisons = snapshot.benchmarkComparisons.sortedWith(
            compareBy<BenchmarkComparison> { statusRank(it.status) }.thenBy { it.key.stableId },
        )
        val history = snapshot.benchmarkHistory.sortedWith(
            compareByDescending<BenchmarkBaseline> { it.capturedAtEpochMs }.thenBy { it.key.stableId },
        )
        if (comparisons.isEmpty() && history.isEmpty()) {
            return ConsoleScreen(
                title = "Benchmark regressions",
                subtitle = "Active baselines, post-baseline comparisons and retained history",
                cards = listOf(emptyCard("No benchmark baselines recorded")),
            )
        }

        val cards = mutableListOf(summaryCard(comparisons, history))
        if (comparisons.isEmpty()) {
            cards += emptyCard("No active baseline is available for comparison", "Regression comparison")
        } else {
            cards += comparisons.map(::comparisonCard)
        }
        if (history.isEmpty()) {
            cards += emptyCard("No retained baseline history is available", "Baseline history")
        } else {
            cards += history.map { baseline -> historyCard(baseline, snapshot.benchmarkBaselines) }
        }

        return ConsoleScreen(
            title = "Benchmark regressions",
            subtitle = "Active baselines, post-baseline comparisons and retained history",
            cards = cards,
            charts = historyCharts(history),
        )
    }

    private fun summaryCard(comparisons: List<BenchmarkComparison>, history: List<BenchmarkBaseline>): ConsoleCard = ConsoleCard(
        title = "Benchmark summary",
        lines = listOf(
            "Active keys: ${comparisons.size}",
            "Retained captures: ${history.size}",
            "Pass: ${comparisons.count { it.status == HealthStatus.PASS }}",
            "Warn: ${comparisons.count { it.status == HealthStatus.WARN }}",
            "Fail: ${comparisons.count { it.status == HealthStatus.FAIL }}",
        ),
        emphasis = comparisons.minByOrNull { statusRank(it.status) }?.status.toEmphasis(),
    )

    private fun comparisonCard(comparison: BenchmarkComparison): ConsoleCard {
        val baseline = comparison.baseline
        val lines = mutableListOf(
            "Model: ${shortDigest(comparison.key.modelDigest.sha256)}",
            "Load class: ${comparison.key.modelLoadKind.name}",
            "Baseline captured: ${baseline?.capturedAtEpochMs?.let(::formatTimestamp) ?: "Unavailable"}",
            "Baseline samples: ${baseline?.sampleCount ?: "Unavailable"}",
            "Post-baseline samples: ${comparison.availableSamples} / ${comparison.requiredSamples}",
            "Comparison state: ${if (comparison.comparisonReady) "Ready" else "Preview or unavailable"}",
            "Detail: ${comparison.detail}",
        )
        lines += comparison.metrics.map { metric -> metricLine(metric, comparison.comparisonReady) }
        return ConsoleCard(
            title = "${comparison.status.name} · ${comparison.key.applicationId.value} · ${comparison.key.useCaseId.value}",
            lines = lines,
            emphasis = comparison.status.toEmphasis(),
        )
    }

    private fun historyCard(baseline: BenchmarkBaseline, activeBaselines: List<BenchmarkBaseline>): ConsoleCard = ConsoleCard(
        title = "Baseline · ${formatTimestamp(baseline.capturedAtEpochMs)}",
        lines = listOf(
            "Application: ${baseline.key.applicationId.value}",
            "Use case: ${baseline.key.useCaseId.value}",
            "Model: ${shortDigest(baseline.key.modelDigest.sha256)}",
            "Load class: ${baseline.key.modelLoadKind.name}",
            "Active: ${activeBaselines.any { it == baseline }}",
            "Samples: ${baseline.sampleCount}",
            "Median TTFT: ${formatMetric(baseline.medianTimeToFirstTokenMs, "ms")}",
            "p95 TTFT: ${formatMetric(baseline.p95TimeToFirstTokenMs, "ms")}",
            "Median total: ${formatMetric(baseline.medianTotalMs, "ms")}",
            "p95 total: ${formatMetric(baseline.p95TotalMs, "ms")}",
            "Median decode: ${formatMetric(baseline.medianDecodeTokensPerSecond, "tok/s")}",
        ),
        emphasis = if (activeBaselines.any { it == baseline }) {
            ConsoleEmphasis.POSITIVE
        } else {
            ConsoleEmphasis.NEUTRAL
        },
    )

    private fun metricLine(metric: BenchmarkMetricComparison, comparisonReady: Boolean): String {
        val policy = when (metric.metric.thresholdDirection) {
            BenchmarkThresholdDirection.MAXIMUM_RATIO -> "limit ≤ ${formatRatioValue(metric.thresholdRatio)}"
            BenchmarkThresholdDirection.MINIMUM_RATIO -> "limit ≥ ${formatRatioValue(metric.thresholdRatio)}"
        }
        val assessment = when {
            !metric.comparable -> "Unavailable"
            !comparisonReady -> "Preview"
            metric.regressed -> "Regression"
            else -> "Within policy"
        }
        return "${metric.metric.displayName}: " +
            "${formatMetric(metric.baselineValue, metric.metric.unit)} → " +
            "${formatMetric(metric.currentValue, metric.metric.unit)} · " +
            "${formatRatio(metric.ratio)} · $policy · $assessment"
    }

    private fun historyCharts(history: List<BenchmarkBaseline>): List<ConsoleChart> = listOfNotNull(
        historyChart(
            title = "Median TTFT history",
            valueUnit = "ms",
            history = history,
            value = BenchmarkBaseline::medianTimeToFirstTokenMs,
        ),
        historyChart(
            title = "p95 total latency history",
            valueUnit = "ms",
            history = history,
            value = BenchmarkBaseline::p95TotalMs,
        ),
        historyChart(
            title = "Median decode throughput history",
            valueUnit = "tok/s",
            history = history,
            value = BenchmarkBaseline::medianDecodeTokensPerSecond,
        ),
    )

    private fun historyChart(
        title: String,
        valueUnit: String,
        history: List<BenchmarkBaseline>,
        value: (BenchmarkBaseline) -> Double?,
    ): ConsoleChart? {
        val series = history.groupBy(BenchmarkBaseline::key)
            .toSortedMap(compareBy(BenchmarkKey::stableId))
            .mapNotNull { (key, baselines) ->
                val points = baselines.sortedBy(BenchmarkBaseline::capturedAtEpochMs).map { baseline ->
                    ConsoleChartPoint(
                        timestampEpochMs = baseline.capturedAtEpochMs,
                        value = value(baseline),
                    )
                }
                points.takeIf { values -> values.any { it.value != null } }?.let {
                    ConsoleChartSeries(label = seriesLabel(key), points = points)
                }
            }
        if (series.isEmpty()) return null
        return ConsoleChart(
            title = title,
            subtitle = "Retained baseline captures grouped by application, use case, model and load class",
            valueUnit = valueUnit,
            series = series,
            minimumValue = 0.0,
        )
    }

    private fun seriesLabel(key: BenchmarkKey): String =
        "${key.applicationId.value}/${key.useCaseId.value} · ${key.modelLoadKind.name} · ${shortDigest(key.modelDigest.sha256)}"

    private fun formatTimestamp(epochMs: Long): String = timestampFormatter.format(Instant.ofEpochMilli(epochMs))

    private fun formatMetric(value: Double?, unit: String): String = value
        ?.let { String.format(Locale.US, "%.2f %s", it, unit) }
        ?: "Unavailable"

    private fun formatRatio(value: Double?): String = value
        ?.let { ratio ->
            String.format(Locale.US, "%.2fx (%+.1f%%)", ratio, (ratio - 1.0) * 100.0)
        }
        ?: "ratio unavailable"

    private fun formatRatioValue(value: Double): String = String.format(Locale.US, "%.2fx", value)

    private fun shortDigest(value: String): String = value.take(SHORT_DIGEST_LENGTH)

    private fun statusRank(status: HealthStatus): Int = when (status) {
        HealthStatus.FAIL -> 0
        HealthStatus.WARN -> 1
        HealthStatus.NOT_RUN -> 2
        HealthStatus.PASS -> 3
    }

    private fun HealthStatus?.toEmphasis(): ConsoleEmphasis = when (this) {
        HealthStatus.PASS -> ConsoleEmphasis.POSITIVE
        HealthStatus.WARN, HealthStatus.NOT_RUN -> ConsoleEmphasis.WARNING
        HealthStatus.FAIL -> ConsoleEmphasis.NEGATIVE
        null -> ConsoleEmphasis.NEUTRAL
    }

    private fun emptyCard(message: String, title: String = "Empty state"): ConsoleCard = ConsoleCard(
        title = title,
        lines = listOf(message),
        emphasis = ConsoleEmphasis.WARNING,
    )

    private companion object {
        const val SHORT_DIGEST_LENGTH = 12
    }
}
