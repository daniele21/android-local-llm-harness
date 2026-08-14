package io.github.daniele21.localllm.evaluation

data class SamplingPolicyRef(val id: SamplingPolicyId, val version: Int) {
    init {
        require(version > 0) { "Sampling policy version must be positive" }
    }
}

data class SamplingSelection(
    val dataset: EvaluationDatasetIdentity,
    val policy: SamplingPolicyRef,
    val seed: Long,
    val orderedCaseIds: List<EvaluationCaseId>,
    val digest: SampleSetDigest,
) {
    init {
        require(orderedCaseIds.isNotEmpty()) { "Sampling selection must contain at least one case" }
        require(orderedCaseIds.size <= MAX_SAMPLE_CASES) { "Sampling selection exceeds $MAX_SAMPLE_CASES cases" }
        require(orderedCaseIds.distinct().size == orderedCaseIds.size) { "Sampling selection must not contain duplicate case IDs" }
        require(digest == CanonicalEvaluationHasher.sampleSetDigest(orderedCaseIds)) {
            "Sampling selection digest does not match ordered case IDs"
        }
    }

    companion object {
        fun create(
            dataset: EvaluationDatasetIdentity,
            policy: SamplingPolicyRef,
            seed: Long,
            orderedCaseIds: List<EvaluationCaseId>,
        ): SamplingSelection =
            SamplingSelection(
                dataset = dataset,
                policy = policy,
                seed = seed,
                orderedCaseIds = orderedCaseIds.toList(),
                digest = CanonicalEvaluationHasher.sampleSetDigest(orderedCaseIds),
            )
    }
}

const val MAX_SAMPLE_CASES: Int = 10_000
