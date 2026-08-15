package io.github.daniele21.localllm.evaluation.comparison

import io.github.daniele21.localllm.evaluation.EvaluationCompatibility
import io.github.daniele21.localllm.evaluation.EvaluationRunIdentity
import io.github.daniele21.localllm.evaluation.EvaluationRunSummary
import io.github.daniele21.localllm.evaluation.QualityCompatibility
import io.github.daniele21.localllm.evaluation.QualityMismatchReason
import io.github.daniele21.localllm.evaluation.RuntimeCompatibility
import io.github.daniele21.localllm.evaluation.RuntimeMismatchReason

enum class EvaluationComparisonUnavailableReason {
    LEFT_IDENTITY_MISSING,
    RIGHT_IDENTITY_MISSING,
}

sealed interface EvaluationComparisonAssessment {
    data class Available(val compatibility: EvaluationCompatibility) : EvaluationComparisonAssessment

    data class Unavailable(val reason: EvaluationComparisonUnavailableReason) : EvaluationComparisonAssessment
}

class EvaluationCompatibilityService {
    fun compare(left: EvaluationRunSummary, right: EvaluationRunSummary): EvaluationComparisonAssessment {
        val leftIdentity = left.identity
            ?: return EvaluationComparisonAssessment.Unavailable(
                EvaluationComparisonUnavailableReason.LEFT_IDENTITY_MISSING,
            )
        val rightIdentity = right.identity
            ?: return EvaluationComparisonAssessment.Unavailable(
                EvaluationComparisonUnavailableReason.RIGHT_IDENTITY_MISSING,
            )
        return EvaluationComparisonAssessment.Available(compare(leftIdentity, rightIdentity))
    }

    fun compare(left: EvaluationRunIdentity, right: EvaluationRunIdentity): EvaluationCompatibility {
        val quality = QualityCompatibility(qualityMismatches(left, right))
        return EvaluationCompatibility(
            quality = quality,
            runtime = RuntimeCompatibility(runtimeMismatches(left, right, quality)),
        )
    }
}

private fun qualityMismatches(left: EvaluationRunIdentity, right: EvaluationRunIdentity): Set<QualityMismatchReason> = buildSet {
    if (left.dataset.digest != right.dataset.digest) add(QualityMismatchReason.DATASET_DIGEST)
    if (left.sampleSetDigest != right.sampleSetDigest) add(QualityMismatchReason.SAMPLE_SET)
    if (left.evaluatorSetDigest != right.evaluatorSetDigest) add(QualityMismatchReason.EVALUATOR_SET)
    if (left.semanticExecution.fingerprint != right.semanticExecution.fingerprint) {
        add(QualityMismatchReason.SEMANTIC_EXECUTION)
    }
}

private fun runtimeMismatches(
    left: EvaluationRunIdentity,
    right: EvaluationRunIdentity,
    quality: QualityCompatibility,
): Set<RuntimeMismatchReason> = buildSet {
    if (!quality.compatible) add(RuntimeMismatchReason.QUALITY_INCOMPATIBLE)
    val leftRuntime = left.runtimeEnvironment
    val rightRuntime = right.runtimeEnvironment
    if (leftRuntime.deviceClass != rightRuntime.deviceClass) add(RuntimeMismatchReason.DEVICE_CLASS)
    if (leftRuntime.androidApiLevel != rightRuntime.androidApiLevel) add(RuntimeMismatchReason.ANDROID_API_LEVEL)
    if (leftRuntime.abi != rightRuntime.abi) add(RuntimeMismatchReason.ABI)
    if (leftRuntime.backendRevision != rightRuntime.backendRevision) add(RuntimeMismatchReason.BACKEND_REVISION)
    if (leftRuntime.harnessBuildIdentity != rightRuntime.harnessBuildIdentity) add(RuntimeMismatchReason.HARNESS_BUILD)
    if (
        leftRuntime.runtimeTuningProfileId != rightRuntime.runtimeTuningProfileId ||
        leftRuntime.runtimeTuningProfileVersion != rightRuntime.runtimeTuningProfileVersion
    ) {
        add(RuntimeMismatchReason.RUNTIME_TUNING_PROFILE)
    }
    if (leftRuntime.loadPolicy != rightRuntime.loadPolicy) add(RuntimeMismatchReason.MODEL_LOAD_POLICY)
    if (leftRuntime.warmupPolicy != rightRuntime.warmupPolicy) add(RuntimeMismatchReason.WARMUP_POLICY)
}
