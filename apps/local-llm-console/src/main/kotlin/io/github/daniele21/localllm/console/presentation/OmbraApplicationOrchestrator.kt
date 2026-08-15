package io.github.daniele21.localllm.console.presentation

import io.github.daniele21.localllm.console.application.InMemoryOmbraSensitiveTaskStore
import io.github.daniele21.localllm.console.application.NoOpOmbraDocumentSourceCapabilityCleanup
import io.github.daniele21.localllm.console.application.OmbraAnalysisClient
import io.github.daniele21.localllm.console.application.OmbraDocumentExporter
import io.github.daniele21.localllm.console.application.OmbraDocumentExtractor
import io.github.daniele21.localllm.console.application.OmbraDocumentSourceCapabilityCleanup
import io.github.daniele21.localllm.console.application.OmbraSensitiveTaskStore

/**
 * Presentation/application-flow orchestrator used by OMBRA. Android lifecycle, URI, Binder and
 * PDF objects are intentionally absent. Reducer effects delegate only to application-layer ports.
 */
internal class OmbraApplicationOrchestrator(
    extractor: OmbraDocumentExtractor,
    analysisClient: OmbraAnalysisClient,
    exporter: OmbraDocumentExporter,
    taskStore: OmbraSensitiveTaskStore = InMemoryOmbraSensitiveTaskStore(),
    sourceCapabilityCleanup: OmbraDocumentSourceCapabilityCleanup = NoOpOmbraDocumentSourceCapabilityCleanup,
    private val onStateChanged: (OmbraWorkflowState) -> Unit = {},
) {
    private val effectExecutor =
        OmbraWorkflowEffectExecutor(
            extractor = extractor,
            analysisClient = analysisClient,
            exporter = exporter,
            taskStore = taskStore,
            sourceCapabilityCleanup = sourceCapabilityCleanup,
        )

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

    fun returnToReview(): Boolean = dispatch(OmbraWorkflowAction.ReturnToReviewRequested)

    fun reset(): Boolean = dispatch(OmbraWorkflowAction.ResetRequested)

    fun onProcessRecreated(): Boolean = dispatch(OmbraWorkflowAction.ProcessRecreated)

    private fun dispatch(action: OmbraWorkflowAction): Boolean {
        val transition = OmbraWorkflowReducer.reduce(state, action)
        val changed = transition.state != state || transition.effects.isNotEmpty()
        state = transition.state
        if (changed) onStateChanged(state)
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
