package io.github.daniele21.localllm.console.presentation

import io.github.daniele21.localllm.console.analysis.ValidatedFinding
import io.github.daniele21.localllm.console.application.OmbraAnalysisClient
import io.github.daniele21.localllm.console.application.OmbraAnalysisRequest
import io.github.daniele21.localllm.console.application.OmbraDocumentExporter
import io.github.daniele21.localllm.console.application.OmbraDocumentExtractor
import io.github.daniele21.localllm.console.application.OmbraExportRequest
import io.github.daniele21.localllm.console.application.OmbraExtractedDocument
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

class OmbraApplicationOrchestratorTest {
    @Test
    fun pureFakeFlowDrivesImportDefinitionsFindingsReviewAndExport() {
        val fixture = Fixture()
        val extractor = ImmediateExtractor(fixture.document)
        val analysis = ImmediateAnalysisClient(fixture.findings)
        val exporter = ImmediateExporter()
        val orchestrator = OmbraApplicationOrchestrator(extractor, analysis, exporter)

        assertTrue(orchestrator.startImport(OmbraDocumentSourceRef(1)))
        assertEquals(OmbraWorkflowStage.DOCUMENT_SELECTED, orchestrator.state.stage)
        assertEquals(1, orchestrator.state.counts.documentPageCount)
        assertEquals(1, orchestrator.state.counts.segmentCount)

        assertTrue(orchestrator.setDefinitions(fixture.definitions))
        assertEquals(OmbraWorkflowStage.DEFINITIONS_READY, orchestrator.state.stage)
        assertEquals(2, orchestrator.state.counts.activeDefinitionCount)

        assertTrue(orchestrator.startAnalysis())
        assertEquals(OmbraWorkflowStage.REVIEW_READY, orchestrator.state.stage)
        assertEquals(2, orchestrator.state.counts.findingCount)
        assertEquals(2, orchestrator.state.counts.reviewOccurrenceCount)
        assertEquals(2, analysis.lastRequest?.definitions?.size)

        assertFalse(orchestrator.startExport(OmbraExportDestinationRef(10)))
        assertEquals(OmbraWorkflowStage.REVIEW_READY, orchestrator.state.stage)

        val review = orchestrator.taskSnapshot().reviewOccurrences
        assertEquals(2, review.size)
        assertTrue(orchestrator.setDecision(review[0].id, ReviewDecisionState.ACCEPTED))
        assertTrue(orchestrator.setDecision(review[1].id, ReviewDecisionState.IGNORED))

        assertTrue(orchestrator.startExport(OmbraExportDestinationRef(10)))
        assertEquals(OmbraWorkflowStage.EXPORTED, orchestrator.state.stage)
        assertEquals(OmbraExportReceipt(pageCount = 1, byteCount = 2048), orchestrator.state.exportReceipt)
        val exportedReview = requireNotNull(exporter.lastRequest).reviewOccurrences
        assertEquals(ReviewDecisionState.ACCEPTED, exportedReview[0].decision)
        assertEquals(ReviewDecisionState.IGNORED, exportedReview[1].decision)

        assertTrue(orchestrator.reset())
        assertEquals(OmbraWorkflowStage.IDLE, orchestrator.state.stage)
        val cleared = orchestrator.taskSnapshot()
        assertNull(cleared.descriptor)
        assertTrue(cleared.segments.isEmpty())
        assertTrue(cleared.findings.isEmpty())
    }

    @Test
    fun zeroFindingAnalysisCanExportAfterReviewGate() {
        val fixture = Fixture()
        val exporter = ImmediateExporter()
        val orchestrator = OmbraApplicationOrchestrator(
            extractor = ImmediateExtractor(fixture.document),
            analysisClient = ImmediateAnalysisClient(emptyList()),
            exporter = exporter,
        )

        assertTrue(orchestrator.startImport(OmbraDocumentSourceRef(3)))
        assertTrue(orchestrator.setDefinitions(fixture.definitions))
        assertTrue(orchestrator.startAnalysis())
        assertEquals(OmbraWorkflowStage.REVIEW_READY, orchestrator.state.stage)
        assertEquals(0, orchestrator.state.counts.reviewOccurrenceCount)

        assertTrue(orchestrator.startExport(OmbraExportDestinationRef(4)))
        assertEquals(OmbraWorkflowStage.EXPORTED, orchestrator.state.stage)
        assertTrue(requireNotNull(exporter.lastRequest).reviewOccurrences.isEmpty())
    }

    @Test
    fun cancelledExtractionIgnoresLateCallbackAndKeepsTaskEmpty() {
        val fixture = Fixture()
        val extractor = DeferredExtractor()
        val orchestrator = OmbraApplicationOrchestrator(
            extractor = extractor,
            analysisClient = ImmediateAnalysisClient(emptyList()),
            exporter = ImmediateExporter(),
        )

        assertTrue(orchestrator.startImport(OmbraDocumentSourceRef(12)))
        assertEquals(OmbraWorkflowStage.EXTRACTING, orchestrator.state.stage)
        val operationId = requireNotNull(extractor.operationId)

        assertTrue(orchestrator.cancel())
        assertEquals(OmbraWorkflowStage.IDLE, orchestrator.state.stage)
        assertEquals(listOf(operationId), extractor.cancelled)

        extractor.complete(Result.success(fixture.document))
        assertEquals(OmbraWorkflowStage.IDLE, orchestrator.state.stage)
        assertNull(orchestrator.taskSnapshot().descriptor)
    }

    private class ImmediateExtractor(private val document: OmbraExtractedDocument) : OmbraDocumentExtractor {
        val cancelled = mutableListOf<OmbraOperationId>()

        override fun extract(
            operationId: OmbraOperationId,
            sourceRef: OmbraDocumentSourceRef,
            onResult: (Result<OmbraExtractedDocument>) -> Unit,
        ) {
            onResult(Result.success(document))
        }

        override fun cancel(operationId: OmbraOperationId, onCancelled: () -> Unit) {
            cancelled += operationId
            onCancelled()
        }
    }

    private class DeferredExtractor : OmbraDocumentExtractor {
        var operationId: OmbraOperationId? = null
        private var callback: ((Result<OmbraExtractedDocument>) -> Unit)? = null
        val cancelled = mutableListOf<OmbraOperationId>()

        override fun extract(
            operationId: OmbraOperationId,
            sourceRef: OmbraDocumentSourceRef,
            onResult: (Result<OmbraExtractedDocument>) -> Unit,
        ) {
            this.operationId = operationId
            callback = onResult
        }

        override fun cancel(operationId: OmbraOperationId, onCancelled: () -> Unit) {
            cancelled += operationId
            onCancelled()
        }

        fun complete(result: Result<OmbraExtractedDocument>) {
            requireNotNull(callback).invoke(result)
        }
    }

    private class ImmediateAnalysisClient(private val findings: List<ValidatedFinding>) : OmbraAnalysisClient {
        var lastRequest: OmbraAnalysisRequest? = null
        val cancelled = mutableListOf<OmbraOperationId>()

        override fun analyze(
            operationId: OmbraOperationId,
            request: OmbraAnalysisRequest,
            onResult: (Result<List<ValidatedFinding>>) -> Unit,
        ) {
            lastRequest = request
            onResult(Result.success(findings))
        }

        override fun cancel(operationId: OmbraOperationId, onCancelled: () -> Unit) {
            cancelled += operationId
            onCancelled()
        }
    }

    private class ImmediateExporter : OmbraDocumentExporter {
        var lastRequest: OmbraExportRequest? = null
        val cancelled = mutableListOf<OmbraOperationId>()

        override fun export(
            operationId: OmbraOperationId,
            destinationRef: OmbraExportDestinationRef,
            request: OmbraExportRequest,
            onResult: (Result<OmbraExportReceipt>) -> Unit,
        ) {
            lastRequest = request
            onResult(Result.success(OmbraExportReceipt(pageCount = request.descriptor.pageCount, byteCount = 2048)))
        }

        override fun cancel(operationId: OmbraOperationId, onCancelled: () -> Unit) {
            cancelled += operationId
            onCancelled()
        }
    }

    private class Fixture {
        private val text = "Cliente Mario Rossi email mario.rossi@example.it"
        private val segment = DocumentSegment(
            id = SegmentId.fromIndices(pageIndex = 0, blockIndex = 0),
            pageIndex = 0,
            blockIndex = 0,
            normalizedText = text,
        )
        val document = OmbraExtractedDocument(
            descriptor = DocumentDescriptor(displayName = "contratto-mario.pdf", pageCount = 1),
            segments = listOf(segment),
        )
        val definitions = listOf(
            OmbraBuiltInPiiDefinitions.all.single { it.id == PiiTypeId.parse("full-name") },
            OmbraBuiltInPiiDefinitions.all.single { it.id == PiiTypeId.parse("email") },
        )
        val findings = listOf(
            finding("full-name", "Mario Rossi"),
            finding("email", "mario.rossi@example.it"),
        )

        private fun finding(typeId: String, surface: String): ValidatedFinding {
            val start = text.indexOf(surface)
            return ValidatedFinding(
                typeId = PiiTypeId.parse(typeId),
                surface = surface,
                occurrences =
                listOf(
                    SourceOccurrence(
                        segmentId = segment.id,
                        range = SourceRange(startInclusive = start, endExclusive = start + surface.length),
                    ),
                ),
            )
        }
    }
}
