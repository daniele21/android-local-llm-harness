package io.github.daniele21.localllm.console.document

/** Half-open range inside one normalized document segment. */
internal data class SourceRange(val startInclusive: Int, val endExclusive: Int) {
    init {
        require(startInclusive >= 0) { "startInclusive must be non-negative" }
        require(endExclusive > startInclusive) { "endExclusive must be greater than startInclusive" }
    }

    val length: Int
        get() = endExclusive - startInclusive

    fun overlaps(other: SourceRange): Boolean = startInclusive < other.endExclusive && other.startInclusive < endExclusive
}

/** Exact source location calculated locally after a model surface has been validated. */
internal data class SourceOccurrence(val segmentId: SegmentId, val range: SourceRange)
