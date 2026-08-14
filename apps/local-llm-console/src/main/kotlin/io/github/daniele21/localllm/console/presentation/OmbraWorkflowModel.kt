package io.github.daniele21.localllm.console.presentation

internal enum class OmbraWorkflowStage {
    IDLE,
    DOCUMENT_SELECTED,
    DEFINITIONS_READY,
    EXTRACTING,
    ANALYZING,
    REVIEW_READY,
    EXPORTING,
    EXPORTED,
    CANCELLING,
    FAILED,
}

internal enum class OmbraOperationKind {
    EXTRACTION,
    ANALYSIS,
    EXPORT,
}

internal enum class OmbraFailureCode {
    EXTRACTION_FAILED,
    ANALYSIS_FAILED,
    EXPORT_FAILED,
}

internal enum class OmbraRetryTarget {
    EXTRACTION,
    ANALYSIS,
    EXPORT,
}

internal data class OmbraActiveOperation(val id: OmbraOperationId, val kind: OmbraOperationKind)

internal data class OmbraWorkflowCounts(
    val documentPageCount: Int = 0,
    val segmentCount: Int = 0,
    val activeDefinitionCount: Int = 0,
    val findingCount: Int = 0,
    val reviewOccurrenceCount: Int = 0,
) {
    init {
        require(documentPageCount >= 0) { "documentPageCount must be non-negative" }
        require(segmentCount >= 0) { "segmentCount must be non-negative" }
        require(activeDefinitionCount >= 0) { "activeDefinitionCount must be non-negative" }
        require(findingCount >= 0) { "findingCount must be non-negative" }
        require(reviewOccurrenceCount >= 0) { "reviewOccurrenceCount must be non-negative" }
    }
}

/**
 * Immutable workflow state. Sensitive document/definition/finding values live in the task store,
 * while this surface contains only counts, content-free references and lifecycle state.
 */
internal data class OmbraWorkflowState(
    val stage: OmbraWorkflowStage = OmbraWorkflowStage.IDLE,
    val activeOperation: OmbraActiveOperation? = null,
    val cancelReturnStage: OmbraWorkflowStage? = null,
    val retryTarget: OmbraRetryTarget? = null,
    val sourceRef: OmbraDocumentSourceRef? = null,
    val exportDestinationRef: OmbraExportDestinationRef? = null,
    val counts: OmbraWorkflowCounts = OmbraWorkflowCounts(),
    val exportReceipt: OmbraExportReceipt? = null,
    val failureCode: OmbraFailureCode? = null,
    val nextOperationOrdinal: Long = 1,
) {
    init {
        require(nextOperationOrdinal > 0) { "nextOperationOrdinal must be positive" }
        require(stage == OmbraWorkflowStage.CANCELLING || cancelReturnStage == null) {
            "cancelReturnStage is valid only while cancelling"
        }
        require(stage == OmbraWorkflowStage.FAILED || retryTarget == null) {
            "retryTarget is valid only while failed"
        }
        require(stage == OmbraWorkflowStage.FAILED || failureCode == null) {
            "failureCode is valid only while failed"
        }
    }
}
