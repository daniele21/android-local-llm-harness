package io.github.daniele21.localllm.evaluation.comparison

import io.github.daniele21.localllm.contracts.ChatTemplateSource
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.SeedPolicyType
import io.github.daniele21.localllm.contracts.ThinkingMode
import io.github.daniele21.localllm.evaluation.CaseExecutionSemanticsDigest
import io.github.daniele21.localllm.evaluation.EvaluationDatasetDigest
import io.github.daniele21.localllm.evaluation.EvaluationDatasetId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetIdentity
import io.github.daniele21.localllm.evaluation.EvaluationDatasetVersion
import io.github.daniele21.localllm.evaluation.EvaluationExecutionProfileId
import io.github.daniele21.localllm.evaluation.EvaluationExecutionProfileRef
import io.github.daniele21.localllm.evaluation.EvaluationModelIdentity
import io.github.daniele21.localllm.evaluation.EvaluationModelLoadPolicy
import io.github.daniele21.localllm.evaluation.EvaluationRunIdentity
import io.github.daniele21.localllm.evaluation.EvaluationRuntimeEnvironmentIdentity
import io.github.daniele21.localllm.evaluation.EvaluationSemanticExecution
import io.github.daniele21.localllm.evaluation.EvaluationSemanticExecutionIdentity
import io.github.daniele21.localllm.evaluation.EvaluationWarmupPolicy
import io.github.daniele21.localllm.evaluation.EvaluatorSetDigest
import io.github.daniele21.localllm.evaluation.QualityMismatchReason
import io.github.daniele21.localllm.evaluation.RuntimeMismatchReason
import io.github.daniele21.localllm.evaluation.SampleSetDigest
import io.github.daniele21.localllm.evaluation.SamplingPolicyId
import io.github.daniele21.localllm.evaluation.SamplingPolicyRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluationCompatibilityServiceTest {
    private val service = EvaluationCompatibilityService()

    @Test
    fun identicalWorkAndRuntimeAreFullyCompatible() {
        val compatibility = service.compare(identity(), identity())

        assertTrue(compatibility.quality.compatible)
        assertTrue(compatibility.runtime.compatible)
    }

    @Test
    fun differentModelDoesNotInvalidateSameQualityWorkOrRuntimeEnvelope() {
        val left = identity(modelDigest = "a".repeat(64))
        val right = identity(modelDigest = "b".repeat(64))

        val compatibility = service.compare(left, right)

        assertTrue(compatibility.quality.compatible)
        assertTrue(compatibility.runtime.compatible)
    }

    @Test
    fun datasetAndSampleChangesProduceTypedQualityMismatches() {
        val left = identity()
        val right = identity(datasetDigest = "9".repeat(64), sampleDigest = "8".repeat(64))

        val compatibility = service.compare(left, right)

        assertEquals(
            setOf(QualityMismatchReason.DATASET_DIGEST, QualityMismatchReason.SAMPLE_SET),
            compatibility.quality.mismatchReasons,
        )
        assertEquals(
            setOf(RuntimeMismatchReason.QUALITY_INCOMPATIBLE),
            compatibility.runtime.mismatchReasons,
        )
    }

    @Test
    fun semanticExecutionMismatchBlocksQualityAndRuntimeComparison() {
        val left = identity(contextSize = 2_048)
        val right = identity(contextSize = 4_096)

        val compatibility = service.compare(left, right)

        assertEquals(setOf(QualityMismatchReason.SEMANTIC_EXECUTION), compatibility.quality.mismatchReasons)
        assertTrue(RuntimeMismatchReason.QUALITY_INCOMPATIBLE in compatibility.runtime.mismatchReasons)
    }

    @Test
    fun runtimeDifferencesRemainSeparateWhenQualityIsCompatible() {
        val left = identity(deviceClass = "class-a", tuningVersion = 1)
        val right = identity(deviceClass = "class-b", tuningVersion = 2)

        val compatibility = service.compare(left, right)

        assertTrue(compatibility.quality.compatible)
        assertFalse(compatibility.runtime.compatible)
        assertEquals(
            setOf(RuntimeMismatchReason.DEVICE_CLASS, RuntimeMismatchReason.RUNTIME_TUNING_PROFILE),
            compatibility.runtime.mismatchReasons,
        )
    }

    private fun identity(
        modelDigest: String = "a".repeat(64),
        datasetDigest: String = "1".repeat(64),
        sampleDigest: String = "2".repeat(64),
        contextSize: Int = 2_048,
        deviceClass: String = "class-a",
        tuningVersion: Int = 1,
    ): EvaluationRunIdentity {
        val semantic = EvaluationSemanticExecutionIdentity.create(
            EvaluationSemanticExecution(
                profile = EvaluationExecutionProfileRef(EvaluationExecutionProfileId("direct"), 1),
                backendRevision = "backend-rev",
                contextSize = contextSize,
                preset = null,
                thinkingMode = ThinkingMode.DISABLED,
                temperature = 0f,
                topP = 1f,
                topK = 1,
                minP = 0f,
                presencePenalty = 0f,
                repeatPenalty = 1f,
                repeatLastN = 64,
                seedPolicy = SeedPolicyType.FIXED,
                effectiveSeed = 7,
                maxOutputTokens = 128,
                chatTemplateId = "qwen35",
                chatTemplateSource = ChatTemplateSource.GGUF,
                systemPromptVersion = "eval-v1",
                caseExecutionSemanticsDigest = CaseExecutionSemanticsDigest("4".repeat(64)),
            ),
        )
        return EvaluationRunIdentity.create(
            model = EvaluationModelIdentity(
                artifactDigest = ModelDigest(modelDigest),
                modelProfileId = "candidate",
            ),
            dataset = EvaluationDatasetIdentity(
                id = EvaluationDatasetId("general-purpose"),
                version = EvaluationDatasetVersion("1.0.0"),
                digest = EvaluationDatasetDigest(datasetDigest),
            ),
            sampleSetDigest = SampleSetDigest(sampleDigest),
            samplingPolicy = SamplingPolicyRef(SamplingPolicyId("fixed"), 1),
            samplingSeed = 7,
            evaluatorSetDigest = EvaluatorSetDigest("3".repeat(64)),
            semanticExecution = semantic,
            runtimeEnvironment = EvaluationRuntimeEnvironmentIdentity(
                deviceClass = deviceClass,
                androidApiLevel = 36,
                abi = "arm64-v8a",
                backendRevision = "backend-rev",
                harnessBuildIdentity = "build-a",
                runtimeTuningProfileId = "candidate-profile",
                runtimeTuningProfileVersion = tuningVersion,
                loadPolicy = EvaluationModelLoadPolicy.PRESERVE_CURRENT_RESIDENCY,
                warmupPolicy = EvaluationWarmupPolicy.NONE,
            ),
        )
    }
}
