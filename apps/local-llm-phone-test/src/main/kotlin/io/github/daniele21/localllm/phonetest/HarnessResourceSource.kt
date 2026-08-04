package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.observability.ResourceSnapshot
import io.github.daniele21.localllm.observability.TelemetryRepository
import io.github.daniele21.localllm.observability.android.ResourceSnapshotRecorder

internal data class DiagnosticsResourceUi(
    val processPss: String,
    val nativeHeap: String,
    val javaHeap: String,
    val availableMemory: String,
    val lowMemory: String,
    val thermalStatus: String,
    val timestampEpochMs: Long,
    val processPssBytes: Long? = null,
    val nativeHeapBytes: Long? = null,
    val javaHeapBytes: Long? = null,
    val availableMemoryBytes: Long? = null,
)

internal data class DiagnosticsResourceHistoryUi(
    val snapshots: List<DiagnosticsResourceUi> = emptyList(),
    val sampleCount: Int = 0,
    val currentProcessPss: String = "Unavailable",
    val minimumProcessPss: String = "Unavailable",
    val maximumProcessPss: String = "Unavailable",
    val processPssTrend: String = "Unavailable",
    val lowMemorySamples: Int = 0,
    val observedThermalStates: String = "Unavailable",
)

internal class HarnessResourceSource(
    private val recorder: ResourceSnapshotRecorder,
    private val telemetryRepository: TelemetryRepository,
) {
    fun capture(): DiagnosticsResourceUi = recorder.capture().toUi()

    fun recent(limit: Int = DEFAULT_LIMIT): List<DiagnosticsResourceUi> =
        telemetryRepository.recentResourceSnapshots(limit).map { it.toUi() }

    fun history(limit: Int = DEFAULT_LIMIT): DiagnosticsResourceHistoryUi {
        val snapshots = recent(limit)
        val processPssValues = snapshots.mapNotNull(DiagnosticsResourceUi::processPssBytes)
        val chronological = snapshots.asReversed().mapNotNull(DiagnosticsResourceUi::processPssBytes)
        return DiagnosticsResourceHistoryUi(
            snapshots = snapshots,
            sampleCount = snapshots.size,
            currentProcessPss = snapshots.firstOrNull()?.processPss ?: "Unavailable",
            minimumProcessPss = processPssValues.minOrNull().asBytes(),
            maximumProcessPss = processPssValues.maxOrNull().asBytes(),
            processPssTrend = chronological.trend(),
            lowMemorySamples = snapshots.count { it.lowMemory == "Yes" },
            observedThermalStates = snapshots.map(DiagnosticsResourceUi::thermalStatus)
                .distinct()
                .sorted()
                .joinToString()
                .ifBlank { "Unavailable" },
        )
    }

    private fun ResourceSnapshot.toUi(): DiagnosticsResourceUi = DiagnosticsResourceUi(
        processPss = processPssBytes.asBytes(),
        nativeHeap = nativeHeapBytes.asBytes(),
        javaHeap = javaHeapUsedBytes.asBytes(),
        availableMemory = availableMemoryBytes.asBytes(),
        lowMemory = lowMemory?.let { if (it) "Yes" else "No" } ?: "Unavailable",
        thermalStatus = thermalStatus.name,
        timestampEpochMs = timestampEpochMs,
        processPssBytes = processPssBytes,
        nativeHeapBytes = nativeHeapBytes,
        javaHeapBytes = javaHeapUsedBytes,
        availableMemoryBytes = availableMemoryBytes,
    )

    private fun List<Long>.trend(): String {
        if (size < MINIMUM_TREND_SAMPLES) return "Need at least $MINIMUM_TREND_SAMPLES samples"
        val delta = last() - first()
        return when {
            delta > 0 -> "Increased by ${delta.asBytes()}"
            delta < 0 -> "Decreased by ${(-delta).asBytes()}"
            else -> "Stable"
        }
    }

    private fun Long?.asBytes(): String = this?.let { bytes ->
        when {
            bytes >= GIBIBYTE -> "%.2f GiB".format(bytes / GIBIBYTE.toDouble())
            bytes >= MEBIBYTE -> "%.1f MiB".format(bytes / MEBIBYTE.toDouble())
            bytes >= KIBIBYTE -> "%.1f KiB".format(bytes / KIBIBYTE.toDouble())
            else -> "$bytes B"
        }
    } ?: "Unavailable"

    private companion object {
        const val DEFAULT_LIMIT = 30
        const val MINIMUM_TREND_SAMPLES = 3
        const val KIBIBYTE = 1_024L
        const val MEBIBYTE = KIBIBYTE * 1_024L
        const val GIBIBYTE = MEBIBYTE * 1_024L
    }
}
