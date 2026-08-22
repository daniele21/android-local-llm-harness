package io.github.daniele21.localllm.evaluation.runtimeadapter

import io.github.daniele21.localllm.contracts.LocalLlmClient
import io.github.daniele21.localllm.evaluation.EvaluationCaseMetrics
import io.github.daniele21.localllm.evaluation.EvaluationCaseResult
import io.github.daniele21.localllm.evaluation.EvaluationRunConfig
import io.github.daniele21.localllm.evaluation.engine.DeterministicEvaluationCaseScorer
import io.github.daniele21.localllm.evaluation.engine.EvaluationBatchExecutionPort
import io.github.daniele21.localllm.evaluation.engine.EvaluationCaseBatch
import io.github.daniele21.localllm.evaluation.engine.EvaluationCaseDefinitionSource
import io.github.daniele21.localllm.evaluation.engine.EvaluationCaseGenerationRequestFactory
import io.github.daniele21.localllm.evaluation.engine.EvaluationRuntimeBindingSource
import io.github.daniele21.localllm.evaluation.engine.EvaluationStepResult
import io.github.daniele21.localllm.evaluation.engine.EvaluationTelemetryCorrelationPort
import io.github.daniele21.localllm.runtime.RuntimeEvaluationBatchClient
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CancellationException

/**
 * Production composition adapter for LLRT-9 evaluation batching.
 *
 * The evaluation engine depends only on [EvaluationBatchExecutionPort]. This adapter is the only
 * layer that composes the runtime-only batch client. It deliberately has no dependency on llama.cpp
 * or any backend implementation type.
 *
 * Native batching remains bounded to widths 2..4. A one-case tail delegates to the supplied serial
 * compatibility port so arbitrary immutable sample sizes retain exact ordering.
 */
@Suppress("LongParameterList")
class RuntimeEvaluationBatchExecutionAdapter(
    client: LocalLlmClient,
    batchClient: RuntimeEvaluationBatchClient,
    bindingSource: EvaluationRuntimeBindingSource,
    caseSource: EvaluationCaseDefinitionSource,
    requestFactory: EvaluationCaseGenerationRequestFactory,
    private val singletonFallback: EvaluationBatchExecutionPort,
    scorer: DeterministicEvaluationCaseScorer = DeterministicEvaluationCaseScorer(),
    telemetry: EvaluationTelemetryCorrelationPort = EvaluationTelemetryCorrelationPort { EvaluationCaseMetrics() },
) : EvaluationBatchExecutionPort {
    private val preparer = RuntimeEvaluationBatchPreparer(client, bindingSource, caseSource, requestFactory)
    private val awaiter = RuntimeEvaluationBatchAwaiter(batchClient)
    private val mapper = RuntimeEvaluationBatchResultMapper(scorer, telemetry)

    override suspend fun execute(
        config: EvaluationRunConfig,
        batch: EvaluationCaseBatch,
    ): EvaluationStepResult<List<EvaluationCaseResult>> {
        if (batch.orderedCaseIds.size == 1) return singletonFallback.execute(config, batch)
        return when (val preparation = preparer.prepare(config, batch)) {
            is RuntimeBatchPreparation.Failure -> preparation.result
            is RuntimeBatchPreparation.Ready -> executePrepared(config, preparation.batch)
        }
    }

    private suspend fun executePrepared(
        config: EvaluationRunConfig,
        prepared: PreparedRuntimeEvaluationBatch,
    ): EvaluationStepResult<List<EvaluationCaseResult>> {
        var primary: EvaluationStepResult<List<EvaluationCaseResult>>? = null
        var cancellation: CancellationException? = null
        var closeFailed = false
        try {
            primary = try {
                val outcome = withTimeout(config.caseTimeoutMs) { awaiter.await(prepared.runtimeRequest) }
                mapper.mapOutcome(prepared, outcome)
            } catch (_: TimeoutCancellationException) {
                EvaluationStepResult.Success(mapper.timeoutResults(prepared))
            } catch (error: CancellationException) {
                cancellation = error
                null
            } catch (_: Exception) {
                runtimeFailure(prepared.firstCaseId)
            }
        } finally {
            closeFailed = preparer.closeSessions(prepared.sessions)
        }
        cancellation?.let { throw it }
        return if (closeFailed && primary is EvaluationStepResult.Success) {
            runtimeFailure(prepared.firstCaseId)
        } else {
            primary ?: runtimeFailure(prepared.firstCaseId)
        }
    }
}
