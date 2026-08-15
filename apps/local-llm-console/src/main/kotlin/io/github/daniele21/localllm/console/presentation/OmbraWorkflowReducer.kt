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

    data object ReturnToReviewRequested : OmbraWorkflowAction

    data object ResetRequested : OmbraWorkflowAction

    data object ProcessRecreated : OmbraWorkflowAction
}

internal sealed interface OmbraWorkflowEffect {
    data class ExtractDocument(val operationId: OmbraOperationId, val sourceRef: OmbraDocumentSourceRef) : OmbraWorkflowEffect

    data class AnalyzeTask(val operationId: OmbraOperationId) : OmbraWorkflowEffect

    data class ExportTask(val operationId: OmbraOperationId, val destinationRef: OmbraExportDestinationRef) : OmbraWorkflowEffect

    data class CancelOperation(val operationId: OmbraOperationId, val operationKind: OmbraOperationKind) : OmbraWorkflowEffect

    data object ClearSensitiveTask : OmbraWorkflowEffect

    data object ReleaseDocumentSources : OmbraWorkflowEffect
}

internal data class OmbraWorkflowTransition(val state: OmbraWorkflowState, val effects: List<OmbraWorkflowEffect> = emptyList())

/** Routes actions to small transition groups; transition behavior remains pure and Android-free. */
internal object OmbraWorkflowReducer {
    fun reduce(state: OmbraWorkflowState, action: OmbraWorkflowAction): OmbraWorkflowTransition = when (action) {
        is OmbraWorkflowAction.StartImport -> OmbraWorkflowStartTransitions.startImport(state, action)

        is OmbraWorkflowAction.ExtractionSucceeded -> OmbraWorkflowCompletionTransitions.extractionSucceeded(state, action)

        is OmbraWorkflowAction.DefinitionsStored -> OmbraWorkflowStartTransitions.definitionsStored(state, action)

        OmbraWorkflowAction.StartAnalysis -> OmbraWorkflowStartTransitions.startAnalysis(state)

        is OmbraWorkflowAction.AnalysisSucceeded -> OmbraWorkflowCompletionTransitions.analysisSucceeded(state, action)

        is OmbraWorkflowAction.StartExport -> OmbraWorkflowStartTransitions.startExport(state, action)

        is OmbraWorkflowAction.ExportSucceeded -> OmbraWorkflowCompletionTransitions.exportSucceeded(state, action)

        is OmbraWorkflowAction.OperationFailed -> OmbraWorkflowCompletionTransitions.operationFailed(state, action)

        OmbraWorkflowAction.CancelRequested -> OmbraWorkflowLifecycleTransitions.cancel(state)

        is OmbraWorkflowAction.CancellationAcknowledged ->
            OmbraWorkflowLifecycleTransitions.cancellationAcknowledged(state, action)

        OmbraWorkflowAction.RetryRequested -> OmbraWorkflowLifecycleTransitions.retry(state)

        OmbraWorkflowAction.ReturnToReviewRequested -> OmbraWorkflowLifecycleTransitions.returnToReview(state)

        OmbraWorkflowAction.ResetRequested -> OmbraWorkflowLifecycleTransitions.reset(state, cancelActive = true)

        OmbraWorkflowAction.ProcessRecreated -> OmbraWorkflowLifecycleTransitions.reset(state, cancelActive = false)
    }
}
