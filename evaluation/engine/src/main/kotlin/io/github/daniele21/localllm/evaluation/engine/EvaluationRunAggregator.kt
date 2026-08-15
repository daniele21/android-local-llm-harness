package io.github.daniele21.localllm.evaluation.engine

import io.github.daniele21.localllm.evaluation.EvaluationCaseId
import io.github.daniele21.localllm.evaluation.EvaluationCaseMetrics
import io.github.daniele21.localllm.evaluation.EvaluationCaseResult
import io.github.daniele21.localllm.evaluation.EvaluationCaseStatus
import io.github.daniele21.localllm.evaluation.EvaluationDatasetCategoryDefinition
import io.github.daniele21.localllm.evaluation.EvaluationQualitySummary
import io.github.daniele21.localllm.evaluation.EvaluationReliabilitySummary
import io.github.daniele21.localllm.evaluation.EvaluatorOutcomeCode
import io.github.daniele21.localllm.evaluation.evaluators.EvaluationQualityAggregator
import kotlin.math.ceil

data class EvaluationMetricDistribution(val median: Double?, val p95: Double?, val sampleCount: Int) {
    init {
        require(sampleCount >= 0) { "Metric distribution sample count must not be negative" }
        require((sampleCount == 0) == (median == null && p95 == null)) {
            "Empty metric distribution must not expose percentiles"
        }
        require((sampleCount > 0) == (median != null && p95 != null)) {
            "Non-empty metric distribution requires median and p95"
        }
        require(median == null || (median.isFinite() && median >= 0.0)) {
            "Metric distribution median must be finite and non-negative"
        }
        require(p95 == null || (p95.isFinite() && p95 >= 0.0)) {
            "Metric distribution p95 must be finite and non-negative"
        }
    }
}

data class EvaluationRuntimeSummary(
    val timeToFirstTokenMs: EvaluationMetricDistribution,
    val totalMs: EvaluationMetricDistribution,
    val prefillMs: EvaluationMetricDistribution,
    val decodeMs: EvaluationMetricDistribution,
    val decodeTokensPerSecond: EvaluationMetricDistribution,
)

data class EvaluationResourceSummary(
    val processPssBytes: EvaluationMetricDistribution,
    val availableMemoryBytes: EvaluationMetricDistribution,
    val thermalStatusCounts: Map<String, Int>,
) {
    init {
        require(thermalStatusCounts.values.all { it > 0 }) { "Thermal status counts must be positive" }
    }
}

data class EvaluationRunAggregation(
    val quality: EvaluationQualitySummary,
    val runtime: EvaluationRuntimeSummary,
    val resources: EvaluationResourceSummary,
    val reliability: EvaluationReliabilitySummary,
)

class EvaluationRunAggregator(private val qualityAggregator: EvaluationQualityAggregator = EvaluationQualityAggregator()) {
    fun aggregate(
        selectedCaseIds: List<EvaluationCaseId>,
        categories: List<EvaluationDatasetCategoryDefinition>,
        caseResults: List<EvaluationCaseResult>,
    ): EvaluationRunAggregation {
        require(selectedCaseIds.isNotEmpty()) { "Run aggregation requires a non-empty selected sample" }
        require(selectedCaseIds.distinct().size == selectedCaseIds.size) {
            "Run aggregation selected case IDs must be unique"
        }
        require(caseResults.map { it.caseId }.distinct().size == caseResults.size) {
            "Run aggregation case result IDs must be unique"
        }
        val selected = selectedCaseIds.toSet()
        require(caseResults.all { it.caseId in selected }) {
            "Run aggregation results must belong to the selected sample"
        }

        return EvaluationRunAggregation(
            quality = qualityAggregator.aggregate(categories, caseResults),
            runtime = runtimeSummary(caseResults),
            resources = resourceSummary(caseResults),
            reliability = reliabilitySummary(selectedCaseIds.size, caseResults),
        )
    }
}

private fun runtimeSummary(caseResults: List<EvaluationCaseResult>) = EvaluationRuntimeSummary(
    timeToFirstTokenMs = caseResults.metricDistribution { it.timeToFirstTokenMs?.toDouble() },
    totalMs = caseResults.metricDistribution { it.totalMs?.toDouble() },
    prefillMs = caseResults.metricDistribution { it.prefillMs?.toDouble() },
    decodeMs = caseResults.metricDistribution { it.decodeMs?.toDouble() },
    decodeTokensPerSecond = caseResults.metricDistribution(EvaluationCaseMetrics::decodeTokensPerSecond),
)

private fun resourceSummary(caseResults: List<EvaluationCaseResult>) = EvaluationResourceSummary(
    processPssBytes = caseResults.metricDistribution { it.processPssBytes?.toDouble() },
    availableMemoryBytes = caseResults.metricDistribution { it.availableMemoryBytes?.toDouble() },
    thermalStatusCounts = caseResults
        .mapNotNull { it.metrics.thermalStatus }
        .groupingBy { it }
        .eachCount()
        .toSortedMap(),
)

private fun reliabilitySummary(totalCases: Int, caseResults: List<EvaluationCaseResult>): EvaluationReliabilitySummary {
    val scored = caseResults.filter { it.status == EvaluationCaseStatus.SCORED }
    return EvaluationReliabilitySummary(
        totalCases = totalCases,
        completedAndScored = scored.size,
        incorrectButValid = scored.count { it.outcome?.code != EvaluatorOutcomeCode.CORRECT },
        invalidOutput = caseResults.count { it.status == EvaluationCaseStatus.INVALID_OUTPUT },
        timeout = caseResults.count { it.status == EvaluationCaseStatus.TIMEOUT },
        runtimeFailure = caseResults.count { it.status == EvaluationCaseStatus.RUNTIME_FAILURE },
        cancelled = caseResults.count { it.status == EvaluationCaseStatus.CANCELLED },
        skipped = totalCases - caseResults.size,
    )
}

private fun List<EvaluationCaseResult>.metricDistribution(selector: (EvaluationCaseMetrics) -> Double?): EvaluationMetricDistribution {
    val values = mapNotNull { selector(it.metrics) }
    return EvaluationMetricDistribution(
        median = values.median(),
        p95 = values.percentile95(),
        sampleCount = values.size,
    )
}

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
