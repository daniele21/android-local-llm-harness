package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.evaluation.EvaluationDatasetId
import io.github.daniele21.localllm.evaluation.EvaluationDatasetVersion
import io.github.daniele21.localllm.evaluation.EvaluationProgress
import io.github.daniele21.localllm.evaluation.EvaluationRunId
import io.github.daniele21.localllm.evaluation.EvaluationRunState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceUiContractsTest {
    @Test
    fun `Performance route contract exposes four stable sections`() {
        assertEquals(
            listOf(
                PerformanceRoutes.RUN,
                PerformanceRoutes.DATASETS,
                PerformanceRoutes.HISTORY,
                PerformanceRoutes.COMPARE,
            ),
            PerformanceRoutes.topLevelSections,
        )
        assertEquals(PerformanceSection.RUN, PerformanceSection.fromRoute(null))
        assertEquals(PerformanceSection.COMPARE, PerformanceSection.fromRoute(PerformanceRoutes.COMPARE))
    }

    @Test
    fun `default state starts on run with incomplete readiness`() {
        val state = PerformanceState()
        assertEquals(PerformanceSection.RUN, state.selectedSection)
        assertEquals(PerformanceSampleSelection.Standard, state.runSetup.sampleSelection)
        assertTrue(state.runSetup.readiness is PerformanceRunReadiness.Incomplete)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `custom sample count must be a multiple of ten`() {
        PerformanceSampleSelection.Custom(15)
    }

    @Test
    fun `custom sample count accepts supported UI step`() {
        assertEquals(30, PerformanceSampleSelection.Custom(30).count)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blocked readiness cannot hide an empty reason list`() {
        PerformanceRunReadiness.Blocked(emptyList())
    }

    @Test
    fun `active state carries evaluation lifecycle without duplicating it`() {
        val active = PerformanceActiveRunState(
            runId = EvaluationRunId("run-1"),
            state = EvaluationRunState.RUNNING,
            progress = EvaluationProgress(totalCases = 20, attemptedCases = 3, completedCases = 2),
            elapsedMs = 1_500,
        )
        assertEquals(EvaluationRunState.RUNNING, active.state)
        assertEquals(2, active.progress.completedCases)
    }

    @Test
    fun `dataset selection retains immutable evaluation identity parts`() {
        val selection = PerformanceDatasetSelection(
            id = EvaluationDatasetId("general-purpose"),
            version = EvaluationDatasetVersion("1.0.0"),
            displayName = "General Purpose v1",
            caseCount = 200,
        )
        assertEquals("general-purpose", selection.id.value)
        assertEquals("1.0.0", selection.version.value)
    }
}
