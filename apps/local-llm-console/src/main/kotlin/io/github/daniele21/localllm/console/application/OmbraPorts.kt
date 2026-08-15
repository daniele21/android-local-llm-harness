package io.github.daniele21.localllm.console.application

import io.github.daniele21.localllm.console.analysis.ValidatedFinding
import io.github.daniele21.localllm.console.document.DocumentDescriptor
import io.github.daniele21.localllm.console.document.DocumentSegment
import io.github.daniele21.localllm.console.pii.PiiDefinition
import io.github.daniele21.localllm.console.redaction.ReviewOccurrence

internal data class OmbraExtractedDocument(val descriptor: DocumentDescriptor, val segments: List<DocumentSegment>) {
    init {
        require(segments.isNotEmpty()) { "Extracted document must contain at least one text segment" }
    }

    override fun toString(): String = "OmbraExtractedDocument(pageCount=${descriptor.pageCount}, segmentCount=${segments.size})"
}

internal data class OmbraAnalysisRequest(val segments: List<DocumentSegment>, val definitions: List<PiiDefinition>) {
    init {
        require(segments.isNotEmpty()) { "Analysis requires document segments" }
        require(definitions.isNotEmpty()) { "Analysis requires PII definitions" }
    }

    override fun toString(): String = "OmbraAnalysisRequest(segmentCount=${segments.size}, definitionCount=${definitions.size})"
}

internal data class OmbraExportRequest(
    val descriptor: DocumentDescriptor,
    val segments: List<DocumentSegment>,
    val definitions: List<PiiDefinition>,
    val reviewOccurrences: List<ReviewOccurrence>,
) {
    init {
        require(segments.isNotEmpty()) { "Export requires document segments" }
        require(definitions.isNotEmpty() || reviewOccurrences.isEmpty()) {
            "Export with findings requires active definitions"
        }
    }

    override fun toString(): String =
        "OmbraExportRequest(pageCount=${descriptor.pageCount}, segmentCount=${segments.size}, " +
            "definitionCount=${definitions.size}, reviewCount=${reviewOccurrences.size})"
}

/**
 * Long-running application ports are callback based so Android/coroutine implementations can
 * complete asynchronously while the reducer retains operation-ID ownership and rejects late work.
 * Cancellation has a separate completion callback so the state machine stays in CANCELLING until
 * the implementation has reached its local terminal/cleanup point.
 */
internal interface OmbraDocumentExtractor {
    fun extract(operationId: OmbraOperationId, sourceRef: OmbraDocumentSourceRef, onResult: (Result<OmbraExtractedDocument>) -> Unit)

    fun cancel(operationId: OmbraOperationId, onCancelled: () -> Unit)
}

internal interface OmbraAnalysisClient {
    fun analyze(operationId: OmbraOperationId, request: OmbraAnalysisRequest, onResult: (Result<List<ValidatedFinding>>) -> Unit)

    fun cancel(operationId: OmbraOperationId, onCancelled: () -> Unit)
}

internal interface OmbraDocumentExporter {
    fun export(
        operationId: OmbraOperationId,
        destinationRef: OmbraExportDestinationRef,
        request: OmbraExportRequest,
        onResult: (Result<OmbraExportReceipt>) -> Unit,
    )

    fun cancel(operationId: OmbraOperationId, onCancelled: () -> Unit)
}
