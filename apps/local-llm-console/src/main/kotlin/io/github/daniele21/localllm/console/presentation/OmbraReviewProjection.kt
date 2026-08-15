package io.github.daniele21.localllm.console.presentation

import io.github.daniele21.localllm.console.document.DocumentSegment
import io.github.daniele21.localllm.console.document.SegmentId
import io.github.daniele21.localllm.console.document.SourceRange
import io.github.daniele21.localllm.console.pii.PiiDefinition
import io.github.daniele21.localllm.console.pii.PiiTypeId
import io.github.daniele21.localllm.console.redaction.OccurrenceId
import io.github.daniele21.localllm.console.redaction.OmbraPlaceholderKeys
import io.github.daniele21.localllm.console.redaction.ReviewDecisionState
import io.github.daniele21.localllm.console.redaction.ReviewOccurrence

internal enum class OmbraReviewProjectionFailureCode {
    UNKNOWN_SEGMENT,
    MISSING_DEFINITION,
    SOURCE_MISMATCH,
    DUPLICATE_OCCURRENCE,
    DUPLICATE_SEGMENT,
    DUPLICATE_DEFINITION,
    HIDDEN_CONTENT_NOT_SAFE,
    REVEAL_MAPPING_CLEARED,
    UNKNOWN_REVEAL_OCCURRENCE,
}

internal sealed interface OmbraReviewProjectionResult {
    data class Ready(val session: OmbraReviewProjectionSession) : OmbraReviewProjectionResult

    data class Blocked(val code: OmbraReviewProjectionFailureCode) : OmbraReviewProjectionResult
}

internal sealed interface OmbraReviewPresentationResult {
    data class Ready(val model: OmbraReviewPresentationModel) : OmbraReviewPresentationResult

    data class Blocked(val code: OmbraReviewProjectionFailureCode) : OmbraReviewPresentationResult
}

/** A normalized preview segment whose text contains masks, never known candidate surfaces. */
internal data class OmbraHiddenPreviewSegment(val segmentId: SegmentId, val text: String) {
    override fun toString(): String = "OmbraHiddenPreviewSegment(segmentId=$segmentId, text=<redacted>)"
}

internal enum class OmbraReviewConflictState {
    NONE,
    RESOLVED,
    REQUIRES_DECISION,
}

/** Content-safe candidate metadata used by both visual rendering and accessibility semantics. */
internal data class OmbraHiddenReviewCandidate(
    val occurrenceId: OccurrenceId,
    val typeLabel: String,
    val placeholder: String,
    val position: Int,
    val total: Int,
    val decision: ReviewDecisionState,
    val conflictState: OmbraReviewConflictState,
    val accessibilityDescription: String,
) {
    fun revealedAccessibilityDescription(surface: String): String = buildAccessibilityDescription(
        typeLabel = typeLabel,
        position = position,
        total = total,
        decision = decision,
        conflictState = conflictState,
        revealed = true,
    ) + ", valore $surface"

    override fun toString(): String = "OmbraHiddenReviewCandidate(occurrenceId=$occurrenceId, typeLabel=<redacted>, " +
        "placeholder=$placeholder, position=$position, total=$total, decision=$decision, " +
        "conflictState=$conflictState, accessibilityDescription=<redacted>)"
}

internal data class OmbraReviewSummary(
    val candidateCount: Int,
    val acceptedCount: Int,
    val ignoredCount: Int,
    val pendingCount: Int,
    val unresolvedConflictCount: Int,
    val canContinue: Boolean,
) {
    init {
        require(candidateCount >= 0) { "Candidate count must be non-negative" }
        require(acceptedCount + ignoredCount + pendingCount == candidateCount) {
            "Decision counts must match candidate count"
        }
        require(unresolvedConflictCount >= 0) { "Conflict count must be non-negative" }
        require(canContinue == (pendingCount == 0 && unresolvedConflictCount == 0)) {
            "Continuation state must fail closed"
        }
    }
}

/**
 * Default review projection. This graph deliberately contains no original candidate surface, so it
 * is safe to hand to hidden Compose content and semantics.
 */
internal data class OmbraHiddenReviewModel(
    val segments: List<OmbraHiddenPreviewSegment>,
    val candidates: List<OmbraHiddenReviewCandidate>,
    val summary: OmbraReviewSummary,
)

/** Sensitive presentation value created only for an explicit reveal request. */
internal data class OmbraRevealedReviewCandidate(
    val occurrenceId: OccurrenceId,
    val surface: String,
    val accessibilityDescription: String,
) {
    override fun toString(): String =
        "OmbraRevealedReviewCandidate(occurrenceId=$occurrenceId, surface=<redacted>, accessibilityDescription=<redacted>)"
}

/** Reveal is an ephemeral presentation choice and never becomes a review-domain decision. */
internal data class OmbraReviewPresentationModel(
    val hidden: OmbraHiddenReviewModel,
    val revealedCandidate: OmbraRevealedReviewCandidate?,
) {
    override fun toString(): String = "OmbraReviewPresentationModel(hidden=$hidden, hasRevealedCandidate=${revealedCandidate != null})"
}

/**
 * Owns the sensitive source mapping in process memory. Callers clear the session on export, reset,
 * cancellation or presentation-owner destruction. No reveal selection is retained by this class.
 */
internal class OmbraReviewProjectionSession internal constructor(
    val hiddenModel: OmbraHiddenReviewModel,
    sourceMapping: Map<OccurrenceId, String>,
) {
    private val sourceMapping = sourceMapping.toMutableMap()
    private var cleared = false

    fun present(revealedOccurrenceId: OccurrenceId? = null): OmbraReviewPresentationResult {
        if (revealedOccurrenceId == null) {
            return OmbraReviewPresentationResult.Ready(
                OmbraReviewPresentationModel(hidden = hiddenModel, revealedCandidate = null),
            )
        }
        if (cleared) {
            return OmbraReviewPresentationResult.Blocked(OmbraReviewProjectionFailureCode.REVEAL_MAPPING_CLEARED)
        }
        val surface = sourceMapping[revealedOccurrenceId]
            ?: return OmbraReviewPresentationResult.Blocked(
                OmbraReviewProjectionFailureCode.UNKNOWN_REVEAL_OCCURRENCE,
            )
        val candidate = hiddenModel.candidates.first { it.occurrenceId == revealedOccurrenceId }
        return OmbraReviewPresentationResult.Ready(
            OmbraReviewPresentationModel(
                hidden = hiddenModel,
                revealedCandidate = OmbraRevealedReviewCandidate(
                    occurrenceId = revealedOccurrenceId,
                    surface = surface,
                    accessibilityDescription = candidate.revealedAccessibilityDescription(surface),
                ),
            ),
        )
    }

    fun clearSensitiveMapping() {
        sourceMapping.clear()
        cleared = true
    }

    override fun toString(): String =
        "OmbraReviewProjectionSession(hiddenModel=$hiddenModel, mappingState=${if (cleared) "cleared" else "available"})"
}

/** Pure-JVM OMB-5C normalized hidden/reveal projection builder. */
internal object OmbraReviewProjector {
    fun build(
        segments: List<DocumentSegment>,
        definitions: List<PiiDefinition>,
        reviewOccurrences: List<ReviewOccurrence>,
    ): OmbraReviewProjectionResult {
        val definitionById = definitions.associateBy(PiiDefinition::id)
        val segmentById = segments.associateBy(DocumentSegment::id)
        validate(segments, definitions, reviewOccurrences, definitionById, segmentById)?.let { failure ->
            return OmbraReviewProjectionResult.Blocked(failure)
        }

        val segmentOrder = segments.withIndex().associate { indexed -> indexed.value.id to indexed.index }
        val orderedOccurrences = reviewOccurrences.sortedWith(sourceComparator(segmentOrder))
        val conflicts = conflictPairs(orderedOccurrences)
        val unresolvedConflicts = conflicts.filterNot(::isResolved)
        val placeholders = placeholders(orderedOccurrences, definitions)
        val candidateCount = orderedOccurrences.size
        val sensitiveSurfaces = orderedOccurrences.map(ReviewOccurrence::surface)
        val candidates = orderedOccurrences.mapIndexed { index, occurrence ->
            val candidateConflicts = conflicts.filter { pair -> pair.contains(occurrence.id) }
            val conflictState = when {
                candidateConflicts.isEmpty() -> OmbraReviewConflictState.NONE

                candidateConflicts.any { pair -> pair in unresolvedConflicts } ->
                    OmbraReviewConflictState.REQUIRES_DECISION

                else -> OmbraReviewConflictState.RESOLVED
            }
            val typeLabel = safeTypeLabel(
                label = requireNotNull(definitionById[occurrence.id.typeId]).label,
                sensitiveSurfaces = sensitiveSurfaces,
            )
            OmbraHiddenReviewCandidate(
                occurrenceId = occurrence.id,
                typeLabel = typeLabel,
                placeholder = placeholders.getValue(occurrence.id),
                position = index + 1,
                total = candidateCount,
                decision = occurrence.decision,
                conflictState = conflictState,
                accessibilityDescription = buildAccessibilityDescription(
                    typeLabel = typeLabel,
                    position = index + 1,
                    total = candidateCount,
                    decision = occurrence.decision,
                    conflictState = conflictState,
                    revealed = false,
                ),
            )
        }
        val hiddenSegments = segments.map { segment ->
            OmbraHiddenPreviewSegment(
                segmentId = segment.id,
                text = maskSegment(segment, orderedOccurrences, placeholders),
            )
        }
        if (!isHiddenContentSafe(hiddenSegments, candidates, sensitiveSurfaces)) {
            return OmbraReviewProjectionResult.Blocked(
                OmbraReviewProjectionFailureCode.HIDDEN_CONTENT_NOT_SAFE,
            )
        }
        val pendingCount = orderedOccurrences.count { it.decision == ReviewDecisionState.PENDING }
        val summary = OmbraReviewSummary(
            candidateCount = candidateCount,
            acceptedCount = orderedOccurrences.count { it.decision == ReviewDecisionState.ACCEPTED },
            ignoredCount = orderedOccurrences.count { it.decision == ReviewDecisionState.IGNORED },
            pendingCount = pendingCount,
            unresolvedConflictCount = unresolvedConflicts.size,
            canContinue = pendingCount == 0 && unresolvedConflicts.isEmpty(),
        )
        return OmbraReviewProjectionResult.Ready(
            OmbraReviewProjectionSession(
                hiddenModel = OmbraHiddenReviewModel(hiddenSegments, candidates, summary),
                sourceMapping = orderedOccurrences.associate { it.id to it.surface },
            ),
        )
    }

    private fun validate(
        segments: List<DocumentSegment>,
        definitions: List<PiiDefinition>,
        reviewOccurrences: List<ReviewOccurrence>,
        definitionById: Map<PiiTypeId, PiiDefinition>,
        segmentById: Map<SegmentId, DocumentSegment>,
    ): OmbraReviewProjectionFailureCode? = when {
        segmentById.size != segments.size -> OmbraReviewProjectionFailureCode.DUPLICATE_SEGMENT

        definitionById.size != definitions.size -> OmbraReviewProjectionFailureCode.DUPLICATE_DEFINITION

        reviewOccurrences.map(ReviewOccurrence::id).distinct().size != reviewOccurrences.size ->
            OmbraReviewProjectionFailureCode.DUPLICATE_OCCURRENCE

        reviewOccurrences.any { it.id.typeId !in definitionById } ->
            OmbraReviewProjectionFailureCode.MISSING_DEFINITION

        reviewOccurrences.any { it.id.source.segmentId !in segmentById } ->
            OmbraReviewProjectionFailureCode.UNKNOWN_SEGMENT

        reviewOccurrences.any { occurrence ->
            val segment = requireNotNull(segmentById[occurrence.id.source.segmentId])
            val range = occurrence.id.source.range
            range.endExclusive > segment.normalizedText.length ||
                segment.normalizedText.substring(range.startInclusive, range.endExclusive) != occurrence.surface
        } -> OmbraReviewProjectionFailureCode.SOURCE_MISMATCH

        else -> null
    }

    private fun placeholders(occurrences: List<ReviewOccurrence>, definitions: List<PiiDefinition>): Map<OccurrenceId, String> {
        val keys = OmbraPlaceholderKeys.fromDefinitions(definitions)
        val counters = mutableMapOf<PiiTypeId, Int>()
        return occurrences.associate { occurrence ->
            val ordinal = counters.getOrDefault(occurrence.id.typeId, 0) + 1
            counters[occurrence.id.typeId] = ordinal
            occurrence.id to "[${keys.getValue(occurrence.id.typeId)}_$ordinal]"
        }
    }

    private fun safeTypeLabel(label: String, sensitiveSurfaces: List<String>): String =
        if (sensitiveSurfaces.any { surface -> label.contains(surface, ignoreCase = true) }) {
            GENERIC_TYPE_LABEL
        } else {
            label
        }

    private fun isHiddenContentSafe(
        segments: List<OmbraHiddenPreviewSegment>,
        candidates: List<OmbraHiddenReviewCandidate>,
        sensitiveSurfaces: List<String>,
    ): Boolean = sensitiveSurfaces.none { surface ->
        segments.any { segment -> segment.text.contains(surface, ignoreCase = true) } ||
            candidates.any { candidate ->
                candidate.typeLabel.contains(surface, ignoreCase = true) ||
                    candidate.placeholder.contains(surface, ignoreCase = true) ||
                    candidate.accessibilityDescription.contains(surface, ignoreCase = true)
            }
    }

    private fun maskSegment(
        segment: DocumentSegment,
        occurrences: List<ReviewOccurrence>,
        placeholders: Map<OccurrenceId, String>,
    ): String {
        val masks = occurrences
            .filter { it.id.source.segmentId == segment.id }
            .map { occurrence -> Mask(occurrence.id.source.range, placeholders.getValue(occurrence.id)) }
            .sortedBy { mask -> mask.range.startInclusive }
            .fold(mutableListOf<Mask>()) { merged, mask ->
                val previous = merged.lastOrNull()
                if (previous == null || !previous.range.overlaps(mask.range)) {
                    merged += mask
                } else {
                    merged[merged.lastIndex] = Mask(
                        range = SourceRange(
                            previous.range.startInclusive,
                            maxOf(previous.range.endExclusive, mask.range.endExclusive),
                        ),
                        replacement = CONFLICT_MASK,
                    )
                }
                merged
            }
        return masks.sortedByDescending { it.range.startInclusive }.fold(segment.normalizedText) { text, mask ->
            text.replaceRange(mask.range.startInclusive, mask.range.endExclusive, mask.replacement)
        }
    }

    private fun conflictPairs(occurrences: List<ReviewOccurrence>): List<ConflictPair> = occurrences
        .groupBy { it.id.source.segmentId }
        .values
        .flatMap(::conflictPairsForSegment)

    private fun conflictPairsForSegment(occurrences: List<ReviewOccurrence>): List<ConflictPair> =
        occurrences.flatMapIndexed { leftIndex, left ->
            occurrences.drop(leftIndex + 1).mapNotNull { right ->
                if (left.id.source.range.overlaps(right.id.source.range)) ConflictPair(left, right) else null
            }
        }

    private fun isResolved(pair: ConflictPair): Boolean = pair.left.decision != ReviewDecisionState.PENDING &&
        pair.right.decision != ReviewDecisionState.PENDING &&
        !(
            pair.left.decision == ReviewDecisionState.ACCEPTED &&
                pair.right.decision == ReviewDecisionState.ACCEPTED
            )

    private fun sourceComparator(segmentOrder: Map<SegmentId, Int>): Comparator<ReviewOccurrence> = compareBy<ReviewOccurrence>(
        { segmentOrder.getValue(it.id.source.segmentId) },
        { it.id.source.range.startInclusive },
        { it.id.source.range.endExclusive },
        { it.id.typeId.value },
    )

    private data class Mask(val range: SourceRange, val replacement: String)

    private data class ConflictPair(val left: ReviewOccurrence, val right: ReviewOccurrence) {
        fun contains(occurrenceId: OccurrenceId): Boolean = left.id == occurrenceId || right.id == occurrenceId
    }

    private const val CONFLICT_MASK = "[CONFLITTO]"
    private const val GENERIC_TYPE_LABEL = "Dato sensibile"
}

private fun buildAccessibilityDescription(
    typeLabel: String,
    position: Int,
    total: Int,
    decision: ReviewDecisionState,
    conflictState: OmbraReviewConflictState,
    revealed: Boolean,
): String = buildString {
    append("$typeLabel, occorrenza $position di $total, ")
    append(if (revealed) "rivelata" else "nascosta")
    append(", decisione ${decision.name.lowercase()}")
    when (conflictState) {
        OmbraReviewConflictState.NONE -> Unit
        OmbraReviewConflictState.RESOLVED -> append(", conflitto risolto")
        OmbraReviewConflictState.REQUIRES_DECISION -> append(", conflitto da risolvere")
    }
}
