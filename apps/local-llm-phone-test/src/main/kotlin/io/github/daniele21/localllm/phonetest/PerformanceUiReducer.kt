package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.evaluation.EvaluationDatasetId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetVersion
import io.github.daniele21.localllm.evaluation.EvaluationRunId

internal data class PerformanceReduction(
    val state: PerformanceState,
    val effects: List<PerformanceEffect> = emptyList(),
    val commands: List<PerformanceCommand> = emptyList(),
)

internal sealed interface PerformanceCommand {
    data class StartRun(val setup: PerformanceRunSetupState) : PerformanceCommand

    data class CancelRun(val runId: EvaluationRunId) : PerformanceCommand

    data class DeleteDataset(val id: EvaluationDatasetId, val version: EvaluationDatasetVersion) : PerformanceCommand

    data class OpenRun(val runId: EvaluationRunId) : PerformanceCommand

    data object RefreshDatasets : PerformanceCommand

    data object RefreshHistory : PerformanceCommand

    data object RefreshCompare : PerformanceCommand
}

internal object PerformanceUiReducer {
    fun reduce(current: PerformanceState, intent: PerformanceIntent): PerformanceReduction = when (intent) {
        is PerformanceIntent.SelectSection -> reduction(current.copy(selectedSection = intent.section))

        is PerformanceIntent.SelectModel -> updateSetup(current) { copy(model = intent.model) }

        is PerformanceIntent.SelectDataset -> updateSetup(current) { copy(dataset = intent.dataset) }

        is PerformanceIntent.SelectSample -> updateSetup(current) { copy(sampleSelection = intent.selection) }

        is PerformanceIntent.SelectExecutionProfile -> updateSetup(current) { copy(executionProfile = intent.profile) }

        PerformanceIntent.StartRun -> startRun(current)

        PerformanceIntent.CancelRun -> cancelRun(current)

        PerformanceIntent.ImportDataset -> reduction(current, effects = listOf(PerformanceEffect.OpenDocumentPicker))

        is PerformanceIntent.DeleteDataset -> reduction(
            current,
            commands = listOf(PerformanceCommand.DeleteDataset(intent.id, intent.version)),
        )

        is PerformanceIntent.OpenRun -> reduction(
            current,
            commands = listOf(PerformanceCommand.OpenRun(intent.runId)),
        )

        is PerformanceIntent.SelectCompareRun -> selectCompareRun(current, intent.runId)

        PerformanceIntent.Refresh -> reduction(
            current,
            commands = listOf(
                PerformanceCommand.RefreshDatasets,
                PerformanceCommand.RefreshHistory,
                PerformanceCommand.RefreshCompare,
            ),
        )
    }

    fun applyDatasetState(current: PerformanceState, datasets: PerformanceDatasetState): PerformanceState =
        current.copy(datasets = datasets)

    fun applyHistoryState(current: PerformanceState, history: PerformanceHistoryState): PerformanceState = current.copy(history = history)

    fun applyActiveRun(current: PerformanceState, activeRun: PerformanceActiveRunState?): PerformanceState =
        current.copy(activeRun = activeRun)

    private fun updateSetup(
        current: PerformanceState,
        transform: PerformanceRunSetupState.() -> PerformanceRunSetupState,
    ): PerformanceReduction {
        val updated = current.runSetup.transform()
        return reduction(current.copy(runSetup = updated.copy(readiness = performanceReadiness(updated))))
    }

    private fun startRun(current: PerformanceState): PerformanceReduction {
        val setup = current.runSetup.copy(readiness = performanceReadiness(current.runSetup))
        val state = current.copy(runSetup = setup)
        return when (val readiness = setup.readiness) {
            PerformanceRunReadiness.Ready -> reduction(
                state,
                commands = listOf(PerformanceCommand.StartRun(setup)),
            )

            PerformanceRunReadiness.Incomplete -> blockedStart(state, emptyList())

            is PerformanceRunReadiness.Blocked -> blockedStart(state, readiness.reasons)
        }
    }

    private fun blockedStart(current: PerformanceState, reasons: List<PerformanceBlockReason>): PerformanceReduction {
        val modelMissing = current.runSetup.model == null || PerformanceBlockReason.MODEL_REQUIRED in reasons
        return if (modelMissing) {
            reduction(current, effects = listOf(PerformanceEffect.NavigateToModels))
        } else {
            reduction(
                current,
                effects = listOf(PerformanceEffect.ShowMessage("Evaluation setup is not ready")),
            )
        }
    }

    private fun cancelRun(current: PerformanceState): PerformanceReduction {
        val active = current.activeRun ?: return reduction(
            current,
            effects = listOf(PerformanceEffect.ShowMessage("No evaluation run is active")),
        )
        return reduction(current, commands = listOf(PerformanceCommand.CancelRun(active.runId)))
    }

    private fun selectCompareRun(current: PerformanceState, runId: EvaluationRunId): PerformanceReduction {
        val selected = current.compare.selectedRunIds
        val updated = when {
            runId in selected -> selected - runId

            selected.size < MAX_COMPARE_RUNS -> selected + runId

            else -> return reduction(
                current,
                effects = listOf(PerformanceEffect.ShowMessage("Compare supports two selected runs in v1")),
            )
        }
        return reduction(current.copy(compare = current.compare.copy(selectedRunIds = updated)))
    }

    private fun reduction(
        state: PerformanceState,
        effects: List<PerformanceEffect> = emptyList(),
        commands: List<PerformanceCommand> = emptyList(),
    ) = PerformanceReduction(state = state, effects = effects, commands = commands)

    private const val MAX_COMPARE_RUNS = 2
}

private fun performanceReadiness(setup: PerformanceRunSetupState): PerformanceRunReadiness {
    val reasons = buildList {
        if (setup.model == null) add(PerformanceBlockReason.MODEL_REQUIRED)
        if (setup.dataset == null) add(PerformanceBlockReason.DATASET_REQUIRED)
        if (setup.executionProfile == null) add(PerformanceBlockReason.EXECUTION_PROFILE_REQUIRED)
        if (!performanceSampleAvailable(setup.sampleSelection, setup.dataset?.caseCount)) {
            add(PerformanceBlockReason.SAMPLE_SELECTION_UNAVAILABLE)
        }
    }
    return if (reasons.isEmpty()) PerformanceRunReadiness.Ready else PerformanceRunReadiness.Blocked(reasons)
}

private fun performanceSampleAvailable(selection: PerformanceSampleSelection, caseCount: Int?): Boolean {
    if (caseCount == null) return true
    val requested = when (selection) {
        PerformanceSampleSelection.Smoke -> 20
        PerformanceSampleSelection.Quick -> 50
        PerformanceSampleSelection.Standard -> 100
        PerformanceSampleSelection.Extended -> 200
        PerformanceSampleSelection.All -> return true
        is PerformanceSampleSelection.Custom -> selection.count
    }
    return requested <= caseCount
}
