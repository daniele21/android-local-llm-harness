package io.github.daniele21.localllm.console.presentation

internal sealed interface OmbraWorkflowAction {
    data class StartImport(val sourceRef: OmbraDocumentSourceRef) : OmbraWorkflowAction

    data class ExtractionSucceeded(val operationId: OmbraOperationId, val pageCount: Int, val segmentCount: Int) : OmbraWorkflowAction

    data class DefinitionsStored(val activeDefinitionCount: Int) : OmbraWorkflowAction

    data object StartAnalysis : OmbraWorkflowAction

    data class AnalysisSucceeded(val operationId: OmbraOperationId, val findingCount: Int, val reviewOccurrenceCount: Int) :
        OmbraWorkflowAction

    data class StartExport(val destinationRef: OmbraExportDestinationRef) : OmbraWorkflowAction

    data class ExportSucceeded(val operationId: OmbraOperationId, val receipt: OmbraExportReceipt) : OmbraWorkflowAction

    data class OperationFailed(val operationId: OmbraOperationId, val failureCode: OmbraFailureCode) : OmbraWorkflowAction

    data object CancelRequested : OmbraWorkflowAction

    data class CancellationAcknowledged(val operationId: OmbraOperationId) : OmbraWorkflowAction

    data object RetryRequested : OmbraWorkflowAction

    data object ResetRequested : OmbraWorkflowAction

    data object ProcessRecreated : OmbraWorkflowAction
}

internal sealed interface OmbraWorkflowEffect {
    data class ExtractDocument(val operationId: OmbraOperationId, val sourceRef: OmbraDocumentSourceRef) : OmbraWorkflowEffect

    data class AnalyzeTask(val operationId: OmbraOperationId) : OmbraWorkflowEffect

    data class ExportTask(val operationId: OmbraOperationId, val destinationRef: OmbraExportDestinationRef) : OmbraWorkflowEffect

    data class CancelOperation(val operationId: OmbraOperationId, val operationKind: OmbraOperationKind) : OmbraWorkflowEffect

    data object ClearSensitiveTask : OmbraWorkflowEffect
}

internal data class OmbraWorkflowTransition(val state: OmbraWorkflowState, val effects: List<OmbraWorkflowEffect> = emptyList())

internal object OmbraWorkflowReducer {
    fun reduce(state: OmbraWorkflowState, action: OmbraWorkflowAction): OmbraWorkflowTransition = when (action) {
        is OmbraWorkflowAction.StartImport -> startImport(state, action)
        is OmbraWorkflowAction.ExtractionSucceeded -> extractionSucceeded(state, action)
        is OmbraWorkflowAction.DefinitionsStored -> definitionsStored(state, action)
        OmbraWorkflowAction.StartAnalysis -> startAnalysis(state)
        is OmbraWorkflowAction.AnalysisSucceeded -> analysisSucceeded(state, action)
        is OmbraWorkflowAction.StartExport -> startExport(state, action)
        is OmbraWorkflowAction.ExportSucceeded -> exportSucceeded(state, action)
        is OmbraWorkflowAction.OperationFailed -> operationFailed(state, action)
        OmbraWorkflowAction.CancelRequested -> cancel(state)
        is OmbraWorkflowAction.CancellationAcknowledged -> cancellationAcknowledged(state, action)
        OmbraWorkflowAction.RetryRequested -> retry(state)
        OmbraWorkflowAction.ResetRequested -> reset(state, cancelActive = true)
        OmbraWorkflowAction.ProcessRecreated -> reset(state, cancelActive = false)
    }

    private fun startImport(state: OmbraWorkflowState, action: OmbraWorkflowAction.StartImport): OmbraWorkflowTransition {
        if (state.activeOperation != null) return unchanged(state)
        val (operationId, nextOrdinal) = allocateOperation(state)
        return OmbraWorkflowTransition(
            state =
            OmbraWorkflowState(
                stage = OmbraWorkflowStage.EXTRACTING,
                activeOperation = OmbraActiveOperation(operationId, OmbraOperationKind.EXTRACTION),
                sourceRef = action.sourceRef,
                nextOperationOrdinal = nextOrdinal,
            ),
            effects =
            listOf(
                OmbraWorkflowEffect.ClearSensitiveTask,
                OmbraWorkflowEffect.ExtractDocument(operationId, action.sourceRef),
            ),
        )
    }

    private fun extractionSucceeded(state: OmbraWorkflowState, action: OmbraWorkflowAction.ExtractionSucceeded): OmbraWorkflowTransition {
        if (!matchesActive(state, action.operationId, OmbraOperationKind.EXTRACTION)) return unchanged(state)
        require(action.pageCount > 0) { "pageCount must be positive" }
        require(action.segmentCount > 0) { "segmentCount must be positive" }
        return OmbraWorkflowTransition(
            state =
            state.copy(
                stage = OmbraWorkflowStage.DOCUMENT_SELECTED,
                activeOperation = null,
                counts =
                state.counts.copy(
                    documentPageCount = action.pageCount,
                    segmentCount = action.segmentCount,
                ),
                failureCode = null,
                retryTarget = null,
            ),
        )
    }

    private fun definitionsStored(state: OmbraWorkflowState, action: OmbraWorkflowAction.DefinitionsStored): OmbraWorkflowTransition {
        if (state.stage !in setOf(OmbraWorkflowStage.DOCUMENT_SELECTED, OmbraWorkflowStage.DEFINITIONS_READY)) {
            return unchanged(state)
        }
        require(action.activeDefinitionCount > 0) { "activeDefinitionCount must be positive" }
        return OmbraWorkflowTransition(
            state =
            state.copy(
                stage = OmbraWorkflowStage.DEFINITIONS_READY,
                counts =
                state.counts.copy(
                    activeDefinitionCount = action.activeDefinitionCount,
                    findingCount = 0,
                    reviewOccurrenceCount = 0,
                ),
                exportReceipt = null,
                failureCode = null,
                retryTarget = null,
            ),
        )
    }

    private fun startAnalysis(state: OmbraWorkflowState): OmbraWorkflowTransition {
        if (state.stage != OmbraWorkflowStage.DEFINITIONS_READY || state.activeOperation != null) return unchanged(state)
        val (operationId, nextOrdinal) = allocateOperation(state)
        return OmbraWorkflowTransition(
            state =
            state.copy(
                stage = OmbraWorkflowStage.ANALYZING,
                activeOperation = OmbraActiveOperation(operationId, OmbraOperationKind.ANALYSIS),
                nextOperationOrdinal = nextOrdinal,
                failureCode = null,
                retryTarget = null,
            ),
            effects = listOf(OmbraWorkflowEffect.AnalyzeTask(operationId)),
        )
    }

    private fun analysisSucceeded(state: OmbraWorkflowState, action: OmbraWorkflowAction.AnalysisSucceeded): OmbraWorkflowTransition {
        if (!matchesActive(state, action.operationId, OmbraOperationKind.ANALYSIS)) return unchanged(state)
        require(action.findingCount >= 0) { "findingCount must be non-negative" }
        require(action.reviewOccurrenceCount >= 0) { "reviewOccurrenceCount must be non-negative" }
        return OmbraWorkflowTransition(
            state =
            state.copy(
                stage = OmbraWorkflowStage.REVIEW_READY,
                activeOperation = null,
                counts =
                state.counts.copy(
                    findingCount = action.findingCount,
                    reviewOccurrenceCount = action.reviewOccurrenceCount,
                ),
                exportReceipt = null,
                failureCode = null,
                retryTarget = null,
            ),
        )
    }

    private fun startExport(state: OmbraWorkflowState, action: OmbraWorkflowAction.StartExport): OmbraWorkflowTransition {
        if (state.stage != OmbraWorkflowStage.REVIEW_READY || state.activeOperation != null) return unchanged(state)
        val (operationId, nextOrdinal) = allocateOperation(state)
        return OmbraWorkflowTransition(
            state =
            state.copy(
                stage = OmbraWorkflowStage.EXPORTING,
                activeOperation = OmbraActiveOperation(operationId, OmbraOperationKind.EXPORT),
                exportDestinationRef = action.destinationRef,
                nextOperationOrdinal = nextOrdinal,
                failureCode = null,
                retryTarget = null,
            ),
            effects = listOf(OmbraWorkflowEffect.ExportTask(operationId, action.destinationRef)),
        )
    }

    private fun exportSucceeded(state: OmbraWorkflowState, action: OmbraWorkflowAction.ExportSucceeded): OmbraWorkflowTransition {
        if (!matchesActive(state, action.operationId, OmbraOperationKind.EXPORT)) return unchanged(state)
        return OmbraWorkflowTransition(
            state =
            state.copy(
                stage = OmbraWorkflowStage.EXPORTED,
                activeOperation = null,
                exportReceipt = action.receipt,
                failureCode = null,
                retryTarget = null,
            ),
        )
    }

    private fun operationFailed(state: OmbraWorkflowState, action: OmbraWorkflowAction.OperationFailed): OmbraWorkflowTransition {
        val operation = state.activeOperation ?: return unchanged(state)
        if (operation.id != action.operationId) return unchanged(state)
        return OmbraWorkflowTransition(
            state =
            state.copy(
                stage = OmbraWorkflowStage.FAILED,
                activeOperation = null,
                retryTarget = retryTarget(operation.kind),
                failureCode = action.failureCode,
                cancelReturnStage = null,
            ),
        )
    }

    private fun cancel(state: OmbraWorkflowState): OmbraWorkflowTransition {
        val operation = state.activeOperation ?: return unchanged(state)
        return OmbraWorkflowTransition(
            state =
            state.copy(
                stage = OmbraWorkflowStage.CANCELLING,
                cancelReturnStage = cancelReturnStage(operation.kind),
            ),
            effects = listOf(OmbraWorkflowEffect.CancelOperation(operation.id, operation.kind)),
        )
    }

    private fun cancellationAcknowledged(
        state: OmbraWorkflowState,
        action: OmbraWorkflowAction.CancellationAcknowledged,
    ): OmbraWorkflowTransition {
        if (state.stage != OmbraWorkflowStage.CANCELLING || state.activeOperation?.id != action.operationId) {
            return unchanged(state)
        }
        return OmbraWorkflowTransition(
            state =
            state.copy(
                stage = requireNotNull(state.cancelReturnStage),
                activeOperation = null,
                cancelReturnStage = null,
                failureCode = null,
                retryTarget = null,
            ),
        )
    }

    private fun retry(state: OmbraWorkflowState): OmbraWorkflowTransition {
        if (state.stage != OmbraWorkflowStage.FAILED || state.retryTarget == null) return unchanged(state)
        val (operationId, nextOrdinal) = allocateOperation(state)
        return when (state.retryTarget) {
            OmbraRetryTarget.EXTRACTION -> retryExtraction(state, operationId, nextOrdinal)

            OmbraRetryTarget.ANALYSIS -> OmbraWorkflowTransition(
                state =
                state.copy(
                    stage = OmbraWorkflowStage.ANALYZING,
                    activeOperation = OmbraActiveOperation(operationId, OmbraOperationKind.ANALYSIS),
                    failureCode = null,
                    retryTarget = null,
                    nextOperationOrdinal = nextOrdinal,
                ),
                effects = listOf(OmbraWorkflowEffect.AnalyzeTask(operationId)),
            )

            OmbraRetryTarget.EXPORT -> retryExport(state, operationId, nextOrdinal)
        }
    }

    private fun retryExtraction(state: OmbraWorkflowState, operationId: OmbraOperationId, nextOrdinal: Long): OmbraWorkflowTransition {
        val sourceRef = state.sourceRef ?: return unchanged(state)
        return OmbraWorkflowTransition(
            state =
            state.copy(
                stage = OmbraWorkflowStage.EXTRACTING,
                activeOperation = OmbraActiveOperation(operationId, OmbraOperationKind.EXTRACTION),
                failureCode = null,
                retryTarget = null,
                nextOperationOrdinal = nextOrdinal,
            ),
            effects = listOf(OmbraWorkflowEffect.ExtractDocument(operationId, sourceRef)),
        )
    }

    private fun retryExport(state: OmbraWorkflowState, operationId: OmbraOperationId, nextOrdinal: Long): OmbraWorkflowTransition {
        val destinationRef = state.exportDestinationRef ?: return unchanged(state)
        return OmbraWorkflowTransition(
            state =
            state.copy(
                stage = OmbraWorkflowStage.EXPORTING,
                activeOperation = OmbraActiveOperation(operationId, OmbraOperationKind.EXPORT),
                failureCode = null,
                retryTarget = null,
                nextOperationOrdinal = nextOrdinal,
            ),
            effects = listOf(OmbraWorkflowEffect.ExportTask(operationId, destinationRef)),
        )
    }

    private fun reset(state: OmbraWorkflowState, cancelActive: Boolean): OmbraWorkflowTransition {
        val effects = buildList {
            val operation = state.activeOperation
            if (cancelActive && operation != null) {
                add(OmbraWorkflowEffect.CancelOperation(operation.id, operation.kind))
            }
            add(OmbraWorkflowEffect.ClearSensitiveTask)
        }
        return OmbraWorkflowTransition(
            state = OmbraWorkflowState(nextOperationOrdinal = state.nextOperationOrdinal),
            effects = effects,
        )
    }

    private fun allocateOperation(state: OmbraWorkflowState): Pair<OmbraOperationId, Long> {
        val operationId = OmbraOperationId(state.nextOperationOrdinal)
        return operationId to (state.nextOperationOrdinal + 1)
    }

    private fun matchesActive(state: OmbraWorkflowState, operationId: OmbraOperationId, operationKind: OmbraOperationKind): Boolean =
        state.activeOperation == OmbraActiveOperation(operationId, operationKind)

    private fun retryTarget(kind: OmbraOperationKind): OmbraRetryTarget = when (kind) {
        OmbraOperationKind.EXTRACTION -> OmbraRetryTarget.EXTRACTION
        OmbraOperationKind.ANALYSIS -> OmbraRetryTarget.ANALYSIS
        OmbraOperationKind.EXPORT -> OmbraRetryTarget.EXPORT
    }

    private fun cancelReturnStage(kind: OmbraOperationKind): OmbraWorkflowStage = when (kind) {
        OmbraOperationKind.EXTRACTION -> OmbraWorkflowStage.IDLE
        OmbraOperationKind.ANALYSIS -> OmbraWorkflowStage.DEFINITIONS_READY
        OmbraOperationKind.EXPORT -> OmbraWorkflowStage.REVIEW_READY
    }

    private fun unchanged(state: OmbraWorkflowState): OmbraWorkflowTransition = OmbraWorkflowTransition(state)
}
