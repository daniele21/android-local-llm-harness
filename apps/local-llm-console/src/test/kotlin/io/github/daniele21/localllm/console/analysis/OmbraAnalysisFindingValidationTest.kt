package io.github.daniele21.localllm.console.analysis

import io.github.daniele21.localllm.console.document.DocumentSegment
import io.github.daniele21.localllm.console.document.SegmentId
import io.github.daniele21.localllm.console.document.SourceOccurrence
import io.github.daniele21.localllm.console.document.SourceRange
import io.github.daniele21.localllm.console.pii.PiiDefinition
import io.github.daniele21.localllm.console.pii.PiiDefinitionSource
import io.github.daniele21.localllm.console.pii.PiiTypeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OmbraAnalysisFindingValidationTest {
    private val email = definition("email")
    private val name = definition("full-name")

    @Test
    fun `exact surface produces every non-overlapping source occurrence`() {
        val source = segment("alice@example.test and alice@example.test")
        val chunk = chunk("p0001-b0001", source.normalizedText)
        val index = OmbraAnalysisSourceIndex.build(listOf(chunk), listOf(source))
        val parsed =
            OmbraParsedAnalysisResult(
                listOf(OmbraRawFinding("email", "alice@example.test", "p0001-b0001")),
            )

        val validation = OmbraAnalysisFindingValidator.validate(parsed, chunk, listOf(email), index)

        assertTrue(validation.isComplete)
        assertEquals(
            listOf(SourceRange(0, 18), SourceRange(23, 41)),
            validation.findings.single().occurrences.map(SourceOccurrence::range),
        )
    }

    @Test
    fun `invented surface and unselected type are counted without entering findings`() {
        val source = segment("Alice lives here")
        val chunk = chunk("p0001-b0001", source.normalizedText)
        val index = OmbraAnalysisSourceIndex.build(listOf(chunk), listOf(source))
        val parsed =
            OmbraParsedAnalysisResult(
                listOf(
                    OmbraRawFinding("email", "invented@example.test", "p0001-b0001"),
                    OmbraRawFinding("full-name", "Alice", "p0001-b0001"),
                ),
            )

        val validation = OmbraAnalysisFindingValidator.validate(parsed, chunk, listOf(email), index)

        assertFalse(validation.isComplete)
        assertTrue(validation.findings.isEmpty())
        assertEquals(2, validation.invalidFindingCount)
        assertEquals(1, validation.issueCounts[OmbraFindingValidationIssue.SOURCE_SURFACE_NOT_FOUND])
        assertEquals(1, validation.issueCounts[OmbraFindingValidationIssue.UNSELECTED_TYPE])
    }

    @Test
    fun `fragment occurrence maps back to original source offset`() {
        val source = segment("abcdef")
        val first = chunk("p0001-b0001-f0001", "abc", ordinal = 0)
        val second = chunk("p0001-b0001-f0002", "def", ordinal = 1)
        val index = OmbraAnalysisSourceIndex.build(listOf(first, second), listOf(source))
        val parsed =
            OmbraParsedAnalysisResult(
                listOf(OmbraRawFinding("email", "de", "p0001-b0001-f0002")),
            )

        val validation = OmbraAnalysisFindingValidator.validate(parsed, second, listOf(email), index)

        assertEquals(SourceRange(3, 5), validation.findings.single().occurrences.single().range)
        assertEquals(source.id, validation.findings.single().occurrences.single().segmentId)
    }

    @Test
    fun `result cannot reference segment submitted in another chunk`() {
        val firstSource = segment("Alice", blockIndex = 0)
        val secondSource = segment("Bob", blockIndex = 1)
        val first = chunk("p0001-b0001", "Alice", ordinal = 0)
        val second = chunk("p0001-b0002", "Bob", ordinal = 1)
        val index = OmbraAnalysisSourceIndex.build(listOf(first, second), listOf(firstSource, secondSource))
        val parsed =
            OmbraParsedAnalysisResult(
                listOf(OmbraRawFinding("full-name", "Alice", "p0001-b0001")),
            )

        val validation = OmbraAnalysisFindingValidator.validate(parsed, second, listOf(name), index)

        assertTrue(validation.findings.isEmpty())
        assertEquals(1, validation.issueCounts[OmbraFindingValidationIssue.UNKNOWN_SUBMITTED_SEGMENT])
    }

    @Test
    fun `merger deduplicates exact occurrences and preserves overlap conflict`() {
        val sourceId = SegmentId.fromIndices(0, 0)
        val exact = SourceOccurrence(sourceId, SourceRange(0, 5))
        val overlap = SourceOccurrence(sourceId, SourceRange(3, 8))
        val first =
            OmbraChunkFindingValidation(
                findings = listOf(ValidatedFinding(email.id, "Alice", listOf(exact))),
                invalidFindingCount = 0,
                issueCounts = emptyMap(),
            )
        val second =
            OmbraChunkFindingValidation(
                findings =
                listOf(
                    ValidatedFinding(email.id, "Alice", listOf(exact)),
                    ValidatedFinding(name.id, "Alice X", listOf(overlap)),
                ),
                invalidFindingCount = 1,
                issueCounts = mapOf(OmbraFindingValidationIssue.SOURCE_SURFACE_NOT_FOUND to 1),
            )

        val merged = OmbraAnalysisFindingMerger.merge(listOf(first, second))

        assertEquals(2, merged.findings.size)
        assertEquals(1, merged.findings.single { it.typeId == email.id }.occurrences.size)
        assertEquals(1, merged.conflicts.size)
        assertEquals(1, merged.invalidFindingCount)
        assertFalse(merged.isComplete)
    }

    private fun definition(id: String): PiiDefinition =
        PiiDefinition(
            id = PiiTypeId.parse(id),
            label = id,
            definition = "Synthetic test definition",
            source = PiiDefinitionSource.BUILT_IN,
        )

    private fun segment(text: String, blockIndex: Int = 0): DocumentSegment =
        DocumentSegment(
            id = SegmentId.fromIndices(0, blockIndex),
            pageIndex = 0,
            blockIndex = blockIndex,
            normalizedText = text,
        )

    private fun chunk(segmentId: String, text: String, ordinal: Int = 0): OmbraAnalysisChunk =
        OmbraAnalysisChunk(
            ordinal = ordinal,
            segments = listOf(OmbraAnalysisSegmentData(segmentId, text)),
            dataPayload = "{}",
        )
}
