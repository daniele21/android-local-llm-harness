package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.observability.ResourceSnapshot
import io.github.daniele21.localllm.observability.ThermalStatus
import io.github.daniele21.localllm.observability.android.ResourceSnapshotRecorder
import io.github.daniele21.localllm.observability.store.InMemoryTelemetryRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class HarnessResourceSourceTest {
    @Test
    fun `capture persists and formats resource snapshot`() {
        val repository = InMemoryTelemetryRepository(maxRuns = 10, maxLogs = 10)
        val snapshot = ResourceSnapshot(
            timestampEpochMs = 123L,
            processPssBytes = 32L * 1_024L * 1_024L,
            nativeHeapBytes = 12L * 1_024L * 1_024L,
            javaHeapUsedBytes = 8L * 1_024L * 1_024L,
            availableMemoryBytes = 2L * 1_024L * 1_024L * 1_024L,
            lowMemory = false,
            thermalStatus = ThermalStatus.MODERATE,
        )
        val source = HarnessResourceSource(
            recorder = ResourceSnapshotRecorder({ snapshot }, repository),
            telemetryRepository = repository,
        )

        val captured = source.capture()
        val recent = source.recent()

        assertEquals("32.0 MiB", captured.processPss)
        assertEquals("2.00 GiB", captured.availableMemory)
        assertEquals("No", captured.lowMemory)
        assertEquals("MODERATE", captured.thermalStatus)
        assertEquals(listOf(captured), recent)
    }

    @Test
    fun `unsupported values remain unavailable instead of zero`() {
        val repository = InMemoryTelemetryRepository(maxRuns = 10, maxLogs = 10)
        val snapshot = ResourceSnapshot(
            timestampEpochMs = 456L,
            processPssBytes = null,
            nativeHeapBytes = null,
            javaHeapUsedBytes = null,
            availableMemoryBytes = null,
            lowMemory = null,
            thermalStatus = ThermalStatus.UNKNOWN,
        )
        val source = HarnessResourceSource(
            recorder = ResourceSnapshotRecorder({ snapshot }, repository),
            telemetryRepository = repository,
        )

        val captured = source.capture()

        assertEquals("Unavailable", captured.processPss)
        assertEquals("Unavailable", captured.nativeHeap)
        assertEquals("Unavailable", captured.javaHeap)
        assertEquals("Unavailable", captured.availableMemory)
        assertEquals("Unavailable", captured.lowMemory)
        assertEquals("UNKNOWN", captured.thermalStatus)
    }
}
