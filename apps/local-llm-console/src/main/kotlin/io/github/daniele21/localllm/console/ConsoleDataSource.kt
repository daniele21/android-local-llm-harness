package io.github.daniele21.localllm.console

import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.observability.TelemetryRepository

interface ConsoleDataSource {
    fun load(): ConsoleSnapshot

    fun loadRequest(requestId: RequestId): ConsoleRequestDetail
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
                sourceError = TELEMETRY_SOURCE_ERROR,
            )
        }
    }

    override fun loadRequest(requestId: RequestId): ConsoleRequestDetail = try {
        ConsoleRequestDetail(
            requestId = requestId,
            run = telemetryRepository.findRun(requestId),
            timeline = telemetryRepository
                .recentLogs(logLimit, requestId)
                .sortedBy { it.timestampEpochMs },
        )
    } catch (_: RuntimeException) {
        ConsoleRequestDetail(
            requestId = requestId,
            run = null,
            timeline = emptyList(),
            sourceError = TELEMETRY_SOURCE_ERROR,
        )
    }

    private fun safelyLoadRuntime(): ConsoleRuntimeState = try {
        runtimeStateProvider.snapshot()
    } catch (_: RuntimeException) {
        DisconnectedRuntimeStateProvider.snapshot()
    }

    private companion object {
        const val TELEMETRY_SOURCE_ERROR = "Telemetry source unavailable"
    }
}
