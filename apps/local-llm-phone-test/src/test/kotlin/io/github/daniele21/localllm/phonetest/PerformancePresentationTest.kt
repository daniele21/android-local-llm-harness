package io.github.daniele21.localllm.phonetest

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

    @Test(expected = IllegalArgumentException::class)
    fun `custom sample count must remain a positive multiple of ten`() {
        PerformanceSampleSelection.Custom(25)
    }
}
