package io.github.daniele21.localllm.console.redaction

import io.github.daniele21.localllm.console.document.DocumentSegment
import io.github.daniele21.localllm.console.document.SegmentId
import io.github.daniele21.localllm.console.pii.PiiDefinition
import io.github.daniele21.localllm.console.pii.PiiTypeId
import java.text.Normalizer
import java.util.Locale

internal enum class OmbraRedactionPlanFailureCode {
    PENDING_DECISION,
    UNKNOWN_SEGMENT,
    MISSING_DEFINITION,
    SOURCE_MISMATCH,
    DUPLICATE_OCCURRENCE,
    OVERLAP_CONFLICT,
}

internal sealed interface OmbraRedactionPlanResult {
    data class Ready(val plan: OmbraRedactionPlan) : OmbraRedactionPlanResult

    data class Blocked(val code: OmbraRedactionPlanFailureCode, val conflictCount: Int = 0) : OmbraRedactionPlanResult
}

internal data class OmbraRenderedSegment(val segmentId: SegmentId, val text: String) {
    override fun toString(): String = "OmbraRenderedSegment(segmentId=$segmentId, text=<redacted>)"
}

internal data class OmbraRedactionReplacement(val occurrenceId: OccurrenceId, val sourceSurface: String, val placeholder: String) {
    override fun toString(): String =
        "OmbraRedactionReplacement(occurrenceId=$occurrenceId, sourceSurface=<redacted>, placeholder=$placeholder)"
}

internal data class OmbraRedactionPlan(
    val renderedSegments: List<OmbraRenderedSegment>,
    val replacements: List<OmbraRedactionReplacement>,
    val acceptedCount: Int,
    val ignoredCount: Int,
) {
    init {
        require(acceptedCount == replacements.size) { "Accepted count must match replacement count" }
        require(ignoredCount >= 0) { "Ignored count must be non-negative" }
    }
}

/** Pure deterministic OMB-5A planner. Android destinations and PDF writing are intentionally outside this boundary. */
internal object OmbraRedactionPlanner {
    fun build(
        segments: List<DocumentSegment>,
        definitions: List<PiiDefinition>,
        reviewOccurrences: List<ReviewOccurrence>,
    ): OmbraRedactionPlanResult {
        val definitionById = definitions.associateBy(PiiDefinition::id)
        val segmentById = segments.associateBy(DocumentSegment::id)
        val segmentOrder = segments.withIndex().associate { it.value.id to it.index }
        val inputFailure = validateInputs(reviewOccurrences, definitionById, segmentById)
        if (inputFailure != null) return OmbraRedactionPlanResult.Blocked(inputFailure)

        val accepted =
            reviewOccurrences
                .filter { it.decision == ReviewDecisionState.ACCEPTED }
                .sortedWith(sourceComparator(segmentOrder))
        val conflicts = countAcceptedOverlapConflicts(accepted)
        if (conflicts > 0) {
            return OmbraRedactionPlanResult.Blocked(
                code = OmbraRedactionPlanFailureCode.OVERLAP_CONFLICT,
                conflictCount = conflicts,
            )
        }

        val placeholderKeys = OmbraPlaceholderKeys.fromDefinitions(definitions)
        val counters = mutableMapOf<PiiTypeId, Int>()
        val replacements =
            accepted.map { occurrence ->
                val number = counters.getOrDefault(occurrence.id.typeId, 0) + 1
                counters[occurrence.id.typeId] = number
                OmbraRedactionReplacement(
                    occurrenceId = occurrence.id,
                    sourceSurface = occurrence.surface,
                    placeholder = "[${placeholderKeys.getValue(occurrence.id.typeId)}_$number]",
                )
            }

        val replacementsBySegment = replacements.groupBy { it.occurrenceId.source.segmentId }
        val rendered =
            segments.map { segment ->
                val transformed =
                    replacementsBySegment[segment.id]
                        .orEmpty()
                        .sortedByDescending { it.occurrenceId.source.range.startInclusive }
                        .fold(segment.normalizedText) { current, replacement ->
                            val range = replacement.occurrenceId.source.range
                            current.replaceRange(range.startInclusive, range.endExclusive, replacement.placeholder)
                        }
                OmbraRenderedSegment(segment.id, transformed)
            }

        return OmbraRedactionPlanResult.Ready(
            OmbraRedactionPlan(
                renderedSegments = rendered,
                replacements = replacements,
                acceptedCount = accepted.size,
                ignoredCount = reviewOccurrences.count { it.decision == ReviewDecisionState.IGNORED },
            ),
        )
    }

    private fun validateInputs(
        reviewOccurrences: List<ReviewOccurrence>,
        definitionById: Map<PiiTypeId, PiiDefinition>,
        segmentById: Map<SegmentId, DocumentSegment>,
    ): OmbraRedactionPlanFailureCode? = when {
        reviewOccurrences.any { it.decision == ReviewDecisionState.PENDING } ->
            OmbraRedactionPlanFailureCode.PENDING_DECISION

        reviewOccurrences.map(ReviewOccurrence::id).distinct().size != reviewOccurrences.size ->
            OmbraRedactionPlanFailureCode.DUPLICATE_OCCURRENCE

        reviewOccurrences.any { it.id.typeId !in definitionById } ->
            OmbraRedactionPlanFailureCode.MISSING_DEFINITION

        reviewOccurrences.any { it.id.source.segmentId !in segmentById } ->
            OmbraRedactionPlanFailureCode.UNKNOWN_SEGMENT

        reviewOccurrences.any { occurrence ->
            !matchesSource(occurrence, requireNotNull(segmentById[occurrence.id.source.segmentId]))
        } -> OmbraRedactionPlanFailureCode.SOURCE_MISMATCH

        else -> null
    }

    private fun matchesSource(occurrence: ReviewOccurrence, segment: DocumentSegment): Boolean {
        val range = occurrence.id.source.range
        if (range.endExclusive > segment.normalizedText.length) return false
        return segment.normalizedText.substring(range.startInclusive, range.endExclusive) == occurrence.surface
    }

    private fun countAcceptedOverlapConflicts(accepted: List<ReviewOccurrence>): Int =
        accepted.groupBy { it.id.source.segmentId }.values.sumOf(::countGroupOverlapConflicts)

    private fun countGroupOverlapConflicts(group: List<ReviewOccurrence>): Int {
        var conflicts = 0
        for (leftIndex in group.indices) {
            conflicts += countLaterOverlaps(group, leftIndex)
        }
        return conflicts
    }

    private fun countLaterOverlaps(group: List<ReviewOccurrence>, leftIndex: Int): Int {
        var conflicts = 0
        for (rightIndex in leftIndex + 1 until group.size) {
            if (group[leftIndex].id.source.range.overlaps(group[rightIndex].id.source.range)) conflicts += 1
        }
        return conflicts
    }

    private fun sourceComparator(segmentOrder: Map<SegmentId, Int>): Comparator<ReviewOccurrence> = compareBy<ReviewOccurrence>(
        { segmentOrder.getValue(it.id.source.segmentId) },
        { it.id.source.range.startInclusive },
        { it.id.source.range.endExclusive },
        { it.id.typeId.value },
    )
}

internal object OmbraPlaceholderKeys {
    private const val MAX_KEY_LENGTH = 32

    fun fromDefinitions(definitions: List<PiiDefinition>): Map<PiiTypeId, String> {
        val baseKeys = definitions.associate { it.id to sanitize(it.label) }
        val result = mutableMapOf<PiiTypeId, String>()
        baseKeys.entries
            .groupBy(Map.Entry<PiiTypeId, String>::value)
            .toSortedMap()
            .forEach { (baseKey, entries) ->
                entries.sortedBy { it.key.value }.forEachIndexed { index, entry ->
                    result[entry.key] = if (index == 0) baseKey else withCollisionSuffix(baseKey, index + 1)
                }
            }
        return result
    }

    private fun sanitize(label: String): String {
        val decomposed = Normalizer.normalize(label, Normalizer.Form.NFD)
        val ascii =
            buildString {
                decomposed.forEach { character ->
                    when {
                        character.code in 'A'.code..'Z'.code ||
                            character.code in 'a'.code..'z'.code ||
                            character.isDigit() ->
                            append(character.uppercaseChar())

                        Character.getType(character) == Character.NON_SPACING_MARK.toInt() -> Unit

                        else -> append('_')
                    }
                }
            }
        val collapsed = ascii.replace(Regex("_+"), "_").trim('_')
        return bounded(collapsed.ifEmpty { "PII" }.uppercase(Locale.ROOT))
    }

    private fun withCollisionSuffix(baseKey: String, ordinal: Int): String {
        val suffix = "_$ordinal"
        val prefix = baseKey.take((MAX_KEY_LENGTH - suffix.length).coerceAtLeast(1)).trimEnd('_')
        return "$prefix$suffix"
    }

    private fun bounded(value: String): String = value.take(MAX_KEY_LENGTH).trimEnd('_').ifEmpty { "PII" }
}
