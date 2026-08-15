package io.github.daniele21.localllm.console.redaction

import io.github.daniele21.localllm.console.document.DocumentSegment
import io.github.daniele21.localllm.console.document.SegmentId
import io.github.daniele21.localllm.console.document.SourceOccurrence
import io.github.daniele21.localllm.console.document.SourceRange
import io.github.daniele21.localllm.console.pii.PiiDefinition
import io.github.daniele21.localllm.console.pii.PiiDefinitionSource
import io.github.daniele21.localllm.console.pii.PiiTypeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OmbraRedactionPlannerTest {
    @Test
    fun `accepted placeholders follow source order while ignored values remain`() {
        val text = "alice@example.test and bob@example.test; keep carol@example.test"
        val segment = segment(text)
        val email = definition("email", "Email")
        val alice = occurrence(segment, email.id, text, "alice@example.test", ReviewDecisionState.ACCEPTED)
        val bob = occurrence(segment, email.id, text, "bob@example.test", ReviewDecisionState.ACCEPTED)
        val carol = occurrence(segment, email.id, text, "carol@example.test", ReviewDecisionState.IGNORED)

        val result = OmbraRedactionPlanner.build(
            segments = listOf(segment),
            definitions = listOf(email),
            reviewOccurrences = listOf(carol, bob, alice),
        )

        val plan = (result as OmbraRedactionPlanResult.Ready).plan
        assertEquals(2, plan.acceptedCount)
        assertEquals(1, plan.ignoredCount)
        assertEquals(listOf("[EMAIL_1]", "[EMAIL_2]"), plan.replacements.map { it.placeholder })
        assertEquals("[EMAIL_1] and [EMAIL_2]; keep carol@example.test", plan.renderedSegments.single().text)
    }

    @Test
    fun `highest offset replacement keeps later source ranges valid`() {
        val text = "AA 1234 BB 5678"
        val segment = segment(text)
        val code = definition("code", "Code")
        val first = occurrence(segment, code.id, text, "1234", ReviewDecisionState.ACCEPTED)
        val second = occurrence(segment, code.id, text, "5678", ReviewDecisionState.ACCEPTED)

        val result = OmbraRedactionPlanner.build(listOf(segment), listOf(code), listOf(first, second))

        val plan = (result as OmbraRedactionPlanResult.Ready).plan
        assertEquals("AA [CODE_1] BB [CODE_2]", plan.renderedSegments.single().text)
    }

    @Test
    fun `accepted overlap blocks export instead of choosing a winner`() {
        val text = "ABCD"
        val segment = segment(text)
        val firstType = definition("first", "First")
        val secondType = definition("second", "Second")
        val first = explicitOccurrence(segment, firstType.id, "ABC", 0, 3, ReviewDecisionState.ACCEPTED)
        val second = explicitOccurrence(segment, secondType.id, "BCD", 1, 4, ReviewDecisionState.ACCEPTED)

        val result = OmbraRedactionPlanner.build(
            listOf(segment),
            listOf(firstType, secondType),
            listOf(first, second),
        )

        assertEquals(
            OmbraRedactionPlanResult.Blocked(OmbraRedactionPlanFailureCode.OVERLAP_CONFLICT, conflictCount = 1),
            result,
        )
    }

    @Test
    fun `ignored overlap does not block accepted replacement`() {
        val text = "ABCD"
        val segment = segment(text)
        val firstType = definition("first", "First")
        val secondType = definition("second", "Second")
        val first = explicitOccurrence(segment, firstType.id, "ABC", 0, 3, ReviewDecisionState.ACCEPTED)
        val second = explicitOccurrence(segment, secondType.id, "BCD", 1, 4, ReviewDecisionState.IGNORED)

        val result = OmbraRedactionPlanner.build(
            listOf(segment),
            listOf(firstType, secondType),
            listOf(first, second),
        )

        val plan = (result as OmbraRedactionPlanResult.Ready).plan
        assertEquals("[FIRST_1]D", plan.renderedSegments.single().text)
        assertEquals(1, plan.ignoredCount)
    }

    @Test
    fun `source mismatch fails closed`() {
        val text = "alice@example.test"
        val segment = segment(text)
        val email = definition("email", "Email")
        val mismatched = explicitOccurrence(
            segment = segment,
            typeId = email.id,
            surface = "wrong@example.test",
            start = 0,
            end = text.length,
            decision = ReviewDecisionState.ACCEPTED,
        )

        assertEquals(
            OmbraRedactionPlanResult.Blocked(OmbraRedactionPlanFailureCode.SOURCE_MISMATCH),
            OmbraRedactionPlanner.build(listOf(segment), listOf(email), listOf(mismatched)),
        )
    }

    @Test
    fun `pending decision blocks plan`() {
        val text = "alice@example.test"
        val segment = segment(text)
        val email = definition("email", "Email")
        val pending = occurrence(segment, email.id, text, text, ReviewDecisionState.PENDING)

        assertEquals(
            OmbraRedactionPlanResult.Blocked(OmbraRedactionPlanFailureCode.PENDING_DECISION),
            OmbraRedactionPlanner.build(listOf(segment), listOf(email), listOf(pending)),
        )
    }

    @Test
    fun `placeholder keys are sanitized bounded and collision safe`() {
        val first = definition("custom-a", "Matrìcola dipendente")
        val second = definition("custom-b", "Matricola dipendente")
        val long = definition("custom-c", "Identificativo dipendente estremamente lungo per il documento")

        val keys = OmbraPlaceholderKeys.fromDefinitions(listOf(second, long, first))

        assertEquals("MATRICOLA_DIPENDENTE", keys.getValue(first.id))
        assertEquals("MATRICOLA_DIPENDENTE_2", keys.getValue(second.id))
        assertTrue(keys.getValue(long.id).length <= 32)
        assertTrue(keys.values.all { it.matches(Regex("[A-Z0-9_]+")) })
    }

    @Test
    fun `zero findings preserves normalized document`() {
        val segment = segment("No personal information in this synthetic fixture.")

        val result = OmbraRedactionPlanner.build(
            segments = listOf(segment),
            definitions = listOf(definition("email", "Email")),
            reviewOccurrences = emptyList(),
        )

        val plan = (result as OmbraRedactionPlanResult.Ready).plan
        assertEquals(segment.normalizedText, plan.renderedSegments.single().text)
        assertEquals(0, plan.acceptedCount)
        assertEquals(0, plan.ignoredCount)
        assertTrue(plan.replacements.isEmpty())
    }

    private fun segment(text: String): DocumentSegment = DocumentSegment(
        id = SegmentId.fromIndices(pageIndex = 0, blockIndex = 0),
        pageIndex = 0,
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
