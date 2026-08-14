package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.evaluation.EvaluationDatasetId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetVersion
import io.github.daniele21.localllm.evaluation.EvaluationExecutionProfileRef
import io.github.daniele21.localllm.evaluation.EvaluationModelIdentity
import io.github.daniele21.localllm.evaluation.EvaluationProgress
import io.github.daniele21.localllm.evaluation.EvaluationRunId
import io.github.daniele21.localllm.evaluation.EvaluationRunState

internal object PerformanceRoutes {
    const val ROOT = "performance"
    const val RUN = "performance/run"
    const val DATASETS = "performance/datasets"
    const val HISTORY = "performance/history"
    const val COMPARE = "performance/compare"

    val topLevelSections = listOf(RUN, DATASETS, HISTORY, COMPARE)
}

internal enum class PerformanceSection(val route: String) {
    RUN(PerformanceRoutes.RUN),
    DATASETS(PerformanceRoutes.DATASETS),
    HISTORY(PerformanceRoutes.HISTORY),
    COMPARE(PerformanceRoutes.COMPARE),
    ;

    companion object {
        fun fromRoute(route: String?): PerformanceSection = entries.firstOrNull { it.route == route } ?: RUN
    }
}

internal sealed interface PerformanceSampleSelection {
    data object Smoke : PerformanceSampleSelection

    data object Quick : PerformanceSampleSelection

    data object Standard : PerformanceSampleSelection

    data object Extended : PerformanceSampleSelection

    data object All : PerformanceSampleSelection

    data class Custom(val count: Int) : PerformanceSampleSelection {
        init {
            require(count > 0 && count % 10 == 0) {
                "Custom Performance sample count must be a positive multiple of 10"
            }
        }
    }
}

internal data class PerformanceDatasetSelection(
    val id: EvaluationDatasetId,
    val version: EvaluationDatasetVersion,
    val displayName: String,
    val caseCount: Int,
) {
    init {
        require(displayName.isNotBlank()) { "Performance dataset display name must not be blank" }
        require(caseCount > 0) { "Performance dataset case count must be positive" }
    }
}

internal data class PerformanceRunSetupState(
    val model: EvaluationModelIdentity? = null,
    val dataset: PerformanceDatasetSelection? = null,
    val sampleSelection: PerformanceSampleSelection = PerformanceSampleSelection.Standard,
    val executionProfile: EvaluationExecutionProfileRef? = null,
    val readiness: PerformanceRunReadiness = PerformanceRunReadiness.Incomplete,
)

internal sealed interface PerformanceRunReadiness {
    data object Incomplete : PerformanceRunReadiness

    data object Ready : PerformanceRunReadiness

    data class Blocked(val reasons: List<PerformanceBlockReason>) : PerformanceRunReadiness {
        init {
            require(reasons.isNotEmpty()) { "Blocked Performance readiness requires at least one reason" }
            require(reasons.distinct().size == reasons.size) { "Blocked Performance reasons must be unique" }
        }
    }
}

internal enum class PerformanceBlockReason {
    MODEL_REQUIRED,
    DATASET_REQUIRED,
    SAMPLE_SELECTION_UNAVAILABLE,
    EXECUTION_PROFILE_REQUIRED,
    MODEL_UNAVAILABLE,
}

internal data class PerformanceActiveRunState(
    val runId: EvaluationRunId,
    val state: EvaluationRunState,
    val progress: EvaluationProgress,
    val elapsedMs: Long,
) {
    init {
        require(elapsedMs >= 0) { "Performance run elapsed duration must not be negative" }
    }
}

internal data class PerformanceDatasetState(
    val loading: Boolean = false,
    val installedCount: Int = 0,
    val error: String? = null,
) {
    init {
        require(installedCount >= 0) { "Installed dataset count must not be negative" }
    }
}

internal data class PerformanceHistoryState(
    val loading: Boolean = false,
    val runCount: Int = 0,
    val error: String? = null,
) {
    init {
        require(runCount >= 0) { "Performance history run count must not be negative" }
    }
}

internal data class PerformanceCompareState(
    val selectedRunIds: List<EvaluationRunId> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
) {
    init {
        require(selectedRunIds.distinct().size == selectedRunIds.size) {
            "Performance comparison run IDs must be unique"
        }
    }
}

internal data class PerformanceState(
    val selectedSection: PerformanceSection = PerformanceSection.RUN,
    val runSetup: PerformanceRunSetupState = PerformanceRunSetupState(),
    val activeRun: PerformanceActiveRunState? = null,
    val datasets: PerformanceDatasetState = PerformanceDatasetState(),
    val history: PerformanceHistoryState = PerformanceHistoryState(),
    val compare: PerformanceCompareState = PerformanceCompareState(),
)

internal sealed interface PerformanceIntent {
    data class SelectSection(val section: PerformanceSection) : PerformanceIntent

    data class SelectModel(val model: EvaluationModelIdentity) : PerformanceIntent

    data class SelectDataset(val dataset: PerformanceDatasetSelection) : PerformanceIntent

    data class SelectSample(val selection: PerformanceSampleSelection) : PerformanceIntent

    data class SelectExecutionProfile(val profile: EvaluationExecutionProfileRef) : PerformanceIntent

    data object StartRun : PerformanceIntent

    data object CancelRun : PerformanceIntent

    data object ImportDataset : PerformanceIntent

    data class DeleteDataset(val id: EvaluationDatasetId, val version: EvaluationDatasetVersion) : PerformanceIntent

    data class OpenRun(val runId: EvaluationRunId) : PerformanceIntent

    data class SelectCompareRun(val runId: EvaluationRunId) : PerformanceIntent

    data object Refresh : PerformanceIntent
}

internal sealed interface PerformanceEffect {
    data object OpenDocumentPicker : PerformanceEffect

    data object NavigateToModels : PerformanceEffect

    data class ShowMessage(val message: String) : PerformanceEffect {
        init {
            require(message.isNotBlank()) { "Performance message must not be blank" }
        }
    }
}
