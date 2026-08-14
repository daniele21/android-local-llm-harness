package io.github.daniele21.localllm.evaluation

enum class QualityMismatchReason {
    DATASET_DIGEST,
    SAMPLE_SET,
    EVALUATOR_SET,
    SEMANTIC_EXECUTION,
}

enum class RuntimeMismatchReason {
    QUALITY_INCOMPATIBLE,
    DEVICE_CLASS,
    ANDROID_API_LEVEL,
    ABI,
    BACKEND_REVISION,
    HARNESS_BUILD,
    RUNTIME_TUNING_PROFILE,
    MODEL_LOAD_POLICY,
    WARMUP_POLICY,
}

data class QualityCompatibility(
    val mismatchReasons: Set<QualityMismatchReason>,
) {
    val compatible: Boolean
        get() = mismatchReasons.isEmpty()
}

data class RuntimeCompatibility(
    val mismatchReasons: Set<RuntimeMismatchReason>,
) {
    val compatible: Boolean
        get() = mismatchReasons.isEmpty()
}

data class EvaluationCompatibility(
    val quality: QualityCompatibility,
    val runtime: RuntimeCompatibility,
)

enum class EvaluationFailureStage {
    PREFLIGHT,
    MODEL_PREPARATION,
    GENERATION,
    EVALUATION,
    PERSISTENCE,
    CANCELLATION,
}

enum class EvaluationFailureCode {
    INVALID_CONFIGURATION,
    UNSUPPORTED_SCHEMA_VERSION,
    DATASET_NOT_FOUND,
    DATASET_DIGEST_MISMATCH,
    SAMPLE_SET_INVALID,
    UNKNOWN_EVALUATOR,
    INVALID_EVALUATOR_PARAMETERS,
    UNSUPPORTED_EXECUTION_PROFILE,
    MODEL_NOT_INSTALLED,
    MODEL_UNSUPPORTED,
    RUNTIME_FAILURE,
    CASE_TIMEOUT,
    EVALUATOR_FAILURE,
    PERSISTENCE_FAILURE,
    PARTIAL_PERSISTENCE_FAILURE,
    CANCELLED,
}

data class EvaluationFailure(
    val stage: EvaluationFailureStage,
    val code: EvaluationFailureCode,
    val caseId: EvaluationCaseId? = null,
    val retryable: Boolean = false,
) {
    init {
        when (code) {
            EvaluationFailureCode.CASE_TIMEOUT -> require(stage == EvaluationFailureStage.GENERATION) {
                "Case timeout must be a generation-stage failure"
            }

            EvaluationFailureCode.CANCELLED -> require(stage == EvaluationFailureStage.CANCELLATION) {
                "Cancelled failure must use cancellation stage"
            }

            EvaluationFailureCode.PERSISTENCE_FAILURE,
            EvaluationFailureCode.PARTIAL_PERSISTENCE_FAILURE,
            -> require(stage == EvaluationFailureStage.PERSISTENCE) {
                "Persistence failure code must use persistence stage"
            }

            else -> Unit
        }
    }
}
