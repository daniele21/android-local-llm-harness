package io.github.daniele21.localllm.console.presentation

import io.github.daniele21.localllm.console.analysis.ValidatedFinding
import io.github.daniele21.localllm.console.application.OmbraAnalysisClient
import io.github.daniele21.localllm.console.application.OmbraAnalysisRequest
import io.github.daniele21.localllm.console.application.OmbraDocumentExporter
import io.github.daniele21.localllm.console.application.OmbraDocumentExtractor
import io.github.daniele21.localllm.console.application.OmbraDocumentSourceCapabilityCleanup
import io.github.daniele21.localllm.console.application.OmbraExportReceipt
import io.github.daniele21.localllm.console.application.OmbraExportRequest
import io.github.daniele21.localllm.console.application.OmbraExtractedDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OmbraDocumentSourceCleanupTest {
    @Test
    fun resetReleasesRegisteredSourceCapabilitiesWithoutClearingThemAtImportStart() {
        val cleanup = RecordingCleanup()
        val orchestrator = orchestrator(cleanup)

        assertTrue(orchestrator.startImport(OmbraDocumentSourceRef(1)))
        assertEquals(0, cleanup.releaseCalls)

        assertTrue(orchestrator.reset())
        assertEquals(1, cleanup.releaseCalls)
        assertEquals(OmbraWorkflowStage.IDLE, orchestrator.state.stage)
    }

    @Test
    fun processRecreationAlsoReleasesSourceCapabilities() {
        val cleanup = RecordingCleanup()
        val orchestrator = orchestrator(cleanup)

        assertTrue(orchestrator.startImport(OmbraDocumentSourceRef(2)))
        assertTrue(orchestrator.onProcessRecreated())

        assertEquals(1, cleanup.releaseCalls)
        assertEquals(OmbraWorkflowStage.IDLE, orchestrator.state.stage)
    }

    private fun orchestrator(cleanup: RecordingCleanup): OmbraApplicationOrchestrator =
        OmbraApplicationOrchestrator(
            extractor = DeferredExtractor(),
            analysisClient = NoOpAnalysisClient,
            exporter = NoOpExporter,
            sourceCapabilityCleanup = cleanup,
        )

    private class RecordingCleanup : OmbraDocumentSourceCapabilityCleanup {
        var releaseCalls = 0
            private set

        override fun releaseAll() {
            releaseCalls += 1
        }
    }

    private class DeferredExtractor : OmbraDocumentExtractor {
        override fun extract(
            operationId: OmbraOperationId,
            sourceRef: OmbraDocumentSourceRef,
            onResult: (Result<OmbraExtractedDocument>) -> Unit,
        ) = Unit

        override fun cancel(operationId: OmbraOperationId, onCancelled: () -> Unit) = onCancelled()
    }

    private object NoOpAnalysisClient : OmbraAnalysisClient {
        override fun analyze(
            operationId: OmbraOperationId,
            request: OmbraAnalysisRequest,
            onResult: (Result<List<ValidatedFinding>>) -> Unit,
        ) = Unit

        override fun cancel(operationId: OmbraOperationId, onCancelled: () -> Unit) = onCancelled()
    }

    private object NoOpExporter : OmbraDocumentExporter {
        override fun export(
            operationId: OmbraOperationId,
            destinationRef: OmbraExportDestinationRef,
            request: OmbraExportRequest,
            onResult: (Result<OmbraExportReceipt>) -> Unit,
        ) = Unit

        override fun cancel(operationId: OmbraOperationId, onCancelled: () -> Unit) = onCancelled()
    }
}
