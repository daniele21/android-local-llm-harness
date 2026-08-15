package io.github.daniele21.localllm.console.presentation

internal object OmbraWorkflowLifecycleTransitions {
    fun cancel(state: OmbraWorkflowState): OmbraWorkflowTransition {
        val operation = state.activeOperation ?: return OmbraWorkflowTransitionSupport.unchanged(state)
        return OmbraWorkflowTransition(
            state =
            state.copy(
                stage = OmbraWorkflowStage.CANCELLING,
                cancelReturnStage = OmbraWorkflowTransitionSupport.cancelReturnStage(operation.kind),
            ),
            effects = listOf(OmbraWorkflowEffect.CancelOperation(operation.id, operation.kind)),
        )
    }

    fun cancellationAcknowledged(
        state: OmbraWorkflowState,
        action: OmbraWorkflowAction.CancellationAcknowledged,
    ): OmbraWorkflowTransition {
        if (state.stage != OmbraWorkflowStage.CANCELLING || state.activeOperation?.id != action.operationId) {
            return OmbraWorkflowTransitionSupport.unchanged(state)
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

    fun retry(state: OmbraWorkflowState): OmbraWorkflowTransition {
        val retryTarget = state.retryTarget
        if (state.stage != OmbraWorkflowStage.FAILED || retryTarget == null) {
            return OmbraWorkflowTransitionSupport.unchanged(state)
        }
        val (operationId, nextOrdinal) = OmbraWorkflowTransitionSupport.allocateOperation(state)
        return when (retryTarget) {
            OmbraRetryTarget.EXTRACTION -> retryExtraction(state, operationId, nextOrdinal)

            OmbraRetryTarget.ANALYSIS ->
                OmbraWorkflowTransition(
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

    fun reset(state: OmbraWorkflowState, cancelActive: Boolean): OmbraWorkflowTransition {
        val effects =
            buildList {
                val operation = state.activeOperation
                if (cancelActive && operation != null) {
                    add(OmbraWorkflowEffect.CancelOperation(operation.id, operation.kind))
                }
                add(OmbraWorkflowEffect.ClearSensitiveTask)
                add(OmbraWorkflowEffect.ReleaseDocumentSources)
            }
        return OmbraWorkflowTransition(
            state = OmbraWorkflowState(nextOperationOrdinal = state.nextOperationOrdinal),
            effects = effects,
        )
    }

    private fun retryExtraction(state: OmbraWorkflowState, operationId: OmbraOperationId, nextOrdinal: Long): OmbraWorkflowTransition {
        val sourceRef = state.sourceRef ?: return OmbraWorkflowTransitionSupport.unchanged(state)
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
        val destinationRef = state.exportDestinationRef ?: return OmbraWorkflowTransitionSupport.unchanged(state)
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
}
