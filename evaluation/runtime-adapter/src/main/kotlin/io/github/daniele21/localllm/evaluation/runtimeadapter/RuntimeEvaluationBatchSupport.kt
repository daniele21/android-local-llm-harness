package io.github.daniele21.localllm.evaluation.runtimeadapter

import io.github.daniele21.localllm.contracts.GenerationRequest
import io.github.daniele21.localllm.contracts.LocalLlmClient
import io.github.daniele21.localllm.contracts.LocalLlmError
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
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

internal data class PreparedEvaluationCase(
    val caseId: EvaluationCaseId,
    val case: EvaluationDatasetCaseV1,
)

internal data class PreparedRuntimeEvaluationBatch(
    val firstCaseId: EvaluationCaseId,
    val cases: List<PreparedEvaluationCase>,
    val sessions: List<SessionId>,
    val requests: List<GenerationRequest>,
    val runtimeRequest: RuntimeEvaluationBatchRequest,
)

internal sealed interface RuntimeBatchPreparation {
    data class Ready(val batch: PreparedRuntimeEvaluationBatch) : RuntimeBatchPreparation

    data class Failure(val result: EvaluationStepResult.Failure) : RuntimeBatchPreparation
}

internal class RuntimeEvaluationBatchPreparer(
    private val client: LocalLlmClient,
    private val bindingSource: EvaluationRuntimeBindingSource,
    private val caseSource: EvaluationCaseDefinitionSource,
    private val requestFactory: EvaluationCaseGenerationRequestFactory,
) {
    fun prepare(config: EvaluationRunConfig, batch: EvaluationCaseBatch): RuntimeBatchPreparation {
        val firstCaseId = batch.orderedCaseIds.firstOrNull()
        val validationFailure = validateBatch(config, batch, firstCaseId)
        if (validationFailure != null) return RuntimeBatchPreparation.Failure(validationFailure)

        val binding = resolvePreparedBinding(config)
            ?: return RuntimeBatchPreparation.Failure(runtimeFailure(firstCaseId))
        val cases = loadCases(config, batch)
            ?: return RuntimeBatchPreparation.Failure(invalidConfiguration(firstCaseId))
        return prepareResidentBatch(config, batch, binding, cases)
    }

    fun closeSessions(sessions: List<SessionId>): Boolean {
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

    private fun validateBatch(
        config: EvaluationRunConfig,
        batch: EvaluationCaseBatch,
        firstCaseId: EvaluationCaseId?,
    ): EvaluationStepResult.Failure? = when {
        batch.orderedCaseIds.size !in MIN_BATCH_WIDTH..MAX_BATCH_WIDTH -> invalidConfiguration(firstCaseId)
        !config.sampling.orderedCaseIds.containsOrderedSubsequence(batch.orderedCaseIds) -> invalidConfiguration(firstCaseId)
        else -> null
    }

    private fun resolvePreparedBinding(config: EvaluationRunConfig): EvaluationRuntimeBinding? {
        val binding = try {
            bindingSource.resolve(config.model, config.executionProfile)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        val exactBinding = binding?.takeIf {
            it.model == config.model && it.executionProfile == config.executionProfile
        } ?: return null
        return try {
            exactBinding.takeIf { client.runtimeSnapshot().loadedModel == config.model.artifactDigest }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    private fun loadCases(config: EvaluationRunConfig, batch: EvaluationCaseBatch): List<PreparedEvaluationCase>? {
        val prepared = ArrayList<PreparedEvaluationCase>(batch.orderedCaseIds.size)
        for (caseId in batch.orderedCaseIds) {
            val case = try {
                caseSource.load(config, caseId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            } ?: return null
            if (case.id != caseId) return null
            prepared += PreparedEvaluationCase(caseId, case)
        }
        return prepared
    }

    private fun prepareResidentBatch(
        config: EvaluationRunConfig,
        batch: EvaluationCaseBatch,
        binding: EvaluationRuntimeBinding,
        cases: List<PreparedEvaluationCase>,
    ): RuntimeBatchPreparation {
        val firstCaseId = cases.first().caseId
        val sessions = createSessions(binding, cases.size)
            ?: return RuntimeBatchPreparation.Failure(runtimeFailure(firstCaseId))
        val requests = createRequests(config, binding, cases, sessions)
        val runtimeRequest = requests?.let { createRuntimeRequest(config, batch, it) }
        return if (requests == null || runtimeRequest == null) {
            closeSessions(sessions)
            RuntimeBatchPreparation.Failure(invalidConfiguration(firstCaseId))
        } else {
            RuntimeBatchPreparation.Ready(
                PreparedRuntimeEvaluationBatch(firstCaseId, cases, sessions, requests, runtimeRequest),
            )
        }
    }

    private fun createSessions(binding: EvaluationRuntimeBinding, count: Int): List<SessionId>? {
        val sessions = ArrayList<SessionId>(count)
        return try {
            repeat(count) {
                sessions += client.createSession(
                    binding.applicationId,
                    binding.useCaseId,
                    SessionOptions(kind = SessionKind.STATELESS),
                )
            }
            if (sessions.distinct().size == sessions.size) sessions else null.also { closeSessions(sessions) }
        } catch (error: CancellationException) {
            closeSessions(sessions)
            throw error
        } catch (_: Exception) {
            closeSessions(sessions)
            null
        }
    }

    private fun createRequests(
        config: EvaluationRunConfig,
        binding: EvaluationRuntimeBinding,
        cases: List<PreparedEvaluationCase>,
        sessions: List<SessionId>,
    ): List<GenerationRequest>? = try {
        cases.zip(sessions).map { (preparedCase, sessionId) ->
            requestFactory.create(config, preparedCase.case, binding, sessionId)
        }.takeIf { requests ->
            requests.zip(sessions).all { (request, sessionId) -> request.matches(binding, sessionId) } &&
                requests.map(GenerationRequest::requestId).distinct().size == requests.size
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private fun createRuntimeRequest(
        config: EvaluationRunConfig,
        batch: EvaluationCaseBatch,
        requests: List<GenerationRequest>,
    ): RuntimeEvaluationBatchRequest? = try {
        RuntimeEvaluationBatchRequest(
            RequestId("evaluation-batch:${config.runId.value}:${batch.ordinal}"),
            requests,
        )
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val MIN_BATCH_WIDTH = 2
        const val MAX_BATCH_WIDTH = 4
    }
}

internal class RuntimeEvaluationBatchAwaiter(private val batchClient: RuntimeEvaluationBatchClient) {
    suspend fun await(request: RuntimeEvaluationBatchRequest): RuntimeEvaluationBatchOutcome =
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
                    continuation.resume(
                        RuntimeEvaluationBatchOutcome.Failed(
                            LocalLlmError.NativeRuntime("evaluation batch start failed"),
                        ),
                    )
                }
            }
        }
}

internal class RuntimeEvaluationBatchResultMapper(
    private val scorer: DeterministicEvaluationCaseScorer,
    private val telemetry: EvaluationTelemetryCorrelationPort,
) {
    fun mapOutcome(
        prepared: PreparedRuntimeEvaluationBatch,
        outcome: RuntimeEvaluationBatchOutcome,
    ): EvaluationStepResult<List<EvaluationCaseResult>> = when (outcome) {
        is RuntimeEvaluationBatchOutcome.Failed -> runtimeFailure(prepared.firstCaseId)
        is RuntimeEvaluationBatchOutcome.Completed -> mapCompleted(prepared, outcome)
    }

    fun timeoutResults(prepared: PreparedRuntimeEvaluationBatch): List<EvaluationCaseResult> =
        prepared.cases.zip(prepared.requests).map { (preparedCase, request) ->
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

    private fun mapCompleted(
        prepared: PreparedRuntimeEvaluationBatch,
        outcome: RuntimeEvaluationBatchOutcome.Completed,
    ): EvaluationStepResult<List<EvaluationCaseResult>> {
        val expectedIds = prepared.requests.map(GenerationRequest::requestId)
        if (outcome.cases.map(RuntimeEvaluationBatchCaseResult::requestId) != expectedIds) {
            return invalidConfiguration(prepared.firstCaseId)
        }
        return EvaluationStepResult.Success(
            outcome.cases.zip(prepared.cases).map { (runtimeCase, preparedCase) ->
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

    private fun cancelledResult(prepared: PreparedEvaluationCase, requestId: RequestId): EvaluationCaseResult =
        EvaluationCaseResult(
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
}

private fun GenerationRequest.matches(binding: EvaluationRuntimeBinding, sessionId: SessionId): Boolean =
    this.sessionId == sessionId && applicationId == binding.applicationId && useCaseId == binding.useCaseId

private fun List<EvaluationCaseId>.containsOrderedSubsequence(candidate: List<EvaluationCaseId>): Boolean {
    if (candidate.isEmpty()) return false
    val start = indexOf(candidate.first())
    if (start < 0 || start + candidate.size > size) return false
    return subList(start, start + candidate.size) == candidate
}

internal fun invalidConfiguration(caseId: EvaluationCaseId?): EvaluationStepResult.Failure = EvaluationStepResult.Failure(
    EvaluationFailure(
        stage = EvaluationFailureStage.GENERATION,
        code = EvaluationFailureCode.INVALID_CONFIGURATION,
        caseId = caseId,
    ),
)

internal fun runtimeFailure(caseId: EvaluationCaseId?): EvaluationStepResult.Failure = EvaluationStepResult.Failure(
    EvaluationFailure(
        stage = EvaluationFailureStage.GENERATION,
        code = EvaluationFailureCode.RUNTIME_FAILURE,
        caseId = caseId,
        retryable = true,
    ),
)
