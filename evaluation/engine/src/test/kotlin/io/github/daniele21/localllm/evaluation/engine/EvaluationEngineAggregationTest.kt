package io.github.daniele21.localllm.evaluation.engine

import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.evaluation.EvaluationCaseId
import io.github.daniele21.localllm.evaluation.EvaluationCaseResult
import io.github.daniele21.localllm.evaluation.EvaluationCaseStatus
import io.github.daniele21.localllm.evaluation.EvaluationCategoryId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetCategoryDefinition
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
import io.github.daniele21.localllm.evaluation.EvaluationRunState
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluationEngineAggregationTest {
    @Test
    fun `configured aggregation runs before completed and is observable`() = runBlocking {
        val config = config()
        val states = mutableListOf<EvaluationRunState>()
        var observed: EvaluationRunAggregation? = null
        val aggregationPort = CanonicalEvaluationRunAggregationPort(
            EvaluationDatasetCategorySource {
                listOf(EvaluationDatasetCategoryDefinition(EvaluationCategoryId("general"), "General"))
            },
        )
        val engine = EvaluationEngine(
            preflight = successPreflight(),
            modelPreparation = successPreparation(),
            caseExecution = successCaseExecution(),
            runAggregation = aggregationPort,
        )

        val terminal = engine.run(
            config,
            object : EvaluationEngineObserver {
                override suspend fun onStateChanged(runId: EvaluationRunId, state: EvaluationRunState) {
                    states += state
                }

                override suspend fun onAggregation(runId: EvaluationRunId, aggregation: EvaluationRunAggregation) {
                    observed = aggregation
                }
            },
        )

        assertTrue(terminal is EvaluationEngineTerminal.Completed)
        val completed = terminal as EvaluationEngineTerminal.Completed
        assertNotNull(completed.aggregation)
        assertEquals(completed.aggregation, observed)
        assertEquals(
            listOf(
                EvaluationRunState.CREATED,
                EvaluationRunState.VALIDATING,
                EvaluationRunState.PREPARING_MODEL,
                EvaluationRunState.RUNNING,
                EvaluationRunState.AGGREGATING,
                EvaluationRunState.COMPLETED,
            ),
            states,
        )
    }

    @Test
    fun `aggregation failure terminates after aggregating without completed state`() = runBlocking {
        val states = mutableListOf<EvaluationRunState>()
        val failure = EvaluationFailure(
            stage = EvaluationFailureStage.EVALUATION,
            code = EvaluationFailureCode.DATASET_NOT_FOUND,
        )
        val engine = EvaluationEngine(
            preflight = successPreflight(),
            modelPreparation = successPreparation(),
            caseExecution = successCaseExecution(),
            runAggregation = EvaluationRunAggregationPort { _, _ -> EvaluationStepResult.Failure(failure) },
        )

        val terminal = engine.run(
            config(),
            object : EvaluationEngineObserver {
                override suspend fun onStateChanged(runId: EvaluationRunId, state: EvaluationRunState) {
                    states += state
                }
            },
        )

        assertTrue(terminal is EvaluationEngineTerminal.Failed)
        assertEquals(failure, (terminal as EvaluationEngineTerminal.Failed).failure)
        assertEquals(EvaluationRunState.AGGREGATING, states[states.lastIndex - 1])
        assertEquals(EvaluationRunState.FAILED, states.last())
    }

    private fun successPreflight() = object : EvaluationPreflightPort {
        override suspend fun validate(config: EvaluationRunConfig) = EvaluationStepResult.Success(Unit)
    }

    private fun successPreparation() = object : EvaluationModelPreparationPort {
        override suspend fun prepare(config: EvaluationRunConfig) = EvaluationStepResult.Success(Unit)
        override suspend fun warmup(config: EvaluationRunConfig) = EvaluationStepResult.Success(Unit)
    }

    private fun successCaseExecution() = object : EvaluationCaseExecutionPort {
        override suspend fun execute(config: EvaluationRunConfig, caseId: EvaluationCaseId): EvaluationStepResult<EvaluationCaseResult> =
            EvaluationStepResult.Success(scored(caseId))
    }

    private fun scored(caseId: EvaluationCaseId) = EvaluationCaseResult(
        caseId = caseId,
        categoryId = EvaluationCategoryId("general"),
        evaluator = EvaluatorSpec(EvaluatorType.EXACT_MATCH, EvaluatorVersion(1)),
        status = EvaluationCaseStatus.SCORED,
        outcome = EvaluationOutcome(NormalizedScore(1.0), EvaluatorOutcomeCode.CORRECT),
        requestId = null,
    )

    private fun config(): EvaluationRunConfig {
        val dataset = EvaluationDatasetIdentity(
            EvaluationDatasetId("fixture"),
            EvaluationDatasetVersion("1"),
            EvaluationDatasetDigest("1".repeat(64)),
        )
        return EvaluationRunConfig(
            runId = EvaluationRunId("run-engine-aggregation"),
            model = EvaluationModelIdentity(ModelDigest("a".repeat(64)), "supported-model"),
            dataset = dataset,
            sampling = SamplingSelection.create(
                dataset = dataset,
                policy = SamplingPolicyRef(SamplingPolicyId("fixed"), 1),
                seed = 0,
                orderedCaseIds = listOf(EvaluationCaseId("case-a")),
            ),
            executionProfile = EvaluationExecutionProfileRef(EvaluationExecutionProfileId("profile"), 1),
            loadPolicy = EvaluationModelLoadPolicy.PRESERVE_CURRENT_RESIDENCY,
            warmupPolicy = EvaluationWarmupPolicy.NONE,
            caseTimeoutMs = 30_000,
        )
    }
}
