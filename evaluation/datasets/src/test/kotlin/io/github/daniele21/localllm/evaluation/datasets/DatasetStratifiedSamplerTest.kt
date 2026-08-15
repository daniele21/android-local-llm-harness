package io.github.daniele21.localllm.evaluation.datasets

import io.github.daniele21.localllm.evaluation.EvaluationCaseId
import io.github.daniele21.localllm.evaluation.EvaluationCaseMessage
import io.github.daniele21.localllm.evaluation.EvaluationCategoryId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetCaseV1
import io.github.daniele21.localllm.evaluation.EvaluationDatasetCategoryDefinition
import io.github.daniele21.localllm.evaluation.EvaluationDatasetDigest
import io.github.daniele21.localllm.evaluation.EvaluationDatasetId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetManifestV1
import io.github.daniele21.localllm.evaluation.EvaluationDatasetOrigin
import io.github.daniele21.localllm.evaluation.EvaluationDatasetVersion
import io.github.daniele21.localllm.evaluation.EvaluationExpectedAnswer
import io.github.daniele21.localllm.evaluation.EvaluationExpectedAnswerKind
import io.github.daniele21.localllm.evaluation.EvaluationMessageRole
import io.github.daniele21.localllm.evaluation.EvaluatorSpec
import io.github.daniele21.localllm.evaluation.EvaluatorType
import io.github.daniele21.localllm.evaluation.EvaluatorVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DatasetStratifiedSamplerTest {
    private val sampler = EvaluationStratifiedSampler()

    @Test
    fun `ranking is independent of source list ordering`() {
        val cases = cases(mapOf("a" to 20, "b" to 20, "c" to 20))
        val manifest = manifest(cases, categoryWeights = emptyMap())

        val forward = sampler.rank(manifest, cases, seed = 17).orderedCaseIds
        val reversed = sampler.rank(manifest, cases.reversed(), seed = 17).orderedCaseIds

        assertEquals(forward, reversed)
    }

    @Test
    fun `seed deterministically changes ordering inside a category`() {
        val cases = cases(mapOf("a" to 40))
        val manifest = manifest(cases, categoryWeights = emptyMap())

        val first = sampler.rank(manifest, cases, seed = 1).orderedCaseIds
        val repeated = sampler.rank(manifest, cases.shuffled(), seed = 1).orderedCaseIds
        val second = sampler.rank(manifest, cases, seed = 2).orderedCaseIds

        assertEquals(first, repeated)
        assertNotEquals(first, second)
    }

    @Test
    fun `weighted categories retain declared three to one proportion in early prefix`() {
        val cases = cases(mapOf("a" to 30, "b" to 10))
        val manifest = manifest(cases, categoryWeights = mapOf("a" to 3.0, "b" to 1.0))

        val prefix = sampler.rank(manifest, cases, seed = 5).orderedCaseIds.take(8)

        assertEquals(6, prefix.count { it.value.startsWith("a-") })
        assertEquals(2, prefix.count { it.value.startsWith("b-") })
    }

    @Test
    fun `unweighted categories are evenly interleaved`() {
        val cases = cases(mapOf("a" to 8, "b" to 8, "c" to 8, "d" to 8))
        val manifest = manifest(cases, categoryWeights = emptyMap())

        val prefix = sampler.rank(manifest, cases, seed = 9).orderedCaseIds.take(8)

        listOf("a", "b", "c", "d").forEach { category ->
            assertEquals(2, prefix.count { it.value.startsWith("$category-") })
        }
    }

    @Test
    fun `selection prefixes are nested and carry canonical sample digests`() {
        val cases = cases(mapOf("a" to 40, "b" to 40, "c" to 40))
        val manifest = manifest(cases, categoryWeights = emptyMap())
        val ranking = sampler.rank(manifest, cases, seed = 99)

        val smoke = ranking.selection(20)
        val quick = ranking.selection(50)
        val standard = ranking.selection(100)

        assertEquals(smoke.orderedCaseIds, quick.orderedCaseIds.take(20))
        assertEquals(quick.orderedCaseIds, standard.orderedCaseIds.take(50))
        assertEquals("stratified-prefix", smoke.policy.id.value)
        assertEquals(1, smoke.policy.version)
        assertTrue(smoke.digest.sha256.matches(Regex("[0-9a-f]{64}")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `mixed category weight policy is rejected`() {
        val cases = cases(mapOf("a" to 4, "b" to 4))
        val manifest = manifest(cases, categoryWeights = mapOf("a" to 1.0))

        sampler.rank(manifest, cases, seed = 0)
    }

    private fun manifest(
        cases: List<EvaluationDatasetCaseV1>,
        categoryWeights: Map<String, Double>,
    ): EvaluationDatasetManifestV1 {
        val categories = cases.map { it.categoryId }.distinct().sortedBy { it.value }
        return EvaluationDatasetManifestV1(
            datasetId = EvaluationDatasetId("sampling-fixture"),
            version = EvaluationDatasetVersion("1"),
            displayName = "Sampling fixture",
            origin = EvaluationDatasetOrigin.BUILT_IN,
            caseCount = cases.size,
            contentDigest = EvaluationDatasetDigest("1".repeat(64)),
            categories = categories.map { category ->
                EvaluationDatasetCategoryDefinition(
                    id = category,
                    displayName = category.value.uppercase(),
                    weight = categoryWeights[category.value],
                )
            },
        )
    }

    private fun cases(categoryCounts: Map<String, Int>): List<EvaluationDatasetCaseV1> =
        categoryCounts.toSortedMap().flatMap { (category, count) ->
            (0 until count).map { index -> case(category, index) }
        }

    private fun case(category: String, index: Int) = EvaluationDatasetCaseV1(
        id = EvaluationCaseId("$category-${index.toString().padStart(3, '0')}"),
        categoryId = EvaluationCategoryId(category),
        messages = listOf(EvaluationCaseMessage(EvaluationMessageRole.USER, "Question $category $index")),
        expected = EvaluationExpectedAnswer(EvaluationExpectedAnswerKind.TEXT, "answer"),
        evaluator = EvaluatorSpec(EvaluatorType.EXACT_MATCH, EvaluatorVersion(1)),
    )
}
