package io.github.daniele21.localllm.evaluation.engine

import io.github.daniele21.localllm.evaluation.EvaluationDatasetIdentity
import io.github.daniele21.localllm.evaluation.EvaluationExecutionProfileRef
import io.github.daniele21.localllm.evaluation.EvaluationFailure
import io.github.daniele21.localllm.evaluation.EvaluationFailureStage
import io.github.daniele21.localllm.evaluation.EvaluationRunConfig
import io.github.daniele21.localllm.evaluation.SamplingSelection

interface EvaluationDatasetPreflight {
    fun validate(dataset: EvaluationDatasetIdentity, sampling: SamplingSelection): EvaluationFailure?
}

interface EvaluationEvaluatorPreflight {
    fun validate(dataset: EvaluationDatasetIdentity): EvaluationFailure?
}

interface EvaluationExecutionProfilePreflight {
    fun validate(profile: EvaluationExecutionProfileRef, model: ResolvedEvaluationModel): EvaluationFailure?
}

class EvaluationRunPreflight(
    private val modelResolver: ControlledEvaluationModelResolver,
    private val datasetPreflight: EvaluationDatasetPreflight,
    private val evaluatorPreflight: EvaluationEvaluatorPreflight,
    private val executionProfilePreflight: EvaluationExecutionProfilePreflight,
) : EvaluationPreflightPort {
    override suspend fun validate(config: EvaluationRunConfig): EvaluationStepResult<Unit> {
        val resolvedModel = when (val resolution = modelResolver.resolve(config.model)) {
            is EvaluationModelResolution.Rejected -> return EvaluationStepResult.Failure(resolution.failure)
            is EvaluationModelResolution.Resolved -> resolution.model
        }

        return firstFailure(
            datasetPreflight.validate(config.dataset, config.sampling),
            evaluatorPreflight.validate(config.dataset),
            executionProfilePreflight.validate(config.executionProfile, resolvedModel),
        )?.let(EvaluationStepResult::Failure) ?: EvaluationStepResult.Success(Unit)
    }

    private fun firstFailure(vararg failures: EvaluationFailure?): EvaluationFailure? = failures.firstOrNull { failure ->
        failure != null
    }?.also { failure ->
        require(failure.stage == EvaluationFailureStage.PREFLIGHT) {
            "Evaluation preflight checks must return PREFLIGHT failures"
        }
    }
}
