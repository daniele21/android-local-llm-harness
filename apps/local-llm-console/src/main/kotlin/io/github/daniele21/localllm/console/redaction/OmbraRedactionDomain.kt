package io.github.daniele21.localllm.console.redaction

import io.github.daniele21.localllm.console.document.SourceOccurrence
import io.github.daniele21.localllm.console.pii.PiiTypeId

/** Content-free deterministic identity for one typed source occurrence. */
internal data class OccurrenceId(val typeId: PiiTypeId, val source: SourceOccurrence)

internal enum class ReviewDecisionState {
    PENDING,
    ACCEPTED,
    IGNORED,
}

/** Local human-review state. Reveal state intentionally does not enter this domain decision. */
internal data class RedactionDecision(val occurrenceId: OccurrenceId, val state: ReviewDecisionState)

/** One reviewable typed occurrence before placeholder generation/export. */
internal data class ReviewOccurrence(
    val id: OccurrenceId,
    val surface: String,
    val decision: ReviewDecisionState = ReviewDecisionState.PENDING,
) {
    init {
        require(surface.isNotBlank()) { "Review occurrence surface must not be blank" }
    }

    override fun toString(): String = "ReviewOccurrence(id=$id, surface=<redacted>, decision=$decision)"

    fun accept(): ReviewOccurrence = copy(decision = ReviewDecisionState.ACCEPTED)

    fun ignore(): ReviewOccurrence = copy(decision = ReviewDecisionState.IGNORED)

    fun resetDecision(): ReviewOccurrence = copy(decision = ReviewDecisionState.PENDING)
}
