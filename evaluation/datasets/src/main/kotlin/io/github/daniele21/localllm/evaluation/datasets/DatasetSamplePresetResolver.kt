package io.github.daniele21.localllm.evaluation.datasets

import io.github.daniele21.localllm.evaluation.MAX_SAMPLE_CASES
import io.github.daniele21.localllm.evaluation.SamplingSelection

enum class EvaluationSamplePreset(val caseCount: Int) {
    SMOKE(20),
    QUICK(50),
    STANDARD(100),
    EXTENDED(200),
}

sealed interface EvaluationSampleRequest {
    data class Preset(val preset: EvaluationSamplePreset) : EvaluationSampleRequest

    data object All : EvaluationSampleRequest

    data class Custom(val count: Int) : EvaluationSampleRequest {
        init {
            require(count > 0) { "Custom evaluation sample count must be positive" }
            require(count <= MAX_SAMPLE_CASES) { "Custom evaluation sample count exceeds $MAX_SAMPLE_CASES cases" }
            require(count % CUSTOM_COUNT_STEP == 0) {
                "Custom evaluation sample count must be a multiple of $CUSTOM_COUNT_STEP"
            }
        }
    }
}

enum class DatasetSampleUnavailableReason {
    REQUESTED_COUNT_EXCEEDS_DATASET,
}

sealed interface DatasetSampleResolution {
    data class Resolved(val selection: SamplingSelection) : DatasetSampleResolution

    data class Unavailable(val reason: DatasetSampleUnavailableReason, val requestedCount: Int, val availableCount: Int) :
        DatasetSampleResolution {
        init {
            require(requestedCount > availableCount) {
                "Unavailable sample resolution requires requested count to exceed available count"
            }
        }
    }
}

object EvaluationSamplePresetResolver {
    fun resolve(ranking: StratifiedSamplingRanking, request: EvaluationSampleRequest): DatasetSampleResolution {
        val requestedCount = when (request) {
            is EvaluationSampleRequest.Preset -> request.preset.caseCount
            EvaluationSampleRequest.All -> ranking.orderedCaseIds.size
            is EvaluationSampleRequest.Custom -> request.count
        }
        return if (requestedCount > ranking.orderedCaseIds.size) {
            DatasetSampleResolution.Unavailable(
                reason = DatasetSampleUnavailableReason.REQUESTED_COUNT_EXCEEDS_DATASET,
                requestedCount = requestedCount,
                availableCount = ranking.orderedCaseIds.size,
            )
        } else {
            DatasetSampleResolution.Resolved(ranking.selection(requestedCount))
        }
    }
}

private const val CUSTOM_COUNT_STEP = 10
