package io.github.daniele21.localllm.observability.benchmark

import io.github.daniele21.localllm.observability.HealthStatus
import io.github.daniele21.localllm.observability.ResourceSnapshot

data class MemoryWindowSummary(
    val sampleCount: Int,
    val baselinePssBytes: Long?,
    val peakPssBytes: Long?,
    val residualPssBytes: Long?,
    val peakDeltaBytes: Long?,
    val residualDeltaBytes: Long?,
    val minimumAvailableMemoryBytes: Long?,
)

object MemoryWindowSummarizer {
    fun summarize(snapshots: List<ResourceSnapshot>): MemoryWindowSummary {
        val ordered = snapshots.sortedBy(ResourceSnapshot::timestampEpochMs)
        val pss = ordered.mapNotNull(ResourceSnapshot::processPssBytes)
        val baseline = pss.firstOrNull()
        val peak = pss.maxOrNull()
        val residual = pss.lastOrNull()
        return MemoryWindowSummary(
            sampleCount = ordered.size,
            baselinePssBytes = baseline,
            peakPssBytes = peak,
            residualPssBytes = residual,
            peakDeltaBytes = nonNegativeDelta(peak, baseline),
            residualDeltaBytes = nonNegativeDelta(residual, baseline),
            minimumAvailableMemoryBytes = ordered.mapNotNull(ResourceSnapshot::availableMemoryBytes).minOrNull(),
        )
    }

    private fun nonNegativeDelta(value: Long?, baseline: Long?): Long? {
        if (value == null || baseline == null) return null
        return (value - baseline).coerceAtLeast(0L)
    }
}

data class MemoryRegressionPolicy(
    val minimumSamples: Int = 3,
    val maxPeakPssRatio: Double = 1.20,
    val maxResidualPssRatio: Double = 1.25,
    val minimumAvailableMemoryBytes: Long? = null,
) {
    init {
        require(minimumSamples > 0) { "Minimum memory samples must be positive" }
        require(maxPeakPssRatio >= 1.0) { "Peak PSS ratio must be at least one" }
        require(maxResidualPssRatio >= 1.0) { "Residual PSS ratio must be at least one" }
        require(minimumAvailableMemoryBytes == null || minimumAvailableMemoryBytes >= 0L) {
            "Minimum available memory must not be negative"
        }
    }
}

enum class MemoryRegressionMetric {
    PEAK_PSS,
    RESIDUAL_PSS,
    AVAILABLE_MEMORY_FLOOR,
}

data class MemoryRegressionFinding(
    val metric: MemoryRegressionMetric,
    val baselineValue: Long?,
    val currentValue: Long?,
    val ratio: Double?,
    val regressed: Boolean,
)

data class MemoryRegressionComparison(val status: HealthStatus, val detail: String, val findings: List<MemoryRegressionFinding>)

class MemoryRegressionEvaluator(private val policy: MemoryRegressionPolicy = MemoryRegressionPolicy()) {
    fun compare(baseline: MemoryWindowSummary, current: MemoryWindowSummary): MemoryRegressionComparison {
        if (baseline.sampleCount < policy.minimumSamples || current.sampleCount < policy.minimumSamples) {
            return MemoryRegressionComparison(
                status = HealthStatus.WARN,
                detail = "Memory comparison needs at least ${policy.minimumSamples} samples in both windows",
                findings = emptyList(),
            )
        }

        val findings = listOf(
            ratioFinding(
                MemoryRegressionMetric.PEAK_PSS,
                baseline.peakPssBytes,
                current.peakPssBytes,
                policy.maxPeakPssRatio,
            ),
            ratioFinding(
                MemoryRegressionMetric.RESIDUAL_PSS,
                baseline.residualPssBytes,
                current.residualPssBytes,
                policy.maxResidualPssRatio,
            ),
            floorFinding(current.minimumAvailableMemoryBytes),
        )
        val comparable = findings.filter {
            it.currentValue != null &&
                (it.baselineValue != null || it.metric == MemoryRegressionMetric.AVAILABLE_MEMORY_FLOOR)
        }
        if (comparable.isEmpty()) {
            return MemoryRegressionComparison(
                status = HealthStatus.WARN,
                detail = "Memory windows do not contain comparable resource measurements",
                findings = findings,
            )
        }

        val regressions = comparable.filter(MemoryRegressionFinding::regressed)
        return MemoryRegressionComparison(
            status = if (regressions.isEmpty()) HealthStatus.PASS else HealthStatus.FAIL,
            detail = if (regressions.isEmpty()) {
                "Memory window is within configured regression policy"
            } else {
                "Memory regression detected: ${regressions.joinToString { it.metric.name }}"
            },
            findings = findings,
        )
    }

    private fun ratioFinding(
        metric: MemoryRegressionMetric,
        baseline: Long?,
        current: Long?,
        maximumRatio: Double,
    ): MemoryRegressionFinding {
        val ratio = if (baseline != null && current != null && baseline > 0L) current.toDouble() / baseline else null
        return MemoryRegressionFinding(
            metric = metric,
            baselineValue = baseline,
            currentValue = current,
            ratio = ratio,
            regressed = ratio != null && ratio > maximumRatio,
        )
    }

    private fun floorFinding(current: Long?): MemoryRegressionFinding {
        val minimum = policy.minimumAvailableMemoryBytes
        return MemoryRegressionFinding(
            metric = MemoryRegressionMetric.AVAILABLE_MEMORY_FLOOR,
            baselineValue = minimum,
            currentValue = current,
            ratio = null,
            regressed = minimum != null && current != null && current < minimum,
        )
    }
}
