package io.github.daniele21.localllm.evaluation.persistence

import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.evaluation.EvaluationCaseId
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
import io.github.daniele21.localllm.evaluation.EvaluationRunConfig
import io.github.daniele21.localllm.evaluation.EvaluationRunId
import io.github.daniele21.localllm.evaluation.EvaluationWarmupPolicy
import io.github.daniele21.localllm.evaluation.EvaluatorSpec
import io.github.daniele21.localllm.evaluation.EvaluatorType
import io.github.daniele21.localllm.evaluation.EvaluatorVersion
import io.github.daniele21.localllm.evaluation.SamplingPolicyId
import io.github.daniele21.localllm.evaluation.SamplingPolicyRef
import io.github.daniele21.localllm.evaluation.SamplingSelection
import io.github.daniele21.localllm.evaluation.engine.EvaluationCaseExecutionPort
import io.github.daniele21.localllm.evaluation.engine.EvaluationEngine
import io.github.daniele21.localllm.evaluation.engine.EvaluationEngineObserver
import io.github.daniele21.localllm.evaluation.engine.EvaluationEngineTerminal
import io.github.daniele21.localllm.evaluation.engine.EvaluationModelPreparationPort
import io.github.daniele21.localllm.evaluation.engine.EvaluationPreflightPort
import io.github.daniele21.localllm.evaluation.engine.EvaluationStepResult
import io.github.daniele21.localllm.evaluation.store.memory.InMemoryEvaluationResultRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluationCaseResultPersistenceTest {
    @Test
    fun `completed case is durable before a later case fails the run`() = runBlocking {
        val repository = InMemoryEvaluationResultRepository()
        val persistedResult = runtimeFailureResult(CASE_A)
        val observed = mutableListOf<EvaluationCaseResult>()
        val engine = EvaluationEngine(
            preflight = successfulPreflight(),
            modelPreparation = successfulPreparation(),
            caseExecution = firstCaseSucceedsThenFails(persistedResult),
        )
        val persistence = EvaluationLifecyclePersistence(
            repository = repository,
            clock = EvaluationClock { 1_000L },
        )

        val terminal = persistence.run(
            engine = engine,
            config = CONFIG,
            identity = null,
            observer = object : EvaluationEngineObserver {
                override suspend fun onCaseResult(runId: EvaluationRunId, result: EvaluationCaseResult) {
                    observed += result
                }
            },
        )
        val stored = requireNotNull(repository.getRun(RUN_ID))

        assertTrue(terminal is EvaluationEngineTerminal.Failed)
        assertEquals(listOf(persistedResult), stored.caseResults)
        assertEquals(listOf(persistedResult), observed)
    }

    private fun successfulPreflight() = object : EvaluationPreflightPort {
        override suspend fun validate(config: EvaluationRunConfig): EvaluationStepResult<Unit> = EvaluationStepResult.Success(Unit)
    }

    private fun successfulPreparation() = object : EvaluationModelPreparationPort {
        override suspend fun prepare(config: EvaluationRunConfig): EvaluationStepResult<Unit> = EvaluationStepResult.Success(Unit)

        override suspend fun warmup(config: EvaluationRunConfig): EvaluationStepResult<Unit> = EvaluationStepResult.Success(Unit)
    }

    private fun firstCaseSucceedsThenFails(persistedResult: EvaluationCaseResult) = object : EvaluationCaseExecutionPort {
        override suspend fun execute(config: EvaluationRunConfig, caseId: EvaluationCaseId): EvaluationStepResult<EvaluationCaseResult> =
            if (caseId == CASE_A) {
                EvaluationStepResult.Success(persistedResult)
            } else {
                EvaluationStepResult.Failure(
                    EvaluationFailure(
                        stage = EvaluationFailureStage.GENERATION,
                        code = EvaluationFailureCode.RUNTIME_FAILURE,
                        caseId = caseId,
                    ),
                )
            }
    }

    private fun runtimeFailureResult(caseId: EvaluationCaseId) = EvaluationCaseResult(
        caseId = caseId,
        categoryId = EvaluationCategoryId("general"),
        evaluator = EvaluatorSpec(EvaluatorType.EXACT_MATCH, EvaluatorVersion(1)),
        status = EvaluationCaseStatus.RUNTIME_FAILURE,
        outcome = null,
        requestId = RequestId("request-a"),
        failure = EvaluationFailure(
            stage = EvaluationFailureStage.GENERATION,
            code = EvaluationFailureCode.RUNTIME_FAILURE,
            caseId = caseId,
        ),
    )

    private companion object {
        val RUN_ID = EvaluationRunId("run-p07")
        val CASE_A = EvaluationCaseId("case-a")
        val CASE_B = EvaluationCaseId("case-b")
        val DATASET = EvaluationDatasetIdentity(
            id = EvaluationDatasetId("fixture"),
            version = EvaluationDatasetVersion("1"),
            digest = EvaluationDatasetDigest("1".repeat(64)),
        )
        val CONFIG = EvaluationRunConfig(
            runId = RUN_ID,
            model = EvaluationModelIdentity(ModelDigest("a".repeat(64)), "supported-model", quantization = "Q4_K_M"),
            dataset = DATASET,
            sampling = SamplingSelection.create(
                dataset = DATASET,
                policy = SamplingPolicyRef(SamplingPolicyId("fixed"), 1),
                seed = 0,
                orderedCaseIds = listOf(CASE_A, CASE_B),
            ),
            executionProfile = EvaluationExecutionProfileRef(EvaluationExecutionProfileId("direct"), 1),
            loadPolicy = EvaluationModelLoadPolicy.PRESERVE_CURRENT_RESIDENCY,
            warmupPolicy = EvaluationWarmupPolicy.NONE,
            caseTimeoutMs = 30_000,
        )
    }
}
