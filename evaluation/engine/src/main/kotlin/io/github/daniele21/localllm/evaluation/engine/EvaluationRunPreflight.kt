package io.github.daniele21.localllm.evaluation.engine

import io.github.daniele21.localllm.evaluation.EvaluationDatasetIdentity
import io.github.daniele21.localllm.evaluation.EvaluationExecutionProfileRef
import io.github.daniele21.localllm.evaluation.EvaluationFailure
import io.github.daniele21.localllm.evaluation.EvaluationFailureStage
import io.github.daniele21.localllm.evaluation.EvaluationRunConfig
import io.github.daniele21.localllm.evaluation.SamplingSelection

fun interface EvaluationDatasetPreflight {
    fun validate(
        dataset: EvaluationDatasetIdentity,
        sampling: SamplingSelection,
    ): EvaluationFailure?
}

fun interface EvaluationEvaluatorPreflight {
    fun validate(dataset: EvaluationDatasetIdentity): EvaluationFailure?
}

fun interface EvaluationExecutionProfilePreflight {
    fun validate(
        profile: EvaluationExecutionProfileRef,
        model: ResolvedEvaluationModel,
    ): EvaluationFailure?
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

        datasetPreflight.validate(config.dataset, config.sampling)?.let { return failure(it) }
        evaluatorPreflight.validate(config.dataset)?.let { return failure(it) }
        executionProfilePreflight.validate(config.executionProfile, resolvedModel)?.let { return failure(it) }
        return EvaluationStepResult.Success(Unit)
    }

    private fun failure(failure: EvaluationFailure): EvaluationStepResult.Failure {
        require(failure.stage == EvaluationFailureStage.PREFLIGHT) {
            "Evaluation preflight checks must return PREFLIGHT failures"
        }
        return EvaluationStepResult.Failure(failure)
    }
}
