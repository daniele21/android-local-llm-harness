package io.github.daniele21.localllm.evaluation.store.memory

import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.evaluation.EvaluationDatasetDigest
import io.github.daniele21.localllm.evaluation.EvaluationDatasetId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetIdentity
import io.github.daniele21.localllm.evaluation.EvaluationDatasetVersion
import io.github.daniele21.localllm.evaluation.EvaluationExecutionProfileId
import io.github.daniele21.localllm.evaluation.EvaluationExecutionProfileRef
import io.github.daniele21.localllm.evaluation.EvaluationModelIdentity
import io.github.daniele21.localllm.evaluation.EvaluationModelLoadPolicy
import io.github.daniele21.localllm.evaluation.EvaluationProgress
import io.github.daniele21.localllm.evaluation.EvaluationRetentionPolicy
import io.github.daniele21.localllm.evaluation.EvaluationRunConfig
import io.github.daniele21.localllm.evaluation.EvaluationRunDeleteStatus
import io.github.daniele21.localllm.evaluation.EvaluationRunId
import io.github.daniele21.localllm.evaluation.EvaluationRunQuery
import io.github.daniele21.localllm.evaluation.EvaluationRunState
import io.github.daniele21.localllm.evaluation.EvaluationRunSummary
import io.github.daniele21.localllm.evaluation.EvaluationWarmupPolicy
import io.github.daniele21.localllm.evaluation.SamplingPolicyId
import io.github.daniele21.localllm.evaluation.SamplingPolicyRef
import io.github.daniele21.localllm.evaluation.SamplingSelection
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class InMemoryEvaluationResultRepositoryTest {
    @Test
    fun `history order is deterministic by newest start then run id`() = runSuspend {
        val repository = InMemoryEvaluationResultRepository { 10_000 }
        repository.createRun(summary("run-b", 100))
        repository.createRun(summary("run-c", 200))
        repository.createRun(summary("run-a", 200))

        assertEquals(
            listOf("run-a", "run-c", "run-b"),
            repository.queryRuns(EvaluationRunQuery()).map { it.runId.value },
        )
    }

    @Test
    fun `query filters by dataset model state and timestamp`() = runSuspend {
        val repository = InMemoryEvaluationResultRepository { 10_000 }
        repository.createRun(summary("matching", 100, datasetId = "dataset-a", modelDigest = "a".repeat(64)))
        repository.createRun(summary("other-dataset", 90, datasetId = "dataset-b", modelDigest = "a".repeat(64)))
        repository.createRun(summary("other-model", 80, datasetId = "dataset-a", modelDigest = "b".repeat(64)))

        val result = repository.queryRuns(
            EvaluationRunQuery(
                states = setOf(EvaluationRunState.CREATED),
                datasetId = EvaluationDatasetId("dataset-a"),
                modelDigest = ModelDigest("a".repeat(64)),
                startedBeforeEpochMs = 150,
            ),
        )

        assertEquals(listOf("matching"), result.map { it.runId.value })
    }

    @Test
    fun `terminal deletion is allowed while active deletion is rejected`() = runSuspend {
        val repository = InMemoryEvaluationResultRepository { 10_000 }
        repository.createRun(summary("active", 100))
        repository.createRun(summary("terminal", 90, state = EvaluationRunState.CANCELLED))

        assertEquals(EvaluationRunDeleteStatus.ACTIVE_RUN, repository.deleteRun(EvaluationRunId("active")))
        assertEquals(EvaluationRunDeleteStatus.DELETED, repository.deleteRun(EvaluationRunId("terminal")))
        assertNotNull(repository.getRun(EvaluationRunId("active")))
        assertNull(repository.getRun(EvaluationRunId("terminal")))
    }

    @Test
    fun `retention removes oldest terminal runs but never active runs`() = runSuspend {
        val repository = InMemoryEvaluationResultRepository { 10_000 }
        repository.createRun(summary("active", 10, state = EvaluationRunState.CREATED))
        repository.createRun(summary("new-terminal", 300, state = EvaluationRunState.CANCELLED))
        repository.createRun(summary("middle-terminal", 200, state = EvaluationRunState.CANCELLED))
        repository.createRun(summary("old-terminal", 100, state = EvaluationRunState.CANCELLED))

        val result = repository.applyRetention(EvaluationRetentionPolicy(maxTerminalRuns = 2))

        assertEquals(listOf("old-terminal"), result.deletedRunIds.map { it.value })
        assertNotNull(repository.getRun(EvaluationRunId("active")))
        assertNotNull(repository.getRun(EvaluationRunId("new-terminal")))
        assertNotNull(repository.getRun(EvaluationRunId("middle-terminal")))
        assertNull(repository.getRun(EvaluationRunId("old-terminal")))
    }

    @Test
    fun `age retention uses injected clock deterministically`() = runSuspend {
        val repository = InMemoryEvaluationResultRepository { 1_000 }
        repository.createRun(summary("expired", 100, state = EvaluationRunState.CANCELLED))
        repository.createRun(summary("fresh", 950, state = EvaluationRunState.CANCELLED))

        repository.applyRetention(EvaluationRetentionPolicy(maxTerminalRuns = 10, maxAgeMs = 500))

        assertNull(repository.getRun(EvaluationRunId("expired")))
        assertNotNull(repository.getRun(EvaluationRunId("fresh")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `run configuration is immutable after creation`() = runSuspend {
        val repository = InMemoryEvaluationResultRepository { 1_000 }
        val created = summary("run", 100)
        repository.createRun(created)
        val changed = created.copy(
            config = created.config.copy(caseTimeoutMs = created.config.caseTimeoutMs + 1),
        )

        repository.updateRunSummary(changed)
    }

    private fun summary(
        runId: String,
        startedAt: Long,
        datasetId: String = "dataset-a",
        modelDigest: String = "a".repeat(64),
        state: EvaluationRunState = EvaluationRunState.CREATED,
    ): EvaluationRunSummary {
        val dataset = EvaluationDatasetIdentity(
            id = EvaluationDatasetId(datasetId),
            version = EvaluationDatasetVersion("1"),
            digest = EvaluationDatasetDigest("d".repeat(64)),
        )
        val id = EvaluationRunId(runId)
        val sampling = SamplingSelection.create(
            dataset = dataset,
            policy = SamplingPolicyRef(SamplingPolicyId("fixed"), 1),
            seed = 0,
            orderedCaseIds = listOf(io.github.daniele21.localllm.evaluation.EvaluationCaseId("case-1")),
        )
        val config = EvaluationRunConfig(
            runId = id,
            model = EvaluationModelIdentity(
                artifactDigest = ModelDigest(modelDigest),
                modelProfileId = "model",
            ),
            dataset = dataset,
            sampling = sampling,
            executionProfile = EvaluationExecutionProfileRef(EvaluationExecutionProfileId("direct"), 1),
            loadPolicy = EvaluationModelLoadPolicy.PRESERVE_CURRENT_RESIDENCY,
            warmupPolicy = EvaluationWarmupPolicy.NONE,
            caseTimeoutMs = 1_000,
        )
        val terminal = state == EvaluationRunState.COMPLETED ||
            state == EvaluationRunState.CANCELLED ||
            state == EvaluationRunState.FAILED
        return EvaluationRunSummary(
            runId = id,
            config = config,
            identity = null,
            state = state,
            progress = EvaluationProgress(totalCases = 1, attemptedCases = 0, completedCases = 0),
            quality = null,
            reliability = null,
            startedAtEpochMs = startedAt,
            completedAtEpochMs = if (terminal) startedAt + 10 else null,
            failure = null,
        )
    }

    private fun <T> runSuspend(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(
            object : Continuation<T> {
                override val context = EmptyCoroutineContext

                override fun resumeWith(result: Result<T>) {
                    outcome = result
                }
            },
        )
        return requireNotNull(outcome).getOrThrow()
    }
}
