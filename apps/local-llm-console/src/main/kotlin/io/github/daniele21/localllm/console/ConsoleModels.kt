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
    RUNTIME("Runtime"),
    RUNS("Runs"),
    LOGS("Logs"),
    HEALTH("Health"),
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

data class ConsoleInstalledModel(
    val digest: ModelDigest,
    val sizeBytes: Long,
    val integrity: ConsoleModelIntegrity,
)

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
    val sourceError: String? = null,
)

data class ConsoleRequestDetail(
    val requestId: RequestId,
    val run: GenerationRunRecord?,
    val timeline: List<StructuredLog>,
    val sourceError: String? = null,
)

data class ConsoleScreen(val title: String, val subtitle: String, val cards: List<ConsoleCard>)

data class ConsoleCard(
    val title: String,
    val lines: List<String>,
    val emphasis: ConsoleEmphasis = ConsoleEmphasis.NEUTRAL,
    val openRequestId: RequestId? = null,
)

enum class ConsoleEmphasis {
    NEUTRAL,
    POSITIVE,
    WARNING,
    NEGATIVE,
}
