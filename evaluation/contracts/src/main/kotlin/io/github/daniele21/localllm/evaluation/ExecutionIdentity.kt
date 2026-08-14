package io.github.daniele21.localllm.evaluation

import io.github.daniele21.localllm.contracts.ChatTemplateSource
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.MAX_NATIVE_SEED
import io.github.daniele21.localllm.contracts.SeedPolicyType
import io.github.daniele21.localllm.contracts.ThinkingMode

data class EvaluationExecutionProfileRef(val id: EvaluationExecutionProfileId, val version: Int) {
    init {
        require(version > 0) { "Execution profile version must be positive" }
    }
}

enum class EvaluationWarmupPolicy {
    NONE,
    ONE_UNSCORED_GENERATION,
}

enum class EvaluationModelLoadPolicy {
    PRESERVE_CURRENT_RESIDENCY,
    REQUIRE_COLD_LOAD,
}

data class CaseExecutionSemanticIdentity(val caseId: EvaluationCaseId, val outputConstraintDigest: String) {
    init {
        validateSha256(outputConstraintDigest, "Output-constraint digest")
    }
}

@Suppress("LongParameterList")
data class EvaluationSemanticExecution(
    val semanticsVersion: Int = 1,
    val profile: EvaluationExecutionProfileRef,
    val backendRevision: String,
    val contextSize: Int,
    val preset: InferencePresetRef?,
    val thinkingMode: ThinkingMode,
    val temperature: Float,
    val topP: Float,
    val topK: Int,
    val minP: Float,
    val presencePenalty: Float,
    val repeatPenalty: Float,
    val repeatLastN: Int,
    val seedPolicy: SeedPolicyType,
    val effectiveSeed: Long,
    val maxOutputTokens: Int,
    val chatTemplateId: String,
    val chatTemplateSource: ChatTemplateSource,
    val systemPromptVersion: String?,
    val caseExecutionSemanticsDigest: CaseExecutionSemanticsDigest,
) {
    init {
        require(semanticsVersion > 0) { "Semantic execution version must be positive" }
        validateStableText(backendRevision, "Backend revision", 128)
        require(contextSize > 0) { "Context size must be positive" }
        require(temperature.isFinite() && temperature >= 0f) { "Temperature must be finite and non-negative" }
        require(topP.isFinite() && topP in 0f..1f) { "Top-p must be in [0, 1]" }
        require(topK >= 0) { "Top-k must be non-negative" }
        require(minP.isFinite() && minP in 0f..1f) { "Min-p must be in [0, 1]" }
        require(presencePenalty.isFinite() && presencePenalty in 0f..2f) { "Presence penalty must be in [0, 2]" }
        require(repeatPenalty.isFinite() && repeatPenalty > 0f) { "Repeat penalty must be finite and positive" }
        require(repeatLastN >= 0) { "Repeat-last-N must be non-negative" }
        require(effectiveSeed in 0..MAX_NATIVE_SEED) { "Effective seed must fit the native seed range" }
        require(maxOutputTokens > 0) { "Max output tokens must be positive" }
        validateStableText(chatTemplateId, "Chat template ID", 128)
        validateOptionalStableText(systemPromptVersion, "System prompt version", 128)
    }
}

data class EvaluationSemanticExecutionIdentity(
    val execution: EvaluationSemanticExecution,
    val fingerprint: EvaluationSemanticExecutionFingerprint,
) {
    init {
        require(fingerprint == CanonicalEvaluationHasher.semanticExecutionFingerprint(execution)) {
            "Semantic execution fingerprint does not match execution fields"
        }
    }

    companion object {
        fun create(execution: EvaluationSemanticExecution): EvaluationSemanticExecutionIdentity = EvaluationSemanticExecutionIdentity(
            execution = execution,
            fingerprint = CanonicalEvaluationHasher.semanticExecutionFingerprint(execution),
        )
    }
}

data class EvaluationRuntimeEnvironmentIdentity(
    val deviceClass: String,
    val androidApiLevel: Int,
    val abi: String,
    val backendRevision: String,
    val harnessBuildIdentity: String,
    val runtimeTuningProfileId: String,
    val runtimeTuningProfileVersion: Int,
    val loadPolicy: EvaluationModelLoadPolicy,
    val warmupPolicy: EvaluationWarmupPolicy,
) {
    init {
        validateStableText(deviceClass, "Device class", 160)
        require(androidApiLevel > 0) { "Android API level must be positive" }
        validateStableText(abi, "ABI", 64)
        validateStableText(backendRevision, "Backend revision", 128)
        validateStableText(harnessBuildIdentity, "Harness build identity", 160)
        validateStableText(runtimeTuningProfileId, "Runtime tuning profile ID", 128)
        require(runtimeTuningProfileVersion > 0) { "Runtime tuning profile version must be positive" }
    }
}

data class EvaluationRunIdentity(
    val model: EvaluationModelIdentity,
    val dataset: EvaluationDatasetIdentity,
    val sampleSetDigest: SampleSetDigest,
    val samplingPolicy: SamplingPolicyRef,
    val samplingSeed: Long,
    val evaluatorSetDigest: EvaluatorSetDigest,
    val semanticExecution: EvaluationSemanticExecutionIdentity,
    val runtimeEnvironment: EvaluationRuntimeEnvironmentIdentity,
    val fingerprint: EvaluationRunFingerprint,
) {
    init {
        require(runtimeEnvironment.backendRevision == semanticExecution.execution.backendRevision) {
            "Runtime backend revision must match semantic execution backend revision"
        }
        require(
            fingerprint == CanonicalEvaluationHasher.runFingerprint(
                EvaluationRunIdentityUnchecked(
                    model = model,
                    dataset = dataset,
                    sampleSetDigest = sampleSetDigest,
                    samplingPolicy = samplingPolicy,
                    samplingSeed = samplingSeed,
                    evaluatorSetDigest = evaluatorSetDigest,
                    semanticExecution = semanticExecution,
                    runtimeEnvironment = runtimeEnvironment,
                ),
            ),
        ) { "Evaluation run fingerprint does not match identity fields" }
    }

    companion object {
        @Suppress("LongParameterList")
        fun create(
            model: EvaluationModelIdentity,
            dataset: EvaluationDatasetIdentity,
            sampleSetDigest: SampleSetDigest,
            samplingPolicy: SamplingPolicyRef,
            samplingSeed: Long,
            evaluatorSetDigest: EvaluatorSetDigest,
            semanticExecution: EvaluationSemanticExecutionIdentity,
            runtimeEnvironment: EvaluationRuntimeEnvironmentIdentity,
        ): EvaluationRunIdentity {
            val withoutFingerprint = EvaluationRunIdentityUnchecked(
                model = model,
                dataset = dataset,
                sampleSetDigest = sampleSetDigest,
                samplingPolicy = samplingPolicy,
                samplingSeed = samplingSeed,
                evaluatorSetDigest = evaluatorSetDigest,
                semanticExecution = semanticExecution,
                runtimeEnvironment = runtimeEnvironment,
            )
            return EvaluationRunIdentity(
                model = model,
                dataset = dataset,
                sampleSetDigest = sampleSetDigest,
                samplingPolicy = samplingPolicy,
                samplingSeed = samplingSeed,
                evaluatorSetDigest = evaluatorSetDigest,
                semanticExecution = semanticExecution,
                runtimeEnvironment = runtimeEnvironment,
                fingerprint = CanonicalEvaluationHasher.runFingerprint(withoutFingerprint),
            )
        }
    }
}

internal data class EvaluationRunIdentityUnchecked(
    val model: EvaluationModelIdentity,
    val dataset: EvaluationDatasetIdentity,
    val sampleSetDigest: SampleSetDigest,
    val samplingPolicy: SamplingPolicyRef,
    val samplingSeed: Long,
    val evaluatorSetDigest: EvaluatorSetDigest,
    val semanticExecution: EvaluationSemanticExecutionIdentity,
    val runtimeEnvironment: EvaluationRuntimeEnvironmentIdentity,
)
