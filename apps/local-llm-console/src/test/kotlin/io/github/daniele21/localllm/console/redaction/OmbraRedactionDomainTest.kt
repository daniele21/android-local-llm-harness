package io.github.daniele21.localllm.console.redaction

import io.github.daniele21.localllm.console.document.SegmentId
import io.github.daniele21.localllm.console.document.SourceOccurrence
import io.github.daniele21.localllm.console.document.SourceRange
import io.github.daniele21.localllm.console.pii.PiiTypeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OmbraRedactionDomainTest {
    @Test
    fun reviewDecisionTransitionsRemainLocalAndReversible() {
        val id = OccurrenceId(
            typeId = PiiTypeId.parse("email"),
            source = SourceOccurrence(
                segmentId = SegmentId.fromIndices(pageIndex = 0, blockIndex = 0),
                range = SourceRange(startInclusive = 9, endExclusive = 32),
            ),
        )
        val pending = ReviewOccurrence(id = id, surface = "mario.rossi@example.it")

        assertEquals(ReviewDecisionState.PENDING, pending.decision)
        assertEquals(ReviewDecisionState.ACCEPTED, pending.accept().decision)
        assertEquals(ReviewDecisionState.IGNORED, pending.accept().ignore().decision)
        assertEquals(ReviewDecisionState.PENDING, pending.ignore().resetDecision().decision)
    }

    @Test
    fun occurrenceIdentityContainsNoOriginalSurface() {
        val id = OccurrenceId(
            typeId = PiiTypeId.parse("full-name"),
            source = SourceOccurrence(
                segmentId = SegmentId.fromIndices(pageIndex = 1, blockIndex = 2),
                range = SourceRange(startInclusive = 4, endExclusive = 15),
            ),
        )

        val debugIdentity = id.toString()
        assertFalse(debugIdentity.contains("Mario Rossi"))
    }

    @Test
    fun reviewOccurrenceDebugStringDoesNotExposeDetectedSurface() {
        val secret = "mario.rossi@example.it"
        val occurrence = ReviewOccurrence(
            id = OccurrenceId(
                typeId = PiiTypeId.parse("email"),
                source = SourceOccurrence(
                    segmentId = SegmentId.fromIndices(pageIndex = 0, blockIndex = 0),
                    range = SourceRange(startInclusive = 9, endExclusive = 32),
                ),
            ),
            surface = secret,
        )

        assertFalse(occurrence.toString().contains(secret))
    }
}
