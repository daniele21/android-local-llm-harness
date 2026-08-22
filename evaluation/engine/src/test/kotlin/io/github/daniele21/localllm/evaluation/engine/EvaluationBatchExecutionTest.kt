package io.github.daniele21.localllm.evaluation.engine

import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.evaluation.EvaluationCaseId
import io.github.daniele21.localllm.evaluation.EvaluationCaseMetrics
import io.github.daniele21.localllm.evaluation.EvaluationCaseResult
import io.github.daniele21.localllm.evaluation.EvaluationCaseStatus
import io.github.daniele21.localllm.evaluation.EvaluationCategoryId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetDigest
import io.github.daniele21.localllm.evaluation.EvaluationDatasetId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetIdentity
import io.github.daniele21.localllm.evaluation.EvaluationDatasetVersion
import io.github.daniele21.localllm.evaluation.EvaluationExecutionProfileId
import io.github.daniele21.localllm.evaluation.EvaluationExecutionProfileRef
import io.github.daniele21.localllm.evaluation.EvaluationFailure
import io.github.daniele21.localllm.evaluation.EvaluationFailureCode
import io.github.daniele21.localllm.evaluation.EvaluationFailureStage
import io.github.daniele21.localllm.evaluation.EvaluationModelIdentity
import io.github.daniele21.localllm.evaluation.EvaluationModelLoadPolicy
import io.github.daniele21.localllm.evaluation.EvaluationOutcome
import io.github.daniele21.localllm.evaluation.EvaluationRunConfig
import io.github.daniele21.localllm.evaluation.EvaluationRunId
import io.github.daniele21.localllm.evaluation.EvaluationWarmupPolicy
import io.github.daniele21.localllm.evaluation.EvaluatorOutcomeCode
import io.github.daniele21.localllm.evaluation.EvaluatorSpec
import io.github.daniele21.localllm.evaluation.EvaluatorType
import io.github.daniele21.localllm.evaluation.EvaluatorVersion
import io.github.daniele21.localllm.evaluation.NormalizedScore
import io.github.daniele21.localllm.evaluation.SamplingPolicyId
import io.github.daniele21.localllm.evaluation.SamplingPolicyRef
import io.github.daniele21.localllm.evaluation.SamplingSelection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluationBatchExecutionTest {
    @Test
    fun `batch plan preserves exact order and bounded batch size`() {
        val ids = listOf("case-c", "case-a", "case-d", "case-b", "case-e").map(::EvaluationCaseId)

        val plan = EvaluationBatchPlan.create(ids, maxCasesPerBatch = 2)

        assertEquals(ids, plan.orderedCaseIds)
        assertEquals(listOf(0, 1, 2), plan.batches.map { it.ordinal })
        assertEquals(listOf(2, 2, 1), plan.batches.map { it.orderedCaseIds.size })
    }

    @Test
    fun `sequential compatibility execution preserves attribution and stops on failure`() = runBlocking {
        val config = config(listOf("case-a", "case-b", "case-c"))
        val executed = mutableListOf<EvaluationCaseId>()
        val failure = EvaluationFailure(
            stage = EvaluationFailureStage.GENERATION,
            code = EvaluationFailureCode.RUNTIME_FAILURE,
            caseId = EvaluationCaseId("case-b"),
        )
        val port = SequentialEvaluationBatchExecution { _, caseId ->
            executed += caseId
            if (caseId.value == "case-b") EvaluationStepResult.Failure(failure) else EvaluationStepResult.Success(scored(caseId))
        }

        val outcome = port.execute(config, EvaluationCaseBatch(0, config.sampling.orderedCaseIds))

        assertTrue(outcome is EvaluationStepResult.Failure)
        assertEquals(listOf("case-a", "case-b"), executed.map { it.value })
        assertEquals(failure, (outcome as EvaluationStepResult.Failure).failure)
    }

    @Test
    fun `orchestrator rejects a plan that does not match selected sample identity`() = runBlocking {
        val config = config(listOf("case-a", "case-b"))
        val plan = EvaluationBatchPlan.create(listOf(EvaluationCaseId("case-b"), EvaluationCaseId("case-a")), 1)
        var called = false
        val orchestrator = EvaluationBatchOrchestrator { _, _ ->
            called = true
            EvaluationStepResult.Success(emptyList())
        }

        val outcome = orchestrator.execute(config, plan)

        assertTrue(outcome is EvaluationStepResult.Failure)
        val failure = (outcome as EvaluationStepResult.Failure).failure
        assertEquals(EvaluationFailureStage.PREFLIGHT, failure.stage)
        assertEquals(EvaluationFailureCode.INVALID_CONFIGURATION, failure.code)
        assertTrue(!called)
    }

    @Test
    fun `orchestrator fails closed when batch result attribution drifts`() = runBlocking {
        val config = config(listOf("case-a", "case-b"))
        val plan = EvaluationBatchPlan.create(config.sampling.orderedCaseIds, 2)
        val orchestrator = EvaluationBatchOrchestrator { _, _ ->
            EvaluationStepResult.Success(
                listOf(
                    scored(EvaluationCaseId("case-b")),
                    scored(EvaluationCaseId("case-a")),
                ),
            )
        }

        val outcome = orchestrator.execute(config, plan)

        assertTrue(outcome is EvaluationStepResult.Failure)
        val failure = (outcome as EvaluationStepResult.Failure).failure
        assertEquals(EvaluationFailureCode.INVALID_CONFIGURATION, failure.code)
    }

    @Test
    fun `orchestrator returns results in exact sample order across multiple batches`() = runBlocking {
        val config = config(listOf("case-c", "case-a", "case-b"))
        val plan = EvaluationBatchPlan.create(config.sampling.orderedCaseIds, 2)
        val observedBatches = mutableListOf<List<String>>()
        val orchestrator = EvaluationBatchOrchestrator { _, batch ->
            observedBatches += batch.orderedCaseIds.map { it.value }
            EvaluationStepResult.Success(batch.orderedCaseIds.map(::scored))
        }

        val outcome = orchestrator.execute(config, plan)

        assertTrue(outcome is EvaluationStepResult.Success)
        val results = (outcome as EvaluationStepResult.Success).value
        assertEquals(listOf(listOf("case-c", "case-a"), listOf("case-b")), observedBatches)
        assertEquals(config.sampling.orderedCaseIds, results.map { it.caseId })
    }

    private fun scored(caseId: EvaluationCaseId) = EvaluationCaseResult(
        caseId = caseId,
        categoryId = EvaluationCategoryId("general"),
        evaluator = EvaluatorSpec(EvaluatorType.EXACT_MATCH, EvaluatorVersion(1)),
        status = EvaluationCaseStatus.SCORED,
        outcome = EvaluationOutcome(NormalizedScore(1.0), EvaluatorOutcomeCode.CORRECT),
        requestId = null,
        metrics = EvaluationCaseMetrics(),
    )

    private fun config(caseIds: List<String>): EvaluationRunConfig {
        val dataset = EvaluationDatasetIdentity(
            id = EvaluationDatasetId("fixture"),
            version = EvaluationDatasetVersion("1"),
            digest = EvaluationDatasetDigest("1".repeat(64)),
        )
        return EvaluationRunConfig(
            runId = EvaluationRunId("run-llrt9"),
            model = EvaluationModelIdentity(
                artifactDigest = ModelDigest("a".repeat(64)),
                modelProfileId = "supported-model",
            ),
            dataset = dataset,
            sampling = SamplingSelection.create(
                dataset = dataset,
                policy = SamplingPolicyRef(SamplingPolicyId("fixed"), 1),
                seed = 0,
                orderedCaseIds = caseIds.map(::EvaluationCaseId),
            ),
            executionProfile = EvaluationExecutionProfileRef(EvaluationExecutionProfileId("direct"), 1),
            loadPolicy = EvaluationModelLoadPolicy.PRESERVE_CURRENT_RESIDENCY,
            warmupPolicy = EvaluationWarmupPolicy.NONE,
            caseTimeoutMs = 30_000,
        )
    }
}
