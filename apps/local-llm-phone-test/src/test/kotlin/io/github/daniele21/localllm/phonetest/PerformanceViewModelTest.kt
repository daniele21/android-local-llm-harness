package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.evaluation.EvaluationDatasetId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceViewModelTest {
    @Test
    fun `dispatch updates state and forwards one-shot effects`() {
        val effects = mutableListOf<PerformanceEffect>()
        val viewModel = PerformanceViewModel()
        val sink = PerformanceEffectSink(effects::add)
        viewModel.attachEffectSink(sink)

        viewModel.dispatch(PerformanceIntent.SelectSection(PerformanceSection.DATASETS))
        viewModel.dispatch(PerformanceIntent.ImportDataset)

        assertEquals(PerformanceSection.DATASETS, viewModel.state.value.selectedSection)
        assertEquals(listOf(PerformanceEffect.OpenDocumentPicker), effects)
        viewModel.detachEffectSink(sink)
    }

    @Test
    fun `commands stay behind injected sink instead of calling storage from reducer`() {
        val commands = mutableListOf<PerformanceCommand>()
        val viewModel = PerformanceViewModel()
        val sink = PerformanceCommandSink(commands::add)
        viewModel.attachCommandSink(sink)

        viewModel.dispatch(
            PerformanceIntent.DeleteDataset(
                EvaluationDatasetId("custom-eval"),
                EvaluationDatasetVersion("2"),
            ),
        )

        assertTrue(commands.single() is PerformanceCommand.DeleteDataset)
        viewModel.detachCommandSink(sink)
    }

    @Test
    fun `fake repository snapshots are applied without changing reducer ownership`() {
        val viewModel = PerformanceViewModel()

        viewModel.applyDatasets(PerformanceDatasetState(installedCount = 3))
        viewModel.applyHistory(PerformanceHistoryState(runCount = 7))

        assertEquals(3, viewModel.state.value.datasets.installedCount)
        assertEquals(7, viewModel.state.value.history.runCount)
    }
}
