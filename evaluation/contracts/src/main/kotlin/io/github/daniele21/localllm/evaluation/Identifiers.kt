package io.github.daniele21.localllm.evaluation

import io.github.daniele21.localllm.contracts.ModelDigest

@JvmInline
value class EvaluationDatasetId(val value: String) {
    init {
        validateStableText(value, "Dataset ID", 128)
    }
}

@JvmInline
value class EvaluationDatasetVersion(val value: String) {
    init {
        validateStableText(value, "Dataset version", 64)
    }
}

@JvmInline
value class EvaluationCaseId(val value: String) {
    init {
        validateStableText(value, "Case ID", 160)
    }
}

@JvmInline
value class EvaluationCategoryId(val value: String) {
    init {
        validateStableText(value, "Category ID", 128)
    }
}

@JvmInline
value class EvaluationExecutionProfileId(val value: String) {
    init {
        validateStableText(value, "Execution profile ID", 96)
    }
}

@JvmInline
value class SamplingPolicyId(val value: String) {
    init {
        validateStableText(value, "Sampling policy ID", 96)
    }
}

@JvmInline
value class EvaluationRunId(val value: String) {
    init {
        validateStableText(value, "Evaluation run ID", 160)
    }
}

@JvmInline
value class EvaluationDatasetDigest(val sha256: String) {
    init {
        validateSha256(sha256, "Dataset digest")
    }
}

@JvmInline
value class SampleSetDigest(val sha256: String) {
    init {
        validateSha256(sha256, "Sample-set digest")
    }
}

@JvmInline
value class EvaluatorSetDigest(val sha256: String) {
    init {
        validateSha256(sha256, "Evaluator-set digest")
    }
}

@JvmInline
value class CaseExecutionSemanticsDigest(val sha256: String) {
    init {
        validateSha256(sha256, "Case execution semantics digest")
    }
}

@JvmInline
value class EvaluationSemanticExecutionFingerprint(val sha256: String) {
    init {
        validateSha256(sha256, "Semantic execution fingerprint")
    }
}

@JvmInline
value class EvaluationRunFingerprint(val sha256: String) {
    init {
        validateSha256(sha256, "Evaluation run fingerprint")
    }
}

data class EvaluationDatasetIdentity(
    val id: EvaluationDatasetId,
    val version: EvaluationDatasetVersion,
    val digest: EvaluationDatasetDigest,
)

data class EvaluationModelIdentity(
    val artifactDigest: ModelDigest,
    val modelProfileId: String,
    val tier: String? = null,
    val quantization: String? = null,
) {
    init {
        validateSha256(artifactDigest.sha256, "Model artifact digest")
        validateStableText(modelProfileId, "Model profile ID", 128)
        validateOptionalStableText(tier, "Model tier", 64)
        validateOptionalStableText(quantization, "Model quantization", 64)
    }
}

internal fun validateStableText(value: String, label: String, maxLength: Int) {
    require(value.isNotBlank()) { "$label must not be blank" }
    require('\u0000' !in value) { "$label must not contain NUL" }
    require(value.length <= maxLength) { "$label must not exceed $maxLength characters" }
}

internal fun validateOptionalStableText(value: String?, label: String, maxLength: Int) {
    if (value != null) {
        validateStableText(value, label, maxLength)
    }
}

internal fun validateSha256(value: String, label: String) {
    require(SHA256_PATTERN.matches(value)) { "$label must be lowercase SHA-256" }
}

private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
