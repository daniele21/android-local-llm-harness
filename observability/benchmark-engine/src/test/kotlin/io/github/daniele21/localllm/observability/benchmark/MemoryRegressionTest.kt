package io.github.daniele21.localllm.observability.benchmark

import io.github.daniele21.localllm.observability.HealthStatus
import io.github.daniele21.localllm.observability.ResourceSnapshot
import io.github.daniele21.localllm.observability.ThermalStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRegressionTest {
    @Test
    fun `summarizer derives baseline peak residual and available floor`() {
        val summary = MemoryWindowSummarizer.summarize(
            listOf(
                snapshot(1, pss = 1_000, available = 5_000),
                snapshot(2, pss = 1_600, available = 4_200),
                snapshot(3, pss = 1_300, available = 4_500),
            ),
        )

        assertEquals(3, summary.sampleCount)
        assertEquals(1_000L, summary.baselinePssBytes)
        assertEquals(1_600L, summary.peakPssBytes)
        assertEquals(1_300L, summary.residualPssBytes)
        assertEquals(600L, summary.peakDeltaBytes)
        assertEquals(300L, summary.residualDeltaBytes)
        assertEquals(4_200L, summary.minimumAvailableMemoryBytes)
    }

    @Test
    fun `summarizer keeps missing measurements unavailable`() {
        val summary = MemoryWindowSummarizer.summarize(
            listOf(
                snapshot(1, pss = null, available = null),
                snapshot(2, pss = null, available = 4_000),
            ),
        )

        assertEquals(null, summary.baselinePssBytes)
        assertEquals(null, summary.peakPssBytes)
        assertEquals(null, summary.residualPssBytes)
        assertEquals(null, summary.peakDeltaBytes)
        assertEquals(4_000L, summary.minimumAvailableMemoryBytes)
    }

    @Test
    fun `comparison fails peak pss regression independently from available memory`() {
        val evaluator = MemoryRegressionEvaluator(
            MemoryRegressionPolicy(
                minimumSamples = 3,
                maxPeakPssRatio = 1.10,
                maxResidualPssRatio = 1.50,
                minimumAvailableMemoryBytes = 1_000,
            ),
        )

        val comparison = evaluator.compare(
            baseline = summary(peak = 1_000, residual = 800, available = 5_000),
            current = summary(peak = 1_200, residual = 900, available = 4_000),
        )

        assertEquals(HealthStatus.FAIL, comparison.status)
        assertTrue(comparison.findings.single { it.metric == MemoryRegressionMetric.PEAK_PSS }.regressed)
    }

    @Test
    fun `comparison fails when available memory floor is crossed`() {
        val evaluator = MemoryRegressionEvaluator(
            MemoryRegressionPolicy(minimumAvailableMemoryBytes = 2_000),
        )

        val comparison = evaluator.compare(
            baseline = summary(peak = 1_000, residual = 800, available = 5_000),
            current = summary(peak = 1_000, residual = 800, available = 1_500),
        )

        assertEquals(HealthStatus.FAIL, comparison.status)
        assertTrue(comparison.findings.single { it.metric == MemoryRegressionMetric.AVAILABLE_MEMORY_FLOOR }.regressed)
    }

    @Test
    fun `comparison warns when windows are undersampled`() {
        val evaluator = MemoryRegressionEvaluator(MemoryRegressionPolicy(minimumSamples = 3))

        val comparison = evaluator.compare(
            baseline = summary(peak = 1_000, residual = 800, available = 5_000, samples = 2),
            current = summary(peak = 1_000, residual = 800, available = 5_000, samples = 3),
        )

        assertEquals(HealthStatus.WARN, comparison.status)
        assertTrue(comparison.findings.isEmpty())
    }

    @Test
    fun `comparison passes when comparable measurements stay within policy`() {
        val comparison = MemoryRegressionEvaluator().compare(
            baseline = summary(peak = 1_000, residual = 800, available = 5_000),
            current = summary(peak = 1_100, residual = 900, available = 4_000),
        )

        assertEquals(HealthStatus.PASS, comparison.status)
    }

    private fun snapshot(timestamp: Long, pss: Long?, available: Long?): ResourceSnapshot = ResourceSnapshot(
        timestampEpochMs = timestamp,
        processPssBytes = pss,
        nativeHeapBytes = null,
        javaHeapUsedBytes = null,
        availableMemoryBytes = available,
        lowMemory = false,
        thermalStatus = ThermalStatus.NONE,
    )

    private fun summary(peak: Long?, residual: Long?, available: Long?, samples: Int = 3): MemoryWindowSummary = MemoryWindowSummary(
        sampleCount = samples,
        baselinePssBytes = 700,
        peakPssBytes = peak,
        residualPssBytes = residual,
        peakDeltaBytes = peak?.minus(700),
        residualDeltaBytes = residual?.minus(700),
        minimumAvailableMemoryBytes = available,
    )
}
