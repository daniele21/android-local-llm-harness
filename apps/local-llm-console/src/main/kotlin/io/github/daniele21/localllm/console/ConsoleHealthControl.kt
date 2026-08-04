package io.github.daniele21.localllm.console

import io.github.daniele21.localllm.observability.HealthCheckResult
import io.github.daniele21.localllm.observability.HealthStatus
import io.github.daniele21.localllm.observability.health.HealthEngine
import io.github.daniele21.localllm.observability.health.HealthSuiteReport

data class ConsoleHealthControlState(
    val available: Boolean,
    val source: String,
    val checkIds: List<String>,
    val executionInProgress: Boolean = false,
    val sourceError: String? = null,
)

data class ConsoleHealthRunOutcome(
    val requestedCheckIds: List<String>,
    val results: List<HealthCheckResult>,
    val status: HealthStatus?,
    val sourceError: String? = null,
)

interface ConsoleHealthControl {
    fun snapshot(): ConsoleHealthControlState

    fun runAll(): ConsoleHealthRunOutcome

    fun run(checkIds: Collection<String>): ConsoleHealthRunOutcome
}

object DisconnectedHealthControl : ConsoleHealthControl {
    override fun snapshot(): ConsoleHealthControlState = ConsoleHealthControlState(
        available = false,
        source = "Not connected",
        checkIds = emptyList(),
    )

    override fun runAll(): ConsoleHealthRunOutcome = unavailable(emptyList())

    override fun run(checkIds: Collection<String>): ConsoleHealthRunOutcome = unavailable(checkIds.toList())

    private fun unavailable(checkIds: List<String>): ConsoleHealthRunOutcome = ConsoleHealthRunOutcome(
        requestedCheckIds = checkIds,
        results = emptyList(),
        status = null,
        sourceError = HEALTH_EXECUTION_SOURCE_ERROR,
    )
}

@Suppress("TooGenericExceptionCaught")
class HealthEngineConsoleHealthControl(
    private val healthEngine: HealthEngine,
    private val source: String,
) : ConsoleHealthControl {
    init {
        require(source.isNotBlank()) { "Health control source must not be blank" }
    }

    override fun snapshot(): ConsoleHealthControlState = try {
        ConsoleHealthControlState(
            available = true,
            source = source,
            checkIds = healthEngine.availableChecks().sorted(),
        )
    } catch (_: RuntimeException) {
        ConsoleHealthControlState(
            available = false,
            source = "Unavailable",
            checkIds = emptyList(),
            sourceError = HEALTH_EXECUTION_SOURCE_ERROR,
        )
    }

    override fun runAll(): ConsoleHealthRunOutcome = execute(
        requestedCheckIds = healthEngine.availableChecks().sorted(),
        execution = healthEngine::runAll,
    )

    override fun run(checkIds: Collection<String>): ConsoleHealthRunOutcome {
        val requested = checkIds.toList()
        return execute(requested) { healthEngine.run(requested) }
    }

    private fun execute(
        requestedCheckIds: List<String>,
        execution: () -> HealthSuiteReport,
    ): ConsoleHealthRunOutcome = try {
        val report = execution()
        ConsoleHealthRunOutcome(
            requestedCheckIds = requestedCheckIds,
            results = report.results,
            status = report.status,
        )
    } catch (_: RuntimeException) {
        ConsoleHealthRunOutcome(
            requestedCheckIds = requestedCheckIds,
            results = emptyList(),
            status = null,
            sourceError = HEALTH_EXECUTION_SOURCE_ERROR,
        )
    }
}

const val HEALTH_EXECUTION_SOURCE_ERROR = "Health execution unavailable"
