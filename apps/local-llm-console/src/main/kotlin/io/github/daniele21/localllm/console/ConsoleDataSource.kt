package io.github.daniele21.localllm.console

import io.github.daniele21.localllm.observability.TelemetryRepository

fun interface ConsoleDataSource {
    fun load(): ConsoleSnapshot
}

@Suppress("TooGenericExceptionCaught")
class TelemetryConsoleDataSource(
    private val telemetryRepository: TelemetryRepository,
    private val runtimeStateProvider: ConsoleRuntimeStateProvider = DisconnectedRuntimeStateProvider,
    private val clockEpochMs: () -> Long = System::currentTimeMillis,
    private val runLimit: Int = 100,
    private val logLimit: Int = 500,
    private val resourceLimit: Int = 100,
) : ConsoleDataSource {
    init {
        require(runLimit > 0) { "runLimit must be positive" }
        require(logLimit > 0) { "logLimit must be positive" }
        require(resourceLimit > 0) { "resourceLimit must be positive" }
    }

    override fun load(): ConsoleSnapshot {
        val capturedAt = clockEpochMs()
        val runtime = safelyLoadRuntime()

        return try {
            ConsoleSnapshot(
                capturedAtEpochMs = capturedAt,
                runtime = runtime,
                runs = telemetryRepository.recentRuns(runLimit),
                logs = telemetryRepository.recentLogs(logLimit),
                health = telemetryRepository.healthResults(),
                resources = telemetryRepository.recentResourceSnapshots(resourceLimit),
                benchmarkBaselines = telemetryRepository.benchmarkBaselines(),
            )
        } catch (_: RuntimeException) {
            ConsoleSnapshot(
                capturedAtEpochMs = capturedAt,
                runtime = runtime,
                runs = emptyList(),
                logs = emptyList(),
                health = emptyList(),
                resources = emptyList(),
                benchmarkBaselines = emptyList(),
                sourceError = "Telemetry source unavailable",
            )
        }
    }

    private fun safelyLoadRuntime(): ConsoleRuntimeState = try {
        runtimeStateProvider.snapshot()
    } catch (_: RuntimeException) {
        DisconnectedRuntimeStateProvider.snapshot()
    }
}
