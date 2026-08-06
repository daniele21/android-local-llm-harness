package io.github.daniele21.localllm.phonetest

import java.util.Locale

internal enum class PlaygroundPresentationTone {
    NEUTRAL,
    ACTIVE,
    SUCCESS,
    ERROR,
    WARNING,
}

internal data class PlaygroundPresentation(
    val statusLabel: String,
    val statusTone: PlaygroundPresentationTone,
    val detail: String,
    val responseText: String,
    val runLabel: String,
    val runEnabled: Boolean,
    val stopVisible: Boolean,
    val stopEnabled: Boolean,
    val inputsEnabled: Boolean,
    val ttft: String,
    val total: String,
    val decode: String,
)

internal fun HarnessUiState.toPlaygroundPresentation(): PlaygroundPresentation {
    val playgroundState = playground
    val metrics = playgroundState.metrics
    return PlaygroundPresentation(
        statusLabel = playgroundState.phase.statusLabel(),
        statusTone = playgroundState.phase.statusTone(),
        detail = playgroundState.detail,
        responseText = playgroundState.output.ifBlank { EMPTY_RESPONSE },
        runLabel = if (playgroundState.active) "Generating…" else "Run locally",
        runEnabled = importedModel != null && !busy,
        stopVisible = playgroundState.active || playgroundState.cancellationAvailable,
        stopEnabled = playgroundState.cancellationAvailable,
        inputsEnabled = !busy,
        ttft = metrics?.timeToFirstTokenMs?.let { "$it ms" } ?: UNAVAILABLE,
        total = metrics?.totalMs?.let { "$it ms" } ?: UNAVAILABLE,
        decode = metrics?.decodeTokensPerSecond?.let {
            String.format(Locale.US, "%.2f tok/s", it)
        } ?: UNAVAILABLE,
    )
}

private fun PlaygroundPhase.statusLabel(): String = when (this) {
    PlaygroundPhase.IDLE -> "Idle"
    PlaygroundPhase.PREPARING -> "Preparing"
    PlaygroundPhase.QUEUED -> "Queued"
    PlaygroundPhase.GENERATING -> "●  Streaming"
    PlaygroundPhase.COMPLETED -> "Completed"
    PlaygroundPhase.FAILED -> "Failed"
    PlaygroundPhase.CANCELLED -> "Cancelled"
}

private fun PlaygroundPhase.statusTone(): PlaygroundPresentationTone = when (this) {
    PlaygroundPhase.IDLE -> PlaygroundPresentationTone.NEUTRAL

    PlaygroundPhase.PREPARING,
    PlaygroundPhase.QUEUED,
    PlaygroundPhase.GENERATING,
    -> PlaygroundPresentationTone.ACTIVE

    PlaygroundPhase.COMPLETED -> PlaygroundPresentationTone.SUCCESS

    PlaygroundPhase.FAILED -> PlaygroundPresentationTone.ERROR

    PlaygroundPhase.CANCELLED -> PlaygroundPresentationTone.WARNING
}

private const val EMPTY_RESPONSE = "Generated output will appear here."
private const val UNAVAILABLE = "Unavailable"
