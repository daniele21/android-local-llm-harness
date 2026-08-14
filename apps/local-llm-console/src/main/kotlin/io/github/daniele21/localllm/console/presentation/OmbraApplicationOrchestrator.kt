package io.github.daniele21.localllm.console.presentation

import io.github.daniele21.localllm.console.application.InMemoryOmbraSensitiveTaskStore
import io.github.daniele21.localllm.console.application.OmbraAnalysisClient
import io.github.daniele21.localllm.console.application.OmbraDocumentExporter
import io.github.daniele21.localllm.console.application.OmbraDocumentExtractor
import io.github.daniele21.localllm.console.application.OmbraSensitiveTaskSnapshot
import io.github.daniele21.localllm.console.application.OmbraSensitiveTaskStore
import io.github.daniele21.localllm.console.pii.PiiDefinition
import io.github.daniele21.localllm.console.pii.PiiDefinitionSet
import io.github.daniele21.localllm.console.redaction.OccurrenceId
import io.github.daniele21.localllm.console.redaction.ReviewDecisionState

/**
 * Presentation/application-flow orchestrator used by OMB-1B. Android lifecycle, URI, Binder and
 * PDF objects are intentionally absent. Reducer effects delegate only to application-layer ports.
 */
internal class OmbraApplicationOrchestrator(
    extractor: OmbraDocumentExtractor,
    analysisClient: OmbraAnalysisClient,
    exporter: OmbraDocumentExporter,
    private val taskStore: OmbraSensitiveTaskStore = InMemoryOmbraSensitiveTaskStore(),
) {
    private val effectExecutor = OmbraWorkflowEffectExecutor(extractor, analysisClient, exporter, taskStore)

    var state: OmbraWorkflowState = OmbraWorkflowState()
        private set

    fun startImport(sourceRef: OmbraDocumentSourceRef): Boolean = dispatch(OmbraWorkflowAction.StartImport(sourceRef))

    fun setDefinitions(definitions: Collection<PiiDefinition>): Boolean {
        if (state.stage !in setOf(OmbraWorkflowStage.DOCUMENT_SELECTED, OmbraWorkflowStage.DEFINITIONS_READY)) {
            return false
        }
        val definitionSet = PiiDefinitionSet.create(definitions).getOrNull() ?: return false
        if (runCatching { taskStore.replaceDefinitions(definitionSet.definitions) }.isFailure) return false
        return dispatch(OmbraWorkflowAction.DefinitionsStored(definitionSet.definitions.size))
    }

    fun startAnalysis(): Boolean = dispatch(OmbraWorkflowAction.StartAnalysis)

    fun setDecision(occurrenceId: OccurrenceId, decision: ReviewDecisionState): Boolean {
        if (state.stage != OmbraWorkflowStage.REVIEW_READY) return false
        return taskStore.updateDecision(occurrenceId, decision)
    }

    fun startExport(destinationRef: OmbraExportDestinationRef): Boolean {
        if (state.stage != OmbraWorkflowStage.REVIEW_READY) return false
        val review = taskStore.snapshot().reviewOccurrences
        if (review.any { occurrence -> occurrence.decision == ReviewDecisionState.PENDING }) return false
        return dispatch(OmbraWorkflowAction.StartExport(destinationRef))
    }

    fun cancel(): Boolean = dispatch(OmbraWorkflowAction.CancelRequested)

    fun retry(): Boolean = dispatch(OmbraWorkflowAction.RetryRequested)

    fun reset(): Boolean = dispatch(OmbraWorkflowAction.ResetRequested)

    fun onProcessRecreated(): Boolean = dispatch(OmbraWorkflowAction.ProcessRecreated)

    fun taskSnapshot(): OmbraSensitiveTaskSnapshot = taskStore.snapshot()

    private fun dispatch(action: OmbraWorkflowAction): Boolean {
        val transition = OmbraWorkflowReducer.reduce(state, action)
        val changed = transition.state != state || transition.effects.isNotEmpty()
        state = transition.state
        transition.effects.forEach { effect ->
            effectExecutor.execute(
                effect = effect,
                isOperationActive = { operationId, operationKind ->
                    state.stage != OmbraWorkflowStage.CANCELLING &&
                        state.activeOperation?.id == operationId &&
                        state.activeOperation?.kind == operationKind
                },
                emit = { emitted -> dispatch(emitted) },
            )
        }
        return changed
    }
}
