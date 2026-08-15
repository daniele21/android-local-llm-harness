package io.github.daniele21.localllm.console.ombra

import io.github.daniele21.localllm.console.application.OmbraSensitiveTaskSnapshot
import io.github.daniele21.localllm.console.presentation.OmbraReviewPresentationResult
import io.github.daniele21.localllm.console.presentation.OmbraReviewProjectionResult
import io.github.daniele21.localllm.console.presentation.OmbraReviewProjectionSession
import io.github.daniele21.localllm.console.presentation.OmbraReviewProjector
import io.github.daniele21.localllm.console.redaction.OccurrenceId
import io.github.daniele21.localllm.console.redaction.ReviewDecisionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class OmbraReviewProjectionController(
    private val snapshotProvider: () -> OmbraSensitiveTaskSnapshot,
    private val setDecision: (OccurrenceId, ReviewDecisionState) -> Boolean,
) {
    private val stateMutable = MutableStateFlow<OmbraReviewUiState>(OmbraReviewUiState.NotReady)
    private var session: OmbraReviewProjectionSession? = null

    val state: StateFlow<OmbraReviewUiState> = stateMutable.asStateFlow()

    fun prepare() {
        rebuild(selectedIndex = 0)
    }

    fun move(delta: Int) {
        val current = stateMutable.value as? OmbraReviewUiState.Ready ?: return
        val target = (current.selectedIndex + delta).coerceIn(current.presentation.hidden.candidates.indices)
        publish(target, revealed = false)
    }

    fun toggleReveal() {
        val current = stateMutable.value as? OmbraReviewUiState.Ready ?: return
        val occurrenceId = current.currentCandidate.occurrenceId
        val revealed = current.presentation.revealedCandidate?.occurrenceId != occurrenceId
        publish(current.selectedIndex, revealed)
    }

    fun decide(decision: ReviewDecisionState): Boolean {
        val current = stateMutable.value as? OmbraReviewUiState.Ready ?: return false
        val changed = setDecision(current.currentCandidate.occurrenceId, decision)
        if (changed) rebuild(selectedIndex = current.selectedIndex)
        return changed
    }

    fun canExport(): Boolean = when (val current = stateMutable.value) {
        is OmbraReviewUiState.Empty -> current.presentation.hidden.summary.canContinue

        is OmbraReviewUiState.Ready -> current.presentation.hidden.summary.canContinue

        is OmbraReviewUiState.Blocked,
        OmbraReviewUiState.NotReady,
        -> false
    }

    fun suggestedExportName(): String {
        val rawName = snapshotProvider().descriptor?.displayName.orEmpty()
        val baseName = rawName.substringBeforeLast('.', rawName).ifBlank { "documento" }
        val safeName = baseName.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(80).ifBlank { "documento" }
        return "$safeName-ombra.pdf"
    }

    fun clear() {
        session?.clearSensitiveMapping()
        session = null
        stateMutable.value = OmbraReviewUiState.NotReady
    }

    private fun rebuild(selectedIndex: Int) {
        clear()
        val snapshot = snapshotProvider()
        when (
            val projected =
                OmbraReviewProjector.build(
                    segments = snapshot.segments,
                    definitions = snapshot.definitions,
                    reviewOccurrences = snapshot.reviewOccurrences,
                )
        ) {
            is OmbraReviewProjectionResult.Blocked -> {
                stateMutable.value = OmbraReviewUiState.Blocked(projected.code)
            }

            is OmbraReviewProjectionResult.Ready -> {
                session = projected.session
                val presented = projected.session.present()
                if (presented !is OmbraReviewPresentationResult.Ready) {
                    stateMutable.value = OmbraReviewUiState.NotReady
                    return
                }
                if (presented.model.hidden.candidates.isEmpty()) {
                    stateMutable.value = OmbraReviewUiState.Empty(presented.model)
                } else {
                    val index = selectedIndex.coerceIn(presented.model.hidden.candidates.indices)
                    stateMutable.value = OmbraReviewUiState.Ready(presented.model, index)
                }
            }
        }
    }

    private fun publish(selectedIndex: Int, revealed: Boolean) {
        val current = stateMutable.value as? OmbraReviewUiState.Ready ?: return
        val currentSession = session ?: return
        val candidate = current.presentation.hidden.candidates[selectedIndex]
        val result = currentSession.present(candidate.occurrenceId.takeIf { revealed })
        stateMutable.value =
            when (result) {
                is OmbraReviewPresentationResult.Blocked -> OmbraReviewUiState.Blocked(result.code)
                is OmbraReviewPresentationResult.Ready -> OmbraReviewUiState.Ready(result.model, selectedIndex)
            }
    }
}
