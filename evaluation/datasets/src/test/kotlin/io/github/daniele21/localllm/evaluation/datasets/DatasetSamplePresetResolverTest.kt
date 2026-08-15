package io.github.daniele21.localllm.evaluation.datasets

import io.github.daniele21.localllm.evaluation.EvaluationCaseId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetDigest
import io.github.daniele21.localllm.evaluation.EvaluationDatasetId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetIdentity
import io.github.daniele21.localllm.evaluation.EvaluationDatasetVersion
import io.github.daniele21.localllm.evaluation.SamplingPolicyId
import io.github.daniele21.localllm.evaluation.SamplingPolicyRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DatasetSamplePresetResolverTest {
    @Test
    fun `named presets resolve to deterministic ranking prefixes`() {
        val ranking = ranking(200)

        val smoke = resolved(ranking, EvaluationSampleRequest.Preset(EvaluationSamplePreset.SMOKE))
        val quick = resolved(ranking, EvaluationSampleRequest.Preset(EvaluationSamplePreset.QUICK))
        val standard = resolved(ranking, EvaluationSampleRequest.Preset(EvaluationSamplePreset.STANDARD))
        val extended = resolved(ranking, EvaluationSampleRequest.Preset(EvaluationSamplePreset.EXTENDED))

        assertEquals(20, smoke.orderedCaseIds.size)
        assertEquals(50, quick.orderedCaseIds.size)
        assertEquals(100, standard.orderedCaseIds.size)
        assertEquals(200, extended.orderedCaseIds.size)
        assertEquals(smoke.orderedCaseIds, quick.orderedCaseIds.take(20))
        assertEquals(quick.orderedCaseIds, standard.orderedCaseIds.take(50))
        assertEquals(standard.orderedCaseIds, extended.orderedCaseIds.take(100))
    }

    @Test
    fun `all resolves the complete ranking`() {
        val ranking = ranking(73)

        val result = resolved(ranking, EvaluationSampleRequest.All)

        assertEquals(ranking.orderedCaseIds, result.orderedCaseIds)
    }

    @Test
    fun `custom multiple of ten resolves when available`() {
        val ranking = ranking(80)

        val result = resolved(ranking, EvaluationSampleRequest.Custom(60))

        assertEquals(60, result.orderedCaseIds.size)
        assertEquals(ranking.orderedCaseIds.take(60), result.orderedCaseIds)
    }

    @Test
    fun `preset larger than dataset is explicitly unavailable`() {
        val result = EvaluationSamplePresetResolver.resolve(
            ranking = ranking(40),
            request = EvaluationSampleRequest.Preset(EvaluationSamplePreset.QUICK),
        ) as DatasetSampleResolution.Unavailable

        assertEquals(DatasetSampleUnavailableReason.REQUESTED_COUNT_EXCEEDS_DATASET, result.reason)
        assertEquals(50, result.requestedCount)
        assertEquals(40, result.availableCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `custom count must use increments of ten`() {
        EvaluationSampleRequest.Custom(25)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `custom count must be positive`() {
        EvaluationSampleRequest.Custom(0)
    }

    @Test
    fun `different resolved counts keep canonical digest identity`() {
        val ranking = ranking(200)

        val first = resolved(ranking, EvaluationSampleRequest.Preset(EvaluationSamplePreset.SMOKE))
        val second = resolved(ranking, EvaluationSampleRequest.Preset(EvaluationSamplePreset.STANDARD))

        assertTrue(first.digest.sha256.matches(Regex("[0-9a-f]{64}")))
        assertTrue(second.digest.sha256.matches(Regex("[0-9a-f]{64}")))
        assertTrue(first.digest != second.digest)
    }

    private fun resolved(ranking: StratifiedSamplingRanking, request: EvaluationSampleRequest) =
        (EvaluationSamplePresetResolver.resolve(ranking, request) as DatasetSampleResolution.Resolved).selection

    private fun ranking(count: Int) = StratifiedSamplingRanking(
        dataset = EvaluationDatasetIdentity(
            id = EvaluationDatasetId("fixture"),
            version = EvaluationDatasetVersion("1"),
            digest = EvaluationDatasetDigest("a".repeat(64)),
        ),
        policy = SamplingPolicyRef(SamplingPolicyId("stratified-prefix"), 1),
        seed = 42,
        orderedCaseIds = (1..count).map { EvaluationCaseId("case-${it.toString().padStart(3, '0')}") },
    )
}
