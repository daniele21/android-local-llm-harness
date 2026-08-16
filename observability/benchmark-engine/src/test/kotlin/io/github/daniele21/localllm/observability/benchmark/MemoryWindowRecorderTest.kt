package io.github.daniele21.localllm.observability.benchmark

import io.github.daniele21.localllm.observability.ResourceSnapshot
import io.github.daniele21.localllm.observability.ResourceSnapshotProvider
import io.github.daniele21.localllm.observability.ThermalStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

class MemoryWindowRecorderTest {
    @Test
    fun `capture records canonical provider snapshots`() {
        val timestamp = AtomicLong(1)
        val recorder = MemoryWindowRecorder(
            provider = ResourceSnapshotProvider { snapshot(timestamp.getAndIncrement()) },
            maximumSamples = 3,
        )

        recorder.capture()
        recorder.capture()

        assertEquals(listOf(1L, 2L), recorder.snapshots().map(ResourceSnapshot::timestampEpochMs))
    }

    @Test
    fun `window evicts oldest samples at configured bound`() {
        val recorder = MemoryWindowRecorder(ResourceSnapshotProvider { snapshot(99) }, maximumSamples = 2)

        recorder.add(snapshot(1))
        recorder.add(snapshot(2))
        recorder.add(snapshot(3))

        assertEquals(listOf(2L, 3L), recorder.snapshots().map(ResourceSnapshot::timestampEpochMs))
    }

    @Test
    fun `summary uses retained bounded window`() {
        val recorder = MemoryWindowRecorder(ResourceSnapshotProvider { snapshot(99) }, maximumSamples = 3)
        recorder.add(snapshot(1, pss = 100, available = 1_000))
        recorder.add(snapshot(2, pss = 180, available = 800))
        recorder.add(snapshot(3, pss = 120, available = 900))

        val summary = recorder.summarize()

        assertEquals(3, summary.sampleCount)
        assertEquals(100L, summary.baselinePssBytes)
        assertEquals(180L, summary.peakPssBytes)
        assertEquals(120L, summary.residualPssBytes)
        assertEquals(80L, summary.peakDeltaBytes)
        assertEquals(20L, summary.residualDeltaBytes)
        assertEquals(800L, summary.minimumAvailableMemoryBytes)
    }

    @Test
    fun `clear resets the evidence window`() {
        val recorder = MemoryWindowRecorder(ResourceSnapshotProvider { snapshot(99) })
        recorder.add(snapshot(1))

        recorder.clear()

        assertEquals(emptyList<ResourceSnapshot>(), recorder.snapshots())
    }

    private fun snapshot(timestamp: Long, pss: Long? = null, available: Long? = null) = ResourceSnapshot(
        timestampEpochMs = timestamp,
        processPssBytes = pss,
        nativeHeapBytes = null,
        javaHeapUsedBytes = null,
        availableMemoryBytes = available,
        lowMemory = null,
        thermalStatus = ThermalStatus.UNKNOWN,
    )
}
