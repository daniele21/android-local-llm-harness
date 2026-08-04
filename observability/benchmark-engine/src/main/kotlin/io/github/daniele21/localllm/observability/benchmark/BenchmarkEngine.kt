@file:Suppress("TooManyFunctions")

package io.github.daniele21.localllm.observability.benchmark

import io.github.daniele21.localllm.observability.BenchmarkBaseline
import io.github.daniele21.localllm.observability.BenchmarkKey
import io.github.daniele21.localllm.observability.GenerationRunRecord
import io.github.daniele21.localllm.observability.HealthStatus
import io.github.daniele21.localllm.observability.RunStatus
import io.github.daniele21.localllm.observability.TelemetryRepository
import io.github.daniele21.localllm.observability.health.HealthAssessment
import io.github.daniele21.localllm.observability.health.HealthCheck
import kotlin.math.ceil

fun interface BenchmarkEpochClock {
    fun nowEpochMs(): Long
}

@Suppress("LongParameterList")
data class BenchmarkPolicy(
    val baselineWindowSize: Int = 20,
    val comparisonWindowSize: Int = 10,
    val minimumBaselineSamples: Int = 5,
    val minimumComparisonSamples: Int = 3,
    val maxMedianTimeToFirstTokenRatio: Double = 1.20,
    val maxP95TotalRatio: Double = 1.20,
    val minMedianDecodeThroughputRatio: Double = 0.85,
) {
    init {
        require(baselineWindowSize > 0) { "Baseline window size must be positive" }
        require(comparisonWindowSize > 0) { "Comparison window size must be positive" }
        require(minimumBaselineSamples in 1..baselineWindowSize) {
            "Minimum baseline samples must fit the baseline window"
        }
        require(minimumComparisonSamples in 1..comparisonWindowSize) {
            "Minimum comparison samples must fit the comparison window"
        }
        require(maxMedianTimeToFirstTokenRatio >= 1.0) { "TTFT ratio must not improve by declaring a regression" }
        require(maxP95TotalRatio >= 1.0) { "Total-latency ratio must not improve by declaring a regression" }
        require(minMedianDecodeThroughputRatio in 0.0..1.0) { "Throughput ratio must be between zero and one" }
    }
}

sealed interface BenchmarkCaptureResult {
    data class Captured(val baseline: BenchmarkBaseline) : BenchmarkCaptureResult

    data class InsufficientSamples(val available: Int, val required: Int) : BenchmarkCaptureResult
}

enum class BenchmarkMetric(val displayName: String, val unit: String, val thresholdDirection: BenchmarkThresholdDirection) {
    MEDIAN_TIME_TO_FIRST_TOKEN("median TTFT", "ms", BenchmarkThresholdDirection.MAXIMUM_RATIO),
    P95_TOTAL_LATENCY("p95 total latency", "ms", BenchmarkThresholdDirection.MAXIMUM_RATIO),
    MEDIAN_DECODE_THROUGHPUT("median decode throughput", "tok/s", BenchmarkThresholdDirection.MINIMUM_RATIO),
}

enum class BenchmarkThresholdDirection {
    MAXIMUM_RATIO,
    MINIMUM_RATIO,
}

data class BenchmarkMetricComparison(
    val metric: BenchmarkMetric,
    val baselineValue: Double?,
    val currentValue: Double?,
    val ratio: Double?,
    val thresholdRatio: Double,
    val regressed: Boolean,
) {
    val comparable: Boolean
        get() = baselineValue != null && currentValue != null
}

data class BenchmarkComparison(
    val key: BenchmarkKey,
    val baseline: BenchmarkBaseline?,
    val current: BenchmarkBaseline?,
    val availableSamples: Int,
    val requiredSamples: Int,
    val comparisonReady: Boolean,
    val status: HealthStatus,
    val detail: String,
    val metrics: List<BenchmarkMetricComparison>,
)

class BenchmarkBaselineRecorder(
    private val repository: TelemetryRepository,
    private val policy: BenchmarkPolicy = BenchmarkPolicy(),
    private val clock: BenchmarkEpochClock = BenchmarkEpochClock(System::currentTimeMillis),
) {
    fun capture(key: BenchmarkKey): BenchmarkCaptureResult {
        val runs = matchingRuns(repository.recentRuns(policy.baselineWindowSize * RUN_LOOKBACK_MULTIPLIER), key)
            .take(policy.baselineWindowSize)
        if (runs.size < policy.minimumBaselineSamples) {
            return BenchmarkCaptureResult.InsufficientSamples(runs.size, policy.minimumBaselineSamples)
        }
        val baseline = runs.toBaseline(key, clock.nowEpochMs())
        repository.saveBenchmarkBaseline(baseline)
        return BenchmarkCaptureResult.Captured(baseline)
    }
}

class BenchmarkComparisonEvaluator(private val policy: BenchmarkPolicy = BenchmarkPolicy()) {
    fun compare(
        key: BenchmarkKey,
        baseline: BenchmarkBaseline?,
        runs: List<GenerationRunRecord>,
    ): BenchmarkComparison {
        if (baseline == null) {
            return BenchmarkComparison(
                key = key,
                baseline = null,
                current = null,
                availableSamples = 0,
                requiredSamples = policy.minimumComparisonSamples,
                comparisonReady = false,
                status = HealthStatus.WARN,
                detail = "Benchmark baseline is not available",
                metrics = emptyList(),
            )
        }

        val currentRuns = matchingRuns(runs, key)
            .filter { (it.completedAtEpochMs ?: Long.MIN_VALUE) > baseline.capturedAtEpochMs }
            .take(policy.comparisonWindowSize)
        val current = currentRuns.takeIf(List<GenerationRunRecord>::isNotEmpty)
            ?.toBaseline(key, capturedAtEpochMs = currentRuns.maxOfCompletedAt())
        val metrics = metricComparisons(baseline, current, policy)

        if (currentRuns.size < policy.minimumComparisonSamples) {
            return BenchmarkComparison(
                key = key,
                baseline = baseline,
                current = current,
                availableSamples = currentRuns.size,
                requiredSamples = policy.minimumComparisonSamples,
                comparisonReady = false,
                status = HealthStatus.WARN,
                detail = "Benchmark comparison needs ${policy.minimumComparisonSamples} post-baseline sample(s)",
                metrics = metrics,
            )
        }

        val comparableMetricCount = metrics.count(BenchmarkMetricComparison::comparable)
        if (comparableMetricCount == 0) {
            return BenchmarkComparison(
                key = key,
                baseline = baseline,
                current = current,
                availableSamples = currentRuns.size,
                requiredSamples = policy.minimumComparisonSamples,
                comparisonReady = true,
                status = HealthStatus.WARN,
                detail = "Benchmark samples do not contain comparable metrics",
                metrics = metrics,
            )
        }

        val regressions = metrics.filter(BenchmarkMetricComparison::regressed)
        val status = if (regressions.isEmpty()) HealthStatus.PASS else HealthStatus.FAIL
        val detail = if (regressions.isEmpty()) {
            "Benchmark is within baseline across $comparableMetricCount comparable metric(s)"
        } else {
            "Benchmark regression detected: ${regressions.joinToString { it.metric.displayName }}"
        }
        return BenchmarkComparison(
            key = key,
            baseline = baseline,
            current = current,
            availableSamples = currentRuns.size,
            requiredSamples = policy.minimumComparisonSamples,
            comparisonReady = true,
            status = status,
            detail = detail,
            metrics = metrics,
        )
    }
}

class BenchmarkRegressionHealthCheck(
    private val repository: TelemetryRepository,
    private val key: BenchmarkKey,
    private val policy: BenchmarkPolicy = BenchmarkPolicy(),
) : HealthCheck {
    override val id: String = listOf(
        "benchmark-regression",
        key.applicationId.value,
        key.useCaseId.value,
        key.modelDigest.sha256,
        key.modelLoadKind.name,
    ).joinToString(":")

    override fun evaluate(): HealthAssessment {
        val baseline = repository.benchmarkBaselines().firstOrNull { it.key == key }
        val runs = repository.recentRuns(policy.comparisonWindowSize * RUN_LOOKBACK_MULTIPLIER)
        val comparison = BenchmarkComparisonEvaluator(policy).compare(key, baseline, runs)
        return HealthAssessment(comparison.status, comparison.detail)
    }
}

private fun metricComparisons(
    baseline: BenchmarkBaseline,
    current: BenchmarkBaseline?,
    policy: BenchmarkPolicy,
): List<BenchmarkMetricComparison> = listOf(
    metricComparison(
        metric = BenchmarkMetric.MEDIAN_TIME_TO_FIRST_TOKEN,
        baselineValue = baseline.medianTimeToFirstTokenMs,
        currentValue = current?.medianTimeToFirstTokenMs,
        thresholdRatio = policy.maxMedianTimeToFirstTokenRatio,
    ),
    metricComparison(
        metric = BenchmarkMetric.P95_TOTAL_LATENCY,
        baselineValue = baseline.p95TotalMs,
        currentValue = current?.p95TotalMs,
        thresholdRatio = policy.maxP95TotalRatio,
    ),
    metricComparison(
        metric = BenchmarkMetric.MEDIAN_DECODE_THROUGHPUT,
        baselineValue = baseline.medianDecodeTokensPerSecond,
        currentValue = current?.medianDecodeTokensPerSecond,
        thresholdRatio = policy.minMedianDecodeThroughputRatio,
    ),
)

private fun metricComparison(
    metric: BenchmarkMetric,
    baselineValue: Double?,
    currentValue: Double?,
    thresholdRatio: Double,
): BenchmarkMetricComparison {
    val ratio = if (baselineValue != null && currentValue != null && baselineValue > 0.0) {
        currentValue / baselineValue
    } else {
        null
    }
    val regressed = when (metric.thresholdDirection) {
        BenchmarkThresholdDirection.MAXIMUM_RATIO -> ratio != null && ratio > thresholdRatio
        BenchmarkThresholdDirection.MINIMUM_RATIO -> ratio != null && ratio < thresholdRatio
    }
    return BenchmarkMetricComparison(
        metric = metric,
        baselineValue = baselineValue,
        currentValue = currentValue,
        ratio = ratio,
        thresholdRatio = thresholdRatio,
        regressed = regressed,
    )
}

private fun matchingRuns(runs: List<GenerationRunRecord>, key: BenchmarkKey): List<GenerationRunRecord> = runs.asSequence()
    .filter { it.status == RunStatus.COMPLETED }
    .filter { it.applicationId == key.applicationId }
    .filter { it.useCaseId == key.useCaseId }
    .filter { it.modelDigest == key.modelDigest }
    .filter { it.modelLoadKind == key.modelLoadKind }
    .filter { it.completedAtEpochMs != null }
    .sortedByDescending { it.completedAtEpochMs }
    .toList()

private fun List<GenerationRunRecord>.toBaseline(key: BenchmarkKey, capturedAtEpochMs: Long): BenchmarkBaseline = BenchmarkBaseline(
    key = key,
    capturedAtEpochMs = capturedAtEpochMs,
    sampleCount = size,
    medianTimeToFirstTokenMs = mapNotNull(GenerationRunRecord::timeToFirstTokenMs).map(Long::toDouble).median(),
    p95TimeToFirstTokenMs = mapNotNull(GenerationRunRecord::timeToFirstTokenMs).map(Long::toDouble).percentile95(),
    medianTotalMs = mapNotNull(GenerationRunRecord::totalMs).map(Long::toDouble).median(),
    p95TotalMs = mapNotNull(GenerationRunRecord::totalMs).map(Long::toDouble).percentile95(),
    medianDecodeTokensPerSecond = mapNotNull(GenerationRunRecord::decodeTokensPerSecond).median(),
)

private fun List<GenerationRunRecord>.maxOfCompletedAt(): Long = maxOf { requireNotNull(it.completedAtEpochMs) }

private fun List<Double>.median(): Double? {
    if (isEmpty()) return null
    val sorted = sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 0) {
        (sorted[middle - 1] + sorted[middle]) / 2.0
    } else {
        sorted[middle]
    }
}

private fun List<Double>.percentile95(): Double? {
    if (isEmpty()) return null
    val sorted = sorted()
    val index = (ceil(sorted.size * 0.95).toInt() - 1).coerceIn(sorted.indices)
    return sorted[index]
}

private const val RUN_LOOKBACK_MULTIPLIER = 10