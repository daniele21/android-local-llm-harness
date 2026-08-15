package io.github.daniele21.localllm.console.presentation

internal enum class OmbraFailurePrimaryAction {
    RETRY,
    RETURN_TO_REVIEW,
}

internal data class OmbraFailurePresentation(val detail: String, val primaryAction: OmbraFailurePrimaryAction)

internal object OmbraFailureProjector {
    fun project(workflow: OmbraWorkflowState): OmbraFailurePresentation {
        require(workflow.stage == OmbraWorkflowStage.FAILED) { "Failure presentation requires FAILED workflow state" }

        return OmbraFailurePresentation(
            detail = failureDetail(workflow.failureCode),
            primaryAction =
            if (workflow.retryTarget == OmbraRetryTarget.EXPORT) {
                OmbraFailurePrimaryAction.RETURN_TO_REVIEW
            } else {
                OmbraFailurePrimaryAction.RETRY
            },
        )
    }

    private fun failureDetail(code: OmbraFailureCode?): String = when (code) {
        OmbraFailureCode.EXTRACTION_FAILED ->
            "Il PDF non può essere letto in modo affidabile. Puoi riprovare o scegliere un nuovo documento."

        OmbraFailureCode.ANALYSIS_FAILED ->
            "L’analisi locale non ha prodotto un risultato valido. Nessun risultato parziale viene usato."

        OmbraFailureCode.EXPORT_FAILED ->
            "L’export non è stato completato. Il file parziale viene rimosso e non viene usato come risultato."

        null -> "L’operazione è stata interrotta senza produrre un risultato utilizzabile."
    }
}
