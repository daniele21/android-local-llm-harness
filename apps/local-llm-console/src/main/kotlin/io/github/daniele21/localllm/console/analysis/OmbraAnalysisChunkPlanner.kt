package io.github.daniele21.localllm.console.analysis

import io.github.daniele21.localllm.console.document.DocumentSegment
import io.github.daniele21.localllm.console.pii.PiiDefinition
import io.github.daniele21.localllm.contracts.ConsumerLimits

internal data class OmbraAnalysisPlanningPolicy(
    val templateOverheadCharacters: Int = DEFAULT_TEMPLATE_OVERHEAD_CHARACTERS,
    val maxFragmentsPerSegment: Int = MAX_FRAGMENT_ORDINAL,
) {
    init {
        require(templateOverheadCharacters >= 0) { "Template overhead must be non-negative" }
        require(maxFragmentsPerSegment in 1..MAX_FRAGMENT_ORDINAL) { "Invalid fragment limit" }
    }

    companion object {
        const val DEFAULT_TEMPLATE_OVERHEAD_CHARACTERS = 1_024
        const val MAX_FRAGMENT_ORDINAL = 9_999
    }
}

internal enum class OmbraChunkPlanFailureCode {
    JSON_SCHEMA_LIMIT_EXCEEDED,
    INPUT_OVERHEAD_EXCEEDS_LIMIT,
    FRAGMENT_LIMIT_EXCEEDED,
}

internal sealed interface OmbraChunkPlanResult {
    data class Planned(val chunks: List<OmbraAnalysisChunk>) : OmbraChunkPlanResult {
        init {
            require(chunks.isNotEmpty()) { "A successful plan must contain chunks" }
        }
    }

    data class Rejected(val code: OmbraChunkPlanFailureCode) : OmbraChunkPlanResult
}

/**
 * Greedy, deterministic planner for stateless sequential OMBRA analysis.
 *
 * The host's public character limits are treated as hard ceilings. Definitions are repeated in
 * every chunk and the stable instruction plus expected chat-template overhead are reserved before
 * document text is admitted. A source block stays whole whenever it fits; an oversized block is
 * split only on Unicode code-point boundaries and receives stable `-fNNNN` fragment IDs.
 */
internal class OmbraAnalysisChunkPlanner(private val policy: OmbraAnalysisPlanningPolicy = OmbraAnalysisPlanningPolicy()) {
    fun plan(segments: List<DocumentSegment>, definitions: List<PiiDefinition>, limits: ConsumerLimits): OmbraChunkPlanResult {
        require(segments.isNotEmpty()) { "Chunk planning requires document segments" }
        require(definitions.isNotEmpty()) { "Chunk planning requires PII definitions" }

        if (OmbraAnalysisProtocol.outputJsonSchema.length > limits.maxJsonSchemaCharacters) {
            return OmbraChunkPlanResult.Rejected(OmbraChunkPlanFailureCode.JSON_SCHEMA_LIMIT_EXCEEDED)
        }

        val emptyPayloadLength = minimumPayloadLength(definitions)
        val fixedCharacters =
            OmbraAnalysisProtocol.instruction.length +
                policy.templateOverheadCharacters +
                emptyPayloadLength
        if (fixedCharacters >= limits.maxInputCharacters) {
            return OmbraChunkPlanResult.Rejected(OmbraChunkPlanFailureCode.INPUT_OVERHEAD_EXCEEDS_LIMIT)
        }

        val chunks = mutableListOf<OmbraAnalysisChunk>()
        val pending = ArrayDeque<PendingSegment>()
        segments.forEach { segment -> pending += PendingSegment.whole(segment) }

        while (pending.isNotEmpty()) {
            val chunkSegments = mutableListOf<OmbraAnalysisSegmentData>()
            var continueChunk = true
            while (pending.isNotEmpty() && continueChunk) {
                val candidate = pending.removeFirst()
                val wholeCandidate = candidate.asAnalysisSegment()
                if (fits(definitions, chunkSegments + wholeCandidate, limits)) {
                    chunkSegments += wholeCandidate
                    continue
                }

                if (chunkSegments.isNotEmpty()) {
                    pending.addFirst(candidate)
                    continueChunk = false
                    continue
                }

                val split =
                    largestFittingPrefix(candidate, definitions, limits)
                        ?: return OmbraChunkPlanResult.Rejected(OmbraChunkPlanFailureCode.INPUT_OVERHEAD_EXCEEDS_LIMIT)
                chunkSegments += split.head
                if (split.tail != null) pending.addFirst(split.tail)
            }

            val payload = OmbraAnalysisDataSerializer.serialize(definitions, chunkSegments)
            chunks += OmbraAnalysisChunk(ordinal = chunks.size, segments = chunkSegments, dataPayload = payload)
        }

        return OmbraChunkPlanResult.Planned(chunks)
    }

    private fun largestFittingPrefix(pending: PendingSegment, definitions: List<PiiDefinition>, limits: ConsumerLimits): SplitResult? {
        if (pending.fragmentOrdinal >= policy.maxFragmentsPerSegment) {
            return null
        }

        val totalCodePoints = pending.text.codePointCount(0, pending.text.length)
        var low = 1
        var high = totalCodePoints
        var best: OmbraAnalysisSegmentData? = null
        var bestEndIndex = -1
        while (low <= high) {
            val middle = (low + high) ushr 1
            val endIndex = pending.text.offsetByCodePoints(0, middle)
            val prefix = pending.text.substring(0, endIndex)
            val fragment = pending.fragment(prefix)
            if (fits(definitions, listOf(fragment), limits)) {
                best = fragment
                bestEndIndex = endIndex
                low = middle + 1
            } else {
                high = middle - 1
            }
        }

        val head = best ?: return null
        val remaining = pending.text.substring(bestEndIndex)
        val tail =
            if (remaining.isEmpty()) {
                null
            } else {
                val nextOrdinal = pending.fragmentOrdinal + 1
                if (nextOrdinal > policy.maxFragmentsPerSegment) return null
                pending.copy(text = remaining, fragmentOrdinal = nextOrdinal, forceFragmentId = true)
            }
        return SplitResult(head, tail)
    }

    private fun fits(definitions: List<PiiDefinition>, segments: List<OmbraAnalysisSegmentData>, limits: ConsumerLimits): Boolean {
        val payload = OmbraAnalysisDataSerializer.serialize(definitions, segments)
        return OmbraAnalysisProtocol.instruction.length + policy.templateOverheadCharacters + payload.length <= limits.maxInputCharacters
    }

    private fun minimumPayloadLength(definitions: List<PiiDefinition>): Int {
        val sentinel = OmbraAnalysisSegmentData("p0001-b0001", "x")
        val serialized = OmbraAnalysisDataSerializer.serialize(definitions, listOf(sentinel))
        return serialized.length - 1
    }

    private data class SplitResult(val head: OmbraAnalysisSegmentData, val tail: PendingSegment?)

    private data class PendingSegment(val baseId: String, val text: String, val fragmentOrdinal: Int, val forceFragmentId: Boolean) {
        fun asAnalysisSegment(): OmbraAnalysisSegmentData = OmbraAnalysisSegmentData(
            segmentId = if (forceFragmentId) fragmentId(fragmentOrdinal) else baseId,
            text = text,
        )

        fun fragment(prefix: String): OmbraAnalysisSegmentData =
            OmbraAnalysisSegmentData(segmentId = fragmentId(fragmentOrdinal), text = prefix)

        private fun fragmentId(ordinal: Int): String = "$baseId-f${ordinal.toString().padStart(4, '0')}"

        companion object {
            fun whole(segment: DocumentSegment): PendingSegment = PendingSegment(
                baseId = segment.id.value,
                text = segment.normalizedText,
                fragmentOrdinal = 1,
                forceFragmentId = false,
            )
        }
    }
}
