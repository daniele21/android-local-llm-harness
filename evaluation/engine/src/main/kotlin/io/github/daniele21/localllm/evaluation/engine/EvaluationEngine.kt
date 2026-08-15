package io.github.daniele21.localllm.evaluation.engine

import io.github.daniele21.localllm.evaluation.EvaluationCaseId
import io.github.daniele21.localllm.evaluation.EvaluationCaseResult
import io.github.daniele21.localllm.evaluation.EvaluationFailure
import io.github.daniele21.localllm.evaluation.EvaluationFailureCode
import io.github.daniele21.localllm.evaluation.EvaluationFailureStage
import io.github.daniele21.localllm.evaluation.EvaluationProgress
import io.github.daniele21.localllm.evaluation.EvaluationRunConfig
import io.github.daniele21.localllm.evaluation.EvaluationRunId
import io.github.daniele21.localllm.evaluation.EvaluationRunState
import io.github.daniele21.localllm.evaluation.EvaluationWarmupPolicy
import java.util.concurrent.atomic.AtomicBoolean

sealed interface EvaluationStepResult<out T> {
    data class Success<T>(val value: T) : EvaluationStepResult<T>

    data class Failure(val failure: EvaluationFailure) : EvaluationStepResult<Nothing>
}

interface EvaluationPreflightPort {
    suspend fun validate(config: EvaluationRunConfig): EvaluationStepResult<Unit>
}

interface EvaluationModelPreparationPort {
    suspend fun prepare(config: EvaluationRunConfig): EvaluationStepResult<Unit>

    suspend fun warmup(config: EvaluationRunConfig): EvaluationStepResult<Unit>
}

interface EvaluationCaseExecutionPort {
    suspend fun execute(config: EvaluationRunConfig, caseId: EvaluationCaseId): EvaluationStepResult<EvaluationCaseResult>
}

interface EvaluationEngineObserver {
    suspend fun onStateChanged(runId: EvaluationRunId, state: EvaluationRunState) = Unit

    suspend fun onProgress(runId: EvaluationRunId, progress: EvaluationProgress) = Unit

    suspend fun onCaseResult(runId: EvaluationRunId, result: EvaluationCaseResult) = Unit
}

sealed interface EvaluationEngineTerminal {
    val runId: EvaluationRunId
    val results: List<EvaluationCaseResult>

    data class Completed(override val runId: EvaluationRunId, override val results: List<EvaluationCaseResult>) : EvaluationEngineTerminal

    data class Cancelled(override val runId: EvaluationRunId, override val results: List<EvaluationCaseResult>) : EvaluationEngineTerminal

    data class Failed(
        override val runId: EvaluationRunId,
        override val results: List<EvaluationCaseResult>,
        val failure: EvaluationFailure,
    ) : EvaluationEngineTerminal
}

class EvaluationEngine(
    private val preflight: EvaluationPreflightPort,
    private val modelPreparation: EvaluationModelPreparationPort,
    private val caseExecution: EvaluationCaseExecutionPort,
) {
    private val ownershipLock = Any()
    private val cancelRequested = AtomicBoolean(false)

    @Volatile
    private var activeRunId: EvaluationRunId? = null

    suspend fun run(
        config: EvaluationRunConfig,
        observer: EvaluationEngineObserver = object : EvaluationEngineObserver {},
    ): EvaluationEngineTerminal {
        if (!claim(config.runId)) {
            return EvaluationEngineTerminal.Failed(
                runId = config.runId,
                results = emptyList(),
                failure = EvaluationFailure(
                    stage = EvaluationFailureStage.PREFLIGHT,
                    code = EvaluationFailureCode.INVALID_CONFIGURATION,
                ),
            )
        }

        return try {
            executeClaimed(config, observer)
        } finally {
            release(config.runId)
        }
    }

    fun cancel(runId: EvaluationRunId): Boolean = synchronized(ownershipLock) {
        if (activeRunId != runId) {
            false
        } else {
            cancelRequested.set(true)
            true
        }
    }

    fun activeRun(): EvaluationRunId? = activeRunId

    private suspend fun executeClaimed(config: EvaluationRunConfig, observer: EvaluationEngineObserver): EvaluationEngineTerminal {
        val results = mutableListOf<EvaluationCaseResult>()
        emitState(config, observer, EvaluationRunState.CREATED)

        validatePhase(config, observer, results)?.let { return it }
        prepareModelPhase(config, observer, results)?.let { return it }
        warmupPhase(config, observer, results)?.let { return it }

        return executeCases(config, observer, results)
    }

    private suspend fun validatePhase(
        config: EvaluationRunConfig,
        observer: EvaluationEngineObserver,
        results: List<EvaluationCaseResult>,
    ): EvaluationEngineTerminal? {
        emitState(config, observer, EvaluationRunState.VALIDATING)
        return when (val validation = preflight.validate(config)) {
            is EvaluationStepResult.Failure -> fail(config, observer, results, validation.failure)
            is EvaluationStepResult.Success -> cancellationIfRequested(config, observer, results)
        }
    }

    private suspend fun prepareModelPhase(
        config: EvaluationRunConfig,
        observer: EvaluationEngineObserver,
        results: List<EvaluationCaseResult>,
    ): EvaluationEngineTerminal? {
        emitState(config, observer, EvaluationRunState.PREPARING_MODEL)
        return when (val preparation = modelPreparation.prepare(config)) {
            is EvaluationStepResult.Failure -> fail(config, observer, results, preparation.failure)
            is EvaluationStepResult.Success -> cancellationIfRequested(config, observer, results)
        }
    }

    private suspend fun warmupPhase(
        config: EvaluationRunConfig,
        observer: EvaluationEngineObserver,
        results: List<EvaluationCaseResult>,
    ): EvaluationEngineTerminal? {
        if (config.warmupPolicy != EvaluationWarmupPolicy.ONE_UNSCORED_GENERATION) return null

        emitState(config, observer, EvaluationRunState.WARMING_UP)
        return when (val warmup = modelPreparation.warmup(config)) {
            is EvaluationStepResult.Failure -> fail(config, observer, results, warmup.failure)
            is EvaluationStepResult.Success -> cancellationIfRequested(config, observer, results)
        }
    }

    private suspend fun executeCases(
        config: EvaluationRunConfig,
        observer: EvaluationEngineObserver,
        results: MutableList<EvaluationCaseResult>,
    ): EvaluationEngineTerminal {
        emitState(config, observer, EvaluationRunState.RUNNING)
        val caseIds = config.sampling.orderedCaseIds
        var attempted = 0
        emitProgress(config, observer, attempted, results.size, null)

        for (caseId in caseIds) {
            if (cancelRequested.get()) return cancel(config, observer, results)

            attempted += 1
            emitProgress(config, observer, attempted, results.size, caseId)
            when (val execution = caseExecution.execute(config, caseId)) {
                is EvaluationStepResult.Failure -> return fail(config, observer, results, execution.failure)
                is EvaluationStepResult.Success -> recordCaseResult(config, observer, caseId, execution.value, results, attempted)
            }
        }

        emitState(config, observer, EvaluationRunState.AGGREGATING)
        emitState(config, observer, EvaluationRunState.COMPLETED)
        return EvaluationEngineTerminal.Completed(config.runId, results.toList())
    }

    private suspend fun recordCaseResult(
        config: EvaluationRunConfig,
        observer: EvaluationEngineObserver,
        requestedCaseId: EvaluationCaseId,
        result: EvaluationCaseResult,
        results: MutableList<EvaluationCaseResult>,
        attempted: Int,
    ) {
        require(result.caseId == requestedCaseId) {
            "Evaluation case execution result ID must match requested case ID"
        }
        results += result
        observer.onCaseResult(config.runId, result)
        emitProgress(config, observer, attempted, results.size, null)
    }

    private suspend fun cancellationIfRequested(
        config: EvaluationRunConfig,
        observer: EvaluationEngineObserver,
        results: List<EvaluationCaseResult>,
    ): EvaluationEngineTerminal.Cancelled? = if (cancelRequested.get()) cancel(config, observer, results) else null

    private suspend fun fail(
        config: EvaluationRunConfig,
        observer: EvaluationEngineObserver,
        results: List<EvaluationCaseResult>,
        failure: EvaluationFailure,
    ): EvaluationEngineTerminal.Failed {
        emitState(config, observer, EvaluationRunState.FAILED)
        return EvaluationEngineTerminal.Failed(config.runId, results.toList(), failure)
    }

    private suspend fun cancel(
        config: EvaluationRunConfig,
        observer: EvaluationEngineObserver,
        results: List<EvaluationCaseResult>,
    ): EvaluationEngineTerminal.Cancelled {
        emitState(config, observer, EvaluationRunState.CANCELLING)
        emitState(config, observer, EvaluationRunState.CANCELLED)
        return EvaluationEngineTerminal.Cancelled(config.runId, results.toList())
    }

    private suspend fun emitState(config: EvaluationRunConfig, observer: EvaluationEngineObserver, state: EvaluationRunState) {
        observer.onStateChanged(config.runId, state)
    }

    private suspend fun emitProgress(
        config: EvaluationRunConfig,
        observer: EvaluationEngineObserver,
        attempted: Int,
        completed: Int,
        currentCaseId: EvaluationCaseId?,
    ) {
        observer.onProgress(
            config.runId,
            EvaluationProgress(
                totalCases = config.sampling.orderedCaseIds.size,
                attemptedCases = attempted,
                completedCases = completed,
                currentCaseId = currentCaseId,
            ),
        )
    }

    private fun claim(runId: EvaluationRunId): Boolean = synchronized(ownershipLock) {
        if (activeRunId != null) {
            false
        } else {
            activeRunId = runId
            cancelRequested.set(false)
            true
        }
    }

    private fun release(runId: EvaluationRunId) = synchronized(ownershipLock) {
        if (activeRunId == runId) {
            activeRunId = null
            cancelRequested.set(false)
        }
    }
}
