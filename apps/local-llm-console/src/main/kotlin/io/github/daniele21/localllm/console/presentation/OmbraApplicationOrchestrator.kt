package io.github.daniele21.localllm.console.presentation

import io.github.daniele21.localllm.console.application.InMemoryOmbraSensitiveTaskStore
import io.github.daniele21.localllm.console.application.OmbraAnalysisClient
import io.github.daniele21.localllm.console.application.OmbraDocumentExporter
import io.github.daniele21.localllm.console.application.OmbraDocumentExtractor
import io.github.daniele21.localllm.console.application.OmbraSensitiveTaskStore

/**
 * Presentation/application-flow orchestrator used by OMB-1B. Android lifecycle, URI, Binder and
 * PDF objects are intentionally absent. Reducer effects delegate only to application-layer ports.
 */
internal class OmbraApplicationOrchestrator(
    extractor: OmbraDocumentExtractor,
    analysisClient: OmbraAnalysisClient,
    exporter: OmbraDocumentExporter,
    taskStore: OmbraSensitiveTaskStore = InMemoryOmbraSensitiveTaskStore(),
) {
    private val effectExecutor = OmbraWorkflowEffectExecutor(extractor, analysisClient, exporter, taskStore)

    var state: OmbraWorkflowState = OmbraWorkflowState()
        private set

    val task =
        OmbraTaskActions(
            taskStore = taskStore,
            stateProvider = { state },
            dispatch = { action -> dispatch(action) },
        )

    fun startImport(sourceRef: OmbraDocumentSourceRef): Boolean = dispatch(OmbraWorkflowAction.StartImport(sourceRef))

    fun startAnalysis(): Boolean = dispatch(OmbraWorkflowAction.StartAnalysis)

    fun cancel(): Boolean = dispatch(OmbraWorkflowAction.CancelRequested)

    fun retry(): Boolean = dispatch(OmbraWorkflowAction.RetryRequested)

    fun reset(): Boolean = dispatch(OmbraWorkflowAction.ResetRequested)

    fun onProcessRecreated(): Boolean = dispatch(OmbraWorkflowAction.ProcessRecreated)

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
