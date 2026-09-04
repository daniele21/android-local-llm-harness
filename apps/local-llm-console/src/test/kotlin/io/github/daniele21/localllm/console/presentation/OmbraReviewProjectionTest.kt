package io.github.daniele21.localllm.console.presentation

import io.github.daniele21.localllm.console.document.DocumentSegment
import io.github.daniele21.localllm.console.document.SegmentId
import io.github.daniele21.localllm.console.document.SourceOccurrence
import io.github.daniele21.localllm.console.document.SourceRange
import io.github.daniele21.localllm.console.pii.PiiDefinition
import io.github.daniele21.localllm.console.pii.PiiDefinitionSource
import io.github.daniele21.localllm.console.pii.PiiTypeId
import io.github.daniele21.localllm.console.redaction.OccurrenceId
import io.github.daniele21.localllm.console.redaction.ReviewDecisionState
import io.github.daniele21.localllm.console.redaction.ReviewOccurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OmbraReviewProjectionTest {
    @Test
    fun `hidden projection and semantics contain no candidate surfaces`() {
        val text = "Contatta alice@example.test oppure 3331234567."
        val segment = segment(text)
        val email = definition("email", "Email")
        val phone = definition("phone", "Telefono")
        val occurrences = listOf(
            occurrence(segment, email.id, text, "alice@example.test", ReviewDecisionState.PENDING),
            occurrence(segment, phone.id, text, "3331234567", ReviewDecisionState.IGNORED),
        )

        val session = readySession(listOf(segment), listOf(email, phone), occurrences)
        val hiddenPresentation = session.present() as OmbraReviewPresentationResult.Ready
        val hidden = hiddenPresentation.model.hidden

        assertEquals("Contatta [EMAIL_1] oppure [TELEFONO_1].", hidden.segments.single().text)
        assertNull(hiddenPresentation.model.revealedCandidate)
        assertFalse(hidden.toString().contains("alice@example.test"))
        assertFalse(hidden.toString().contains("3331234567"))
        assertTrue(hidden.candidates.all { candidate -> "nascosta" in candidate.accessibilityDescription })
        assertTrue(
            hidden.candidates.all { candidate ->
                occurrences.none { occurrence -> occurrence.surface in candidate.accessibilityDescription }
            },
        )
        assertFalse(session.toString().contains("alice@example.test"))
    }

    @Test
    fun `explicit reveal exposes only selected candidate and is not retained`() {
        val text = "alice@example.test e bob@example.test"
        val segment = segment(text)
        val email = definition("email", "Email")
        val alice = occurrence(segment, email.id, text, "alice@example.test", ReviewDecisionState.ACCEPTED)
        val bob = occurrence(segment, email.id, text, "bob@example.test", ReviewDecisionState.IGNORED)
        val session = readySession(listOf(segment), listOf(email), listOf(alice, bob))

        val revealed = (session.present(alice.id) as OmbraReviewPresentationResult.Ready).model

        assertEquals("alice@example.test", revealed.revealedCandidate?.surface)
        assertTrue(revealed.revealedCandidate?.accessibilityDescription?.contains("rivelata") == true)
        assertTrue(revealed.revealedCandidate?.accessibilityDescription?.contains("alice@example.test") == true)
        assertFalse(revealed.toString().contains("alice@example.test"))
        assertNull((session.present() as OmbraReviewPresentationResult.Ready).model.revealedCandidate)

        val unknownId = OccurrenceId(
            PiiTypeId.parse("phone"),
            SourceOccurrence(segment.id, SourceRange(0, 1)),
        )
        assertEquals(
            OmbraReviewPresentationResult.Blocked(OmbraReviewProjectionFailureCode.UNKNOWN_REVEAL_OCCURRENCE),
            session.present(unknownId),
        )

        session.clearSensitiveMapping()
        assertEquals(
            OmbraReviewPresentationResult.Blocked(OmbraReviewProjectionFailureCode.REVEAL_MAPPING_CLEARED),
            session.present(alice.id),
        )
        assertTrue(session.present() is OmbraReviewPresentationResult.Ready)
    }

    @Test
    fun `pending and overlapping accepted decisions remain fail closed`() {
        val text = "ABCD"
        val segment = segment(text)
        val firstType = definition("first", "Primo")
        val secondType = definition("second", "Secondo")
        val pending = explicitOccurrence(
            segment,
            firstType.id,
            "ABC",
            start = 0,
            end = 3,
            decision = ReviewDecisionState.PENDING,
        )
        val accepted = explicitOccurrence(
            segment,
            secondType.id,
            "BCD",
            start = 1,
            end = 4,
            decision = ReviewDecisionState.ACCEPTED,
        )

        val pendingModel = readySession(
            listOf(segment),
            listOf(firstType, secondType),
            listOf(pending, accepted),
        ).hiddenModel
        val acceptedConflictModel = readySession(
            listOf(segment),
            listOf(firstType, secondType),
            listOf(pending.copy(decision = ReviewDecisionState.ACCEPTED), accepted),
        ).hiddenModel

        assertEquals("[CONFLITTO]", pendingModel.segments.single().text)
        assertEquals(1, pendingModel.summary.pendingCount)
        assertEquals(1, pendingModel.summary.unresolvedConflictCount)
        assertFalse(pendingModel.summary.canContinue)
        assertEquals(1, acceptedConflictModel.summary.unresolvedConflictCount)
        assertFalse(acceptedConflictModel.summary.canContinue)
        assertTrue(
            acceptedConflictModel.candidates.all { candidate ->
                candidate.conflictState == OmbraReviewConflictState.REQUIRES_DECISION
            },
        )
    }

    @Test
    fun `ignored side resolves overlap and complete decisions allow continuation`() {
        val text = "ABCD"
        val segment = segment(text)
        val firstType = definition("first", "Primo")
        val secondType = definition("second", "Secondo")
        val first = explicitOccurrence(segment, firstType.id, "ABC", 0, 3, ReviewDecisionState.ACCEPTED)
        val second = explicitOccurrence(segment, secondType.id, "BCD", 1, 4, ReviewDecisionState.IGNORED)

        val hidden = readySession(
            listOf(segment),
            listOf(firstType, secondType),
            listOf(first, second),
        ).hiddenModel

        assertEquals(0, hidden.summary.pendingCount)
        assertEquals(0, hidden.summary.unresolvedConflictCount)
        assertTrue(hidden.summary.canContinue)
        assertTrue(hidden.candidates.all { it.conflictState == OmbraReviewConflictState.RESOLVED })
    }

    @Test
    fun `projection order and placeholders are deterministic`() {
        val firstSegment = segment("Second alice@example.test", pageIndex = 1)
        val secondSegment = segment("First bob@example.test", pageIndex = 0)
        val email = definition("email", "Email")
        val alice = occurrence(
            firstSegment,
            email.id,
            firstSegment.normalizedText,
            "alice@example.test",
            ReviewDecisionState.IGNORED,
        )
        val bob = occurrence(
            secondSegment,
            email.id,
            secondSegment.normalizedText,
            "bob@example.test",
            ReviewDecisionState.ACCEPTED,
        )

        val hidden = readySession(
            segments = listOf(secondSegment, firstSegment),
            definitions = listOf(email),
            occurrences = listOf(alice, bob),
        ).hiddenModel

        assertEquals(listOf(bob.id, alice.id), hidden.candidates.map { it.occurrenceId })
        assertEquals(listOf("[EMAIL_1]", "[EMAIL_2]"), hidden.candidates.map { it.placeholder })
        assertEquals("First [EMAIL_1]", hidden.segments[0].text)
        assertEquals("Second [EMAIL_2]", hidden.segments[1].text)
    }

    @Test
    fun `invalid source mapping is rejected without creating a reveal session`() {
        val segment = segment("alice@example.test")
        val email = definition("email", "Email")
        val mismatch = explicitOccurrence(
            segment,
            email.id,
            "wrong@example.test",
            0,
            segment.normalizedText.length,
            ReviewDecisionState.ACCEPTED,
        )

        assertEquals(
            OmbraReviewProjectionResult.Blocked(OmbraReviewProjectionFailureCode.SOURCE_MISMATCH),
            OmbraReviewProjector.build(listOf(segment), listOf(email), listOf(mismatch)),
        )
    }

    @Test
    fun `unmapped repetition cannot leak into hidden preview`() {
        val text = "alice@example.test e ancora alice@example.test"
        val segment = segment(text)
        val email = definition("email", "Email")
        val onlyFirst = occurrence(
            segment,
            email.id,
            text,
            "alice@example.test",
            ReviewDecisionState.ACCEPTED,
        )

        assertEquals(
            OmbraReviewProjectionResult.Blocked(OmbraReviewProjectionFailureCode.HIDDEN_CONTENT_NOT_SAFE),
            OmbraReviewProjector.build(listOf(segment), listOf(email), listOf(onlyFirst)),
        )
    }

    private fun readySession(
        segments: List<DocumentSegment>,
        definitions: List<PiiDefinition>,
        occurrences: List<ReviewOccurrence>,
    ): OmbraReviewProjectionSession =
        (OmbraReviewProjector.build(segments, definitions, occurrences) as OmbraReviewProjectionResult.Ready).session

    private fun segment(text: String, pageIndex: Int = 0): DocumentSegment = DocumentSegment(
        id = SegmentId.fromIndices(pageIndex, 0),
        pageIndex = pageIndex,
        blockIndex = 0,
        normalizedText = text,
    )

    private fun definition(id: String, label: String): PiiDefinition = PiiDefinition(
        id = PiiTypeId.parse(id),
        label = label,
        definition = "Synthetic definition for $label",
        source = PiiDefinitionSource.CUSTOM,
    )

    private fun occurrence(
        segment: DocumentSegment,
        typeId: PiiTypeId,
        sourceText: String,
        surface: String,
        decision: ReviewDecisionState,
    ): ReviewOccurrence {
        val start = sourceText.indexOf(surface)
        require(start >= 0)
        return explicitOccurrence(segment, typeId, surface, start, start + surface.length, decision)
    }

    private fun explicitOccurrence(
        segment: DocumentSegment,
        typeId: PiiTypeId,
        surface: String,
        start: Int,
        end: Int,
        decision: ReviewDecisionState,
    ): ReviewOccurrence = ReviewOccurrence(
        id = OccurrenceId(typeId, SourceOccurrence(segment.id, SourceRange(start, end))),
        surface = surface,
        decision = decision,
    )
}
