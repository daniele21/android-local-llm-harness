package io.github.daniele21.localllm.observability.android

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import io.github.daniele21.localllm.observability.ResourceSnapshot
import io.github.daniele21.localllm.observability.ResourceSnapshotProvider
import io.github.daniele21.localllm.observability.TelemetryRepository
import io.github.daniele21.localllm.observability.ThermalStatus

fun interface ResourceEpochClock {
    fun nowEpochMs(): Long
}

class AndroidResourceSnapshotProvider(
    context: Context,
    private val epochClock: ResourceEpochClock = ResourceEpochClock(System::currentTimeMillis),
) : ResourceSnapshotProvider {
    private val activityManager = context.applicationContext.getSystemService(ActivityManager::class.java)
    private val powerManager = context.applicationContext.getSystemService(PowerManager::class.java)

    override fun snapshot(): ResourceSnapshot {
        val memoryInfo = activityManager?.let { manager ->
            runCatching { ActivityManager.MemoryInfo().also(manager::getMemoryInfo) }.getOrNull()
        }
        return ResourceSnapshot(
            timestampEpochMs = epochClock.nowEpochMs(),
            processPssBytes = runCatching { Debug.getPss().toLong() * BYTES_PER_KIBIBYTE }.getOrNull(),
            nativeHeapBytes = runCatching(Debug::getNativeHeapAllocatedSize).getOrNull(),
            javaHeapUsedBytes = runCatching {
                Runtime.getRuntime().let { runtime -> runtime.totalMemory() - runtime.freeMemory() }
            }.getOrNull(),
            availableMemoryBytes = memoryInfo?.availMem,
            lowMemory = memoryInfo?.lowMemory,
            thermalStatus = currentThermalStatus(),
        )
    }

    private fun currentThermalStatus(): ThermalStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return ThermalStatus.UNKNOWN
        return runCatching { ThermalStatusMapper.fromPlatformStatus(powerManager?.currentThermalStatus) }
            .getOrDefault(ThermalStatus.UNKNOWN)
    }

    private companion object {
        const val BYTES_PER_KIBIBYTE = 1_024L
    }
}

class ResourceSnapshotRecorder(
    private val provider: ResourceSnapshotProvider,
    private val repository: TelemetryRepository,
) {
    fun capture(): ResourceSnapshot = provider.snapshot().also(repository::recordResourceSnapshot)
}

internal object ThermalStatusMapper {
    fun fromPlatformStatus(status: Int?): ThermalStatus = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> ThermalStatus.NONE
        PowerManager.THERMAL_STATUS_LIGHT -> ThermalStatus.LIGHT
        PowerManager.THERMAL_STATUS_MODERATE -> ThermalStatus.MODERATE
        PowerManager.THERMAL_STATUS_SEVERE -> ThermalStatus.SEVERE
        PowerManager.THERMAL_STATUS_CRITICAL -> ThermalStatus.CRITICAL
        PowerManager.THERMAL_STATUS_EMERGENCY -> ThermalStatus.EMERGENCY
        PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalStatus.SHUTDOWN
        else -> ThermalStatus.UNKNOWN
    }
}
