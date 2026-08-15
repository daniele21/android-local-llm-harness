package io.github.daniele21.localllm.console.quality

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OmbraQualityEvidenceTest {
    @Test
    fun `serialized evidence contains identities and metrics but no source surfaces`() {
        val corpus = OmbraSyntheticQualityCorpus.load()
        val outcomes =
            corpus.cases.map { case ->
                QualityCaseOutcome.Structured(caseId = case.id, findings = case.expectedOccurrences)
            }
        val score = OmbraExactOccurrenceScorer.score(corpus, outcomes)
        val policy =
            QualityAcceptancePolicy(
                policyVersion = 99,
                corpusIdentity = corpus.identity,
                requiredTypeIds = score.perType.keys,
                thresholds = permissiveThresholds(),
            )
        val model =
            QualityModelArtifactIdentity(
                modelId = "synthetic-model-fixture",
                artifactSha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            )

        val serialized = OmbraQualityEvidence.serialize(OmbraQualityEvidence.capture(model, policy, score))

        assertTrue(serialized.contains("#modelId=synthetic-model-fixture"))
        assertTrue(serialized.contains("#corpusVersion=${corpus.identity.corpusVersion}"))
        assertTrue(serialized.contains("#accepted=true"))
        assertTrue(serialized.contains("aggregate\t-"))
        score.perType.keys.forEach { typeId -> assertTrue(serialized.contains("type\t$typeId\t")) }
        corpus.cases.flatMap(QualityCase::segments).forEach { segment ->
            assertFalse("Evidence must not contain source segment text", serialized.contains(segment.text))
        }
        corpus.cases.flatMap(QualityCase::expectedOccurrences).forEach { occurrence ->
            assertFalse("Evidence must not contain finding surfaces", serialized.contains(occurrence.surface))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `model evidence rejects free-form identifiers`() {
        QualityModelArtifactIdentity(
            modelId = "model id with spaces",
            artifactSha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        )
    }

    private fun permissiveThresholds(): QualityThresholds = QualityThresholds(
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
