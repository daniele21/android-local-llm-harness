package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.observability.ResourceSnapshot
import io.github.daniele21.localllm.observability.ThermalStatus
import io.github.daniele21.localllm.observability.android.ResourceSnapshotRecorder
import io.github.daniele21.localllm.observability.store.InMemoryTelemetryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessResourceHistoryTest {
    @Test
    fun `summarizes bounded snapshots in newest first order`() {
        val repository = InMemoryTelemetryRepository(maxRuns = 10, maxLogs = 10)
        repository.recordResourceSnapshot(snapshot(100, 10, lowMemory = false, ThermalStatus.NONE))
        repository.recordResourceSnapshot(snapshot(200, 20, lowMemory = true, ThermalStatus.LIGHT))
        repository.recordResourceSnapshot(snapshot(300, 30, lowMemory = false, ThermalStatus.MODERATE))
        val source = HarnessResourceSource(
            recorder = ResourceSnapshotRecorder({ snapshot(400, 40, false, ThermalStatus.NONE) }, repository),
            telemetryRepository = repository,
        )

        val history = source.history()

        assertEquals(listOf(300L, 200L, 100L), history.snapshots.map { it.timestampEpochMs })
        assertEquals(3, history.sampleCount)
        assertEquals("30.0 MiB", history.currentProcessPss)
        assertEquals("10.0 MiB", history.minimumProcessPss)
        assertEquals("30.0 MiB", history.maximumProcessPss)
        assertEquals("Increased by 20.0 MiB", history.processPssTrend)
        assertEquals(1, history.lowMemorySamples)
        assertTrue(history.observedThermalStates.contains("MODERATE"))
    }

    @Test
    fun `history respects the requested bound and does not invent a trend`() {
        val repository = InMemoryTelemetryRepository(maxRuns = 10, maxLogs = 10)
        repository.recordResourceSnapshot(snapshot(100, 10, lowMemory = false, ThermalStatus.NONE))
        repository.recordResourceSnapshot(snapshot(200, 20, lowMemory = false, ThermalStatus.NONE))
        repository.recordResourceSnapshot(snapshot(300, 30, lowMemory = false, ThermalStatus.NONE))
        val source = HarnessResourceSource(
            recorder = ResourceSnapshotRecorder({ snapshot(400, 40, false, ThermalStatus.NONE) }, repository),
            telemetryRepository = repository,
        )

        val history = source.history(limit = 2)

        assertEquals(2, history.sampleCount)
        assertEquals(listOf(300L, 200L), history.snapshots.map { it.timestampEpochMs })
        assertEquals("Need at least 3 samples", history.processPssTrend)
    }

    private fun snapshot(timestamp: Long, processPssMiB: Long, lowMemory: Boolean, thermalStatus: ThermalStatus): ResourceSnapshot =
        ResourceSnapshot(
            timestampEpochMs = timestamp,
            processPssBytes = processPssMiB * MEBIBYTE,
            nativeHeapBytes = null,
            javaHeapUsedBytes = null,
            availableMemoryBytes = null,
            lowMemory = lowMemory,
            thermalStatus = thermalStatus,
        )

    private companion object {
        const val MEBIBYTE = 1_024L * 1_024L
    }
}
