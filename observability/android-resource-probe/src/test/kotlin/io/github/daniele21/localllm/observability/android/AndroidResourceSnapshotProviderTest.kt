package io.github.daniele21.localllm.observability.android

import android.os.PowerManager
import io.github.daniele21.localllm.observability.ResourceSnapshot
import io.github.daniele21.localllm.observability.ResourceSnapshotProvider
import io.github.daniele21.localllm.observability.ThermalStatus
import io.github.daniele21.localllm.observability.store.InMemoryTelemetryRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidResourceSnapshotProviderTest {
    @Test
    fun `maps every platform thermal status`() {
        val mappings = mapOf(
            PowerManager.THERMAL_STATUS_NONE to ThermalStatus.NONE,
            PowerManager.THERMAL_STATUS_LIGHT to ThermalStatus.LIGHT,
            PowerManager.THERMAL_STATUS_MODERATE to ThermalStatus.MODERATE,
            PowerManager.THERMAL_STATUS_SEVERE to ThermalStatus.SEVERE,
            PowerManager.THERMAL_STATUS_CRITICAL to ThermalStatus.CRITICAL,
            PowerManager.THERMAL_STATUS_EMERGENCY to ThermalStatus.EMERGENCY,
            PowerManager.THERMAL_STATUS_SHUTDOWN to ThermalStatus.SHUTDOWN,
        )

        mappings.forEach { (platform, expected) ->
            assertEquals(expected, ThermalStatusMapper.fromPlatformStatus(platform))
        }
        assertEquals(ThermalStatus.UNKNOWN, ThermalStatusMapper.fromPlatformStatus(null))
        assertEquals(ThermalStatus.UNKNOWN, ThermalStatusMapper.fromPlatformStatus(Int.MAX_VALUE))
    }

    @Test
    fun `recorder persists the captured snapshot`() {
        val expected = ResourceSnapshot(
            timestampEpochMs = 10L,
            processPssBytes = 20L,
            nativeHeapBytes = 30L,
            javaHeapUsedBytes = 40L,
            availableMemoryBytes = 50L,
            lowMemory = false,
            thermalStatus = ThermalStatus.MODERATE,
        )
        val repository = InMemoryTelemetryRepository()
        val provider = ResourceSnapshotProvider { expected }

        val captured = ResourceSnapshotRecorder(provider, repository).capture()

        assertEquals(expected, captured)
        assertEquals(listOf(expected), repository.recentResourceSnapshots())
    }
}
