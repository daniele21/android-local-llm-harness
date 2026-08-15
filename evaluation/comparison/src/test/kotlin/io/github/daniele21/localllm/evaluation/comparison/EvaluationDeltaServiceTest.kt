package io.github.daniele21.localllm.evaluation.comparison

import io.github.daniele21.localllm.contracts.ChatTemplateSource
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.SeedPolicyType
import io.github.daniele21.localllm.contracts.ThinkingMode
import io.github.daniele21.localllm.evaluation.CaseExecutionSemanticsDigest
import io.github.daniele21.localllm.evaluation.EvaluationCaseId
import io.github.daniele21.localllm.evaluation.EvaluationCaseMetrics
import io.github.daniele21.localllm.evaluation.EvaluationCaseResult
import io.github.daniele21.localllm.evaluation.EvaluationCaseStatus
import io.github.daniele21.localllm.evaluation.EvaluationCategoryId
import io.github.daniele21.localllm.evaluation.EvaluationCategoryScore
import io.github.daniele21.localllm.evaluation.EvaluationDatasetDigest
import io.github.daniele21.localllm.evaluation.EvaluationDatasetId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetIdentity
import io.github.daniele21.localllm.evaluation.EvaluationDatasetVersion
import io.github.daniele21.localllm.evaluation.EvaluationExecutionProfileId
import io.github.daniele21.localllm.evaluation.EvaluationExecutionProfileRef
import io.github.daniele21.localllm.evaluation.EvaluationModelIdentity
import io.github.daniele21.localllm.evaluation.EvaluationModelLoadPolicy
import io.github.daniele21.localllm.evaluation.EvaluationOutcome
import io.github.daniele21.localllm.evaluation.EvaluationProgress
import io.github.daniele21.localllm.evaluation.EvaluationQualitySummary
import io.github.daniele21.localllm.evaluation.EvaluationRunConfig
import io.github.daniele21.localllm.evaluation.EvaluationRunId
import io.github.daniele21.localllm.evaluation.EvaluationRunIdentity
import io.github.daniele21.localllm.evaluation.EvaluationRunState
import io.github.daniele21.localllm.evaluation.EvaluationRunSummary
import io.github.daniele21.localllm.evaluation.EvaluationRuntimeEnvironmentIdentity
import io.github.daniele21.localllm.evaluation.EvaluationSemanticExecution
import io.github.daniele21.localllm.evaluation.EvaluationSemanticExecutionIdentity
import io.github.daniele21.localllm.evaluation.EvaluationWarmupPolicy
import io.github.daniele21.localllm.evaluation.EvaluatorOutcomeCode
import io.github.daniele21.localllm.evaluation.EvaluatorSetDigest
import io.github.daniele21.localllm.evaluation.EvaluatorSpec
import io.github.daniele21.localllm.evaluation.EvaluatorType
import io.github.daniele21.localllm.evaluation.EvaluatorVersion
import io.github.daniele21.localllm.evaluation.NormalizedScore
import io.github.daniele21.localllm.evaluation.PersistedEvaluationRun
import io.github.daniele21.localllm.evaluation.SamplingPolicyId
import io.github.daniele21.localllm.evaluation.SamplingPolicyRef
import io.github.daniele21.localllm.evaluation.SamplingSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluationDeltaServiceTest {
    private val service = EvaluationDeltaService()

    @Test
    fun `compatible completed runs produce aggregate category and paired runtime deltas`() {
        val left = persisted(
            runId = "left",
            aggregateScore = 0.60,
            categoryScore = 0.50,
            metrics = listOf(
                EvaluationCaseMetrics(totalMs = 100, timeToFirstTokenMs = 20, decodeTokensPerSecond = 10.0),
                EvaluationCaseMetrics(totalMs = 200, timeToFirstTokenMs = 40, decodeTokensPerSecond = 20.0),
            ),
        )
        val right = persisted(
            runId = "right",
            modelDigest = "b".repeat(64),
            aggregateScore = 0.75,
            categoryScore = 0.70,
            metrics = listOf(
                EvaluationCaseMetrics(totalMs = 80, timeToFirstTokenMs = 10, decodeTokensPerSecond = 15.0),
                EvaluationCaseMetrics(totalMs = 160, timeToFirstTokenMs = 30, decodeTokensPerSecond = 30.0),
            ),
        )

        val deltas = service.compare(left, right)
        val quality = (deltas.quality as EvaluationDeltaFamily.Available).value
        val runtime = (deltas.runtime as EvaluationDeltaFamily.Available).value

        assertEquals(0.15, quality.aggregateScore?.absolute ?: -1.0, 0.000001)
        assertEquals(0.20, quality.categories.single().score.absolute, 0.000001)
        assertEquals(-30.0, runtime.totalMs?.absolute ?: 0.0, 0.000001)
        assertEquals(2, runtime.totalMs?.pairedCaseCount)
        assertEquals(7.5, runtime.decodeTokensPerSecond?.absolute ?: 0.0, 0.000001)
    }

    @Test
    fun `runtime mismatch blocks runtime resources but preserves compatible quality delta`() {
        val left = persisted(runId = "left", aggregateScore = 0.50, categoryScore = 0.50)
        val right = persisted(
            runId = "right",
            deviceClass = "different-device-class",
            aggregateScore = 0.70,
            categoryScore = 0.70,
        )

        val deltas = service.compare(left, right)

        assertTrue(deltas.quality is EvaluationDeltaFamily.Available)
        assertEquals(
            EvaluationDeltaUnavailableReason.RUNTIME_INCOMPATIBLE,
            (deltas.runtime as EvaluationDeltaFamily.Unavailable).reason,
        )
        assertEquals(
            EvaluationDeltaUnavailableReason.RUNTIME_INCOMPATIBLE,
            (deltas.resources as EvaluationDeltaFamily.Unavailable).reason,
        )
    }

    @Test
    fun `quality incompatibility blocks every quality and runtime delta`() {
        val left = persisted(runId = "left", aggregateScore = 0.50, categoryScore = 0.50)
        val right = persisted(
            runId = "right",
            datasetDigest = "9".repeat(64),
            aggregateScore = 0.70,
            categoryScore = 0.70,
        )

        val deltas = service.compare(left, right)

        assertEquals(
            EvaluationDeltaUnavailableReason.QUALITY_INCOMPATIBLE,
            (deltas.quality as EvaluationDeltaFamily.Unavailable).reason,
        )
        assertEquals(
            EvaluationDeltaUnavailableReason.RUNTIME_INCOMPATIBLE,
            (deltas.runtime as EvaluationDeltaFamily.Unavailable).reason,
        )
    }

    @Test
    fun `metric means use only matching cases with data on both sides`() {
        val left = persisted(
            runId = "left",
            metrics = listOf(EvaluationCaseMetrics(totalMs = 100), EvaluationCaseMetrics(totalMs = 1_000)),
        )
        val right = persisted(
            runId = "right",
            metrics = listOf(EvaluationCaseMetrics(totalMs = 80), EvaluationCaseMetrics()),
        )

        val runtime = (service.compare(left, right).runtime as EvaluationDeltaFamily.Available).value

        assertEquals(100.0, runtime.totalMs?.left ?: 0.0, 0.0)
        assertEquals(80.0, runtime.totalMs?.right ?: 0.0, 0.0)
        assertEquals(1, runtime.totalMs?.pairedCaseCount)
    }

    @Test
    fun `missing metric stays unavailable instead of becoming zero`() {
        val left = persisted(runId = "left")
        val right = persisted(runId = "right")

        val runtime = (service.compare(left, right).runtime as EvaluationDeltaFamily.Available).value
        val resources = (service.compare(left, right).resources as EvaluationDeltaFamily.Available).value

        assertNull(runtime.totalMs)
        assertNull(runtime.decodeTokensPerSecond)
        assertNull(resources.processPssBytes)
    }

    @Test
    fun `non completed runs reject all delta families`() {
        val completed = persisted(runId = "completed")
        val running = persisted(runId = "running", state = EvaluationRunState.RUNNING)

        val deltas = service.compare(completed, running)

        assertEquals(
            EvaluationDeltaUnavailableReason.RUN_NOT_COMPLETED,
            (deltas.quality as EvaluationDeltaFamily.Unavailable).reason,
        )
        assertEquals(
            EvaluationDeltaUnavailableReason.RUN_NOT_COMPLETED,
            (deltas.runtime as EvaluationDeltaFamily.Unavailable).reason,
        )
        assertEquals(
            EvaluationDeltaUnavailableReason.RUN_NOT_COMPLETED,
            (deltas.resources as EvaluationDeltaFamily.Unavailable).reason,
        )
    }

    @Suppress("LongParameterList")
    private fun persisted(
        runId: String,
        modelDigest: String = "a".repeat(64),
        datasetDigest: String = "1".repeat(64),
        deviceClass: String = "class-a",
        aggregateScore: Double? = 0.50,
        categoryScore: Double? = 0.50,
        metrics: List<EvaluationCaseMetrics> = listOf(EvaluationCaseMetrics(), EvaluationCaseMetrics()),
        state: EvaluationRunState = EvaluationRunState.COMPLETED,
    ): PersistedEvaluationRun {
        val dataset = EvaluationDatasetIdentity(
            id = EvaluationDatasetId("general-purpose"),
            version = EvaluationDatasetVersion("1.0.0"),
            digest = EvaluationDatasetDigest(datasetDigest),
        )
        val caseIds = listOf(EvaluationCaseId("case-1"), EvaluationCaseId("case-2"))
        val sampling = SamplingSelection.create(
            dataset = dataset,
            policy = SamplingPolicyRef(SamplingPolicyId("fixed"), 1),
            seed = 7,
            orderedCaseIds = caseIds,
        )
        val identity = identity(
            modelDigest = modelDigest,
            dataset = dataset,
            sampling = sampling,
            deviceClass = deviceClass,
        )
        val id = EvaluationRunId(runId)
        val config = EvaluationRunConfig(
            runId = id,
            model = identity.model,
            dataset = dataset,
            sampling = sampling,
            executionProfile = identity.semanticExecution.execution.profile,
            loadPolicy = identity.runtimeEnvironment.loadPolicy,
            warmupPolicy = identity.runtimeEnvironment.warmupPolicy,
            caseTimeoutMs = 30_000,
        )
        val quality = categoryScore?.let { score ->
            EvaluationQualitySummary(
                aggregateScore = aggregateScore?.let(::NormalizedScore),
                categoryScores = listOf(
                    EvaluationCategoryScore(
                        categoryId = CATEGORY_ID,
                        score = NormalizedScore(score),
                        scoredCaseCount = 2,
                    ),
                ),
            )
        }
        val completed = state == EvaluationRunState.COMPLETED
        val summary = EvaluationRunSummary(
            runId = id,
            config = config,
            identity = identity,
            state = state,
            progress = EvaluationProgress(
                totalCases = caseIds.size,
                attemptedCases = if (completed) caseIds.size else 1,
                completedCases = if (completed) caseIds.size else 0,
            ),
            quality = quality,
            reliability = null,
            startedAtEpochMs = 1,
            completedAtEpochMs = 2.takeIf { completed },
            failure = null,
        )
        return PersistedEvaluationRun(
            summary = summary,
            caseResults = caseIds.zip(metrics).map { (caseId, caseMetrics) -> result(caseId, caseMetrics) },
        )
    }

    private fun identity(
        modelDigest: String,
        dataset: EvaluationDatasetIdentity,
        sampling: SamplingSelection,
        deviceClass: String,
    ): EvaluationRunIdentity {
        val semantic = EvaluationSemanticExecutionIdentity.create(
            EvaluationSemanticExecution(
                profile = EvaluationExecutionProfileRef(EvaluationExecutionProfileId("direct"), 1),
                backendRevision = "backend-rev",
                contextSize = 2_048,
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
            model = EvaluationModelIdentity(ModelDigest(modelDigest), "candidate"),
            dataset = dataset,
            sampleSetDigest = sampling.digest,
            samplingPolicy = sampling.policy,
            samplingSeed = sampling.seed,
            evaluatorSetDigest = EvaluatorSetDigest("3".repeat(64)),
            semanticExecution = semantic,
            runtimeEnvironment = EvaluationRuntimeEnvironmentIdentity(
                deviceClass = deviceClass,
                androidApiLevel = 36,
                abi = "arm64-v8a",
                backendRevision = "backend-rev",
                harnessBuildIdentity = "build-a",
                runtimeTuningProfileId = "candidate-profile",
                runtimeTuningProfileVersion = 1,
                loadPolicy = EvaluationModelLoadPolicy.PRESERVE_CURRENT_RESIDENCY,
                warmupPolicy = EvaluationWarmupPolicy.NONE,
            ),
        )
    }

    private fun result(caseId: EvaluationCaseId, metrics: EvaluationCaseMetrics) = EvaluationCaseResult(
        caseId = caseId,
        categoryId = CATEGORY_ID,
        evaluator = EvaluatorSpec(EvaluatorType.EXACT_MATCH, EvaluatorVersion(1)),
        status = EvaluationCaseStatus.SCORED,
        outcome = EvaluationOutcome(NormalizedScore(1.0), EvaluatorOutcomeCode.CORRECT),
        requestId = null,
        metrics = metrics,
    )

    private companion object {
        val CATEGORY_ID = EvaluationCategoryId("general")
    }
}
