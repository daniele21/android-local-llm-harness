package io.github.daniele21.localllm.console.quality

import io.github.daniele21.localllm.console.pii.OmbraBuiltInPiiDefinitions
import org.junit.Assert.assertEquals
import org.junit.Test

class OmbraQualitySupportPolicyTest {
    @Test
    fun `v1 support policy is pinned to corpus v2 and all supported categories`() {
        val policy = OmbraQualitySupportPolicyV1.policy

        assertEquals(1, policy.policyVersion)
        assertEquals(1, policy.corpusIdentity.schemaVersion)
        assertEquals("ombra-pii-synthetic-v2", policy.corpusIdentity.corpusVersion)
        assertEquals(
            "a04f79dec42ee4208e4db27512664cc20f66cc863fd80ae4fcdc1019a2f37a5f",
            policy.corpusIdentity.sha256,
        )
        assertEquals(
            setOf(
                "full-name",
                "email",
                "telephone",
                "postal-address",
                "italian-tax-code",
                "iban",
                "custom-1",
            ),
            policy.requiredTypeIds,
        )
    }

    @Test
    fun `v1 policy identity exactly matches the active corpus`() {
        val corpus = OmbraSyntheticQualityCorpus.load()
        val policy = OmbraQualitySupportPolicyV1.policy
        val activeTypeIds =
            OmbraBuiltInPiiDefinitions.all.mapTo(linkedSetOf()) { it.id.value } + corpus.customTypeIds

        assertEquals(corpus.identity, policy.corpusIdentity)
        assertEquals(activeTypeIds, policy.requiredTypeIds)
    }

    @Test
    fun `v1 thresholds preserve recall-first release policy`() {
        val thresholds = OmbraQualitySupportPolicyV1.policy.thresholds

        assertEquals(0.90, thresholds.minAggregatePrecision, 0.0)
        assertEquals(0.98, thresholds.minAggregateRecall, 0.0)
        assertEquals(0.94, thresholds.minAggregateF1, 0.0)
        assertEquals(0.80, thresholds.minPerTypePrecision, 0.0)
        assertEquals(0.90, thresholds.minPerTypeRecall, 0.0)
        assertEquals(0.85, thresholds.minPerTypeF1, 0.0)
        assertEquals(0.98, thresholds.minStructuredCompletionRate, 0.0)
        assertEquals(0.02, thresholds.maxInvalidFindingRate, 0.0)
        assertEquals(0.0, thresholds.maxInvalidResultRate, 0.0)
    }
}
