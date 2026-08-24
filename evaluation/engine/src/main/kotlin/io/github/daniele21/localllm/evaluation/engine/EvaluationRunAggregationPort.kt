package io.github.daniele21.localllm.evaluation.engine

import io.github.daniele21.localllm.evaluation.EvaluationCaseResult
import io.github.daniele21.localllm.evaluation.EvaluationDatasetCategoryDefinition
import io.github.daniele21.localllm.evaluation.EvaluationDatasetIdentity
import io.github.daniele21.localllm.evaluation.EvaluationFailure
import io.github.daniele21.localllm.evaluation.EvaluationFailureCode
import io.github.daniele21.localllm.evaluation.EvaluationFailureStage
import io.github.daniele21.localllm.evaluation.EvaluationRunConfig

fun interface EvaluationDatasetCategorySource {
    fun categories(dataset: EvaluationDatasetIdentity): List<EvaluationDatasetCategoryDefinition>?
}

fun interface EvaluationRunAggregationPort {
    fun aggregate(config: EvaluationRunConfig, caseResults: List<EvaluationCaseResult>): EvaluationStepResult<EvaluationRunAggregation>
}

class CanonicalEvaluationRunAggregationPort(
    private val categorySource: EvaluationDatasetCategorySource,
    private val aggregator: EvaluationRunAggregator = EvaluationRunAggregator(),
) : EvaluationRunAggregationPort {
    override fun aggregate(
        config: EvaluationRunConfig,
        caseResults: List<EvaluationCaseResult>,
    ): EvaluationStepResult<EvaluationRunAggregation> {
        val categories = try {
            categorySource.categories(config.dataset)
        } catch (_: Exception) {
            null
        } ?: return failure(EvaluationFailureCode.DATASET_NOT_FOUND)

        return try {
            EvaluationStepResult.Success(
                aggregator.aggregate(
                    selectedCaseIds = config.sampling.orderedCaseIds,
                    categories = categories,
                    caseResults = caseResults,
                ),
            )
        } catch (_: IllegalArgumentException) {
            failure(EvaluationFailureCode.INVALID_CONFIGURATION)
        } catch (_: IllegalStateException) {
            failure(EvaluationFailureCode.INVALID_CONFIGURATION)
        }
    }

    private fun failure(code: EvaluationFailureCode): EvaluationStepResult.Failure = EvaluationStepResult.Failure(
        EvaluationFailure(
            stage = EvaluationFailureStage.EVALUATION,
            code = code,
        ),
    )
}
