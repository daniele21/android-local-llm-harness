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
import io.github.daniele21.localllm.evaluation.EvaluationModelIdentity
import io.github.daniele21.localllm.evaluation.EvaluationModelLoadPolicy
import io.github.daniele21.localllm.evaluation.EvaluationOutcome
import io.github.daniele21.localllm.evaluation.EvaluationProgress
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluationEngineTest {
    @Test
    fun `successful fake run preserves case order and lifecycle`() = runBlocking {
        val states = mutableListOf<EvaluationRunState>()
        val executed = mutableListOf<EvaluationCaseId>()
        val config = config(caseIds = listOf("case-b", "case-a"), warmup = EvaluationWarmupPolicy.ONE_UNSCORED_GENERATION)
        var warmups = 0
        val engine = EvaluationEngine(
            preflight = successPreflight(),
            modelPreparation = object : EvaluationModelPreparationPort {
                override suspend fun prepare(config: EvaluationRunConfig) = EvaluationStepResult.Success(Unit)

                override suspend fun warmup(config: EvaluationRunConfig): EvaluationStepResult<Unit> {
                    warmups += 1
                    return EvaluationStepResult.Success(Unit)
                }
            },
            caseExecution = object : EvaluationCaseExecutionPort {
                override suspend fun execute(
                    config: EvaluationRunConfig,
                    caseId: EvaluationCaseId,
                ): EvaluationStepResult<EvaluationCaseResult> {
                    executed += caseId
                    return EvaluationStepResult.Success(scored(caseId))
                }
            },
        )

        val terminal = engine.run(
            config,
            object : EvaluationEngineObserver {
                override suspend fun onStateChanged(runId: EvaluationRunId, state: EvaluationRunState) {
                    states += state
                }
            },
        )

        assertTrue(terminal is EvaluationEngineTerminal.Completed)
        assertEquals(listOf("case-b", "case-a"), executed.map { it.value })
        assertEquals(1, warmups)
        assertEquals(
            listOf(
                EvaluationRunState.CREATED,
                EvaluationRunState.VALIDATING,
                EvaluationRunState.PREPARING_MODEL,
                EvaluationRunState.WARMING_UP,
                EvaluationRunState.RUNNING,
                EvaluationRunState.AGGREGATING,
                EvaluationRunState.COMPLETED,
            ),
            states,
        )
        assertNull(engine.activeRun())
    }

    @Test
    fun `preflight failure terminates before preparation and cases`() = runBlocking {
        var prepared = false
        var executed = false
        val failure = io.github.daniele21.localllm.evaluation.EvaluationFailure(
            stage = io.github.daniele21.localllm.evaluation.EvaluationFailureStage.PREFLIGHT,
            code = io.github.daniele21.localllm.evaluation.EvaluationFailureCode.MODEL_NOT_INSTALLED,
        )
        val engine = EvaluationEngine(
            preflight = object : EvaluationPreflightPort {
                override suspend fun validate(config: EvaluationRunConfig) = EvaluationStepResult.Failure(failure)
            },
            modelPreparation = object : EvaluationModelPreparationPort {
                override suspend fun prepare(config: EvaluationRunConfig): EvaluationStepResult<Unit> {
                    prepared = true
                    return EvaluationStepResult.Success(Unit)
                }

                override suspend fun warmup(config: EvaluationRunConfig) = EvaluationStepResult.Success(Unit)
            },
            caseExecution = object : EvaluationCaseExecutionPort {
                override suspend fun execute(
                    config: EvaluationRunConfig,
                    caseId: EvaluationCaseId,
                ): EvaluationStepResult<EvaluationCaseResult> {
                    executed = true
                    return EvaluationStepResult.Success(scored(caseId))
                }
            },
        )

        val terminal = engine.run(config()) as EvaluationEngineTerminal.Failed

        assertEquals(failure, terminal.failure)
        assertFalse(prepared)
        assertFalse(executed)
    }

    @Test
    fun `cooperative cancellation stops before next case and releases ownership`() = runBlocking {
        lateinit var engine: EvaluationEngine
        val executed = mutableListOf<EvaluationCaseId>()
        val config = config(caseIds = listOf("case-a", "case-b"))
        engine = EvaluationEngine(
            preflight = successPreflight(),
            modelPreparation = successPreparation(),
            caseExecution = object : EvaluationCaseExecutionPort {
                override suspend fun execute(
                    config: EvaluationRunConfig,
                    caseId: EvaluationCaseId,
                ): EvaluationStepResult<EvaluationCaseResult> {
                    executed += caseId
                    if (executed.size == 1) {
                        assertTrue(engine.cancel(config.runId))
                    }
                    return EvaluationStepResult.Success(scored(caseId))
                }
            },
        )

        val terminal = engine.run(config)

        assertTrue(terminal is EvaluationEngineTerminal.Cancelled)
        assertEquals(listOf("case-a"), executed.map { it.value })
        assertTrue(terminal.results.isEmpty())
        assertNull(engine.activeRun())
        assertFalse(engine.cancel(config.runId))
    }

    @Test
    fun `active case cancellation interrupts execution and preserves attempted versus completed progress`() = runBlocking {
        val enteredCase = CompletableDeferred<Unit>()
        var activeCaseWasCancelled = false
        val progress = mutableListOf<EvaluationProgress>()
        val config = config(caseIds = listOf("case-a", "case-b", "case-c"))
        val engine = EvaluationEngine(
            preflight = successPreflight(),
            modelPreparation = successPreparation(),
            caseExecution = object : EvaluationCaseExecutionPort {
                override suspend fun execute(
                    config: EvaluationRunConfig,
                    caseId: EvaluationCaseId,
                ): EvaluationStepResult<EvaluationCaseResult> {
                    check(caseId.value == "case-a") { "Cancellation must stop before later cases execute" }
                    enteredCase.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        activeCaseWasCancelled = true
                    }
                }
            },
        )

        val running = async {
            engine.run(
                config,
                object : EvaluationEngineObserver {
                    override suspend fun onProgress(runId: EvaluationRunId, progressValue: EvaluationProgress) {
                        progress += progressValue
                    }
                },
            )
        }
        enteredCase.await()
        assertTrue(engine.cancel(config.runId))
        val terminal = running.await()

        assertTrue(terminal is EvaluationEngineTerminal.Cancelled)
        assertTrue(activeCaseWasCancelled)
        assertTrue(terminal.results.isEmpty())
        assertEquals(1, progress.last().attemptedCases)
        assertEquals(0, progress.last().completedCases)
        assertNull(progress.last().currentCaseId)
        assertEquals(3, progress.last().totalCases)
        assertNull(engine.activeRun())
    }

    private fun successPreflight() = object : EvaluationPreflightPort {
        override suspend fun validate(config: EvaluationRunConfig) = EvaluationStepResult.Success(Unit)
    }

    private fun successPreparation() = object : EvaluationModelPreparationPort {
        override suspend fun prepare(config: EvaluationRunConfig) = EvaluationStepResult.Success(Unit)

        override suspend fun warmup(config: EvaluationRunConfig) = EvaluationStepResult.Success(Unit)
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

    private fun config(
        caseIds: List<String> = listOf("case-a"),
        warmup: EvaluationWarmupPolicy = EvaluationWarmupPolicy.NONE,
    ): EvaluationRunConfig {
        val dataset = EvaluationDatasetIdentity(
            id = EvaluationDatasetId("fixture"),
            version = EvaluationDatasetVersion("1"),
            digest = EvaluationDatasetDigest("1".repeat(64)),
        )
        return EvaluationRunConfig(
            runId = EvaluationRunId("run-1"),
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
            warmupPolicy = warmup,
            caseTimeoutMs = 30_000,
        )
    }
}
