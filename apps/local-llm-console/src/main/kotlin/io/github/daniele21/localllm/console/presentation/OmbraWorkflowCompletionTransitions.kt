package io.github.daniele21.localllm.console.presentation

internal object OmbraWorkflowCompletionTransitions {
    fun extractionSucceeded(
        state: OmbraWorkflowState,
        action: OmbraWorkflowAction.ExtractionSucceeded,
    ): OmbraWorkflowTransition {
        if (!OmbraWorkflowTransitionSupport.matchesActive(state, action.operationId, OmbraOperationKind.EXTRACTION)) {
            return OmbraWorkflowTransitionSupport.unchanged(state)
        }
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

    fun analysisSucceeded(
        state: OmbraWorkflowState,
        action: OmbraWorkflowAction.AnalysisSucceeded,
    ): OmbraWorkflowTransition {
        if (!OmbraWorkflowTransitionSupport.matchesActive(state, action.operationId, OmbraOperationKind.ANALYSIS)) {
            return OmbraWorkflowTransitionSupport.unchanged(state)
        }
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

    fun exportSucceeded(state: OmbraWorkflowState, action: OmbraWorkflowAction.ExportSucceeded): OmbraWorkflowTransition {
        if (!OmbraWorkflowTransitionSupport.matchesActive(state, action.operationId, OmbraOperationKind.EXPORT)) {
            return OmbraWorkflowTransitionSupport.unchanged(state)
        }
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

    fun operationFailed(state: OmbraWorkflowState, action: OmbraWorkflowAction.OperationFailed): OmbraWorkflowTransition {
        val operation = state.activeOperation ?: return OmbraWorkflowTransitionSupport.unchanged(state)
        if (operation.id != action.operationId) return OmbraWorkflowTransitionSupport.unchanged(state)
        return OmbraWorkflowTransition(
            state =
                state.copy(
                    stage = OmbraWorkflowStage.FAILED,
                    activeOperation = null,
                    retryTarget = OmbraWorkflowTransitionSupport.retryTarget(operation.kind),
                    failureCode = action.failureCode,
                    cancelReturnStage = null,
                ),
        )
    }
}
