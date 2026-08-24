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
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

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

    suspend fun onAggregation(runId: EvaluationRunId, aggregation: EvaluationRunAggregation) = Unit
}

sealed interface EvaluationEngineTerminal {
    val runId: EvaluationRunId
    val results: List<EvaluationCaseResult>

    data class Completed(
        override val runId: EvaluationRunId,
        override val results: List<EvaluationCaseResult>,
        val aggregation: EvaluationRunAggregation? = null,
    ) : EvaluationEngineTerminal

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
    private val runAggregation: EvaluationRunAggregationPort? = null,
) {
    private val ownershipLock = Any()
    private val cancelRequested = AtomicBoolean(false)
    private val activeCaseJob = AtomicReference<Job?>(null)

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
            EvaluationRunExecution(
                config = config,
                observer = observer,
                preflight = preflight,
                modelPreparation = modelPreparation,
                caseExecution = caseExecution,
                runAggregation = runAggregation,
                cancelRequested = cancelRequested,
                activeCaseJob = activeCaseJob,
            ).execute()
        } finally {
            release(config.runId)
        }
    }

    fun cancel(runId: EvaluationRunId): Boolean = synchronized(ownershipLock) {
        if (activeRunId != runId) {
            false
        } else {
            cancelRequested.set(true)
            activeCaseJob.get()?.cancel(CancellationException("Evaluation run cancellation requested"))
            true
        }
    }

    fun activeRun(): EvaluationRunId? = activeRunId

    private fun claim(runId: EvaluationRunId): Boolean = synchronized(ownershipLock) {
        if (activeRunId != null) {
            false
        } else {
            activeRunId = runId
            activeCaseJob.set(null)
            cancelRequested.set(false)
            true
        }
    }

    private fun release(runId: EvaluationRunId) = synchronized(ownershipLock) {
        if (activeRunId == runId) {
            activeCaseJob.getAndSet(null)?.cancel()
            activeRunId = null
            cancelRequested.set(false)
        }
    }
}

private class EvaluationRunExecution(
    private val config: EvaluationRunConfig,
    private val observer: EvaluationEngineObserver,
    private val preflight: EvaluationPreflightPort,
    private val modelPreparation: EvaluationModelPreparationPort,
    private val caseExecution: EvaluationCaseExecutionPort,
    private val runAggregation: EvaluationRunAggregationPort?,
    private val cancelRequested: AtomicBoolean,
    private val activeCaseJob: AtomicReference<Job?>,
) {
    suspend fun execute(): EvaluationEngineTerminal {
        val results = mutableListOf<EvaluationCaseResult>()
        observer.emitState(config.runId, EvaluationRunState.CREATED)

        validatePhase(results)?.let { return it }
        prepareModelPhase(results)?.let { return it }
        warmupPhase(results)?.let { return it }

        return executeCases(results)
    }

    private suspend fun validatePhase(results: List<EvaluationCaseResult>): EvaluationEngineTerminal? {
        observer.emitState(config.runId, EvaluationRunState.VALIDATING)
        return when (val validation = preflight.validate(config)) {
            is EvaluationStepResult.Failure -> fail(results, validation.failure)
            is EvaluationStepResult.Success -> cancellationIfRequested(results)
        }
    }

    private suspend fun prepareModelPhase(results: List<EvaluationCaseResult>): EvaluationEngineTerminal? {
        observer.emitState(config.runId, EvaluationRunState.PREPARING_MODEL)
        return when (val preparation = modelPreparation.prepare(config)) {
            is EvaluationStepResult.Failure -> fail(results, preparation.failure)
            is EvaluationStepResult.Success -> cancellationIfRequested(results)
        }
    }

    private suspend fun warmupPhase(results: List<EvaluationCaseResult>): EvaluationEngineTerminal? {
        if (config.warmupPolicy != EvaluationWarmupPolicy.ONE_UNSCORED_GENERATION) return null

        observer.emitState(config.runId, EvaluationRunState.WARMING_UP)
        return when (val warmup = modelPreparation.warmup(config)) {
            is EvaluationStepResult.Failure -> fail(results, warmup.failure)
            is EvaluationStepResult.Success -> cancellationIfRequested(results)
        }
    }

    private suspend fun executeCases(results: MutableList<EvaluationCaseResult>): EvaluationEngineTerminal {
        observer.emitState(config.runId, EvaluationRunState.RUNNING)
        val caseIds = config.sampling.orderedCaseIds
        var attempted = 0
        observer.emitProgress(config, attempted, results.size, null)

        for (caseId in caseIds) {
            if (cancelRequested.get()) return cancel(results)

            attempted += 1
            observer.emitProgress(config, attempted, results.size, caseId)
            val execution = try {
                executeActiveCase(caseId)
            } catch (error: CancellationException) {
                if (!cancelRequested.get()) throw error
                observer.emitProgress(config, attempted, results.size, null)
                return cancel(results)
            }
            when (execution) {
                is EvaluationStepResult.Failure -> return fail(results, execution.failure)
                is EvaluationStepResult.Success -> recordCaseResult(caseId, execution.value, results, attempted)
            }
        }

        return aggregateAndComplete(results)
    }

    private suspend fun aggregateAndComplete(results: List<EvaluationCaseResult>): EvaluationEngineTerminal {
        observer.emitState(config.runId, EvaluationRunState.AGGREGATING)
        val aggregation = when (val port = runAggregation) {
            null -> null

            else -> when (val result = port.aggregate(config, results)) {
                is EvaluationStepResult.Failure -> return fail(results, result.failure)
                is EvaluationStepResult.Success -> result.value
            }
        }
        if (aggregation != null) observer.onAggregation(config.runId, aggregation)
        observer.emitState(config.runId, EvaluationRunState.COMPLETED)
        return EvaluationEngineTerminal.Completed(config.runId, results.toList(), aggregation)
    }

    private suspend fun executeActiveCase(caseId: EvaluationCaseId): EvaluationStepResult<EvaluationCaseResult> = coroutineScope {
        val job = async { caseExecution.execute(config, caseId) }
        check(activeCaseJob.compareAndSet(null, job)) { "Evaluation engine already owns an active case job" }
        try {
            if (cancelRequested.get()) {
                job.cancel(CancellationException("Evaluation run cancellation requested"))
            }
            job.await()
        } finally {
            activeCaseJob.compareAndSet(job, null)
        }
    }

    private suspend fun recordCaseResult(
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
        observer.emitProgress(config, attempted, results.size, null)
    }

    private suspend fun cancellationIfRequested(results: List<EvaluationCaseResult>): EvaluationEngineTerminal.Cancelled? =
        if (cancelRequested.get()) cancel(results) else null

    private suspend fun fail(results: List<EvaluationCaseResult>, failure: EvaluationFailure): EvaluationEngineTerminal.Failed {
        observer.emitState(config.runId, EvaluationRunState.FAILED)
        return EvaluationEngineTerminal.Failed(config.runId, results.toList(), failure)
    }

    private suspend fun cancel(results: List<EvaluationCaseResult>): EvaluationEngineTerminal.Cancelled {
        observer.emitState(config.runId, EvaluationRunState.CANCELLING)
        observer.emitState(config.runId, EvaluationRunState.CANCELLED)
        return EvaluationEngineTerminal.Cancelled(config.runId, results.toList())
    }
}

private suspend fun EvaluationEngineObserver.emitState(runId: EvaluationRunId, state: EvaluationRunState) {
    onStateChanged(runId, state)
}

private suspend fun EvaluationEngineObserver.emitProgress(
    config: EvaluationRunConfig,
    attempted: Int,
    completed: Int,
    currentCaseId: EvaluationCaseId?,
) {
    onProgress(
        config.runId,
        EvaluationProgress(
            totalCases = config.sampling.orderedCaseIds.size,
            attemptedCases = attempted,
            completedCases = completed,
            currentCaseId = currentCaseId,
        ),
    )
}
