package io.github.daniele21.localllm.evaluation.engine

import io.github.daniele21.localllm.evaluation.EvaluationCaseId
import io.github.daniele21.localllm.evaluation.EvaluationCaseResult
import io.github.daniele21.localllm.evaluation.EvaluationFailure
import io.github.daniele21.localllm.evaluation.EvaluationFailureCode
import io.github.daniele21.localllm.evaluation.EvaluationFailureStage
import io.github.daniele21.localllm.evaluation.EvaluationRunConfig
import java.util.concurrent.CancellationException

/**
 * Evaluation-only grouping of the already selected ordered sample set.
 *
 * This does not imply concurrent decode or native llama.cpp multi-sequence execution. The production
 * runtime remains governed by SingleDecodeScheduler until a separate measured backend capability is
 * introduced.
 */
data class EvaluationCaseBatch(val ordinal: Int, val orderedCaseIds: List<EvaluationCaseId>) {
    init {
        require(ordinal >= 0) { "Evaluation batch ordinal must not be negative" }
        require(orderedCaseIds.isNotEmpty()) { "Evaluation batch must contain at least one case" }
        require(orderedCaseIds.distinct().size == orderedCaseIds.size) {
            "Evaluation batch must not contain duplicate case IDs"
        }
    }
}

data class EvaluationBatchPlan(val batches: List<EvaluationCaseBatch>) {
    init {
        require(batches.isNotEmpty()) { "Evaluation batch plan must contain at least one batch" }
        require(batches.map { it.ordinal } == batches.indices.toList()) {
            "Evaluation batch ordinals must be contiguous from zero"
        }
        val caseIds = orderedCaseIds
        require(caseIds.distinct().size == caseIds.size) {
            "Evaluation batch plan must not contain duplicate case IDs across batches"
        }
    }

    val orderedCaseIds: List<EvaluationCaseId>
        get() = batches.flatMap(EvaluationCaseBatch::orderedCaseIds)

    companion object {
        fun create(orderedCaseIds: List<EvaluationCaseId>, maxCasesPerBatch: Int): EvaluationBatchPlan {
            require(orderedCaseIds.isNotEmpty()) { "Evaluation batch plan requires at least one case" }
            require(maxCasesPerBatch > 0) { "Maximum cases per evaluation batch must be positive" }
            require(orderedCaseIds.distinct().size == orderedCaseIds.size) {
                "Evaluation batch plan must not contain duplicate case IDs"
            }
            return EvaluationBatchPlan(
                orderedCaseIds.chunked(maxCasesPerBatch).mapIndexed { index, caseIds ->
                    EvaluationCaseBatch(index, caseIds)
                },
            )
        }
    }
}

fun interface EvaluationBatchExecutionPort {
    suspend fun execute(config: EvaluationRunConfig, batch: EvaluationCaseBatch): EvaluationStepResult<List<EvaluationCaseResult>>
}

/**
 * Compatibility implementation for the current single-case runtime boundary.
 *
 * It establishes deterministic batch attribution without changing decode concurrency. A future
 * native multi-sequence implementation can replace this port only in the evaluation composition.
 */
class SequentialEvaluationBatchExecution(private val caseExecution: EvaluationCaseExecutionPort) : EvaluationBatchExecutionPort {
    override suspend fun execute(
        config: EvaluationRunConfig,
        batch: EvaluationCaseBatch,
    ): EvaluationStepResult<List<EvaluationCaseResult>> {
        val results = ArrayList<EvaluationCaseResult>(batch.orderedCaseIds.size)
        for (caseId in batch.orderedCaseIds) {
            when (val result = caseExecution.execute(config, caseId)) {
                is EvaluationStepResult.Failure -> return result

                is EvaluationStepResult.Success -> {
                    if (result.value.caseId != caseId) return attributionFailure(caseId)
                    results += result.value
                }
            }
        }
        return EvaluationStepResult.Success(results)
    }
}

/**
 * Executes a bounded evaluation batch plan while preserving exact sample-set order and attribution.
 * It deliberately owns no production scheduler or backend-specific concurrency policy.
 */
class EvaluationBatchOrchestrator(private val execution: EvaluationBatchExecutionPort) {
    suspend fun execute(config: EvaluationRunConfig, plan: EvaluationBatchPlan): EvaluationStepResult<List<EvaluationCaseResult>> {
        if (plan.orderedCaseIds != config.sampling.orderedCaseIds) {
            return invalidPlanFailure()
        }

        val results = ArrayList<EvaluationCaseResult>(plan.orderedCaseIds.size)
        for (batch in plan.batches) {
            when (val outcome = executeBatch(config, batch)) {
                is EvaluationStepResult.Failure -> return outcome

                is EvaluationStepResult.Success -> {
                    if (outcome.value.map(EvaluationCaseResult::caseId) != batch.orderedCaseIds) {
                        return attributionFailure(batch.orderedCaseIds.first())
                    }
                    results += outcome.value
                }
            }
        }
        return EvaluationStepResult.Success(results)
    }

    private suspend fun executeBatch(
        config: EvaluationRunConfig,
        batch: EvaluationCaseBatch,
    ): EvaluationStepResult<List<EvaluationCaseResult>> = try {
        execution.execute(config, batch)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        EvaluationStepResult.Failure(
            EvaluationFailure(
                stage = EvaluationFailureStage.GENERATION,
                code = EvaluationFailureCode.RUNTIME_FAILURE,
            ),
        )
    }
}

private fun invalidPlanFailure(): EvaluationStepResult.Failure = EvaluationStepResult.Failure(
    EvaluationFailure(
        stage = EvaluationFailureStage.PREFLIGHT,
        code = EvaluationFailureCode.INVALID_CONFIGURATION,
    ),
)

private fun attributionFailure(caseId: EvaluationCaseId): EvaluationStepResult.Failure = EvaluationStepResult.Failure(
    EvaluationFailure(
        stage = EvaluationFailureStage.GENERATION,
        code = EvaluationFailureCode.INVALID_CONFIGURATION,
        caseId = caseId,
    ),
)
