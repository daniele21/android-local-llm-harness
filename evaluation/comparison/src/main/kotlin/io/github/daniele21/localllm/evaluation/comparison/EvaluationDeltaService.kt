package io.github.daniele21.localllm.evaluation.comparison

import io.github.daniele21.localllm.evaluation.EvaluationCaseMetrics
import io.github.daniele21.localllm.evaluation.EvaluationCategoryId
import io.github.daniele21.localllm.evaluation.EvaluationQualitySummary
import io.github.daniele21.localllm.evaluation.EvaluationRunState
import io.github.daniele21.localllm.evaluation.PersistedEvaluationRun

data class EvaluationNumericDelta(val left: Double, val right: Double, val pairedCaseCount: Int? = null) {
    init {
        require(left.isFinite() && right.isFinite()) { "Comparison values must be finite" }
        require(pairedCaseCount == null || pairedCaseCount > 0) { "Paired case count must be positive when declared" }
    }

    val absolute: Double
        get() = right - left
}

data class EvaluationCategoryDelta(val categoryId: EvaluationCategoryId, val score: EvaluationNumericDelta)

data class EvaluationQualityDeltas(val aggregateScore: EvaluationNumericDelta?, val categories: List<EvaluationCategoryDelta>)

data class EvaluationRuntimeDeltas(
    val timeToFirstTokenMs: EvaluationNumericDelta?,
    val totalMs: EvaluationNumericDelta?,
    val prefillMs: EvaluationNumericDelta?,
    val decodeMs: EvaluationNumericDelta?,
    val decodeTokensPerSecond: EvaluationNumericDelta?,
)

data class EvaluationResourceDeltas(val processPssBytes: EvaluationNumericDelta?, val availableMemoryBytes: EvaluationNumericDelta?)

enum class EvaluationDeltaUnavailableReason {
    COMPARISON_IDENTITY_UNAVAILABLE,
    QUALITY_INCOMPATIBLE,
    RUNTIME_INCOMPATIBLE,
    RUN_NOT_COMPLETED,
    QUALITY_SUMMARY_MISSING,
    QUALITY_SHAPE_MISMATCH,
}

sealed interface EvaluationDeltaFamily<out T> {
    data class Available<T>(val value: T) : EvaluationDeltaFamily<T>

    data class Unavailable(val reason: EvaluationDeltaUnavailableReason) : EvaluationDeltaFamily<Nothing>
}

data class EvaluationRunDeltas(
    val quality: EvaluationDeltaFamily<EvaluationQualityDeltas>,
    val runtime: EvaluationDeltaFamily<EvaluationRuntimeDeltas>,
    val resources: EvaluationDeltaFamily<EvaluationResourceDeltas>,
)

class EvaluationDeltaService(private val compatibilityService: EvaluationCompatibilityService = EvaluationCompatibilityService()) {
    fun compare(left: PersistedEvaluationRun, right: PersistedEvaluationRun): EvaluationRunDeltas {
        if (!left.summary.isCompleted() || !right.summary.isCompleted()) {
            return unavailableAll(EvaluationDeltaUnavailableReason.RUN_NOT_COMPLETED)
        }

        return when (val assessment = compatibilityService.compare(left.summary, right.summary)) {
            is EvaluationComparisonAssessment.Unavailable ->
                unavailableAll(EvaluationDeltaUnavailableReason.COMPARISON_IDENTITY_UNAVAILABLE)

            is EvaluationComparisonAssessment.Available -> EvaluationRunDeltas(
                quality = qualityDeltas(left, right, assessment),
                runtime = runtimeDeltas(left, right, assessment),
                resources = resourceDeltas(left, right, assessment),
            )
        }
    }
}

private fun io.github.daniele21.localllm.evaluation.EvaluationRunSummary.isCompleted(): Boolean = state == EvaluationRunState.COMPLETED

private fun qualityDeltas(
    left: PersistedEvaluationRun,
    right: PersistedEvaluationRun,
    assessment: EvaluationComparisonAssessment.Available,
): EvaluationDeltaFamily<EvaluationQualityDeltas> {
    val unavailableReason = qualityUnavailableReason(left, right, assessment)
    if (unavailableReason != null) return EvaluationDeltaFamily.Unavailable(unavailableReason)

    val leftQuality = checkNotNull(left.summary.quality)
    val rightQuality = checkNotNull(right.summary.quality)
    val rightByCategory = rightQuality.categoryScores.associateBy { it.categoryId }
    val categoryDeltas = leftQuality.categoryScores.map { leftCategory ->
        val rightCategory = checkNotNull(rightByCategory[leftCategory.categoryId])
        EvaluationCategoryDelta(
            categoryId = leftCategory.categoryId,
            score = EvaluationNumericDelta(leftCategory.score.value, rightCategory.score.value),
        )
    }
    return EvaluationDeltaFamily.Available(
        EvaluationQualityDeltas(
            aggregateScore = optionalDelta(leftQuality.aggregateScore?.value, rightQuality.aggregateScore?.value),
            categories = categoryDeltas,
        ),
    )
}

private fun qualityUnavailableReason(
    left: PersistedEvaluationRun,
    right: PersistedEvaluationRun,
    assessment: EvaluationComparisonAssessment.Available,
): EvaluationDeltaUnavailableReason? = when {
    !assessment.compatibility.quality.compatible -> EvaluationDeltaUnavailableReason.QUALITY_INCOMPATIBLE
    left.summary.quality == null || right.summary.quality == null -> EvaluationDeltaUnavailableReason.QUALITY_SUMMARY_MISSING
    !left.summary.quality.sameShapeAs(right.summary.quality) -> EvaluationDeltaUnavailableReason.QUALITY_SHAPE_MISMATCH
    else -> null
}

private fun EvaluationQualitySummary.sameShapeAs(other: EvaluationQualitySummary): Boolean {
    if ((aggregateScore == null) != (other.aggregateScore == null)) return false
    return categoryScores.map { it.categoryId }.toSet() == other.categoryScores.map { it.categoryId }.toSet()
}

private fun runtimeDeltas(
    left: PersistedEvaluationRun,
    right: PersistedEvaluationRun,
    assessment: EvaluationComparisonAssessment.Available,
): EvaluationDeltaFamily<EvaluationRuntimeDeltas> {
    if (!assessment.compatibility.runtime.compatible) {
        return EvaluationDeltaFamily.Unavailable(EvaluationDeltaUnavailableReason.RUNTIME_INCOMPATIBLE)
    }
    return EvaluationDeltaFamily.Available(
        EvaluationRuntimeDeltas(
            timeToFirstTokenMs = pairedMeanDelta(left, right) { it.timeToFirstTokenMs?.toDouble() },
            totalMs = pairedMeanDelta(left, right) { it.totalMs?.toDouble() },
            prefillMs = pairedMeanDelta(left, right) { it.prefillMs?.toDouble() },
            decodeMs = pairedMeanDelta(left, right) { it.decodeMs?.toDouble() },
            decodeTokensPerSecond = pairedMeanDelta(left, right) { it.decodeTokensPerSecond },
        ),
    )
}

private fun resourceDeltas(
    left: PersistedEvaluationRun,
    right: PersistedEvaluationRun,
    assessment: EvaluationComparisonAssessment.Available,
): EvaluationDeltaFamily<EvaluationResourceDeltas> {
    if (!assessment.compatibility.runtime.compatible) {
        return EvaluationDeltaFamily.Unavailable(EvaluationDeltaUnavailableReason.RUNTIME_INCOMPATIBLE)
    }
    return EvaluationDeltaFamily.Available(
        EvaluationResourceDeltas(
            processPssBytes = pairedMeanDelta(left, right) { it.processPssBytes?.toDouble() },
            availableMemoryBytes = pairedMeanDelta(left, right) { it.availableMemoryBytes?.toDouble() },
        ),
    )
}

private fun pairedMeanDelta(
    left: PersistedEvaluationRun,
    right: PersistedEvaluationRun,
    selector: (EvaluationCaseMetrics) -> Double?,
): EvaluationNumericDelta? {
    val rightByCase = right.caseResults.associateBy { it.caseId }
    val pairs = left.caseResults.mapNotNull { leftCase ->
        val rightCase = rightByCase[leftCase.caseId] ?: return@mapNotNull null
        val leftValue = selector(leftCase.metrics) ?: return@mapNotNull null
        val rightValue = selector(rightCase.metrics) ?: return@mapNotNull null
        leftValue to rightValue
    }
    if (pairs.isEmpty()) return null
    return EvaluationNumericDelta(
        left = pairs.map { it.first }.average(),
        right = pairs.map { it.second }.average(),
        pairedCaseCount = pairs.size,
    )
}

private fun optionalDelta(left: Double?, right: Double?): EvaluationNumericDelta? = when {
    left == null && right == null -> null
    left != null && right != null -> EvaluationNumericDelta(left, right)
    else -> null
}

private fun unavailableAll(reason: EvaluationDeltaUnavailableReason) = EvaluationRunDeltas(
    quality = EvaluationDeltaFamily.Unavailable(reason),
    runtime = EvaluationDeltaFamily.Unavailable(reason),
    resources = EvaluationDeltaFamily.Unavailable(reason),
)
