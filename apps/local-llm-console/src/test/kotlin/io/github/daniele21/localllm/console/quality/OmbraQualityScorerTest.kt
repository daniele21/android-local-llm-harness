package io.github.daniele21.localllm.console.quality

import org.junit.Assert.assertEquals
import org.junit.Test

class OmbraQualityScorerTest {
    @Test
    fun `perfect exact occurrences score one for every type and aggregate`() {
        val corpus = OmbraSyntheticQualityCorpus.load()
        val outcomes = corpus.cases.map { case ->
            QualityCaseOutcome.Structured(caseId = case.id, findings = case.expectedOccurrences)
        }

        val score = OmbraExactOccurrenceScorer.score(corpus, outcomes)

        assertEquals(1.0, score.aggregate.precision, 0.0)
        assertEquals(1.0, score.aggregate.recall, 0.0)
        assertEquals(1.0, score.aggregate.f1, 0.0)
        assertEquals(1.0, score.structuredCompletionRate, 0.0)
        assertEquals(0.0, score.invalidFindingRate, 0.0)
        assertEquals(0.0, score.invalidResultRate, 0.0)
        assertEquals(corpus.identity, score.corpusIdentity)
        assertEquals(setOf("full-name", "email", "telephone", "postal-address", "italian-tax-code", "iban", "custom-1"), score.perType.keys)
        assertEquals(emptyList<String>(), score.perType.filterValues { it.f1 != 1.0 }.keys.toList())
    }

    @Test
    fun `duplicate prediction is false positive while missed repetition is false negative`() {
        val source = OmbraSyntheticQualityCorpus.load()
        val repeated = source.cases.single { QualityCaseTag.REPEATED in it.tags }
        val corpus = source.subset(repeated)
        val first = repeated.expectedOccurrences.first()

        val score =
            OmbraExactOccurrenceScorer.score(
                corpus,
                listOf(QualityCaseOutcome.Structured(repeated.id, findings = listOf(first, first))),
            )

        assertEquals(ExactOccurrenceCounts(truePositives = 1, falsePositives = 1, falseNegatives = 1), score.aggregate.counts)
        assertEquals(0.5, score.aggregate.precision, 0.0)
        assertEquals(0.5, score.aggregate.recall, 0.0)
        assertEquals(0.5, score.aggregate.f1, 0.0)
    }

    @Test
    fun `same range under overlapping types remains two exact occurrences`() {
        val source = OmbraSyntheticQualityCorpus.load()
        val overlap = source.cases.single { QualityCaseTag.OVERLAP in it.tags }
        val corpus = source.subset(overlap)

        val score =
            OmbraExactOccurrenceScorer.score(
                corpus,
                listOf(QualityCaseOutcome.Structured(overlap.id, overlap.expectedOccurrences)),
            )

        assertEquals(ExactOccurrenceCounts(truePositives = 2, falsePositives = 0, falseNegatives = 0), score.aggregate.counts)
        assertEquals(1, score.perType.getValue("full-name").counts.truePositives)
        assertEquals(1, score.perType.getValue("custom-1").counts.truePositives)
    }

    @Test
    fun `identical coordinates in different cases cannot match across case boundaries`() {
        val source = OmbraSyntheticQualityCorpus.load()
        val positive = source.cases.single { it.id == "custom-positive" }
        val negative =
            positive.copy(
                id = "same-coordinates-negative",
                tags = setOf(QualityCaseTag.NEGATIVE),
                expectedOccurrences = emptyList(),
            )
        val corpus = source.subset(positive, negative)
        val misplaced = positive.expectedOccurrences.single()

        val score =
            OmbraExactOccurrenceScorer.score(
                corpus,
                listOf(
                    QualityCaseOutcome.Structured(positive.id, findings = emptyList()),
                    QualityCaseOutcome.Structured(negative.id, findings = listOf(misplaced)),
                ),
            )

        assertEquals(ExactOccurrenceCounts(truePositives = 0, falsePositives = 1, falseNegatives = 1), score.aggregate.counts)
    }

    @Test
    fun `invalid findings results and incomplete outputs remain separate rates`() {
        val source = OmbraSyntheticQualityCorpus.load()
        val positive = source.cases.first { QualityCaseTag.BUILT_IN in it.tags }
        val custom = source.cases.first { QualityCaseTag.CUSTOM in it.tags }
        val noPii = source.cases.first { QualityCaseTag.NO_PII in it.tags }
        val corpus = source.subset(positive, custom, noPii)
        val outcomes =
            listOf(
                QualityCaseOutcome.Structured(
                    caseId = positive.id,
                    findings = positive.expectedOccurrences,
                    invalidFindingCount = positive.expectedOccurrences.size,
                ),
                QualityCaseOutcome.InvalidResult(custom.id),
                QualityCaseOutcome.Incomplete(noPii.id),
            )

        val score = OmbraExactOccurrenceScorer.score(corpus, outcomes)

        assertEquals(0.5, score.invalidFindingRate, 0.0)
        assertEquals(1.0 / 3.0, score.invalidResultRate, 0.0)
        assertEquals(1.0 / 3.0, score.structuredCompletionRate, 0.0)
        assertEquals(custom.expectedOccurrences.size, score.aggregate.counts.falseNegatives)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `scoring rejects missing case outcomes`() {
        val corpus = OmbraSyntheticQualityCorpus.load()

        OmbraExactOccurrenceScorer.score(corpus, emptyList())
    }

    private fun QualityCorpus.subset(vararg selectedCases: QualityCase): QualityCorpus = copy(
        identity = identity.copy(corpusVersion = "${identity.corpusVersion}-test"),
        cases = selectedCases.toList(),
    )
}
