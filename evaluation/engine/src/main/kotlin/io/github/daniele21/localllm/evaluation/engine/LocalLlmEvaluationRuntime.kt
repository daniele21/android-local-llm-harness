package io.github.daniele21.localllm.evaluation.engine

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.GenerationEvent
import io.github.daniele21.localllm.contracts.GenerationRequest
import io.github.daniele21.localllm.contracts.LocalLlmClient
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.SessionOptions
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.evaluation.EvaluationCaseId
import io.github.daniele21.localllm.evaluation.EvaluationCaseResult
import io.github.daniele21.localllm.evaluation.EvaluationExecutionProfileRef
import io.github.daniele21.localllm.evaluation.EvaluationFailure
import io.github.daniele21.localllm.evaluation.EvaluationFailureCode
import io.github.daniele21.localllm.evaluation.EvaluationFailureStage
import io.github.daniele21.localllm.evaluation.EvaluationModelIdentity
import io.github.daniele21.localllm.evaluation.EvaluationModelLoadPolicy
import io.github.daniele21.localllm.evaluation.EvaluationRunConfig
import io.github.daniele21.localllm.evaluation.EvaluationWarmupPolicy
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class EvaluationRuntimeBinding(
    val applicationId: ApplicationId,
    val useCaseId: UseCaseId,
    val model: EvaluationModelIdentity,
    val executionProfile: EvaluationExecutionProfileRef,
)

fun interface EvaluationRuntimeBindingSource {
    fun resolve(model: EvaluationModelIdentity, executionProfile: EvaluationExecutionProfileRef): EvaluationRuntimeBinding?
}

class FixedEvaluationRuntimeBindingSource(bindings: Collection<EvaluationRuntimeBinding>) : EvaluationRuntimeBindingSource {
    private val bindingsByIdentity: Map<BindingKey, EvaluationRuntimeBinding>

    init {
        val keys = bindings.map { BindingKey(it.model, it.executionProfile) }
        require(keys.distinct().size == keys.size) {
            "Evaluation runtime model and execution-profile bindings must be unique"
        }
        bindingsByIdentity = bindings.associateBy { BindingKey(it.model, it.executionProfile) }
    }

    override fun resolve(model: EvaluationModelIdentity, executionProfile: EvaluationExecutionProfileRef): EvaluationRuntimeBinding? =
        bindingsByIdentity[BindingKey(model, executionProfile)]

    private data class BindingKey(val model: EvaluationModelIdentity, val executionProfile: EvaluationExecutionProfileRef)
}

fun interface EvaluationModelResidencyControl {
    fun unloadIdleModel(): Boolean
}

fun interface EvaluationWarmupExecutionPort {
    suspend fun execute(config: EvaluationRunConfig, binding: EvaluationRuntimeBinding, sessionId: SessionId): EvaluationStepResult<Unit>
}

fun interface EvaluationScoredCaseExecutionPort {
    suspend fun execute(
        config: EvaluationRunConfig,
        caseId: EvaluationCaseId,
        binding: EvaluationRuntimeBinding,
        sessionId: SessionId,
    ): EvaluationStepResult<EvaluationCaseResult>
}

/**
 * Drives the normal public runtime path while keeping evaluation binding and session ownership explicit.
 * Semantic case execution remains delegated to [EvaluationScoredCaseExecutionPort] for EVAL-R-06.
 */
class LocalLlmEvaluationRuntime(
    private val client: LocalLlmClient,
    private val bindingSource: EvaluationRuntimeBindingSource,
    private val warmupExecution: EvaluationWarmupExecutionPort,
    private val scoredCaseExecution: EvaluationScoredCaseExecutionPort,
    private val residencyControl: EvaluationModelResidencyControl? = null,
) : EvaluationModelPreparationPort,
    EvaluationCaseExecutionPort {
    override suspend fun prepare(config: EvaluationRunConfig): EvaluationStepResult<Unit> {
        val binding = binding(config)
            ?: return runtimeFailure(EvaluationFailureStage.MODEL_PREPARATION)

        return try {
            coldLoadIfRequired(config)?.let { return it }
            val prepared = client.prepare(binding.applicationId, binding.useCaseId)
            if (prepared.ready && prepared.modelDigest == config.model.artifactDigest) {
                EvaluationStepResult.Success(Unit)
            } else {
                runtimeFailure(EvaluationFailureStage.MODEL_PREPARATION)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            runtimeFailure(EvaluationFailureStage.MODEL_PREPARATION)
        }
    }

    override suspend fun warmup(config: EvaluationRunConfig): EvaluationStepResult<Unit> {
        if (config.warmupPolicy != EvaluationWarmupPolicy.ONE_UNSCORED_GENERATION) {
            return invalidConfiguration(EvaluationFailureStage.MODEL_PREPARATION)
        }
        val binding = preparedBinding(config)
            ?: return runtimeFailure(EvaluationFailureStage.MODEL_PREPARATION)
        return withIsolatedSession(
            binding = binding,
            failureStage = EvaluationFailureStage.MODEL_PREPARATION,
            caseId = null,
        ) { sessionId -> warmupExecution.execute(config, binding, sessionId) }
    }

    override suspend fun execute(config: EvaluationRunConfig, caseId: EvaluationCaseId): EvaluationStepResult<EvaluationCaseResult> {
        val binding = preparedBinding(config)
            ?: return runtimeFailure(EvaluationFailureStage.GENERATION, caseId)
        return withIsolatedSession(
            binding = binding,
            failureStage = EvaluationFailureStage.GENERATION,
            caseId = caseId,
        ) { sessionId -> scoredCaseExecution.execute(config, caseId, binding, sessionId) }
    }

    @Suppress("ReturnCount")
    private fun coldLoadIfRequired(config: EvaluationRunConfig): EvaluationStepResult.Failure? {
        if (config.loadPolicy != EvaluationModelLoadPolicy.REQUIRE_COLD_LOAD) return null
        val before = client.runtimeSnapshot()
        if (before.activeSessions != 0 || before.queuedRequests != 0) {
            return runtimeFailure(EvaluationFailureStage.MODEL_PREPARATION, retryable = true)
        }
        if (before.loadedModel == null) return null
        val unloaded = residencyControl?.unloadIdleModel() == true
        if (!unloaded || client.runtimeSnapshot().loadedModel != null) {
            return runtimeFailure(EvaluationFailureStage.MODEL_PREPARATION, retryable = true)
        }
        return null
    }

    private fun preparedBinding(config: EvaluationRunConfig): EvaluationRuntimeBinding? {
        val binding = binding(config) ?: return null
        return try {
            binding.takeIf { client.runtimeSnapshot().loadedModel == config.model.artifactDigest }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    private fun binding(config: EvaluationRunConfig): EvaluationRuntimeBinding? {
        val binding = try {
            bindingSource.resolve(config.model, config.executionProfile)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return null
        }
        return binding?.takeIf {
            it.model == config.model &&
                it.executionProfile == config.executionProfile
        }
    }

    private suspend fun <T> withIsolatedSession(
        binding: EvaluationRuntimeBinding,
        failureStage: EvaluationFailureStage,
        caseId: EvaluationCaseId?,
        execute: suspend (SessionId) -> EvaluationStepResult<T>,
    ): EvaluationStepResult<T> {
        val sessionId = try {
            client.createSession(
                binding.applicationId,
                binding.useCaseId,
                SessionOptions(kind = SessionKind.STATELESS),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return runtimeFailure(failureStage, caseId)
        }

        var cancellation: CancellationException? = null
        val outcome = try {
            execute(sessionId)
        } catch (error: CancellationException) {
            cancellation = error
            null
        } catch (_: Exception) {
            runtimeFailure(failureStage, caseId)
        }
        val closeFailure = try {
            client.closeSession(sessionId)
            null
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            runtimeFailure(failureStage, caseId)
        }
        cancellation?.let { throw it }
        val completedOutcome = checkNotNull(outcome)
        return if (completedOutcome is EvaluationStepResult.Failure) completedOutcome else closeFailure ?: completedOutcome
    }
}

fun interface EvaluationWarmupRequestFactory {
    fun create(config: EvaluationRunConfig, binding: EvaluationRuntimeBinding, sessionId: SessionId): GenerationRequest
}

/** Executes exactly one normal generation and deliberately discards its output and metrics. */
class LocalLlmUnscoredWarmupExecution(private val client: LocalLlmClient, private val requestFactory: EvaluationWarmupRequestFactory) :
    EvaluationWarmupExecutionPort {
    override suspend fun execute(
        config: EvaluationRunConfig,
        binding: EvaluationRuntimeBinding,
        sessionId: SessionId,
    ): EvaluationStepResult<Unit> {
        val request = try {
            requestFactory.create(config, binding, sessionId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return runtimeFailure(EvaluationFailureStage.MODEL_PREPARATION)
        }
        if (
            request.sessionId != sessionId ||
            request.applicationId != binding.applicationId ||
            request.useCaseId != binding.useCaseId
        ) {
            return invalidConfiguration(EvaluationFailureStage.MODEL_PREPARATION)
        }

        return suspendCoroutine<EvaluationStepResult<Unit>> { continuation ->
            val terminal = AtomicBoolean(false)
            try {
                client.generate(request) { event ->
                    val result: EvaluationStepResult<Unit>? = when (event) {
                        is GenerationEvent.Completed -> EvaluationStepResult.Success(Unit)
                        is GenerationEvent.Failed -> runtimeFailure(EvaluationFailureStage.MODEL_PREPARATION)
                        else -> null
                    }
                    if (result != null && terminal.compareAndSet(false, true)) {
                        continuation.resume(result)
                    }
                }
            } catch (_: Exception) {
                if (terminal.compareAndSet(false, true)) {
                    continuation.resume(runtimeFailure(EvaluationFailureStage.MODEL_PREPARATION))
                }
            }
        }
    }
}

private fun invalidConfiguration(stage: EvaluationFailureStage): EvaluationStepResult.Failure = EvaluationStepResult.Failure(
    EvaluationFailure(stage = stage, code = EvaluationFailureCode.INVALID_CONFIGURATION),
)

private fun runtimeFailure(
    stage: EvaluationFailureStage,
    caseId: EvaluationCaseId? = null,
    retryable: Boolean = false,
): EvaluationStepResult.Failure = EvaluationStepResult.Failure(
    EvaluationFailure(
        stage = stage,
        code = EvaluationFailureCode.RUNTIME_FAILURE,
        caseId = caseId,
        retryable = retryable,
    ),
)
