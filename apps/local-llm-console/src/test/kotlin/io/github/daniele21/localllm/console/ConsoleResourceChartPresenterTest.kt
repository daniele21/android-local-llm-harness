package io.github.daniele21.localllm.console

import io.github.daniele21.localllm.observability.ResourceSnapshot
import io.github.daniele21.localllm.observability.ThermalStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleResourceChartPresenterTest {
    private val presenter = ConsoleResourceChartPresenter()

    @Test
    fun `charts sort samples and convert bytes to MiB without inventing missing values`() {
        val charts = presenter.charts(
            listOf(
                snapshot(timestamp = 200, processPss = 4 * MIB, nativeHeap = null, javaHeap = 2 * MIB),
                snapshot(timestamp = 100, processPss = MIB, nativeHeap = 3 * MIB, javaHeap = null),
            ),
        )

        assertEquals(3, charts.size)
        val processMemory = charts[0]
        assertEquals("Process memory", processMemory.title)
        assertEquals(listOf(100L, 200L), processMemory.series[0].points.map { it.timestampEpochMs })
        assertEquals(1.0, processMemory.series[0].points[0].value ?: -1.0, 0.001)
        assertEquals(4.0, processMemory.series[0].points[1].value ?: -1.0, 0.001)
        assertEquals(3.0, processMemory.series[1].points[0].value ?: -1.0, 0.001)
        assertNull(processMemory.series[1].points[1].value)
        assertNull(processMemory.series[2].points[0].value)
    }

    @Test
    fun `thermal chart preserves discrete states and unknown gaps`() {
        val charts = presenter.charts(
            listOf(
                snapshot(timestamp = 10, thermalStatus = ThermalStatus.NONE),
                snapshot(timestamp = 20, thermalStatus = ThermalStatus.SEVERE, lowMemory = true),
                snapshot(timestamp = 30, thermalStatus = ThermalStatus.UNKNOWN),
            ),
        )

        val thermal = charts[2]
        assertEquals("Thermal pressure", thermal.title)
        assertEquals(listOf(0.0, 3.0, null), thermal.series.single().points.map { it.value })
        assertEquals(6.0, thermal.maximumValue ?: -1.0, 0.001)
        assertEquals("SEVERE", thermal.valueLabels[3])
        assertTrue(thermal.subtitle.contains("low-memory signals: 1"))
    }

    @Test
    fun `empty samples do not create placeholder charts`() {
        assertTrue(presenter.charts(emptyList()).isEmpty())
    }

    private fun snapshot(
        timestamp: Long,
        processPss: Long? = null,
        nativeHeap: Long? = null,
        javaHeap: Long? = null,
        availableMemory: Long? = null,
        lowMemory: Boolean? = false,
        thermalStatus: ThermalStatus = ThermalStatus.LIGHT,
    ) = ResourceSnapshot(
        timestampEpochMs = timestamp,
        processPssBytes = processPss,
        nativeHeapBytes = nativeHeap,
        javaHeapUsedBytes = javaHeap,
        availableMemoryBytes = availableMemory,
        lowMemory = lowMemory,
        thermalStatus = thermalStatus,
    )

    private companion object {
        const val MIB = 1024L * 1024L
    }
}
