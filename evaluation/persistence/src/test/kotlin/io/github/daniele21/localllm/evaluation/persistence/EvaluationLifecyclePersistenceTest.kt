package io.github.daniele21.localllm.evaluation.persistence

import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.evaluation.EvaluationCaseId
import io.github.daniele21.localllm.evaluation.EvaluationCaseResult
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
import io.github.daniele21.localllm.evaluation.EvaluationRunState
import io.github.daniele21.localllm.evaluation.EvaluationWarmupPolicy
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluationLifecyclePersistenceTest {
    @Test
    fun `preflight failure is persisted as a terminal failed run without case content`() = runBlocking {
        val failure = EvaluationFailure(
            stage = EvaluationFailureStage.PREFLIGHT,
            code = EvaluationFailureCode.DATASET_DIGEST_MISMATCH,
        )
        val engine = EvaluationEngine(
            preflight = failingPreflight(failure),
            modelPreparation = unusedPreparation(),
            caseExecution = unusedCaseExecution(),
        )
        val repository = InMemoryEvaluationResultRepository()
        val times = ArrayDeque(listOf(1_000L, 2_000L))
        val persistence = EvaluationLifecyclePersistence(
            repository = repository,
            clock = EvaluationClock { times.removeFirst() },
        )
        val config = config()

        val terminal = persistence.run(engine, config, identity = null)
        val stored = requireNotNull(repository.getRun(config.runId))

        assertTrue(terminal is EvaluationEngineTerminal.Failed)
        assertEquals(EvaluationRunState.FAILED, stored.summary.state)
        assertEquals(1_000L, stored.summary.startedAtEpochMs)
        assertEquals(2_000L, stored.summary.completedAtEpochMs)
        assertSame(failure, stored.summary.failure)
        assertTrue(stored.caseResults.isEmpty())
    }

    @Test
    fun `external observer still receives lifecycle states while persistence owns storage`() = runBlocking {
        val states = mutableListOf<EvaluationRunState>()
        val failure = EvaluationFailure(
            stage = EvaluationFailureStage.PREFLIGHT,
            code = EvaluationFailureCode.INVALID_CONFIGURATION,
        )
        val engine = EvaluationEngine(
            preflight = failingPreflight(failure),
            modelPreparation = unusedPreparation(),
            caseExecution = unusedCaseExecution(),
        )
        val persistence = EvaluationLifecyclePersistence(
            repository = InMemoryEvaluationResultRepository(),
            clock = EvaluationClock { 1_000L },
        )

        persistence.run(
            engine = engine,
            config = config(),
            identity = null,
            observer = object : EvaluationEngineObserver {
                override suspend fun onStateChanged(runId: EvaluationRunId, state: EvaluationRunState) {
                    states += state
                }
            },
        )

        assertEquals(
            listOf(
                EvaluationRunState.CREATED,
                EvaluationRunState.VALIDATING,
                EvaluationRunState.FAILED,
            ),
            states,
        )
    }

    private fun failingPreflight(failure: EvaluationFailure) = object : EvaluationPreflightPort {
        override suspend fun validate(config: EvaluationRunConfig): EvaluationStepResult<Unit> = EvaluationStepResult.Failure(failure)
    }

    private fun unusedPreparation() = object : EvaluationModelPreparationPort {
        override suspend fun prepare(config: EvaluationRunConfig): EvaluationStepResult<Unit> = error("Preparation must not run")

        override suspend fun warmup(config: EvaluationRunConfig): EvaluationStepResult<Unit> = error("Warmup must not run")
    }

    private fun unusedCaseExecution() = object : EvaluationCaseExecutionPort {
        override suspend fun execute(config: EvaluationRunConfig, caseId: EvaluationCaseId): EvaluationStepResult<EvaluationCaseResult> =
            error("Case execution must not run")
    }

    private fun config(): EvaluationRunConfig {
        val dataset = EvaluationDatasetIdentity(
            id = EvaluationDatasetId("fixture"),
            version = EvaluationDatasetVersion("1"),
            digest = EvaluationDatasetDigest("1".repeat(64)),
        )
        return EvaluationRunConfig(
            runId = EvaluationRunId("run-persisted"),
            model = EvaluationModelIdentity(
                artifactDigest = ModelDigest("a".repeat(64)),
                modelProfileId = "supported-model",
                quantization = "Q4_K_M",
            ),
            dataset = dataset,
            sampling = SamplingSelection.create(
                dataset = dataset,
                policy = SamplingPolicyRef(SamplingPolicyId("fixed"), 1),
                seed = 0,
                orderedCaseIds = listOf(EvaluationCaseId("case-a")),
            ),
            executionProfile = EvaluationExecutionProfileRef(
                id = EvaluationExecutionProfileId("deterministic"),
                version = 1,
            ),
            loadPolicy = EvaluationModelLoadPolicy.PRESERVE_CURRENT_RESIDENCY,
            warmupPolicy = EvaluationWarmupPolicy.NONE,
            caseTimeoutMs = 30_000,
        )
    }
}
