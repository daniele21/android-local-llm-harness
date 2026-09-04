package io.github.daniele21.localllm.console.quality

internal data class QualityThresholds(
    val minAggregatePrecision: Double,
    val minAggregateRecall: Double,
    val minAggregateF1: Double,
    val minPerTypePrecision: Double,
    val minPerTypeRecall: Double,
    val minPerTypeF1: Double,
    val minStructuredCompletionRate: Double,
    val maxInvalidFindingRate: Double,
    val maxInvalidResultRate: Double,
) {
    init {
        listOf(
            minAggregatePrecision,
            minAggregateRecall,
            minAggregateF1,
            minPerTypePrecision,
            minPerTypeRecall,
            minPerTypeF1,
            minStructuredCompletionRate,
            maxInvalidFindingRate,
            maxInvalidResultRate,
        ).forEach { threshold ->
            require(threshold in 0.0..1.0) { "Quality thresholds must be within [0, 1]" }
        }
    }
}

internal data class QualityAcceptancePolicy(
    val policyVersion: Int,
    val corpusIdentity: QualityCorpusIdentity,
    val requiredTypeIds: Set<String>,
    val thresholds: QualityThresholds,
) {
    init {
        require(policyVersion > 0) { "Quality policy version must be positive" }
        require(requiredTypeIds.isNotEmpty()) { "Quality policy must require at least one type" }
        require(requiredTypeIds.none(String::isBlank)) { "Required quality type IDs must not be blank" }
    }
}

internal enum class QualityGateFailureCode {
    CORPUS_IDENTITY_MISMATCH,
    MISSING_REQUIRED_TYPE,
    AGGREGATE_PRECISION_BELOW_MINIMUM,
    AGGREGATE_RECALL_BELOW_MINIMUM,
    AGGREGATE_F1_BELOW_MINIMUM,
    PER_TYPE_PRECISION_BELOW_MINIMUM,
    PER_TYPE_RECALL_BELOW_MINIMUM,
    PER_TYPE_F1_BELOW_MINIMUM,
    STRUCTURED_COMPLETION_BELOW_MINIMUM,
    INVALID_FINDING_RATE_ABOVE_MAXIMUM,
    INVALID_RESULT_RATE_ABOVE_MAXIMUM,
}

internal data class QualityGateFailure(val code: QualityGateFailureCode, val typeId: String? = null)

internal data class QualityAcceptanceReport(val accepted: Boolean, val failures: List<QualityGateFailure>) {
    init {
        require(accepted == failures.isEmpty()) { "Quality acceptance must match failure presence" }
    }
}

internal object OmbraQualityAcceptanceGate {
    fun evaluate(policy: QualityAcceptancePolicy, score: QualityScore): QualityAcceptanceReport {
        if (score.corpusIdentity != policy.corpusIdentity) {
            return rejected(QualityGateFailure(QualityGateFailureCode.CORPUS_IDENTITY_MISMATCH))
        }

        val failures = mutableListOf<QualityGateFailure>()
        val thresholds = policy.thresholds

        addIfBelow(
            failures,
            score.aggregate.precision,
            thresholds.minAggregatePrecision,
            QualityGateFailureCode.AGGREGATE_PRECISION_BELOW_MINIMUM,
        )
        addIfBelow(
            failures,
            score.aggregate.recall,
            thresholds.minAggregateRecall,
            QualityGateFailureCode.AGGREGATE_RECALL_BELOW_MINIMUM,
        )
        addIfBelow(
            failures,
            score.aggregate.f1,
            thresholds.minAggregateF1,
            QualityGateFailureCode.AGGREGATE_F1_BELOW_MINIMUM,
        )
        addIfBelow(
            failures,
            score.structuredCompletionRate,
            thresholds.minStructuredCompletionRate,
            QualityGateFailureCode.STRUCTURED_COMPLETION_BELOW_MINIMUM,
        )
        addIfAbove(
            failures,
            score.invalidFindingRate,
            thresholds.maxInvalidFindingRate,
            QualityGateFailureCode.INVALID_FINDING_RATE_ABOVE_MAXIMUM,
        )
        addIfAbove(
            failures,
            score.invalidResultRate,
            thresholds.maxInvalidResultRate,
            QualityGateFailureCode.INVALID_RESULT_RATE_ABOVE_MAXIMUM,
        )

        policy.requiredTypeIds.toSortedSet().forEach { typeId ->
            val metrics = score.perType[typeId]
            if (metrics == null) {
                failures += QualityGateFailure(QualityGateFailureCode.MISSING_REQUIRED_TYPE, typeId)
            } else {
                addIfBelow(
                    failures,
                    metrics.precision,
                    thresholds.minPerTypePrecision,
                    QualityGateFailureCode.PER_TYPE_PRECISION_BELOW_MINIMUM,
                    typeId,
                )
                addIfBelow(
                    failures,
                    metrics.recall,
                    thresholds.minPerTypeRecall,
                    QualityGateFailureCode.PER_TYPE_RECALL_BELOW_MINIMUM,
                    typeId,
                )
                addIfBelow(
                    failures,
                    metrics.f1,
                    thresholds.minPerTypeF1,
                    QualityGateFailureCode.PER_TYPE_F1_BELOW_MINIMUM,
                    typeId,
                )
            }
        }

        return QualityAcceptanceReport(accepted = failures.isEmpty(), failures = failures)
    }

    private fun addIfBelow(
        failures: MutableList<QualityGateFailure>,
        actual: Double,
        minimum: Double,
        code: QualityGateFailureCode,
        typeId: String? = null,
    ) {
        if (actual < minimum) failures += QualityGateFailure(code, typeId)
    }

    private fun addIfAbove(failures: MutableList<QualityGateFailure>, actual: Double, maximum: Double, code: QualityGateFailureCode) {
        if (actual > maximum) failures += QualityGateFailure(code)
    }

    private fun rejected(failure: QualityGateFailure): QualityAcceptanceReport =
        QualityAcceptanceReport(accepted = false, failures = listOf(failure))
}
