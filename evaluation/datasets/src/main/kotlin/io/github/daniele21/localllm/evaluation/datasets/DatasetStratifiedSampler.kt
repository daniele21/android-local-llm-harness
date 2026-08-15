package io.github.daniele21.localllm.evaluation.datasets

import io.github.daniele21.localllm.evaluation.EvaluationCaseId
import io.github.daniele21.localllm.evaluation.EvaluationCategoryId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetCaseV1
import io.github.daniele21.localllm.evaluation.EvaluationDatasetIdentity
import io.github.daniele21.localllm.evaluation.EvaluationDatasetManifestV1
import io.github.daniele21.localllm.evaluation.SamplingPolicyId
import io.github.daniele21.localllm.evaluation.SamplingPolicyRef
import io.github.daniele21.localllm.evaluation.SamplingSelection
import java.math.BigDecimal
import java.math.MathContext
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class EvaluationStratifiedSampler(
    val policy: SamplingPolicyRef = SamplingPolicyRef(POLICY_ID, POLICY_VERSION),
) {
    fun rank(
        manifest: EvaluationDatasetManifestV1,
        cases: List<EvaluationDatasetCaseV1>,
        seed: Long,
    ): StratifiedSamplingRanking {
        require(cases.isNotEmpty()) { "Sampling requires at least one case" }
        require(cases.size == manifest.caseCount) { "Sampling case count must match manifest" }
        require(cases.map { it.id }.distinct().size == cases.size) { "Sampling case IDs must be unique" }

        val categories = manifest.categories.associateBy { it.id }
        require(cases.all { it.categoryId in categories }) { "Sampling cases must reference declared categories" }

        val weightedCount = manifest.categories.count { it.weight != null }
        require(weightedCount == 0 || weightedCount == manifest.categories.size) {
            "Sampling requires either all category weights or no category weights"
        }

        val candidates = cases
            .groupBy { it.categoryId }
            .toSortedMap(compareBy(EvaluationCategoryId::value))
            .flatMap { (categoryId, categoryCases) ->
                val category = requireNotNull(categories[categoryId])
                val weight = category.weight?.let(BigDecimal::valueOf) ?: BigDecimal.ONE
                val rankedCases = categoryCases.sortedWith(
                    compareBy<EvaluationDatasetCaseV1> {
                        stableCaseRank(manifest, seed, categoryId, it.id)
                    }.thenBy { it.id.value },
                )
                rankedCases.mapIndexed { index, case ->
                    RankedCandidate(
                        caseId = case.id,
                        categoryId = categoryId,
                        virtualPosition = virtualPosition(index, weight),
                    )
                }
            }
            .sortedWith(
                compareBy<RankedCandidate> { it.virtualPosition }
                    .thenBy { it.categoryId.value }
                    .thenBy { it.caseId.value },
            )

        return StratifiedSamplingRanking(
            dataset = manifest.identity,
            policy = policy,
            seed = seed,
            orderedCaseIds = candidates.map(RankedCandidate::caseId),
        )
    }

    private fun stableCaseRank(
        manifest: EvaluationDatasetManifestV1,
        seed: Long,
        categoryId: EvaluationCategoryId,
        caseId: EvaluationCaseId,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(manifest.contentDigest.sha256.toByteArray(StandardCharsets.US_ASCII))
        digest.update(0.toByte())
        digest.update(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(seed).array())
        digest.update(0.toByte())
        digest.update(categoryId.value.toByteArray(StandardCharsets.UTF_8))
        digest.update(0.toByte())
        digest.update(caseId.value.toByteArray(StandardCharsets.UTF_8))
        return digest.digest().toStableHex()
    }

    private fun virtualPosition(index: Int, weight: BigDecimal): BigDecimal {
        val numerator = BigDecimal.valueOf(index.toLong() * 2L + 1L)
        return numerator.divide(weight.multiply(TWO), MathContext.DECIMAL128)
    }

    private companion object {
        val POLICY_ID = SamplingPolicyId("stratified-prefix")
        const val POLICY_VERSION = 1
        val TWO: BigDecimal = BigDecimal.valueOf(2L)
    }
}

data class StratifiedSamplingRanking(
    val dataset: EvaluationDatasetIdentity,
    val policy: SamplingPolicyRef,
    val seed: Long,
    val orderedCaseIds: List<EvaluationCaseId>,
) {
    init {
        require(orderedCaseIds.isNotEmpty()) { "Sampling ranking must contain at least one case" }
        require(orderedCaseIds.distinct().size == orderedCaseIds.size) { "Sampling ranking case IDs must be unique" }
    }

    fun selection(count: Int): SamplingSelection {
        require(count in 1..orderedCaseIds.size) { "Sample count must be within the ranked case count" }
        return SamplingSelection.create(
            dataset = dataset,
            policy = policy,
            seed = seed,
            orderedCaseIds = orderedCaseIds.take(count),
        )
    }
}

private data class RankedCandidate(
    val caseId: EvaluationCaseId,
    val categoryId: EvaluationCategoryId,
    val virtualPosition: BigDecimal,
)

private fun ByteArray.toStableHex(): String = buildString(size * 2) {
    for (byte in this@toStableHex) {
        val value = byte.toInt() and 0xFF
        append(HEX[value ushr 4])
        append(HEX[value and 0x0F])
    }
}

private const val HEX = "0123456789abcdef"
