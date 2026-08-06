package io.github.daniele21.localllm.phonetest

internal class HarnessModelActions(private val state: () -> HarnessUiState, private val dispatch: (HarnessUiEvent) -> Unit) {
    private var effects: ModelEffects? = null

    fun attach(attachedEffects: ModelEffects) {
        effects = attachedEffects
        val snapshot = attachedEffects.snapshot()
        dispatch(HarnessUiEvent.ModelDistributionChanged(snapshot.distribution))
        dispatch(HarnessUiEvent.ModelChanged(snapshot.selectedModel))
        dispatch(HarnessUiEvent.LoadedModelChanged(snapshot.loadedDigest))
    }

    fun detach(attachedEffects: ModelEffects) {
        if (effects === attachedEffects) effects = null
    }

    fun requestImport(): Boolean {
        if (state().busy) return false
        return execute(ModelEffects::requestImport)
    }

    fun executeCatalog(command: ModelCatalogCommand): Boolean = execute { it.executeCatalog(command) }

    fun selectInstalled(metadata: InstalledCatalogModelMetadata): Boolean = execute { it.selectInstalled(metadata) }

    fun verifySelected(): Boolean {
        val current = state()
        if (current.importedModel == null || current.busy) return false
        return execute(ModelEffects::verifySelected)
    }

    fun requestSelectedRemoval(): Boolean {
        val current = state()
        if (current.importedModel == null || current.busy) return false
        dispatch(HarnessUiEvent.RemovalConfirmationChanged(true))
        return true
    }

    fun cancelSelectedRemoval() {
        dispatch(HarnessUiEvent.RemovalConfirmationChanged(false))
    }

    fun confirmSelectedRemoval(): Boolean {
        val current = state()
        if (!current.removalConfirmationPending || current.importedModel == null || current.busy) return false
        val accepted = execute(ModelEffects::removeSelected)
        if (accepted) dispatch(HarnessUiEvent.RemovalConfirmationChanged(false))
        return accepted
    }

    private fun execute(command: (ModelEffects) -> Boolean): Boolean = runCatching {
        effects?.let(command) ?: false
    }.getOrDefault(false)
}
