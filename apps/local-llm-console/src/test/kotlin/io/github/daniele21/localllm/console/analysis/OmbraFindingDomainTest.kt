package io.github.daniele21.localllm.console.analysis

import io.github.daniele21.localllm.console.document.SegmentId
import io.github.daniele21.localllm.console.document.SourceOccurrence
import io.github.daniele21.localllm.console.document.SourceRange
import io.github.daniele21.localllm.console.pii.PiiTypeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OmbraFindingDomainTest {
    @Test
    fun validatedFindingRequiresExactReviewableContent() {
        val occurrence = SourceOccurrence(
            segmentId = SegmentId.fromIndices(pageIndex = 0, blockIndex = 0),
            range = SourceRange(startInclusive = 10, endExclusive = 33),
        )
        val finding = ValidatedFinding(
            typeId = PiiTypeId.parse("email"),
            surface = "mario.rossi@example.it",
            occurrences = listOf(occurrence),
        )

        assertEquals("email", finding.typeId.value)
        assertEquals(listOf(occurrence), finding.occurrences)
        assertTrue(runCatching { finding.copy(surface = " ") }.isFailure)
        assertTrue(runCatching { finding.copy(occurrences = emptyList()) }.isFailure)
    }

    @Test
    fun sourceRangesUseHalfOpenOverlapSemantics() {
        val first = SourceRange(startInclusive = 0, endExclusive = 5)
        val touching = SourceRange(startInclusive = 5, endExclusive = 8)
        val overlapping = SourceRange(startInclusive = 4, endExclusive = 8)

        assertEquals(5, first.length)
        assertTrue(!first.overlaps(touching))
        assertTrue(first.overlaps(overlapping))
    }

    @Test
    fun findingDebugStringDoesNotExposeDetectedSurface() {
        val secret = "mario.rossi@example.it"
        val finding = ValidatedFinding(
            typeId = PiiTypeId.parse("email"),
            surface = secret,
            occurrences = listOf(
                SourceOccurrence(
                    segmentId = SegmentId.fromIndices(pageIndex = 0, blockIndex = 0),
                    range = SourceRange(startInclusive = 10, endExclusive = 33),
                ),
            ),
        )

        assertFalse(finding.toString().contains(secret))
    }
}
