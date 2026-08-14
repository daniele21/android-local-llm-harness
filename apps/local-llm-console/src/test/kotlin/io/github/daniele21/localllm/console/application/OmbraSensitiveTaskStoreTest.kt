package io.github.daniele21.localllm.console.application

import io.github.daniele21.localllm.console.analysis.ValidatedFinding
import io.github.daniele21.localllm.console.document.DocumentDescriptor
import io.github.daniele21.localllm.console.document.DocumentSegment
import io.github.daniele21.localllm.console.document.SegmentId
import io.github.daniele21.localllm.console.document.SourceOccurrence
import io.github.daniele21.localllm.console.document.SourceRange
import io.github.daniele21.localllm.console.pii.OmbraBuiltInPiiDefinitions
import io.github.daniele21.localllm.console.pii.PiiTypeId
import io.github.daniele21.localllm.console.redaction.ReviewDecisionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OmbraSensitiveTaskStoreTest {
    @Test
    fun storesSensitiveTaskInMemoryWithoutExposingContentInDebugOutput() {
        val store = InMemoryOmbraSensitiveTaskStore()
        val secret = "mario.rossi@example.it"
        val filename = "contratto-mario-rossi.pdf"
        val text = "Email: $secret"
        val segment = DocumentSegment(
            id = SegmentId.fromIndices(pageIndex = 0, blockIndex = 0),
            pageIndex = 0,
            blockIndex = 0,
            normalizedText = text,
        )
        store.replaceDocument(
            OmbraExtractedDocument(
                descriptor = DocumentDescriptor(displayName = filename, pageCount = 1),
                segments = listOf(segment),
            ),
        )

        val emailDefinition = OmbraBuiltInPiiDefinitions.all.single { it.id == PiiTypeId.parse("email") }
        store.replaceDefinitions(listOf(emailDefinition))
        val start = text.indexOf(secret)
        val occurrence = SourceOccurrence(segment.id, SourceRange(start, start + secret.length))
        store.replaceFindings(
            listOf(
                ValidatedFinding(
                    typeId = emailDefinition.id,
                    surface = secret,
                    occurrences = listOf(occurrence),
                ),
            ),
        )

        val pending = store.snapshot()
        assertEquals(1, pending.reviewOccurrences.size)
        assertEquals(ReviewDecisionState.PENDING, pending.reviewOccurrences.single().decision)
        assertFalse(pending.toString().contains(secret))
        assertFalse(pending.toString().contains(filename))

        val id = pending.reviewOccurrences.single().id
        assertTrue(store.updateDecision(id, ReviewDecisionState.ACCEPTED))
        assertEquals(ReviewDecisionState.ACCEPTED, store.snapshot().reviewOccurrences.single().decision)

        store.clear()
        val cleared = store.snapshot()
        assertNull(cleared.descriptor)
        assertTrue(cleared.segments.isEmpty())
        assertTrue(cleared.definitions.isEmpty())
        assertTrue(cleared.findings.isEmpty())
        assertTrue(cleared.reviewOccurrences.isEmpty())
    }

    @Test
    fun rejectsFindingsOutsideActiveDefinitionSet() {
        val store = InMemoryOmbraSensitiveTaskStore()
        val text = "Mario Rossi"
        val segment = DocumentSegment(
            id = SegmentId.fromIndices(pageIndex = 0, blockIndex = 0),
            pageIndex = 0,
            blockIndex = 0,
            normalizedText = text,
        )
        store.replaceDocument(
            OmbraExtractedDocument(
                descriptor = DocumentDescriptor(displayName = "document.pdf", pageCount = 1),
                segments = listOf(segment),
            ),
        )
        val emailDefinition = OmbraBuiltInPiiDefinitions.all.single { it.id == PiiTypeId.parse("email") }
        store.replaceDefinitions(listOf(emailDefinition))

        val invalid = runCatching {
            store.replaceFindings(
                listOf(
                    ValidatedFinding(
                        typeId = PiiTypeId.parse("full-name"),
                        surface = text,
                        occurrences =
                        listOf(
                            SourceOccurrence(
                                segment.id,
                                SourceRange(startInclusive = 0, endExclusive = text.length),
                            ),
                        ),
                    ),
                ),
            )
        }

        assertTrue(invalid.isFailure)
        assertTrue(store.snapshot().findings.isEmpty())
    }
}
