package io.github.daniele21.localllm.phonetest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformancePresentationTest {
    @Test
    fun `fixed samples never clamp beyond dataset size`() {
        assertTrue(performanceSampleEnabled(PerformanceSampleSelection.Smoke, 20))
        assertFalse(performanceSampleEnabled(PerformanceSampleSelection.Quick, 20))
        assertTrue(performanceSampleEnabled(PerformanceSampleSelection.All, 20))
    }

    @Test
    fun `missing dataset only permits all until count is known`() {
        assertFalse(performanceSampleEnabled(PerformanceSampleSelection.Standard, null))
        assertTrue(performanceSampleEnabled(PerformanceSampleSelection.All, null))
    }

    @Test
    fun `runner unavailable is stated before setup readiness`() {
        val detail = performanceReadinessDetail(PerformanceRunReadiness.Ready, runnerAvailable = false)
        assertTrue(detail.contains("not connected"))
        assertFalse(detail.contains("All required"))
    }

    @Test
    fun `no completed runs cannot produce a supported choice`() {
        val presentation = performanceDecisionPresentation(PerformanceState())

        assertEquals(PerformanceDecisionState.NO_EVIDENCE, presentation.state)
        assertEquals("No supported choice yet", presentation.title)
        assertTrue(presentation.detail.contains("Complete repeatable evaluation runs"))
    }

    @Test
    fun `recorded runs remain fail closed until comparable deltas exist`() {
        val presentation = performanceDecisionPresentation(
            PerformanceState(history = PerformanceHistoryState(runCount = 3)),
        )

        assertEquals(PerformanceDecisionState.EVIDENCE_NOT_COMPARABLE, presentation.state)
        assertTrue(presentation.detail.contains("3 evaluation run(s)"))
        assertTrue(presentation.detail.contains("will not rank models or configurations"))
    }

    @Test
    fun `history failures block the decision instead of falling back to a ranking`() {
        val presentation = performanceDecisionPresentation(
            PerformanceState(history = PerformanceHistoryState(error = "History unavailable")),
        )

        assertEquals(PerformanceDecisionState.UNAVAILABLE, presentation.state)
        assertEquals("No supported choice can be made", presentation.title)
        assertEquals("History unavailable", presentation.detail)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `custom sample count must remain a positive multiple of ten`() {
        PerformanceSampleSelection.Custom(25)
    }
}
