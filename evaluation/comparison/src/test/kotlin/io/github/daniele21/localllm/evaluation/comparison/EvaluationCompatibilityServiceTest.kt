package io.github.daniele21.localllm.evaluation.comparison

import io.github.daniele21.localllm.contracts.ChatTemplateSource
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.SeedPolicyType
import io.github.daniele21.localllm.contracts.ThinkingMode
import io.github.daniele21.localllm.evaluation.CaseExecutionSemanticsDigest
import io.github.daniele21.localllm.evaluation.EvaluationCaseId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetDigest
import io.github.daniele21.localllm.evaluation.EvaluationDatasetId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetIdentity
import io.github.daniele21.localllm.evaluation.EvaluationDatasetVersion
import io.github.daniele21.localllm.evaluation.EvaluationExecutionProfileId
import io.github.daniele21.localllm.evaluation.EvaluationExecutionProfileRef
import io.github.daniele21.localllm.evaluation.EvaluationModelIdentity
import io.github.daniele21.localllm.evaluation.EvaluationModelLoadPolicy
import io.github.daniele21.localllm.evaluation.EvaluationProgress
import io.github.daniele21.localllm.evaluation.EvaluationRunConfig
import io.github.daniele21.localllm.evaluation.EvaluationRunId
import io.github.daniele21.localllm.evaluation.EvaluationRunIdentity
import io.github.daniele21.localllm.evaluation.EvaluationRunState
import io.github.daniele21.localllm.evaluation.EvaluationRunSummary
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
import io.github.daniele21.localllm.evaluation.SamplingSelection
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
    fun evaluatorSetMismatchBlocksQualityAndRuntimeComparison() {
        val compatibility = service.compare(identity(), identity(evaluatorSetDigest = "5".repeat(64)))

        assertEquals(setOf(QualityMismatchReason.EVALUATOR_SET), compatibility.quality.mismatchReasons)
        assertEquals(setOf(RuntimeMismatchReason.QUALITY_INCOMPATIBLE), compatibility.runtime.mismatchReasons)
    }

    @Test
    fun backendRevisionMismatchIsBothSemanticAndRuntimeIncompatible() {
        val compatibility = service.compare(identity(), identity(backendRevision = "backend-other"))

        assertEquals(setOf(QualityMismatchReason.SEMANTIC_EXECUTION), compatibility.quality.mismatchReasons)
        assertEquals(
            setOf(RuntimeMismatchReason.QUALITY_INCOMPATIBLE, RuntimeMismatchReason.BACKEND_REVISION),
            compatibility.runtime.mismatchReasons,
        )
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

    @Test
    fun everyRuntimeIdentityDimensionProducesItsTypedMismatch() {
        val left = identity()
        val cases = listOf(
            identity(androidApiLevel = 35) to RuntimeMismatchReason.ANDROID_API_LEVEL,
            identity(abi = "x86_64") to RuntimeMismatchReason.ABI,
            identity(harnessBuildIdentity = "build-b") to RuntimeMismatchReason.HARNESS_BUILD,
            identity(tuningProfileId = "profile-b") to RuntimeMismatchReason.RUNTIME_TUNING_PROFILE,
            identity(loadPolicy = EvaluationModelLoadPolicy.REQUIRE_COLD_LOAD) to RuntimeMismatchReason.MODEL_LOAD_POLICY,
            identity(warmupPolicy = EvaluationWarmupPolicy.ONE_UNSCORED_GENERATION) to RuntimeMismatchReason.WARMUP_POLICY,
        )

        cases.forEach { (right, expectedReason) ->
            val compatibility = service.compare(left, right)

            assertTrue(compatibility.quality.compatible)
            assertEquals(setOf(expectedReason), compatibility.runtime.mismatchReasons)
        }
    }

    @Test
    fun missingPersistedIdentityIsTypedForEitherSide() {
        val leftMissing = service.compare(summary(includeIdentity = false), summary(includeIdentity = true))
        val rightMissing = service.compare(summary(includeIdentity = true), summary(includeIdentity = false))

        assertEquals(
            EvaluationComparisonAssessment.Unavailable(EvaluationComparisonUnavailableReason.LEFT_IDENTITY_MISSING),
            leftMissing,
        )
        assertEquals(
            EvaluationComparisonAssessment.Unavailable(EvaluationComparisonUnavailableReason.RIGHT_IDENTITY_MISSING),
            rightMissing,
        )
    }

    @Suppress("LongParameterList")
    private fun identity(
        modelDigest: String = "a".repeat(64),
        datasetDigest: String = "1".repeat(64),
        sampleDigest: String = "2".repeat(64),
        evaluatorSetDigest: String = "3".repeat(64),
        contextSize: Int = 2_048,
        deviceClass: String = "class-a",
        androidApiLevel: Int = 36,
        abi: String = "arm64-v8a",
        backendRevision: String = "backend-rev",
        harnessBuildIdentity: String = "build-a",
        tuningProfileId: String = "candidate-profile",
        tuningVersion: Int = 1,
        loadPolicy: EvaluationModelLoadPolicy = EvaluationModelLoadPolicy.PRESERVE_CURRENT_RESIDENCY,
        warmupPolicy: EvaluationWarmupPolicy = EvaluationWarmupPolicy.NONE,
    ): EvaluationRunIdentity {
        val semantic = EvaluationSemanticExecutionIdentity.create(
            EvaluationSemanticExecution(
                profile = EvaluationExecutionProfileRef(EvaluationExecutionProfileId("direct"), 1),
                backendRevision = backendRevision,
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
            evaluatorSetDigest = EvaluatorSetDigest(evaluatorSetDigest),
            semanticExecution = semantic,
            runtimeEnvironment = EvaluationRuntimeEnvironmentIdentity(
                deviceClass = deviceClass,
                androidApiLevel = androidApiLevel,
                abi = abi,
                backendRevision = backendRevision,
                harnessBuildIdentity = harnessBuildIdentity,
                runtimeTuningProfileId = tuningProfileId,
                runtimeTuningProfileVersion = tuningVersion,
                loadPolicy = loadPolicy,
                warmupPolicy = warmupPolicy,
            ),
        )
    }

    private fun summary(includeIdentity: Boolean): EvaluationRunSummary {
        val runId = EvaluationRunId(if (includeIdentity) "with-identity" else "without-identity")
        val dataset = EvaluationDatasetIdentity(
            id = EvaluationDatasetId("general-purpose"),
            version = EvaluationDatasetVersion("1.0.0"),
            digest = EvaluationDatasetDigest("1".repeat(64)),
        )
        val sampling = SamplingSelection.create(
            dataset = dataset,
            policy = SamplingPolicyRef(SamplingPolicyId("fixed"), 1),
            seed = 7,
            orderedCaseIds = listOf(EvaluationCaseId("case-1")),
        )
        val resolvedIdentity = identity(sampleDigest = sampling.digest.sha256)
        val config = EvaluationRunConfig(
            runId = runId,
            model = resolvedIdentity.model,
            dataset = dataset,
            sampling = sampling,
            executionProfile = resolvedIdentity.semanticExecution.execution.profile,
            loadPolicy = resolvedIdentity.runtimeEnvironment.loadPolicy,
            warmupPolicy = resolvedIdentity.runtimeEnvironment.warmupPolicy,
            caseTimeoutMs = 30_000,
        )
        return EvaluationRunSummary(
            runId = runId,
            config = config,
            identity = resolvedIdentity.takeIf { includeIdentity },
            state = EvaluationRunState.CREATED,
            progress = EvaluationProgress(totalCases = 1, attemptedCases = 0, completedCases = 0),
            quality = null,
            reliability = null,
            startedAtEpochMs = 1,
            completedAtEpochMs = null,
            failure = null,
        )
    }
}
