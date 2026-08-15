package io.github.daniele21.localllm.evaluation.engine

import io.github.daniele21.localllm.contracts.GenerationEvent
import io.github.daniele21.localllm.contracts.GenerationRequest
import io.github.daniele21.localllm.contracts.LocalLlmClient
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.evaluation.EvaluationCaseId
import io.github.daniele21.localllm.evaluation.EvaluationCaseResult
import io.github.daniele21.localllm.evaluation.EvaluationDatasetCaseV1
import io.github.daniele21.localllm.evaluation.EvaluationFailure
import io.github.daniele21.localllm.evaluation.EvaluationFailureCode
import io.github.daniele21.localllm.evaluation.EvaluationFailureStage
import io.github.daniele21.localllm.evaluation.EvaluationRunConfig
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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

        return suspendCoroutine { continuation ->
            val terminal = AtomicBoolean(false)
            try {
                client.generate(request) { event ->
                    val result = when (event) {
                        is GenerationEvent.Completed -> EvaluationStepResult.Success(
                            scorer.score(
                                case = case,
                                requestId = event.requestId,
                                generated = event.answerOutput,
                            ),
                        )

                        is GenerationEvent.Failed -> generationFailure(caseId)

                        else -> null
                    }
                    if (result != null && terminal.compareAndSet(false, true)) {
                        continuation.resume(result)
                    }
                }
            } catch (_: Exception) {
                if (terminal.compareAndSet(false, true)) {
                    continuation.resume(generationFailure(caseId))
                }
            }
        }
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
