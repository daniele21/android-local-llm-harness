package io.github.daniele21.localllm.evaluation.persistence

import io.github.daniele21.localllm.evaluation.EvaluationCaseResult
import io.github.daniele21.localllm.evaluation.EvaluationProgress
import io.github.daniele21.localllm.evaluation.EvaluationResultRepository
import io.github.daniele21.localllm.evaluation.EvaluationRunConfig
import io.github.daniele21.localllm.evaluation.EvaluationRunIdentity
import io.github.daniele21.localllm.evaluation.EvaluationRunId
import io.github.daniele21.localllm.evaluation.EvaluationRunState
import io.github.daniele21.localllm.evaluation.EvaluationRunSummary
import io.github.daniele21.localllm.evaluation.engine.EvaluationEngine
import io.github.daniele21.localllm.evaluation.engine.EvaluationEngineObserver
import io.github.daniele21.localllm.evaluation.engine.EvaluationEngineTerminal

fun interface EvaluationClock {
    fun nowEpochMs(): Long
}

class EvaluationLifecyclePersistence(
    private val repository: EvaluationResultRepository,
    private val clock: EvaluationClock,
) {
    suspend fun run(
        engine: EvaluationEngine,
        config: EvaluationRunConfig,
        identity: EvaluationRunIdentity?,
        observer: EvaluationEngineObserver = object : EvaluationEngineObserver {},
    ): EvaluationEngineTerminal {
        var summary = initialSummary(config, identity, clock.nowEpochMs())
        repository.createRun(summary)

        val persistenceObserver = object : EvaluationEngineObserver {
            override suspend fun onStateChanged(runId: EvaluationRunId, state: EvaluationRunState) {
                require(runId == config.runId) { "Lifecycle observer run ID must match persisted run" }
                if (!state.isTerminal()) {
                    summary = summary.copy(state = state)
                    repository.updateRunSummary(summary)
                }
                observer.onStateChanged(runId, state)
            }

            override suspend fun onProgress(runId: EvaluationRunId, progress: EvaluationProgress) {
                require(runId == config.runId) { "Progress observer run ID must match persisted run" }
                summary = summary.copy(progress = progress)
                repository.updateRunSummary(summary)
                observer.onProgress(runId, progress)
            }

            override suspend fun onCaseResult(runId: EvaluationRunId, result: EvaluationCaseResult) {
                observer.onCaseResult(runId, result)
            }
        }

        val terminal = engine.run(config, persistenceObserver)
        summary = terminalSummary(summary, terminal, identity, clock.nowEpochMs())
        repository.updateRunSummary(summary)
        return terminal
    }
}

private fun initialSummary(
    config: EvaluationRunConfig,
    identity: EvaluationRunIdentity?,
    startedAtEpochMs: Long,
): EvaluationRunSummary = EvaluationRunSummary(
    runId = config.runId,
    config = config,
    identity = identity,
    state = EvaluationRunState.CREATED,
    progress = EvaluationProgress(
        totalCases = config.sampling.orderedCaseIds.size,
        attemptedCases = 0,
        completedCases = 0,
    ),
    quality = null,
    reliability = null,
    startedAtEpochMs = startedAtEpochMs,
    completedAtEpochMs = null,
    failure = null,
)

private fun terminalSummary(
    current: EvaluationRunSummary,
    terminal: EvaluationEngineTerminal,
    identity: EvaluationRunIdentity?,
    completedAtEpochMs: Long,
): EvaluationRunSummary = when (terminal) {
    is EvaluationEngineTerminal.Completed -> current.copy(
        identity = requireNotNull(identity) { "Completed persisted evaluation run requires a run identity" },
        state = EvaluationRunState.COMPLETED,
        completedAtEpochMs = completedAtEpochMs,
        failure = null,
    )

    is EvaluationEngineTerminal.Cancelled -> current.copy(
        identity = identity,
        state = EvaluationRunState.CANCELLED,
        completedAtEpochMs = completedAtEpochMs,
        failure = null,
    )

    is EvaluationEngineTerminal.Failed -> current.copy(
        identity = identity,
        state = EvaluationRunState.FAILED,
        completedAtEpochMs = completedAtEpochMs,
        failure = terminal.failure,
    )
}

private fun EvaluationRunState.isTerminal(): Boolean = when (this) {
    EvaluationRunState.COMPLETED,
    EvaluationRunState.CANCELLED,
    EvaluationRunState.FAILED,
    -> true

    else -> false
}
