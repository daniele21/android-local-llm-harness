package io.github.daniele21.localllm.evaluation.engine

import io.github.daniele21.localllm.contracts.GenerationEvent
import io.github.daniele21.localllm.contracts.GenerationHandle
import io.github.daniele21.localllm.contracts.GenerationRequest
import io.github.daniele21.localllm.contracts.LocalLlmClient
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.evaluation.EvaluationCaseId
import io.github.daniele21.localllm.evaluation.EvaluationCaseMetrics
import io.github.daniele21.localllm.evaluation.EvaluationCaseResult
import io.github.daniele21.localllm.evaluation.EvaluationCaseStatus
import io.github.daniele21.localllm.evaluation.EvaluationDatasetCaseV1
import io.github.daniele21.localllm.evaluation.EvaluationFailure
import io.github.daniele21.localllm.evaluation.EvaluationFailureCode
import io.github.daniele21.localllm.evaluation.EvaluationFailureStage
import io.github.daniele21.localllm.evaluation.EvaluationRunConfig
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

fun interface EvaluationCaseDefinitionSource {
    fun load(config: EvaluationRunConfig, caseId: EvaluationCaseId): EvaluationDatasetCaseV1?
}

fun interface EvaluationCaseGenerationRequestFactory {
    fun create(
        config: EvaluationRunConfig,
        case: EvaluationDatasetCaseV1,
        binding: EvaluationRuntimeBinding,
        sessionId: SessionId,
    ): GenerationRequest
}

class LocalLlmScoredCaseExecution(
    private val client: LocalLlmClient,
    private val caseSource: EvaluationCaseDefinitionSource,
    private val requestFactory: EvaluationCaseGenerationRequestFactory,
    private val scorer: DeterministicEvaluationCaseScorer = DeterministicEvaluationCaseScorer(),
    private val telemetry: EvaluationTelemetryCorrelationPort = EvaluationTelemetryCorrelationPort { EvaluationCaseMetrics() },
) : EvaluationScoredCaseExecutionPort {
    override suspend fun execute(
        config: EvaluationRunConfig,
        caseId: EvaluationCaseId,
        binding: EvaluationRuntimeBinding,
        sessionId: SessionId,
    ): EvaluationStepResult<EvaluationCaseResult> {
        val case = loadCase(config, caseId) ?: return generationFailure(caseId)
        val request = createRequest(config, case, binding, sessionId) ?: return invalidConfiguration(caseId)
        if (!request.matches(binding, sessionId)) return invalidConfiguration(caseId)

        return try {
            withTimeout(config.caseTimeoutMs) {
                awaitTerminal(case, caseId, request)
            }
        } catch (_: TimeoutCancellationException) {
            timeoutResult(case, request.requestId)
        }
    }

    private suspend fun awaitTerminal(
        case: EvaluationDatasetCaseV1,
        caseId: EvaluationCaseId,
        request: GenerationRequest,
    ): EvaluationStepResult<EvaluationCaseResult> = suspendCancellableCoroutine { continuation ->
        val terminal = AtomicBoolean(false)
        var generationHandle: GenerationHandle? = null
        continuation.invokeOnCancellation {
            if (terminal.compareAndSet(false, true)) {
                cancelQuietly(generationHandle)
            }
        }
        try {
            val startedHandle = client.generate(request) { event ->
                val result = terminalResult(case, caseId, event)
                if (result != null && terminal.compareAndSet(false, true) && continuation.isActive) {
                    continuation.resume(result)
                }
            }
            generationHandle = startedHandle
            if (continuation.isCancelled) {
                cancelQuietly(startedHandle)
            }
        } catch (error: CancellationException) {
            continuation.cancel(error)
        } catch (_: Exception) {
            if (terminal.compareAndSet(false, true) && continuation.isActive) {
                continuation.resume(generationFailure(caseId))
            }
        }
    }

    private fun terminalResult(
        case: EvaluationDatasetCaseV1,
        caseId: EvaluationCaseId,
        event: GenerationEvent,
    ): EvaluationStepResult<EvaluationCaseResult>? = when (event) {
        is GenerationEvent.Completed -> EvaluationStepResult.Success(
            scorer.score(
                case = case,
                requestId = event.requestId,
                generated = event.answerOutput,
                metrics = correlatedMetrics(event.requestId),
            ),
        )

        is GenerationEvent.Failed -> generationFailure(caseId)

        else -> null
    }

    private fun timeoutResult(case: EvaluationDatasetCaseV1, requestId: RequestId): EvaluationStepResult.Success<EvaluationCaseResult> =
        EvaluationStepResult.Success(
            EvaluationCaseResult(
                caseId = case.id,
                categoryId = case.categoryId,
                evaluator = case.evaluator,
                status = EvaluationCaseStatus.TIMEOUT,
                outcome = null,
                requestId = requestId,
                metrics = correlatedMetrics(requestId),
                failure = EvaluationFailure(
                    stage = EvaluationFailureStage.GENERATION,
                    code = EvaluationFailureCode.CASE_TIMEOUT,
                    caseId = case.id,
                    retryable = true,
                ),
            ),
        )

    private fun correlatedMetrics(requestId: RequestId): EvaluationCaseMetrics = try {
        telemetry.metrics(requestId)
    } catch (_: Exception) {
        EvaluationCaseMetrics()
    }

    private fun loadCase(config: EvaluationRunConfig, caseId: EvaluationCaseId): EvaluationDatasetCaseV1? = try {
        caseSource.load(config, caseId)?.takeIf { it.id == caseId }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private fun createRequest(
        config: EvaluationRunConfig,
        case: EvaluationDatasetCaseV1,
        binding: EvaluationRuntimeBinding,
        sessionId: SessionId,
    ): GenerationRequest? = try {
        requestFactory.create(config, case, binding, sessionId)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private fun GenerationRequest.matches(binding: EvaluationRuntimeBinding, sessionId: SessionId): Boolean = this.sessionId == sessionId &&
        applicationId == binding.applicationId &&
        useCaseId == binding.useCaseId

    private fun cancelQuietly(handle: GenerationHandle?) {
        runCatching { handle?.cancel() }
    }
}

private fun generationFailure(caseId: EvaluationCaseId): EvaluationStepResult.Failure = EvaluationStepResult.Failure(
    EvaluationFailure(
        stage = EvaluationFailureStage.GENERATION,
        code = EvaluationFailureCode.RUNTIME_FAILURE,
        caseId = caseId,
    ),
)

private fun invalidConfiguration(caseId: EvaluationCaseId): EvaluationStepResult.Failure = EvaluationStepResult.Failure(
    EvaluationFailure(
        stage = EvaluationFailureStage.GENERATION,
        code = EvaluationFailureCode.INVALID_CONFIGURATION,
        caseId = caseId,
    ),
)
