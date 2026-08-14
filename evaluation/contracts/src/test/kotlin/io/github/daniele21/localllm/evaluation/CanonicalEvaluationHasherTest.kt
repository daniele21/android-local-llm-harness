package io.github.daniele21.localllm.evaluation

import io.github.daniele21.localllm.contracts.ChatTemplateSource
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.SeedPolicyType
import io.github.daniele21.localllm.contracts.ThinkingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CanonicalEvaluationHasherTest {
    @Test
    fun `evaluator digest is deterministic across parameter map order`() {
        val first = CaseEvaluatorIdentity(
            EvaluationCaseId("case-1"),
            EvaluatorSpec(
                EvaluatorType.EXACT_MATCH,
                EvaluatorVersion(1),
                linkedMapOf("ignoreCase" to "true", "trim" to "true"),
            ),
        )
        val second = first.copy(
            evaluator = first.evaluator.copy(parameters = linkedMapOf("trim" to "true", "ignoreCase" to "true")),
        )

        assertEquals(
            CanonicalEvaluationHasher.evaluatorSetDigest(listOf(first)),
            CanonicalEvaluationHasher.evaluatorSetDigest(listOf(second)),
        )
    }

    @Test
    fun `semantic fingerprint changes when generation semantics change`() {
        val baseline = semanticExecution()
        val baselineIdentity = EvaluationSemanticExecutionIdentity.create(baseline)
        val changedIdentity = EvaluationSemanticExecutionIdentity.create(baseline.copy(thinkingMode = ThinkingMode.ENABLED))

        assertNotEquals(baselineIdentity.fingerprint, changedIdentity.fingerprint)
    }

    @Test
    fun `run fingerprint includes sampling policy seed runtime environment and model identity`() {
        val baseline = runIdentity()
        val changedRuntime = EvaluationRunIdentity.create(
            model = baseline.model,
            dataset = baseline.dataset,
            sampleSetDigest = baseline.sampleSetDigest,
            samplingPolicy = baseline.samplingPolicy,
            samplingSeed = baseline.samplingSeed,
            evaluatorSetDigest = baseline.evaluatorSetDigest,
            semanticExecution = baseline.semanticExecution,
            runtimeEnvironment = baseline.runtimeEnvironment.copy(deviceClass = "pixel-class-b"),
        )
        val changedModel = EvaluationRunIdentity.create(
            model = baseline.model.copy(artifactDigest = ModelDigest("b".repeat(64))),
            dataset = baseline.dataset,
            sampleSetDigest = baseline.sampleSetDigest,
            samplingPolicy = baseline.samplingPolicy,
            samplingSeed = baseline.samplingSeed,
            evaluatorSetDigest = baseline.evaluatorSetDigest,
            semanticExecution = baseline.semanticExecution,
            runtimeEnvironment = baseline.runtimeEnvironment,
        )
        val changedSamplingSeed = EvaluationRunIdentity.create(
            model = baseline.model,
            dataset = baseline.dataset,
            sampleSetDigest = baseline.sampleSetDigest,
            samplingPolicy = baseline.samplingPolicy,
            samplingSeed = baseline.samplingSeed + 1,
            evaluatorSetDigest = baseline.evaluatorSetDigest,
            semanticExecution = baseline.semanticExecution,
            runtimeEnvironment = baseline.runtimeEnvironment,
        )

        assertNotEquals(baseline.fingerprint, changedRuntime.fingerprint)
        assertNotEquals(baseline.fingerprint, changedModel.fingerprint)
        assertNotEquals(baseline.fingerprint, changedSamplingSeed.fingerprint)
    }

    private fun runIdentity(): EvaluationRunIdentity = EvaluationRunIdentity.create(
        model = EvaluationModelIdentity(
            artifactDigest = ModelDigest("a".repeat(64)),
            modelProfileId = "qwen35-08b-q4-k-m",
            tier = "0.8B",
            quantization = "Q4_K_M",
        ),
        dataset = EvaluationDatasetIdentity(
            EvaluationDatasetId("general-purpose"),
            EvaluationDatasetVersion("1.0.0"),
            EvaluationDatasetDigest("1".repeat(64)),
        ),
        sampleSetDigest = SampleSetDigest("2".repeat(64)),
        samplingPolicy = SamplingPolicyRef(SamplingPolicyId("stratified"), 1),
        samplingSeed = 7L,
        evaluatorSetDigest = EvaluatorSetDigest("3".repeat(64)),
        semanticExecution = EvaluationSemanticExecutionIdentity.create(semanticExecution()),
        runtimeEnvironment = EvaluationRuntimeEnvironmentIdentity(
            deviceClass = "pixel-class-a",
            androidApiLevel = 36,
            abi = "arm64-v8a",
            backendRevision = "backend-rev",
            harnessBuildIdentity = "commit-123",
            runtimeTuningProfileId = "q35-08b-candidate",
            runtimeTuningProfileVersion = 1,
            loadPolicy = EvaluationModelLoadPolicy.PRESERVE_CURRENT_RESIDENCY,
            warmupPolicy = EvaluationWarmupPolicy.NONE,
        ),
    )

    private fun semanticExecution(): EvaluationSemanticExecution = EvaluationSemanticExecution(
        profile = EvaluationExecutionProfileRef(EvaluationExecutionProfileId("direct-deterministic"), 1),
        backendRevision = "backend-rev",
        contextSize = 2_048,
        preset = InferencePresetRef(InferencePresetId("precise"), 1),
        thinkingMode = ThinkingMode.DISABLED,
        temperature = 0f,
        topP = 1f,
        topK = 1,
        minP = 0f,
        presencePenalty = 0f,
        repeatPenalty = 1f,
        repeatLastN = 64,
        seedPolicy = SeedPolicyType.FIXED,
        effectiveSeed = 7L,
        maxOutputTokens = 256,
        chatTemplateId = "qwen35",
        chatTemplateSource = ChatTemplateSource.GGUF,
        systemPromptVersion = "eval-v1",
        caseExecutionSemanticsDigest = CaseExecutionSemanticsDigest("4".repeat(64)),
    )
}
