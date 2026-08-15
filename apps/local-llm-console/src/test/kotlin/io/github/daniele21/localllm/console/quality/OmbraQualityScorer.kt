package io.github.daniele21.localllm.console.quality

internal sealed interface QualityCaseOutcome {
    val caseId: String

    data class Structured(override val caseId: String, val findings: List<QualityOccurrence>, val invalidFindingCount: Int = 0) :
        QualityCaseOutcome {
        init {
            require(invalidFindingCount >= 0) { "Invalid finding count must be non-negative" }
        }
    }

    data class InvalidResult(override val caseId: String) : QualityCaseOutcome

    data class Incomplete(override val caseId: String) : QualityCaseOutcome
}

internal data class ExactOccurrenceCounts(val truePositives: Int, val falsePositives: Int, val falseNegatives: Int) {
    init {
        require(truePositives >= 0 && falsePositives >= 0 && falseNegatives >= 0) { "Quality counts must be non-negative" }
    }

    operator fun plus(other: ExactOccurrenceCounts): ExactOccurrenceCounts = ExactOccurrenceCounts(
        truePositives = truePositives + other.truePositives,
        falsePositives = falsePositives + other.falsePositives,
        falseNegatives = falseNegatives + other.falseNegatives,
    )
}

internal data class ExactOccurrenceMetrics(val counts: ExactOccurrenceCounts, val precision: Double, val recall: Double, val f1: Double)

internal data class QualityScore(
    val corpusIdentity: QualityCorpusIdentity,
    val aggregate: ExactOccurrenceMetrics,
    val perType: Map<String, ExactOccurrenceMetrics>,
    val invalidFindingRate: Double,
    val invalidResultRate: Double,
    val structuredCompletionRate: Double,
)

internal object OmbraExactOccurrenceScorer {
    fun score(corpus: QualityCorpus, outcomes: List<QualityCaseOutcome>): QualityScore {
        val outcomeByCase = outcomes.associateBy(QualityCaseOutcome::caseId)
        require(outcomeByCase.size == outcomes.size) { "Quality outcomes must have unique case IDs" }
        require(outcomeByCase.keys == corpus.cases.mapTo(linkedSetOf(), QualityCase::id)) {
            "Quality outcomes must cover the corpus exactly"
        }

        var aggregateCounts = ExactOccurrenceCounts(truePositives = 0, falsePositives = 0, falseNegatives = 0)
        val countsByType = linkedMapOf<String, ExactOccurrenceCounts>()
        var invalidFindingCount = 0
        var reportedFindingCount = 0
        var invalidResultCount = 0
        var structuredCompletionCount = 0

        corpus.cases.forEach { case ->
            val predicted =
                when (val outcome = outcomeByCase.getValue(case.id)) {
                    is QualityCaseOutcome.Structured -> {
                        invalidFindingCount += outcome.invalidFindingCount
                        reportedFindingCount += outcome.findings.size + outcome.invalidFindingCount
                        structuredCompletionCount += 1
                        outcome.findings
                    }

                    is QualityCaseOutcome.InvalidResult -> {
                        invalidResultCount += 1
                        emptyList()
                    }

                    is QualityCaseOutcome.Incomplete -> emptyList()
                }
            aggregateCounts += exactCounts(case.expectedOccurrences, predicted)
            val caseTypes = case.selectedTypeIds + predicted.map(QualityOccurrence::typeId)
            caseTypes.forEach { typeId ->
                val typeCounts =
                    exactCounts(
                        expected = case.expectedOccurrences.filter { it.typeId == typeId },
                        predicted = predicted.filter { it.typeId == typeId },
                    )
                countsByType[typeId] = countsByType.getOrDefault(typeId, ZERO_COUNTS) + typeCounts
            }
        }

        val perType = countsByType.toSortedMap().mapValues { (_, counts) -> metrics(counts) }
        return QualityScore(
            corpusIdentity = corpus.identity,
            aggregate = metrics(aggregateCounts),
            perType = perType,
            invalidFindingRate = ratio(invalidFindingCount, reportedFindingCount),
            invalidResultRate = ratio(invalidResultCount, corpus.cases.size),
            structuredCompletionRate = ratio(structuredCompletionCount, corpus.cases.size),
        )
    }

    private fun metrics(counts: ExactOccurrenceCounts): ExactOccurrenceMetrics {
        val precision = ratio(counts.truePositives, counts.truePositives + counts.falsePositives)
        val recall = ratio(counts.truePositives, counts.truePositives + counts.falseNegatives)
        val f1 = if (precision + recall == 0.0) 0.0 else 2.0 * precision * recall / (precision + recall)
        return ExactOccurrenceMetrics(counts = counts, precision = precision, recall = recall, f1 = f1)
    }

    private fun exactCounts(expected: List<QualityOccurrence>, predicted: List<QualityOccurrence>): ExactOccurrenceCounts {
        val remaining = expected.groupingBy { it }.eachCount().toMutableMap()
        var truePositives = 0
        var falsePositives = 0
        predicted.forEach { occurrence ->
            val available = remaining.getOrDefault(occurrence, 0)
            if (available > 0) {
                truePositives += 1
                if (available == 1) remaining.remove(occurrence) else remaining[occurrence] = available - 1
            } else {
                falsePositives += 1
            }
        }
        return ExactOccurrenceCounts(
            truePositives = truePositives,
            falsePositives = falsePositives,
            falseNegatives = remaining.values.sum(),
        )
    }

    private fun ratio(numerator: Int, denominator: Int): Double = if (denominator == 0) 0.0 else numerator.toDouble() / denominator

    private val ZERO_COUNTS = ExactOccurrenceCounts(truePositives = 0, falsePositives = 0, falseNegatives = 0)
}
