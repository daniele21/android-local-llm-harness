package io.github.daniele21.localllm.console

import java.util.Locale

@Suppress("TooManyFunctions")
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
            !inference.available -> cards += emptyCard(inference.detail ?: "Inference playground is not connected")
            inference.targets.isEmpty() -> cards += emptyCard("No application and use-case targets are registered")
            else -> cards += targetsCard(inference)
        }

        if (inference.reasoningOutput.isNotEmpty()) cards += reasoningCard(inference)
        if (inference.answerOutput.isNotEmpty()) cards += answerCard(inference)
        if (inference.reasoningOutput.isEmpty() && inference.answerOutput.isEmpty() && inference.output.isNotEmpty()) {
            cards += legacyOutputCard(inference)
        }
        inference.metrics?.let { metrics -> cards += metricsCard(metrics) }
        inference.errorCode?.let { errorCode -> cards += errorCard(errorCode, inference.detail) }

        return ConsoleScreen(
            title = "Inference playground",
            subtitle = "Explicit one-shot generation through the protected shared Android runtime",
            cards = cards,
            actions = actions(inference),
        )
    }

    fun overview(snapshot: ConsoleSnapshot): ConsoleCard = summary(snapshot.inference)

    private fun actions(state: ConsoleInferenceState): List<ConsoleAction> {
        if (state.connectionState != ConsoleInferenceConnectionState.CONNECTED) {
            return connectionActions(state.connectionState)
        }
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

    private fun connectionActions(state: ConsoleInferenceConnectionState): List<ConsoleAction> = when (state) {
        ConsoleInferenceConnectionState.CONNECTING -> listOf(
            ConsoleAction(
                type = ConsoleActionType.CONNECT_SHARED_RUNTIME,
                label = "Connecting to shared runtime",
                enabled = false,
            ),
        )

        ConsoleInferenceConnectionState.CLOSED -> emptyList()

        ConsoleInferenceConnectionState.CONNECTED -> emptyList()

        ConsoleInferenceConnectionState.DISCONNECTED -> listOf(
            ConsoleAction(
                type = ConsoleActionType.CONNECT_SHARED_RUNTIME,
                label = "Connect shared runtime",
            ),
        )

        ConsoleInferenceConnectionState.HOST_NOT_INSTALLED,
        ConsoleInferenceConnectionState.PERMISSION_DENIED,
        ConsoleInferenceConnectionState.INCOMPATIBLE,
        ConsoleInferenceConnectionState.CONNECTION_LOST,
        -> listOf(
            ConsoleAction(
                type = ConsoleActionType.CONNECT_SHARED_RUNTIME,
                label = "Retry shared runtime connection",
            ),
        )
    }

    private fun summary(state: ConsoleInferenceState): ConsoleCard = ConsoleCard(
        title = "Playground state",
        lines = listOf(
            "Availability: ${if (state.available) "Available" else "Not connected"}",
            "Connection: ${state.connectionState.name}",
            "Phase: ${state.phase.name}",
            "Registered targets: ${state.targets.size}",
            "Active target: ${targetLabel(state)}",
            "Session active: ${state.sessionActive}",
            "Cancellation available: ${state.cancellationAvailable}",
            "Source: ${state.source}",
        ),
        emphasis = phaseEmphasis(state.phase),
    )

    private fun targetsCard(state: ConsoleInferenceState): ConsoleCard = ConsoleCard(
        title = "Registered inference targets",
        lines = state.targets.map { target -> targetLine(target, state.activeTargetId) },
    )

    private fun targetLine(target: ConsoleInferenceTarget, activeTargetId: String?): String {
        val prefix = if (target.id == activeTargetId) "ACTIVE · " else ""
        return "$prefix${target.label} · ${target.applicationId.value}/${target.useCaseId.value}"
    }

    private fun reasoningCard(state: ConsoleInferenceState): ConsoleCard = ConsoleCard(
        title = "Thinking",
        lines = listOf(state.reasoningOutput),
        emphasis = ConsoleEmphasis.NEUTRAL,
    )

    private fun answerCard(state: ConsoleInferenceState): ConsoleCard = ConsoleCard(
        title = "Answer",
        lines = listOf(
            state.answerOutput,
            "Generated tokens: ${state.generatedTokens ?: "Unavailable"}",
            "Display truncated: ${state.outputTruncated}",
        ),
        emphasis = if (state.phase == ConsoleInferencePhase.COMPLETED) ConsoleEmphasis.POSITIVE else ConsoleEmphasis.NEUTRAL,
    )

    private fun legacyOutputCard(state: ConsoleInferenceState): ConsoleCard = ConsoleCard(
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
            "Time to first answer: ${formatDuration(metrics.timeToFirstAnswerMs)}",
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

    private fun hasResult(state: ConsoleInferenceState): Boolean {
        val terminal = state.phase == ConsoleInferencePhase.COMPLETED ||
            state.phase == ConsoleInferencePhase.FAILED ||
            state.phase == ConsoleInferencePhase.CANCELLED
        return state.output.isNotEmpty() || state.reasoningOutput.isNotEmpty() || state.answerOutput.isNotEmpty() ||
            state.metrics != null || state.errorCode != null || terminal
    }

    private fun phaseEmphasis(phase: ConsoleInferencePhase): ConsoleEmphasis = when (phase) {
        ConsoleInferencePhase.COMPLETED -> ConsoleEmphasis.POSITIVE

        ConsoleInferencePhase.FAILED -> ConsoleEmphasis.NEGATIVE

        ConsoleInferencePhase.DISCONNECTED,
        ConsoleInferencePhase.PREPARING,
        ConsoleInferencePhase.QUEUED,
        ConsoleInferencePhase.GENERATING,
        ConsoleInferencePhase.CANCELLED,
        -> ConsoleEmphasis.WARNING

        ConsoleInferencePhase.IDLE -> ConsoleEmphasis.NEUTRAL
    }

    private fun formatDuration(value: Long?): String = value?.let { "$it ms" } ?: "Unavailable"

    private fun formatRate(value: Double?): String = value
        ?.let { String.format(Locale.US, "%.2f tok/s", it) }
        ?: "Unavailable"
}
