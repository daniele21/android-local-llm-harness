package io.github.daniele21.localllm.console

import java.util.Locale

class ConsoleInferencePresenter {
    fun present(snapshot: ConsoleSnapshot): ConsoleScreen {
        val inference = snapshot.inference
        val cards = mutableListOf<ConsoleCard>()
        inference.sourceError?.let { error ->
            cards += ConsoleCard(
                title = "Inference control",
                lines = listOf(error),
                emphasis = ConsoleEmphasis.NEGATIVE,
            )
        }
        cards += summary(inference)
        cards += privacyCard()

        when {
            !inference.available -> cards += emptyCard("Inference playground is not connected")
            inference.targets.isEmpty() -> cards += emptyCard("No application and use-case targets are registered")
            else -> cards += targetsCard(inference)
        }

        if (inference.output.isNotEmpty()) cards += outputCard(inference)
        inference.metrics?.let { metrics -> cards += metricsCard(metrics) }
        inference.errorCode?.let { errorCode -> cards += errorCard(errorCode, inference.detail) }

        return ConsoleScreen(
            title = "Inference playground",
            subtitle = "Explicit one-shot generation through a connected LocalLlmClient",
            cards = cards,
            actions = actions(inference),
        )
    }

    fun overview(snapshot: ConsoleSnapshot): ConsoleCard = summary(snapshot.inference)

    private fun actions(state: ConsoleInferenceState): List<ConsoleAction> {
        val actions = mutableListOf<ConsoleAction>()
        if (state.available && state.targets.isNotEmpty()) {
            actions += ConsoleAction(
                type = ConsoleActionType.START_INFERENCE,
                label = "Run local prompt",
                enabled = !state.executionActive && !state.sessionActive,
            )
        }
        if (state.cancellationAvailable) {
            actions += ConsoleAction(
                type = ConsoleActionType.CANCEL_INFERENCE,
                label = if (state.cancellationRequested) "Cancellation requested" else "Cancel generation",
                enabled = !state.cancellationRequested,
            )
        }
        if (!state.executionActive && !state.sessionActive && hasResult(state)) {
            actions += ConsoleAction(
                type = ConsoleActionType.CLEAR_INFERENCE,
                label = "Clear playground result",
            )
        }
        return actions
    }

    private fun summary(state: ConsoleInferenceState): ConsoleCard = ConsoleCard(
        title = "Playground state",
        lines = listOf(
            "Availability: ${if (state.available) "Available" else "Not connected"}",
            "Phase: ${state.phase.name}",
            "Registered targets: ${state.targets.size}",
            "Active target: ${targetLabel(state)}",
            "Session active: ${state.sessionActive}",
            "Cancellation available: ${state.cancellationAvailable}",
            "Source: ${state.source}",
        ),
        emphasis = when (state.phase) {
            ConsoleInferencePhase.COMPLETED -> ConsoleEmphasis.POSITIVE
            ConsoleInferencePhase.FAILED -> ConsoleEmphasis.NEGATIVE
            ConsoleInferencePhase.CANCELLED,
            ConsoleInferencePhase.PREPARING,
            ConsoleInferencePhase.QUEUED,
            ConsoleInferencePhase.GENERATING,
            -> ConsoleEmphasis.WARNING

            ConsoleInferencePhase.DISCONNECTED -> ConsoleEmphasis.WARNING
            ConsoleInferencePhase.IDLE -> ConsoleEmphasis.NEUTRAL
        },
    )

    private fun targetsCard(state: ConsoleInferenceState): ConsoleCard = ConsoleCard(
        title = "Registered inference targets",
        lines = state.targets.map { target ->
            val active = target.id == state.activeTargetId
            "${if (active) "ACTIVE · " else ""}${target.label} · ${target.applicationId.value}/${target.useCaseId.value}"
        },
    )

    private fun outputCard(state: ConsoleInferenceState): ConsoleCard = ConsoleCard(
        title = "Generated output",
        lines = listOf(
            state.output,
            "Generated tokens: ${state.generatedTokens ?: "Unavailable"}",
            "Display truncated: ${state.outputTruncated}",
        ),
        emphasis = when (state.phase) {
            ConsoleInferencePhase.COMPLETED -> ConsoleEmphasis.POSITIVE
            ConsoleInferencePhase.FAILED -> ConsoleEmphasis.NEGATIVE
            else -> ConsoleEmphasis.NEUTRAL
        },
    )

    private fun metricsCard(metrics: ConsoleInferenceMetrics): ConsoleCard = ConsoleCard(
        title = "Generation metrics",
        lines = listOf(
            "Load class: ${metrics.modelLoadKind}",
            "Queue: ${formatDuration(metrics.queueMs)}",
            "Model load: ${formatDuration(metrics.modelLoadMs)}",
            "Time to first token: ${formatDuration(metrics.timeToFirstTokenMs)}",
            "Prefill: ${formatDuration(metrics.prefillMs)}",
            "Decode: ${formatDuration(metrics.decodeMs)}",
            "Total: ${formatDuration(metrics.totalMs)}",
            "Tokens: ${metrics.inputTokens ?: "?"} in / ${metrics.outputTokens ?: "?"} out",
            "Decode throughput: ${formatRate(metrics.decodeTokensPerSecond)}",
        ),
        emphasis = ConsoleEmphasis.POSITIVE,
    )

    private fun errorCard(errorCode: String, detail: String?): ConsoleCard = ConsoleCard(
        title = "Inference result",
        lines = listOf(
            "Error code: $errorCode",
            "Detail: ${detail ?: "Generation failed"}",
            "Backend message: Not exposed",
        ),
        emphasis = ConsoleEmphasis.NEGATIVE,
    )

    private fun privacyCard(): ConsoleCard = ConsoleCard(
        title = "Privacy boundary",
        lines = listOf(
            "Prompt persistence: Disabled",
            "Generated-output telemetry: Disabled",
            "Playground output: In-memory UI state only",
            "Backend exception text: Not exposed",
            "Refresh starts generation: No",
        ),
    )

    private fun emptyCard(message: String): ConsoleCard = ConsoleCard(
        title = "Playground availability",
        lines = listOf(message),
        emphasis = ConsoleEmphasis.WARNING,
    )

    private fun targetLabel(state: ConsoleInferenceState): String = state.targets
        .firstOrNull { it.id == state.activeTargetId }
        ?.label
        ?: "None"

    private fun hasResult(state: ConsoleInferenceState): Boolean = state.output.isNotEmpty() ||
        state.metrics != null ||
        state.errorCode != null ||
        state.phase in setOf(
            ConsoleInferencePhase.COMPLETED,
            ConsoleInferencePhase.FAILED,
            ConsoleInferencePhase.CANCELLED,
        )

    private fun formatDuration(value: Long?): String = value?.let { "$it ms" } ?: "Unavailable"

    private fun formatRate(value: Double?): String = value
        ?.let { String.format(Locale.US, "%.2f tok/s", it) }
        ?: "Unavailable"
}
