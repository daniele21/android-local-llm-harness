package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.observability.ResourceSnapshot
import io.github.daniele21.localllm.observability.ResourceSnapshotProvider
import io.github.daniele21.localllm.observability.ThermalStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HarnessRuntimeMemoryObservationTest {
    @Test
    fun `maps resource snapshot memory signals without inventing values`() {
        val source = HarnessRuntimeMemoryObservationSource(
            ResourceSnapshotProvider {
                ResourceSnapshot(
                    timestampEpochMs = 100,
                    processPssBytes = 1_000,
                    nativeHeapBytes = 200,
                    javaHeapUsedBytes = null,
                    availableMemoryBytes = 8_000,
                    lowMemory = false,
                    thermalStatus = ThermalStatus.MODERATE,
                )
            },
        )

        val observation = source.observe()

        assertEquals(1_000L, observation.processPssBytes)
        assertEquals(200L, observation.nativeHeapBytes)
        assertNull(observation.javaHeapUsedBytes)
        assertEquals(8_000L, observation.availableMemoryBytes)
        assertEquals(false, observation.lowMemory)
    }

    @Test
    fun `preserves unknown Android memory signals`() {
        val observation = ResourceSnapshot(
            timestampEpochMs = 100,
            processPssBytes = null,
            nativeHeapBytes = null,
            javaHeapUsedBytes = null,
            availableMemoryBytes = null,
            lowMemory = null,
            thermalStatus = ThermalStatus.UNKNOWN,
        ).toRuntimeMemoryObservation()

        assertNull(observation.processPssBytes)
        assertNull(observation.nativeHeapBytes)
        assertNull(observation.javaHeapUsedBytes)
        assertNull(observation.availableMemoryBytes)
        assertNull(observation.lowMemory)
    }
}
