package io.github.daniele21.localllm.phonetest

internal class HarnessModelActions(private val state: () -> HarnessUiState, private val dispatch: (HarnessUiEvent) -> Unit) {
    private var effects: ModelEffects? = null
    val recovery = HarnessModelRecoveryActions(state, dispatch)

    fun attach(attachedEffects: ModelEffects) {
        effects = attachedEffects
        recovery.attach(attachedEffects)
        val snapshot = attachedEffects.snapshot()
        dispatch(HarnessUiEvent.ModelDistributionChanged(snapshot.distribution))
        dispatch(HarnessUiEvent.ModelChanged(snapshot.selectedModel))
        dispatch(HarnessUiEvent.LoadedModelChanged(snapshot.loadedDigest))
    }

    fun detach(attachedEffects: ModelEffects) {
        if (effects === attachedEffects) effects = null
        recovery.detach(attachedEffects)
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

internal class HarnessModelRecoveryActions(private val state: () -> HarnessUiState, private val dispatch: (HarnessUiEvent) -> Unit) {
    private var effects: ModelEffects? = null

    fun attach(attachedEffects: ModelEffects) {
        effects = attachedEffects
    }

    fun detach(attachedEffects: ModelEffects) {
        if (effects === attachedEffects) effects = null
    }

    fun request(identity: String, action: HarnessModelRecoveryAction): Boolean {
        val current = state()
        if (current.busy) return false
        val option = HarnessModelDetails.present(current.modelInventory, identity)
            ?.recoveryOptions
            ?.firstOrNull { it.action == action }
            ?: return false
        return if (option.requiresConfirmation) {
            dispatch(
                HarnessUiEvent.ModelRecoveryConfirmationChanged(
                    HarnessModelRecoveryRequest(identity, action),
                ),
            )
            true
        } else {
            execute(identity, action)
        }
    }

    fun cancel() {
        dispatch(HarnessUiEvent.ModelRecoveryConfirmationChanged(null))
    }

    fun confirm(): Boolean {
        val current = state()
        val pending = current.modelRecoveryConfirmation ?: return false
        if (current.busy) return false
        val accepted = execute(pending.identity, pending.action)
        if (accepted) dispatch(HarnessUiEvent.ModelRecoveryConfirmationChanged(null))
        return accepted
    }

    private fun execute(identity: String, action: HarnessModelRecoveryAction): Boolean {
        val current = state()
        val effects = effects ?: return false
        val command = when (action) {
            HarnessModelRecoveryAction.RELEASE_RUNTIME -> ModelRecoveryCommand.ReleaseRuntime

            HarnessModelRecoveryAction.ADOPT_LOADED_SELECTION -> {
                val item = HarnessModelDetails.resolve(current.modelInventory, identity)
                    ?.takeIf { it.loaded && it.installed }
                    ?: return false
                val metadata = current.modelDistribution.models
                    .firstOrNull { it.stableId == item.stableId }
                    ?.installedModel
                    ?: return false
                ModelRecoveryCommand.AdoptLoadedSelection(metadata)
            }
        }
        return runCatching { effects.executeRecovery(command) }.getOrDefault(false)
    }
}
