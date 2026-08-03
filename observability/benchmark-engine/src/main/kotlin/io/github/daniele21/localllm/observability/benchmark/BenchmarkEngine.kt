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

    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    override fun evaluate(): HealthAssessment {
        val baseline = repository.benchmarkBaselines().firstOrNull { it.key == key }
            ?: return HealthAssessment(HealthStatus.WARN, "Benchmark baseline is not available")
        val currentRuns = matchingRuns(
            repository.recentRuns(policy.comparisonWindowSize * RUN_LOOKBACK_MULTIPLIER),
            key,
        ).filter { (it.completedAtEpochMs ?: Long.MIN_VALUE) > baseline.capturedAtEpochMs }
            .take(policy.comparisonWindowSize)
        if (currentRuns.size < policy.minimumComparisonSamples) {
            return HealthAssessment(
                HealthStatus.WARN,
                "Benchmark comparison needs ${policy.minimumComparisonSamples} post-baseline sample(s)",
            )
        }

        val current = currentRuns.toBaseline(key, capturedAtEpochMs = currentRuns.maxOfCompletedAt())
        val regressions = buildList {
            if (regressedHigher(
                    baseline.medianTimeToFirstTokenMs,
                    current.medianTimeToFirstTokenMs,
                    policy.maxMedianTimeToFirstTokenRatio,
                )
            ) {
                add("median TTFT")
            }
            if (regressedHigher(baseline.p95TotalMs, current.p95TotalMs, policy.maxP95TotalRatio)) {
                add("p95 total latency")
            }
            if (regressedLower(
                    baseline.medianDecodeTokensPerSecond,
                    current.medianDecodeTokensPerSecond,
                    policy.minMedianDecodeThroughputRatio,
                )
            ) {
                add("median decode throughput")
            }
        }
        val comparableMetricCount = comparableMetricCount(baseline, current)
        if (comparableMetricCount == 0) {
            return HealthAssessment(HealthStatus.WARN, "Benchmark samples do not contain comparable metrics")
        }
        return if (regressions.isEmpty()) {
            HealthAssessment(
                HealthStatus.PASS,
                "Benchmark is within baseline across $comparableMetricCount comparable metric(s)",
            )
        } else {
            HealthAssessment(
                HealthStatus.FAIL,
                "Benchmark regression detected: ${regressions.joinToString()}",
            )
        }
    }
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

private fun regressedHigher(baseline: Double?, current: Double?, maxRatio: Double): Boolean =
    baseline != null && current != null && baseline > 0.0 && current / baseline > maxRatio

private fun regressedLower(baseline: Double?, current: Double?, minRatio: Double): Boolean =
    baseline != null && current != null && baseline > 0.0 && current / baseline < minRatio

private fun comparableMetricCount(baseline: BenchmarkBaseline, current: BenchmarkBaseline): Int = listOf(
    baseline.medianTimeToFirstTokenMs to current.medianTimeToFirstTokenMs,
    baseline.p95TotalMs to current.p95TotalMs,
    baseline.medianDecodeTokensPerSecond to current.medianDecodeTokensPerSecond,
).count { (left, right) -> left != null && right != null }

private const val RUN_LOOKBACK_MULTIPLIER = 10
