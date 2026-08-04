package io.github.daniele21.localllm.console

import io.github.daniele21.localllm.observability.HealthCheckResult
import io.github.daniele21.localllm.observability.HealthStatus

class ConsoleHealthPresenter {
    fun present(snapshot: ConsoleSnapshot): ConsoleScreen {
        val control = snapshot.healthControl
        val cards = mutableListOf(controlCard(control))
        cards += snapshot.health
            .sortedWith(compareBy<HealthCheckResult> { healthRank(it.status) }.thenBy { it.id })
            .map(::resultCard)
            .ifEmpty {
                listOf(
                    ConsoleCard(
                        title = "Persisted results",
                        lines = listOf("No health checks recorded"),
                        emphasis = ConsoleEmphasis.WARNING,
                    ),
                )
            }
        return ConsoleScreen(
            title = "Health and sanity",
            subtitle = if (control.executionInProgress) {
                "Health execution in progress; controls remain disabled until completion"
            } else {
                "Explicit health execution with persisted privacy-safe results"
            },
            cards = cards,
            actions = actions(control),
        )
    }

    private fun controlCard(control: ConsoleHealthControlState): ConsoleCard {
        control.sourceError?.let { error ->
            return ConsoleCard(
                title = "Health execution",
                lines = listOf(
                    "Source: ${control.source}",
                    "Status: Unavailable",
                    "Detail: $error",
                ),
                emphasis = ConsoleEmphasis.NEGATIVE,
            )
        }
        if (!control.available) {
            return ConsoleCard(
                title = "Health execution",
                lines = listOf(
                    "Source: ${control.source}",
                    "Status: Not connected",
                    "Registered checks: 0",
                ),
                emphasis = ConsoleEmphasis.WARNING,
            )
        }
        return ConsoleCard(
            title = "Health execution",
            lines = listOf(
                "Source: ${control.source}",
                "Status: ${if (control.executionInProgress) "Running" else "Ready"}",
                "Registered checks: ${control.checkIds.size}",
                "Checks: ${control.checkIds.joinToString().ifEmpty { "None" }}",
            ),
            emphasis = if (control.executionInProgress) {
                ConsoleEmphasis.WARNING
            } else {
                ConsoleEmphasis.NEUTRAL
            },
        )
    }

    private fun actions(control: ConsoleHealthControlState): List<ConsoleAction> {
        if (!control.available || control.checkIds.isEmpty()) return emptyList()
        val enabled = !control.executionInProgress
        return buildList {
            add(
                ConsoleAction(
                    type = ConsoleActionType.RUN_ALL_HEALTH_CHECKS,
                    label = "Run all checks",
                    enabled = enabled,
                ),
            )
            control.checkIds.forEach { checkId ->
                add(
                    ConsoleAction(
                        type = ConsoleActionType.RUN_HEALTH_CHECKS,
                        label = checkLabel(checkId),
                        healthCheckIds = listOf(checkId),
                        enabled = enabled,
                    ),
                )
            }
        }
    }

    private fun resultCard(result: HealthCheckResult): ConsoleCard = ConsoleCard(
        title = "${result.status.name} · ${result.id}",
        lines = listOf(
            "Detail: ${result.detail}",
            "Duration: ${result.durationMs} ms",
        ),
        emphasis = result.status.toEmphasis(),
    )

    private fun checkLabel(checkId: String): String = when {
        checkId == "model-integrity" -> "Run model integrity"
        checkId.startsWith("generation-sanity:") -> "Run ${checkId.replace(':', ' ')}"
        else -> "Run $checkId"
    }

    private fun healthRank(status: HealthStatus): Int = when (status) {
        HealthStatus.FAIL -> 0
        HealthStatus.WARN -> 1
        HealthStatus.NOT_RUN -> 2
        HealthStatus.PASS -> 3
    }

    private fun HealthStatus.toEmphasis(): ConsoleEmphasis = when (this) {
        HealthStatus.PASS -> ConsoleEmphasis.POSITIVE
        HealthStatus.WARN, HealthStatus.NOT_RUN -> ConsoleEmphasis.WARNING
        HealthStatus.FAIL -> ConsoleEmphasis.NEGATIVE
    }
}
