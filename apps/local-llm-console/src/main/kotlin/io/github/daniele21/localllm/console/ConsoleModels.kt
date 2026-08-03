package io.github.daniele21.localllm.console

import io.github.daniele21.localllm.observability.BenchmarkBaseline
import io.github.daniele21.localllm.observability.GenerationRunRecord
import io.github.daniele21.localllm.observability.HealthCheckResult
import io.github.daniele21.localllm.observability.ResourceSnapshot
import io.github.daniele21.localllm.observability.StructuredLog

enum class ConsoleTab(val label: String) {
    OVERVIEW("Overview"),
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
    val sourceError: String? = null,
)

data class ConsoleScreen(
    val title: String,
    val subtitle: String,
    val cards: List<ConsoleCard>,
)

data class ConsoleCard(
    val title: String,
    val lines: List<String>,
    val emphasis: ConsoleEmphasis = ConsoleEmphasis.NEUTRAL,
)

enum class ConsoleEmphasis {
    NEUTRAL,
    POSITIVE,
    WARNING,
    NEGATIVE,
}
