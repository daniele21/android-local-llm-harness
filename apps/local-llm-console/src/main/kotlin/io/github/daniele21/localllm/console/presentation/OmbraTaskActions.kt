package io.github.daniele21.localllm.console.presentation

import io.github.daniele21.localllm.console.application.OmbraSensitiveTaskSnapshot
import io.github.daniele21.localllm.console.application.OmbraSensitiveTaskStore
import io.github.daniele21.localllm.console.pii.PiiDefinition
import io.github.daniele21.localllm.console.pii.PiiDefinitionSet
import io.github.daniele21.localllm.console.redaction.OccurrenceId
import io.github.daniele21.localllm.console.redaction.ReviewDecisionState

/**
 * Owns user-driven mutations of the sensitive task while the orchestrator owns only workflow
 * progression and effect dispatch.
 */
internal class OmbraTaskActions(
    private val taskStore: OmbraSensitiveTaskStore,
    private val stateProvider: () -> OmbraWorkflowState,
    private val dispatch: (OmbraWorkflowAction) -> Boolean,
) {
    fun setDefinitions(definitions: Collection<PiiDefinition>): Boolean {
        val state = stateProvider()
        if (state.stage !in setOf(OmbraWorkflowStage.DOCUMENT_SELECTED, OmbraWorkflowStage.DEFINITIONS_READY)) {
            return false
        }
        val definitionSet = PiiDefinitionSet.create(definitions).getOrNull() ?: return false
        if (runCatching { taskStore.replaceDefinitions(definitionSet.definitions) }.isFailure) return false
        return dispatch(OmbraWorkflowAction.DefinitionsStored(definitionSet.definitions.size))
    }

    fun setDecision(occurrenceId: OccurrenceId, decision: ReviewDecisionState): Boolean {
        if (stateProvider().stage != OmbraWorkflowStage.REVIEW_READY) return false
        return taskStore.updateDecision(occurrenceId, decision)
    }

    fun startExport(destinationRef: OmbraExportDestinationRef): Boolean {
        if (stateProvider().stage != OmbraWorkflowStage.REVIEW_READY) return false
        val review = taskStore.snapshot().reviewOccurrences
        if (review.any { occurrence -> occurrence.decision == ReviewDecisionState.PENDING }) return false
        return dispatch(OmbraWorkflowAction.StartExport(destinationRef))
    }

    fun snapshot(): OmbraSensitiveTaskSnapshot = taskStore.snapshot()
}
