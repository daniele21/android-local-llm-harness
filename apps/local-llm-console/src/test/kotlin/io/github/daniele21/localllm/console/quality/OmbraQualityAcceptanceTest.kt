package io.github.daniele21.localllm.console.quality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OmbraQualityAcceptanceTest {
    @Test
    fun `perfect score passes a pre-registered strict policy`() {
        val corpus = OmbraSyntheticQualityCorpus.load()
        val score = perfectScore(corpus)
        val strictThresholds =
            thresholds().copy(
                minAggregatePrecision = 1.0,
                minAggregateRecall = 1.0,
                minAggregateF1 = 1.0,
                minPerTypePrecision = 1.0,
                minPerTypeRecall = 1.0,
                minPerTypeF1 = 1.0,
                minStructuredCompletionRate = 1.0,
                maxInvalidFindingRate = 0.0,
                maxInvalidResultRate = 0.0,
            )
        val policy =
            QualityAcceptancePolicy(
                policyVersion = 1,
                corpusIdentity = corpus.identity,
                requiredTypeIds = score.perType.keys,
                thresholds = strictThresholds,
            )

        val report = OmbraQualityAcceptanceGate.evaluate(policy, score)

        assertTrue(report.accepted)
        assertEquals(emptyList<QualityGateFailure>(), report.failures)
    }

    @Test
    fun `corpus identity mismatch fails closed before metric evaluation`() {
        val corpus = OmbraSyntheticQualityCorpus.load()
        val score = perfectScore(corpus)
        val policy =
            QualityAcceptancePolicy(
                policyVersion = 1,
                corpusIdentity = corpus.identity.copy(corpusVersion = "different-version"),
                requiredTypeIds = score.perType.keys,
                thresholds = thresholds(),
            )

        val report = OmbraQualityAcceptanceGate.evaluate(policy, score)

        assertFalse(report.accepted)
        assertEquals(
            listOf(QualityGateFailure(QualityGateFailureCode.CORPUS_IDENTITY_MISMATCH)),
            report.failures,
        )
    }

    @Test
    fun `missing required category cannot be accepted`() {
        val corpus = OmbraSyntheticQualityCorpus.load()
        val score = perfectScore(corpus)
        val policy =
            QualityAcceptancePolicy(
                policyVersion = 1,
                corpusIdentity = corpus.identity,
                requiredTypeIds = score.perType.keys + "future-type",
                thresholds = thresholds(),
            )

        val report = OmbraQualityAcceptanceGate.evaluate(policy, score)

        assertFalse(report.accepted)
        assertEquals(
            listOf(QualityGateFailure(QualityGateFailureCode.MISSING_REQUIRED_TYPE, "future-type")),
            report.failures,
        )
    }

    @Test
    fun `aggregate per-type and structured-output thresholds fail independently`() {
        val corpus = OmbraSyntheticQualityCorpus.load()
        val base = perfectScore(corpus)
        val typeId = base.perType.keys.first()
        val degradedType = base.perType.getValue(typeId).copy(precision = 0.7, recall = 0.6, f1 = 0.64)
        val degraded =
            base.copy(
                aggregate = base.aggregate.copy(recall = 0.8),
                perType = base.perType + (typeId to degradedType),
                invalidFindingRate = 0.2,
                invalidResultRate = 0.1,
                structuredCompletionRate = 0.75,
            )
        val degradedThresholds =
            thresholds().copy(
                minAggregateRecall = 0.9,
                minPerTypePrecision = 0.8,
                minPerTypeRecall = 0.8,
                minPerTypeF1 = 0.8,
                minStructuredCompletionRate = 0.9,
                maxInvalidFindingRate = 0.1,
                maxInvalidResultRate = 0.05,
            )
        val policy =
            QualityAcceptancePolicy(
                policyVersion = 1,
                corpusIdentity = corpus.identity,
                requiredTypeIds = setOf(typeId),
                thresholds = degradedThresholds,
            )

        val report = OmbraQualityAcceptanceGate.evaluate(policy, degraded)

        assertFalse(report.accepted)
        assertEquals(
            listOf(
                QualityGateFailure(QualityGateFailureCode.AGGREGATE_RECALL_BELOW_MINIMUM),
                QualityGateFailure(QualityGateFailureCode.STRUCTURED_COMPLETION_BELOW_MINIMUM),
                QualityGateFailure(QualityGateFailureCode.INVALID_FINDING_RATE_ABOVE_MAXIMUM),
                QualityGateFailure(QualityGateFailureCode.INVALID_RESULT_RATE_ABOVE_MAXIMUM),
                QualityGateFailure(QualityGateFailureCode.PER_TYPE_PRECISION_BELOW_MINIMUM, typeId),
                QualityGateFailure(QualityGateFailureCode.PER_TYPE_RECALL_BELOW_MINIMUM, typeId),
                QualityGateFailure(QualityGateFailureCode.PER_TYPE_F1_BELOW_MINIMUM, typeId),
            ),
            report.failures,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `thresholds outside the unit interval are rejected`() {
        thresholds().copy(minAggregateRecall = 1.01)
    }

    private fun perfectScore(corpus: QualityCorpus): QualityScore {
        val outcomes =
            corpus.cases.map { case ->
                QualityCaseOutcome.Structured(caseId = case.id, findings = case.expectedOccurrences)
            }
        return OmbraExactOccurrenceScorer.score(corpus, outcomes)
    }

    private fun thresholds(): QualityThresholds = QualityThresholds(
        minAggregatePrecision = 0.0,
        minAggregateRecall = 0.0,
        minAggregateF1 = 0.0,
        minPerTypePrecision = 0.0,
        minPerTypeRecall = 0.0,
        minPerTypeF1 = 0.0,
        minStructuredCompletionRate = 0.0,
        maxInvalidFindingRate = 1.0,
        maxInvalidResultRate = 1.0,
    )
}
