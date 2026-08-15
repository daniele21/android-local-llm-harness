package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.evaluation.EvaluationDatasetId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetVersion
import io.github.daniele21.localllm.evaluation.EvaluationExecutionProfileId
import io.github.daniele21.localllm.evaluation.EvaluationExecutionProfileRef
import io.github.daniele21.localllm.evaluation.EvaluationModelIdentity
import io.github.daniele21.localllm.evaluation.EvaluationRunId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceUiReducerTest {
    @Test
    fun `complete setup becomes ready and emits start command`() {
        val configured = listOf<PerformanceIntent>(
            PerformanceIntent.SelectModel(model()),
            PerformanceIntent.SelectDataset(dataset(caseCount = 200)),
            PerformanceIntent.SelectExecutionProfile(profile()),
        ).fold(PerformanceState()) { state, intent -> PerformanceUiReducer.reduce(state, intent).state }

        assertEquals(PerformanceRunReadiness.Ready, configured.runSetup.readiness)

        val start = PerformanceUiReducer.reduce(configured, PerformanceIntent.StartRun)

        assertEquals(1, start.commands.size)
        assertTrue(start.commands.single() is PerformanceCommand.StartRun)
        assertTrue(start.effects.isEmpty())
    }

    @Test
    fun `missing model routes developer to existing Models surface`() {
        val state = PerformanceState(
            runSetup = PerformanceRunSetupState(
                dataset = dataset(caseCount = 200),
                executionProfile = profile(),
            ),
        )

        val start = PerformanceUiReducer.reduce(state, PerformanceIntent.StartRun)

        assertEquals(listOf(PerformanceEffect.NavigateToModels), start.effects)
        assertTrue(start.commands.isEmpty())
    }

    @Test
    fun `preset larger than dataset is blocked instead of silently downsampled`() {
        val state = listOf<PerformanceIntent>(
            PerformanceIntent.SelectModel(model()),
            PerformanceIntent.SelectDataset(dataset(caseCount = 50)),
            PerformanceIntent.SelectExecutionProfile(profile()),
        ).fold(PerformanceState()) { current, intent -> PerformanceUiReducer.reduce(current, intent).state }

        val readiness = state.runSetup.readiness as PerformanceRunReadiness.Blocked

        assertEquals(listOf(PerformanceBlockReason.SAMPLE_SELECTION_UNAVAILABLE), readiness.reasons)
    }

    @Test
    fun `all remains available for dataset smaller than standard preset`() {
        val state = listOf<PerformanceIntent>(
            PerformanceIntent.SelectModel(model()),
            PerformanceIntent.SelectDataset(dataset(caseCount = 50)),
            PerformanceIntent.SelectExecutionProfile(profile()),
            PerformanceIntent.SelectSample(PerformanceSampleSelection.All),
        ).fold(PerformanceState()) { current, intent -> PerformanceUiReducer.reduce(current, intent).state }

        assertEquals(PerformanceRunReadiness.Ready, state.runSetup.readiness)
    }

    @Test
    fun `compare selection toggles and never silently replaces a third run`() {
        val runA = EvaluationRunId("run-a")
        val runB = EvaluationRunId("run-b")
        val runC = EvaluationRunId("run-c")
        val stateA = PerformanceUiReducer.reduce(
            PerformanceState(),
            PerformanceIntent.SelectCompareRun(runA),
        ).state
        val stateB = PerformanceUiReducer.reduce(
            stateA,
            PerformanceIntent.SelectCompareRun(runB),
        ).state

        val third = PerformanceUiReducer.reduce(stateB, PerformanceIntent.SelectCompareRun(runC))

        assertEquals(listOf(runA, runB), third.state.compare.selectedRunIds)
        assertTrue(third.effects.single() is PerformanceEffect.ShowMessage)
    }

    @Test
    fun `refresh fans out through independent dataset history and compare seams`() {
        val refresh = PerformanceUiReducer.reduce(PerformanceState(), PerformanceIntent.Refresh)

        assertEquals(
            listOf(
                PerformanceCommand.RefreshDatasets,
                PerformanceCommand.RefreshHistory,
                PerformanceCommand.RefreshCompare,
            ),
            refresh.commands,
        )
    }

    private fun model() = EvaluationModelIdentity(
        artifactDigest = ModelDigest("a".repeat(64)),
        modelProfileId = "qwen35-08b-q4-k-m",
        quantization = "Q4_K_M",
    )

    private fun profile() = EvaluationExecutionProfileRef(
        id = EvaluationExecutionProfileId("deterministic"),
        version = 1,
    )

    private fun dataset(caseCount: Int) = PerformanceDatasetSelection(
        id = EvaluationDatasetId("general-purpose-v1"),
        version = EvaluationDatasetVersion("1.0.0"),
        displayName = "General Purpose v1",
        caseCount = caseCount,
    )
}
