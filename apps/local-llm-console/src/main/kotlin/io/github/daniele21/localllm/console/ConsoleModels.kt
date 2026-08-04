package io.github.daniele21.localllm.console

import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.observability.BenchmarkBaseline
import io.github.daniele21.localllm.observability.GenerationRunRecord
import io.github.daniele21.localllm.observability.HealthCheckResult
import io.github.daniele21.localllm.observability.ResourceSnapshot
import io.github.daniele21.localllm.observability.StructuredLog

enum class ConsoleTab(val label: String) {
    OVERVIEW("Overview"),
    MODELS("Models"),
    PLAYGROUND("Playground"),
    RUNTIME("Runtime"),
    RUNS("Runs"),
    LOGS("Logs"),
    HEALTH("Health"),
    CACHES("Caches"),
    RESOURCES("Resources"),
    BENCHMARKS("Benchmarks"),
}

data class ConsoleRuntimeState(
    val status: String,
    val backend: String,
    val loadedModel: String,
    val activeSessions: Int?,
    val queueDepth: Int?,
    val source: String,
    val connected: Boolean = true,
)

fun interface ConsoleRuntimeStateProvider {
    fun snapshot(): ConsoleRuntimeState
}

object DisconnectedRuntimeStateProvider : ConsoleRuntimeStateProvider {
    override fun snapshot(): ConsoleRuntimeState = ConsoleRuntimeState(
        status = "Not connected",
        backend = "Unavailable",
        loadedModel = "Unavailable",
        activeSessions = null,
        queueDepth = null,
        source = "Local console sandbox",
        connected = false,
    )
}

data class ConsoleInstalledModel(val digest: ModelDigest, val sizeBytes: Long, val integrity: ConsoleModelIntegrity)

enum class ConsoleModelIntegrity {
    VERIFIED,
    NOT_CHECKED,
}

data class ConsoleModelInventory(
    val available: Boolean,
    val modelCount: Int,
    val totalBytes: Long,
    val entries: List<ConsoleInstalledModel>,
    val source: String,
    val sourceError: String? = null,
)

fun interface ConsoleModelInventoryProvider {
    fun snapshot(): ConsoleModelInventory
}

object DisconnectedModelInventoryProvider : ConsoleModelInventoryProvider {
    override fun snapshot(): ConsoleModelInventory = ConsoleModelInventory(
        available = false,
        modelCount = 0,
        totalBytes = 0,
        entries = emptyList(),
        source = "Not connected",
    )
}

data class ConsoleSnapshot(
    val capturedAtEpochMs: Long,
    val runtime: ConsoleRuntimeState,
    val runs: List<GenerationRunRecord>,
    val logs: List<StructuredLog>,
    val health: List<HealthCheckResult>,
    val resources: List<ResourceSnapshot>,
    val benchmarkBaselines: List<BenchmarkBaseline>,
    val modelInventory: ConsoleModelInventory = DisconnectedModelInventoryProvider.snapshot(),
    val healthControl: ConsoleHealthControlState = DisconnectedHealthControl.snapshot(),
    val cacheControl: ConsoleCacheControlState = DisconnectedCacheControl.snapshot(),
    val inference: ConsoleInferenceState = DisconnectedConsoleInferenceControl.snapshot(),
    val sourceError: String? = null,
)

data class ConsoleRequestDetail(
    val requestId: RequestId,
    val run: GenerationRunRecord?,
    val timeline: List<StructuredLog>,
    val sourceError: String? = null,
)

data class ConsoleScreen(
    val title: String,
    val subtitle: String,
    val cards: List<ConsoleCard>,
    val charts: List<ConsoleChart> = emptyList(),
    val actions: List<ConsoleAction> = emptyList(),
)

data class ConsoleCard(
    val title: String,
    val lines: List<String>,
    val emphasis: ConsoleEmphasis = ConsoleEmphasis.NEUTRAL,
    val openRequestId: RequestId? = null,
)

data class ConsoleChart(
    val title: String,
    val subtitle: String,
    val valueUnit: String,
    val series: List<ConsoleChartSeries>,
    val minimumValue: Double? = null,
    val maximumValue: Double? = null,
    val valueLabels: Map<Int, String> = emptyMap(),
)

data class ConsoleChartSeries(val label: String, val points: List<ConsoleChartPoint>)

data class ConsoleChartPoint(val timestampEpochMs: Long, val value: Double?)

enum class ConsoleActionType {
    RUN_ALL_HEALTH_CHECKS,
    RUN_HEALTH_CHECKS,
    REPAIR_CACHE,
    START_INFERENCE,
    CANCEL_INFERENCE,
    CLEAR_INFERENCE,
}

data class ConsoleAction(
    val type: ConsoleActionType,
    val label: String,
    val healthCheckIds: List<String> = emptyList(),
    val cacheId: String? = null,
    val enabled: Boolean = true,
)

enum class ConsoleEmphasis {
    NEUTRAL,
    POSITIVE,
    WARNING,
    NEGATIVE,
}
