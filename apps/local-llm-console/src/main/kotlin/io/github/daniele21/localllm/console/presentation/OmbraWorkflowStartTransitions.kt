package io.github.daniele21.localllm.console.presentation

internal object OmbraWorkflowStartTransitions {
    fun startImport(state: OmbraWorkflowState, action: OmbraWorkflowAction.StartImport): OmbraWorkflowTransition {
        if (state.activeOperation != null) return OmbraWorkflowTransitionSupport.unchanged(state)
        val (operationId, nextOrdinal) = OmbraWorkflowTransitionSupport.allocateOperation(state)
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

    fun definitionsStored(state: OmbraWorkflowState, action: OmbraWorkflowAction.DefinitionsStored): OmbraWorkflowTransition {
        if (state.stage !in setOf(OmbraWorkflowStage.DOCUMENT_SELECTED, OmbraWorkflowStage.DEFINITIONS_READY)) {
            return OmbraWorkflowTransitionSupport.unchanged(state)
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

    fun startAnalysis(state: OmbraWorkflowState): OmbraWorkflowTransition {
        if (state.stage != OmbraWorkflowStage.DEFINITIONS_READY || state.activeOperation != null) {
            return OmbraWorkflowTransitionSupport.unchanged(state)
        }
        val (operationId, nextOrdinal) = OmbraWorkflowTransitionSupport.allocateOperation(state)
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

    fun startExport(state: OmbraWorkflowState, action: OmbraWorkflowAction.StartExport): OmbraWorkflowTransition {
        if (state.stage != OmbraWorkflowStage.REVIEW_READY || state.activeOperation != null) {
            return OmbraWorkflowTransitionSupport.unchanged(state)
        }
        val (operationId, nextOrdinal) = OmbraWorkflowTransitionSupport.allocateOperation(state)
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
}
