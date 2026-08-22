package io.github.daniele21.localllm.evaluation.runtimeadapter

import io.github.daniele21.localllm.contracts.GenerationRequest
import io.github.daniele21.localllm.contracts.LocalLlmClient
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.SessionOptions
import io.github.daniele21.localllm.evaluation.EvaluationCaseId
import io.github.daniele21.localllm.evaluation.EvaluationCaseMetrics
import io.github.daniele21.localllm.evaluation.EvaluationCaseResult
import io.github.daniele21.localllm.evaluation.EvaluationCaseStatus
import io.github.daniele21.localllm.evaluation.EvaluationDatasetCaseV1
import io.github.daniele21.localllm.evaluation.EvaluationFailure
import io.github.daniele21.localllm.evaluation.EvaluationFailureCode
import io.github.daniele21.localllm.evaluation.EvaluationFailureStage
import io.github.daniele21.localllm.evaluation.EvaluationRunConfig
import io.github.daniele21.localllm.evaluation.engine.DeterministicEvaluationCaseScorer
import io.github.daniele21.localllm.evaluation.engine.EvaluationBatchExecutionPort
import io.github.daniele21.localllm.evaluation.engine.EvaluationCaseBatch
import io.github.daniele21.localllm.evaluation.engine.EvaluationCaseDefinitionSource
import io.github.daniele21.localllm.evaluation.engine.EvaluationCaseGenerationRequestFactory
import io.github.daniele21.localllm.evaluation.engine.EvaluationRuntimeBinding
import io.github.daniele21.localllm.evaluation.engine.EvaluationRuntimeBindingSource
import io.github.daniele21.localllm.evaluation.engine.EvaluationStepResult
import io.github.daniele21.localllm.evaluation.engine.EvaluationTelemetryCorrelationPort
import io.github.daniele21.localllm.runtime.RuntimeEvaluationBatchCaseResult
import io.github.daniele21.localllm.runtime.RuntimeEvaluationBatchClient
import io.github.daniele21.localllm.runtime.RuntimeEvaluationBatchHandle
import io.github.daniele21.localllm.runtime.RuntimeEvaluationBatchOutcome
import io.github.daniele21.localllm.runtime.RuntimeEvaluationBatchRequest
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Production composition adapter for LLRT-9 evaluation batching.
 *
 * The evaluation engine depends only on [EvaluationBatchExecutionPort]. This adapter is the only
 * layer that knows the runtime-only batch client. It deliberately has no dependency on llama.cpp or
 * any backend implementation type.
 *
 * Native batching remains bounded to widths 2..4. A one-case tail delegates to the supplied serial
 * compatibility port so arbitrary immutable sample sizes retain exact ordering.
 */
@Suppress("LongParameterList")
class RuntimeEvaluationBatchExecutionAdapter(
    private val client: LocalLlmClient,
    private val batchClient: RuntimeEvaluationBatchClient,
    private val bindingSource: EvaluationRuntimeBindingSource,
    private val caseSource: EvaluationCaseDefinitionSource,
    private val requestFactory: EvaluationCaseGenerationRequestFactory,
    private val singletonFallback: EvaluationBatchExecutionPort,
    private val scorer: DeterministicEvaluationCaseScorer = DeterministicEvaluationCaseScorer(),
    private val telemetry: EvaluationTelemetryCorrelationPort = EvaluationTelemetryCorrelationPort { EvaluationCaseMetrics() },
) : EvaluationBatchExecutionPort {
    override suspend fun execute(
        config: EvaluationRunConfig,
        batch: EvaluationCaseBatch,
    ): EvaluationStepResult<List<EvaluationCaseResult>> {
        if (batch.orderedCaseIds.size == 1) {
            return singletonFallback.execute(config, batch)
        }
        if (batch.orderedCaseIds.size !in MIN_BATCH_WIDTH..MAX_BATCH_WIDTH) {
            return invalidConfiguration(batch.orderedCaseIds.firstOrNull())
        }
        if (!config.sampling.orderedCaseIds.containsOrderedSubsequence(batch.orderedCaseIds)) {
            return invalidConfiguration(batch.orderedCaseIds.first())
        }

        val binding = resolvePreparedBinding(config) ?: return runtimeFailure(batch.orderedCaseIds.first())
        val prepared = prepareCases(config, batch, binding)
            ?: return invalidConfiguration(batch.orderedCaseIds.first())
        val sessions = createSessions(binding, prepared.size)
            ?: return runtimeFailure(batch.orderedCaseIds.first())

        var primary: EvaluationStepResult<List<EvaluationCaseResult>>? = null
        var cancellation: CancellationException? = null
        try {
            val requests = createRequests(config, binding, prepared, sessions)
                ?: return invalidConfiguration(batch.orderedCaseIds.first())
            val runtimeRequest = createRuntimeRequest(config, batch, requests)
                ?: return invalidConfiguration(batch.orderedCaseIds.first())
            primary = try {
                val outcome = withTimeout(config.caseTimeoutMs) { awaitTerminal(runtimeRequest) }
                mapOutcome(prepared, requests, outcome)
            } catch (_: TimeoutCancellationException) {
                EvaluationStepResult.Success(timeoutResults(prepared, requests))
            } catch (error: CancellationException) {
                cancellation = error
                null
            } catch (_: Exception) {
                runtimeFailure(batch.orderedCaseIds.first())
            }
        } finally {
            val closeFailed = closeSessions(sessions)
            cancellation?.let { throw it }
            if (primary is EvaluationStepResult.Success && closeFailed) {
                primary = runtimeFailure(batch.orderedCaseIds.first())
            }
        }
        return checkNotNull(primary)
    }

    private fun resolvePreparedBinding(config: EvaluationRunConfig): EvaluationRuntimeBinding? {
        val binding = try {
            bindingSource.resolve(config.model, config.executionProfile)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return null
        }
        if (binding == null || binding.model != config.model || binding.executionProfile != config.executionProfile) {
            return null
        }
        return try {
            binding.takeIf { client.runtimeSnapshot().loadedModel == config.model.artifactDigest }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    private fun prepareCases(
        config: EvaluationRunConfig,
        batch: EvaluationCaseBatch,
        binding: EvaluationRuntimeBinding,
    ): List<PreparedCase>? {
        val prepared = ArrayList<PreparedCase>(batch.orderedCaseIds.size)
        for (caseId in batch.orderedCaseIds) {
            val case = try {
                caseSource.load(config, caseId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            } ?: return null
            if (case.id != caseId) return null
            prepared += PreparedCase(caseId, case, binding)
        }
        return prepared
    }

    private fun createSessions(binding: EvaluationRuntimeBinding, count: Int): List<SessionId>? {
        val sessions = ArrayList<SessionId>(count)
        try {
            repeat(count) {
                sessions += client.createSession(
                    binding.applicationId,
                    binding.useCaseId,
                    SessionOptions(kind = SessionKind.STATELESS),
                )
            }
            if (sessions.distinct().size != sessions.size) {
                closeSessions(sessions)
                return null
            }
            return sessions
        } catch (error: CancellationException) {
            closeSessions(sessions)
            throw error
        } catch (_: Exception) {
            closeSessions(sessions)
            return null
        }
    }

    private fun createRequests(
        config: EvaluationRunConfig,
        binding: EvaluationRuntimeBinding,
        prepared: List<PreparedCase>,
        sessions: List<SessionId>,
    ): List<GenerationRequest>? {
        val requests = prepared.zip(sessions).map { (preparedCase, sessionId) ->
            val request = try {
                requestFactory.create(config, preparedCase.case, binding, sessionId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                return null
            }
            if (!request.matches(binding, sessionId)) return null
            request
        }
        return requests.takeIf { values -> values.map(GenerationRequest::requestId).distinct().size == values.size }
    }

    private fun createRuntimeRequest(
        config: EvaluationRunConfig,
        batch: EvaluationCaseBatch,
        requests: List<GenerationRequest>,
    ): RuntimeEvaluationBatchRequest? = try {
        val batchId = RequestId("evaluation-batch:${config.runId.value}:${batch.ordinal}")
        RuntimeEvaluationBatchRequest(batchId, requests)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private suspend fun awaitTerminal(request: RuntimeEvaluationBatchRequest): RuntimeEvaluationBatchOutcome =
        suspendCancellableCoroutine { continuation ->
            val terminal = AtomicBoolean(false)
            var handle: RuntimeEvaluationBatchHandle? = null
            continuation.invokeOnCancellation {
                if (terminal.compareAndSet(false, true)) {
                    runCatching { handle?.cancel() }
                }
            }
            try {
                val started = batchClient.generateEvaluationBatch(request) { outcome ->
                    if (terminal.compareAndSet(false, true) && continuation.isActive) {
                        continuation.resume(outcome)
                    }
                }
                handle = started
                if (continuation.isCancelled) {
                    runCatching { started.cancel() }
                }
            } catch (error: CancellationException) {
                continuation.cancel(error)
            } catch (_: Exception) {
                if (terminal.compareAndSet(false, true) && continuation.isActive) {
                    continuation.resume(RuntimeEvaluationBatchOutcome.Failed(io.github.daniele21.localllm.contracts.LocalLlmError.NativeRuntime("evaluation batch start failed")))
                }
            }
        }

    private fun mapOutcome(
        prepared: List<PreparedCase>,
        requests: List<GenerationRequest>,
        outcome: RuntimeEvaluationBatchOutcome,
    ): EvaluationStepResult<List<EvaluationCaseResult>> = when (outcome) {
        is RuntimeEvaluationBatchOutcome.Failed -> runtimeFailure(prepared.first().caseId)
        is RuntimeEvaluationBatchOutcome.Completed -> {
            val expectedIds = requests.map(GenerationRequest::requestId)
            if (outcome.cases.map(RuntimeEvaluationBatchCaseResult::requestId) != expectedIds) {
                invalidConfiguration(prepared.first().caseId)
            } else {
                EvaluationStepResult.Success(
                    outcome.cases.zip(prepared).map { (runtimeCase, preparedCase) ->
                        when (runtimeCase) {
                            is RuntimeEvaluationBatchCaseResult.Completed -> scorer.score(
                                case = preparedCase.case,
                                requestId = runtimeCase.requestId,
                                generated = runtimeCase.output,
                                metrics = correlatedMetrics(runtimeCase.requestId),
                            )

                            is RuntimeEvaluationBatchCaseResult.Cancelled -> cancelledResult(
                                preparedCase,
                                runtimeCase.requestId,
                            )
                        }
                    },
                )
            }
        }
    }

    private fun timeoutResults(
        prepared: List<PreparedCase>,
        requests: List<GenerationRequest>,
    ): List<EvaluationCaseResult> = prepared.zip(requests).map { (preparedCase, request) ->
        EvaluationCaseResult(
            caseId = preparedCase.caseId,
            categoryId = preparedCase.case.categoryId,
            evaluator = preparedCase.case.evaluator,
            status = EvaluationCaseStatus.TIMEOUT,
            outcome = null,
            requestId = request.requestId,
            metrics = correlatedMetrics(request.requestId),
            failure = EvaluationFailure(
                stage = EvaluationFailureStage.GENERATION,
                code = EvaluationFailureCode.CASE_TIMEOUT,
                caseId = preparedCase.caseId,
                retryable = true,
            ),
        )
    }

    private fun cancelledResult(prepared: PreparedCase, requestId: RequestId): EvaluationCaseResult = EvaluationCaseResult(
        caseId = prepared.caseId,
        categoryId = prepared.case.categoryId,
        evaluator = prepared.case.evaluator,
        status = EvaluationCaseStatus.CANCELLED,
        outcome = null,
        requestId = requestId,
        metrics = correlatedMetrics(requestId),
        failure = EvaluationFailure(
            stage = EvaluationFailureStage.CANCELLATION,
            code = EvaluationFailureCode.CANCELLED,
            caseId = prepared.caseId,
            retryable = true,
        ),
    )

    private fun correlatedMetrics(requestId: RequestId): EvaluationCaseMetrics = try {
        telemetry.metrics(requestId)
    } catch (_: Exception) {
        EvaluationCaseMetrics()
    }

    private fun closeSessions(sessions: List<SessionId>): Boolean {
        var failed = false
        sessions.asReversed().forEach { sessionId ->
            try {
                client.closeSession(sessionId)
            } catch (_: Exception) {
                failed = true
            }
        }
        return failed
    }

    private fun GenerationRequest.matches(binding: EvaluationRuntimeBinding, sessionId: SessionId): Boolean =
        this.sessionId == sessionId && applicationId == binding.applicationId && useCaseId == binding.useCaseId

    private fun List<EvaluationCaseId>.containsOrderedSubsequence(candidate: List<EvaluationCaseId>): Boolean {
        if (candidate.isEmpty()) return false
        val start = indexOf(candidate.first())
        if (start < 0 || start + candidate.size > size) return false
        return subList(start, start + candidate.size) == candidate
    }

    private data class PreparedCase(
        val caseId: EvaluationCaseId,
        val case: EvaluationDatasetCaseV1,
        val binding: EvaluationRuntimeBinding,
    )

    private companion object {
        const val MIN_BATCH_WIDTH = 2
        const val MAX_BATCH_WIDTH = 4
    }
}

private fun invalidConfiguration(caseId: EvaluationCaseId?): EvaluationStepResult.Failure = EvaluationStepResult.Failure(
    EvaluationFailure(
        stage = EvaluationFailureStage.GENERATION,
        code = EvaluationFailureCode.INVALID_CONFIGURATION,
        caseId = caseId,
    ),
)

private fun runtimeFailure(caseId: EvaluationCaseId?): EvaluationStepResult.Failure = EvaluationStepResult.Failure(
    EvaluationFailure(
        stage = EvaluationFailureStage.GENERATION,
        code = EvaluationFailureCode.RUNTIME_FAILURE,
        caseId = caseId,
        retryable = true,
    ),
)
