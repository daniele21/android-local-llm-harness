package io.github.daniele21.localllm.console

import io.github.daniele21.localllm.observability.ResourceSnapshot
import io.github.daniele21.localllm.observability.ThermalStatus

class ConsoleResourceChartPresenter {
    fun charts(snapshots: List<ResourceSnapshot>): List<ConsoleChart> {
        if (snapshots.isEmpty()) return emptyList()
        val ordered = snapshots.sortedBy { it.timestampEpochMs }
        return listOf(
            processMemoryChart(ordered),
            availableMemoryChart(ordered),
            thermalChart(ordered),
        )
    }

    private fun processMemoryChart(snapshots: List<ResourceSnapshot>): ConsoleChart = ConsoleChart(
        title = "Process memory",
        subtitle = "PSS, native heap and Java heap across ${snapshots.size} persisted samples",
        valueUnit = "MiB",
        minimumValue = 0.0,
        series = listOf(
            memorySeries("Process PSS", snapshots) { it.processPssBytes },
            memorySeries("Native heap", snapshots) { it.nativeHeapBytes },
            memorySeries("Java heap", snapshots) { it.javaHeapUsedBytes },
        ),
    )

    private fun availableMemoryChart(snapshots: List<ResourceSnapshot>): ConsoleChart = ConsoleChart(
        title = "Available device memory",
        subtitle = "System memory reported at each explicit capture",
        valueUnit = "MiB",
        minimumValue = 0.0,
        series = listOf(memorySeries("Available memory", snapshots) { it.availableMemoryBytes }),
    )

    private fun thermalChart(snapshots: List<ResourceSnapshot>): ConsoleChart {
        val lowMemorySignals = snapshots.count { it.lowMemory == true }
        return ConsoleChart(
            title = "Thermal pressure",
            subtitle = "Android thermal states; low-memory signals: $lowMemorySignals",
            valueUnit = "status",
            minimumValue = 0.0,
            maximumValue = ThermalStatus.SHUTDOWN.level(),
            valueLabels = THERMAL_LABELS,
            series = listOf(
                ConsoleChartSeries(
                    label = "Thermal status",
                    points = snapshots.map { snapshot ->
                        ConsoleChartPoint(
                            timestampEpochMs = snapshot.timestampEpochMs,
                            value = snapshot.thermalStatus.levelOrNull(),
                        )
                    },
                ),
            ),
        )
    }

    private fun memorySeries(
        label: String,
        snapshots: List<ResourceSnapshot>,
        value: (ResourceSnapshot) -> Long?,
    ): ConsoleChartSeries = ConsoleChartSeries(
        label = label,
        points = snapshots.map { snapshot ->
            ConsoleChartPoint(
                timestampEpochMs = snapshot.timestampEpochMs,
                value = value(snapshot)?.toDouble()?.div(BYTES_PER_MIB),
            )
        },
    )

    private fun ThermalStatus.levelOrNull(): Double? = when (this) {
        ThermalStatus.NONE -> 0.0
        ThermalStatus.LIGHT -> 1.0
        ThermalStatus.MODERATE -> 2.0
        ThermalStatus.SEVERE -> 3.0
        ThermalStatus.CRITICAL -> 4.0
        ThermalStatus.EMERGENCY -> 5.0
        ThermalStatus.SHUTDOWN -> 6.0
        ThermalStatus.UNKNOWN -> null
    }

    private fun ThermalStatus.level(): Double = requireNotNull(levelOrNull())

    private companion object {
        const val BYTES_PER_MIB = 1024.0 * 1024.0
        val THERMAL_LABELS = mapOf(
            0 to "NONE",
            1 to "LIGHT",
            2 to "MODERATE",
            3 to "SEVERE",
            4 to "CRITICAL",
            5 to "EMERGENCY",
            6 to "SHUTDOWN",
        )
    }
}
