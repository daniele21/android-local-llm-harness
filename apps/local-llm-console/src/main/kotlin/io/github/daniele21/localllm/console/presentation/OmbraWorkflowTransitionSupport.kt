package io.github.daniele21.localllm.console.presentation

internal object OmbraWorkflowTransitionSupport {
    fun allocateOperation(state: OmbraWorkflowState): Pair<OmbraOperationId, Long> {
        val operationId = OmbraOperationId(state.nextOperationOrdinal)
        return operationId to (state.nextOperationOrdinal + 1)
    }

    fun matchesActive(state: OmbraWorkflowState, operationId: OmbraOperationId, operationKind: OmbraOperationKind): Boolean =
        state.activeOperation == OmbraActiveOperation(operationId, operationKind)

    fun retryTarget(kind: OmbraOperationKind): OmbraRetryTarget = when (kind) {
        OmbraOperationKind.EXTRACTION -> OmbraRetryTarget.EXTRACTION
        OmbraOperationKind.ANALYSIS -> OmbraRetryTarget.ANALYSIS
        OmbraOperationKind.EXPORT -> OmbraRetryTarget.EXPORT
    }

    fun cancelReturnStage(kind: OmbraOperationKind): OmbraWorkflowStage = when (kind) {
        OmbraOperationKind.EXTRACTION -> OmbraWorkflowStage.IDLE
        OmbraOperationKind.ANALYSIS -> OmbraWorkflowStage.DEFINITIONS_READY
        OmbraOperationKind.EXPORT -> OmbraWorkflowStage.REVIEW_READY
    }

    fun unchanged(state: OmbraWorkflowState): OmbraWorkflowTransition = OmbraWorkflowTransition(state)
}
