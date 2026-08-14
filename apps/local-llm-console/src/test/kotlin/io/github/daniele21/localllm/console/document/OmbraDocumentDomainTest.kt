package io.github.daniele21.localllm.console.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OmbraDocumentDomainTest {
    @Test
    fun segmentIdIsStableAndOneBasedInSerializedForm() {
        assertEquals("p0001-b0001", SegmentId.fromIndices(pageIndex = 0, blockIndex = 0).value)
        assertEquals("p0012-b0034", SegmentId.fromIndices(pageIndex = 11, blockIndex = 33).value)
        assertEquals(
            SegmentId.fromIndices(pageIndex = 11, blockIndex = 33),
            SegmentId.parse("p0012-b0034"),
        )
    }

    @Test
    fun documentSegmentRequiresMatchingStableIdentity() {
        val failure =
            runCatching {
                DocumentSegment(
                    id = SegmentId.parse("p0002-b0001"),
                    pageIndex = 0,
                    blockIndex = 0,
                    normalizedText = "Mario Rossi",
                )
            }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun documentSegmentAcceptsNormalizedUnicodeTextAndRejectsUnsafeControls() {
        val segment =
            DocumentSegment(
                id = SegmentId.fromIndices(pageIndex = 0, blockIndex = 1),
                pageIndex = 0,
                blockIndex = 1,
                normalizedText = "Città di Iseo\nJosé Müller\tIBAN",
            )
        assertEquals("Città di Iseo\nJosé Müller\tIBAN", segment.normalizedText)

        val failure =
            runCatching {
                segment.copy(normalizedText = "unsafe\u0000text")
            }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun documentDescriptorRejectsMissingMetadata() {
        assertTrue(
            runCatching { DocumentDescriptor(displayName = " ", pageCount = 1) }.isFailure,
        )
        assertTrue(
            runCatching { DocumentDescriptor(displayName = "document.pdf", pageCount = 0) }.isFailure,
        )
    }

    @Test
    fun sensitiveDocumentValuesAreRedactedFromDebugStrings() {
        val descriptor = DocumentDescriptor(displayName = "Mario-Rossi-referto.pdf", pageCount = 1)
        val segment =
            DocumentSegment(
                id = SegmentId.fromIndices(pageIndex = 0, blockIndex = 0),
                pageIndex = 0,
                blockIndex = 0,
                normalizedText = "Mario Rossi, CF RSSMRA80A01H501U",
            )

        assertFalse(descriptor.toString().contains("Mario-Rossi"))
        assertFalse(segment.toString().contains("Mario Rossi"))
        assertFalse(segment.toString().contains("RSSMRA80A01H501U"))
    }
}
