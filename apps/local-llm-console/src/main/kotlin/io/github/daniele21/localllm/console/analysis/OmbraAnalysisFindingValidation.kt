package io.github.daniele21.localllm.console.analysis

import io.github.daniele21.localllm.console.document.DocumentSegment
import io.github.daniele21.localllm.console.document.SegmentId
import io.github.daniele21.localllm.console.document.SourceOccurrence
import io.github.daniele21.localllm.console.document.SourceRange
import io.github.daniele21.localllm.console.pii.PiiDefinition
import io.github.daniele21.localllm.console.pii.PiiTypeId

internal data class OmbraAnalysisSourceSlice(
    val submittedSegmentId: String,
    val sourceSegmentId: SegmentId,
    val sourceStartOffset: Int,
    val text: String,
) {
    override fun toString(): String =
        "OmbraAnalysisSourceSlice(submittedSegmentId=$submittedSegmentId, sourceSegmentId=$sourceSegmentId, " +
            "sourceStartOffset=$sourceStartOffset, text=<redacted>)"
}

/** Reconstructs stable fragment-to-source offsets from the deterministic OMB-3A chunk plan. */
internal class OmbraAnalysisSourceIndex private constructor(private val slices: Map<String, OmbraAnalysisSourceSlice>) {
    fun resolve(submittedSegmentId: String): OmbraAnalysisSourceSlice? = slices[submittedSegmentId]

    companion object {
        private val SUBMITTED_ID = Regex("^(p[0-9]{4}-b[0-9]{4})(?:-f([0-9]{4}))?$")

        fun build(chunks: List<OmbraAnalysisChunk>, sourceSegments: List<DocumentSegment>): OmbraAnalysisSourceIndex {
            require(chunks.isNotEmpty()) { "Analysis source index requires chunks" }
            val sourceById = sourceSegments.associateBy(DocumentSegment::id)
            val nextOffset = mutableMapOf<SegmentId, Int>()
            val nextFragmentOrdinal = mutableMapOf<SegmentId, Int>()
            val slices = linkedMapOf<String, OmbraAnalysisSourceSlice>()

            chunks.flatMap(OmbraAnalysisChunk::segments).forEach { submitted ->
                val match = requireNotNull(SUBMITTED_ID.matchEntire(submitted.segmentId)) { "Invalid submitted segment ID" }
                val sourceId = SegmentId.parse(match.groupValues[1])
                val source = requireNotNull(sourceById[sourceId]) { "Submitted segment has no source segment" }
                val fragmentOrdinal = match.groupValues[2].takeIf(String::isNotEmpty)?.toInt()
                val startOffset = calculateStartOffset(sourceId, fragmentOrdinal, submitted.text, nextOffset, nextFragmentOrdinal)
                val endOffset = startOffset + submitted.text.length
                require(endOffset <= source.normalizedText.length) { "Submitted fragment exceeds source segment" }
                require(source.normalizedText.substring(startOffset, endOffset) == submitted.text) {
                    "Submitted fragment does not match normalized source"
                }
                val slice = OmbraAnalysisSourceSlice(submitted.segmentId, sourceId, startOffset, submitted.text)
                require(slices.put(submitted.segmentId, slice) == null) { "Duplicate submitted segment ID" }
            }
            return OmbraAnalysisSourceIndex(slices)
        }

        private fun calculateStartOffset(
            sourceId: SegmentId,
            fragmentOrdinal: Int?,
            submittedText: String,
            nextOffset: MutableMap<SegmentId, Int>,
            nextFragmentOrdinal: MutableMap<SegmentId, Int>,
        ): Int {
            if (fragmentOrdinal == null) {
                require(sourceId !in nextOffset) { "Whole source segment cannot follow fragments" }
                nextOffset[sourceId] = submittedText.length
                return 0
            }
            val expectedOrdinal = nextFragmentOrdinal.getOrDefault(sourceId, 1)
            require(fragmentOrdinal == expectedOrdinal) { "Analysis fragments must be contiguous and ordered" }
            val offset = nextOffset.getOrDefault(sourceId, 0)
            nextOffset[sourceId] = offset + submittedText.length
            nextFragmentOrdinal[sourceId] = expectedOrdinal + 1
            return offset
        }
    }
}

internal enum class OmbraFindingValidationIssue {
    UNSELECTED_TYPE,
    UNKNOWN_SUBMITTED_SEGMENT,
    INVALID_TYPE_ID,
    SOURCE_SURFACE_NOT_FOUND,
}

internal data class OmbraChunkFindingValidation(
    val findings: List<ValidatedFinding>,
    val invalidFindingCount: Int,
    val issueCounts: Map<OmbraFindingValidationIssue, Int>,
) {
    init {
        require(invalidFindingCount >= 0) { "Invalid finding count must be non-negative" }
        require(issueCounts.values.sum() == invalidFindingCount) { "Issue counts must match invalid finding count" }
    }

    val isComplete: Boolean
        get() = invalidFindingCount == 0
}

/** Validates model candidates only against the selected definitions and the exact submitted source slice. */
internal object OmbraAnalysisFindingValidator {
    fun validate(
        result: OmbraParsedAnalysisResult,
        chunk: OmbraAnalysisChunk,
        definitions: List<PiiDefinition>,
        sourceIndex: OmbraAnalysisSourceIndex,
    ): OmbraChunkFindingValidation {
        val selectedTypes = definitions.map(PiiDefinition::id).toSet()
        val submittedIds = chunk.segments.map(OmbraAnalysisSegmentData::segmentId).toSet()
        val findings = mutableListOf<ValidatedFinding>()
        val issueCounts = mutableMapOf<OmbraFindingValidationIssue, Int>()

        result.findings.forEach { raw ->
            when (val validated = validateOne(raw, selectedTypes, submittedIds, sourceIndex)) {
                is CandidateValidation.Valid -> findings += validated.finding
                is CandidateValidation.Invalid -> issueCounts[validated.issue] = issueCounts.getOrDefault(validated.issue, 0) + 1
            }
        }
        return OmbraChunkFindingValidation(
            findings = findings,
            invalidFindingCount = issueCounts.values.sum(),
            issueCounts = issueCounts.toSortedMap(compareBy(OmbraFindingValidationIssue::ordinal)),
        )
    }

    private fun validateOne(
        raw: OmbraRawFinding,
        selectedTypes: Set<PiiTypeId>,
        submittedIds: Set<String>,
        sourceIndex: OmbraAnalysisSourceIndex,
    ): CandidateValidation {
        val typeId = runCatching { PiiTypeId.parse(raw.typeId) }.getOrNull()
        val slice = sourceIndex.resolve(raw.segmentId)
        val issue =
            when {
                typeId == null -> OmbraFindingValidationIssue.INVALID_TYPE_ID
                typeId !in selectedTypes -> OmbraFindingValidationIssue.UNSELECTED_TYPE
                raw.segmentId !in submittedIds || slice == null -> OmbraFindingValidationIssue.UNKNOWN_SUBMITTED_SEGMENT
                else -> null
            }
        if (issue != null) return CandidateValidation.Invalid(issue)

        val occurrences = exactOccurrences(requireNotNull(slice), raw.surface)
        return if (occurrences.isEmpty()) {
            CandidateValidation.Invalid(OmbraFindingValidationIssue.SOURCE_SURFACE_NOT_FOUND)
        } else {
            CandidateValidation.Valid(ValidatedFinding(requireNotNull(typeId), raw.surface, occurrences))
        }
    }

    private fun exactOccurrences(slice: OmbraAnalysisSourceSlice, surface: String): List<SourceOccurrence> {
        if (surface.isEmpty()) return emptyList()
        val occurrences = mutableListOf<SourceOccurrence>()
        var searchFrom = 0
        var localStart = slice.text.indexOf(surface, startIndex = searchFrom)
        while (localStart >= 0) {
            val sourceStart = slice.sourceStartOffset + localStart
            occurrences +=
                SourceOccurrence(
                    segmentId = slice.sourceSegmentId,
                    range = SourceRange(sourceStart, sourceStart + surface.length),
                )
            searchFrom = localStart + surface.length
            localStart = slice.text.indexOf(surface, startIndex = searchFrom)
        }
        return occurrences
    }

    private sealed interface CandidateValidation {
        data class Valid(val finding: ValidatedFinding) : CandidateValidation

        data class Invalid(val issue: OmbraFindingValidationIssue) : CandidateValidation
    }
}

internal data class OmbraFindingConflict(
    val leftTypeId: PiiTypeId,
    val leftOccurrence: SourceOccurrence,
    val rightTypeId: PiiTypeId,
    val rightOccurrence: SourceOccurrence,
) {
    override fun toString(): String = "OmbraFindingConflict(leftTypeId=$leftTypeId, leftOccurrence=$leftOccurrence, " +
        "rightTypeId=$rightTypeId, rightOccurrence=$rightOccurrence)"
}

internal data class OmbraMergedAnalysis(
    val findings: List<ValidatedFinding>,
    val conflicts: List<OmbraFindingConflict>,
    val invalidFindingCount: Int,
) {
    val isComplete: Boolean
        get() = invalidFindingCount == 0
}

/** Deduplicates exact occurrences and records exact/partial overlap conflicts without auto-resolution. */
internal object OmbraAnalysisFindingMerger {
    fun merge(chunks: List<OmbraChunkFindingValidation>): OmbraMergedAnalysis {
        val grouped = linkedMapOf<FindingKey, MutableSet<SourceOccurrence>>()
        chunks.flatMap(OmbraChunkFindingValidation::findings).forEach { finding ->
            grouped.getOrPut(FindingKey(finding.typeId, finding.surface)) { linkedSetOf() }.addAll(finding.occurrences)
        }
        val findings =
            grouped.map { (key, occurrences) ->
                ValidatedFinding(
                    typeId = key.typeId,
                    surface = key.surface,
                    occurrences = occurrences.sortedWith(SOURCE_OCCURRENCE_ORDER),
                )
            }.sortedWith(
                compareBy({
                    it.occurrences.first().segmentId.value
                }, { it.occurrences.first().range.startInclusive }, { it.typeId.value }),
            )
        return OmbraMergedAnalysis(
            findings = findings,
            conflicts = findConflicts(findings),
            invalidFindingCount = chunks.sumOf(OmbraChunkFindingValidation::invalidFindingCount),
        )
    }

    private fun findConflicts(findings: List<ValidatedFinding>): List<OmbraFindingConflict> {
        val typedOccurrences =
            findings.flatMap { finding -> finding.occurrences.map { finding.typeId to it } }
                .sortedWith(
                    compareBy({
                        it.second.segmentId.value
                    }, { it.second.range.startInclusive }, { it.second.range.endExclusive }, { it.first.value }),
                )
        return typedOccurrences.flatMapIndexed { leftIndex, left -> conflictsForLeft(left, typedOccurrences.drop(leftIndex + 1)) }
    }

    private fun conflictsForLeft(
        left: Pair<PiiTypeId, SourceOccurrence>,
        later: List<Pair<PiiTypeId, SourceOccurrence>>,
    ): List<OmbraFindingConflict> =
        later.asSequence()
            .takeWhile { right ->
                right.second.segmentId == left.second.segmentId &&
                    right.second.range.startInclusive < left.second.range.endExclusive
            }.filter { right -> left.first != right.first && left.second.range.overlaps(right.second.range) }
            .map { right -> OmbraFindingConflict(left.first, left.second, right.first, right.second) }
            .toList()

    private data class FindingKey(val typeId: PiiTypeId, val surface: String)

    private val SOURCE_OCCURRENCE_ORDER =
        compareBy<SourceOccurrence>({ it.segmentId.value }, { it.range.startInclusive }, { it.range.endExclusive })
}
