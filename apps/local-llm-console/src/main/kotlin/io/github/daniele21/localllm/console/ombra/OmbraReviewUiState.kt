package io.github.daniele21.localllm.console.ombra

import io.github.daniele21.localllm.console.presentation.OmbraReviewPresentationModel
import io.github.daniele21.localllm.console.presentation.OmbraReviewProjectionFailureCode

internal sealed interface OmbraReviewUiState {
    data object NotReady : OmbraReviewUiState

    data class Blocked(val code: OmbraReviewProjectionFailureCode) : OmbraReviewUiState

    data class Empty(val presentation: OmbraReviewPresentationModel) : OmbraReviewUiState {
        init {
            require(presentation.hidden.candidates.isEmpty()) { "Empty review state must not contain candidates" }
        }
    }

    data class Ready(
        val presentation: OmbraReviewPresentationModel,
        val selectedIndex: Int,
    ) : OmbraReviewUiState {
        init {
            require(presentation.hidden.candidates.isNotEmpty()) { "Ready review state requires at least one candidate" }
            require(selectedIndex in presentation.hidden.candidates.indices) { "Review selection must reference a candidate" }
        }

        val currentCandidate
            get() = presentation.hidden.candidates[selectedIndex]
    }
}
