package io.github.daniele21.localllm.phonetest

internal data class ModelEffectsSnapshot(
    val distribution: PhoneModelDistributionState,
    val selectedModel: ImportedPhoneModel?,
    val loadedDigest: String?,
)

internal sealed interface ModelRecoveryCommand {
    data class AdoptLoadedSelection(val metadata: InstalledCatalogModelMetadata) : ModelRecoveryCommand

    data object ReleaseRuntime : ModelRecoveryCommand
}

internal sealed interface ModelCatalogCommand {
    data object Refresh : ModelCatalogCommand

    data class Download(val stableId: String) : ModelCatalogCommand

    data class CancelDownload(val stableId: String) : ModelCatalogCommand

    data class Install(val stableId: String) : ModelCatalogCommand

    data class VerifyInstalled(val stableId: String) : ModelCatalogCommand

    data class RequestRemoval(val stableId: String) : ModelCatalogCommand

    data class CancelRemoval(val stableId: String) : ModelCatalogCommand

    data class ConfirmRemoval(val stableId: String) : ModelCatalogCommand
}

/**
 * Activity-scoped boundary around model import, catalog and model-management effects.
 *
 * Android launchers, controllers, executors and the process-scoped runtime graph remain owned by
 * the Activity. The ViewModel-side coordinator retains only this interface.
 */
internal interface ModelEffects {
    fun snapshot(): ModelEffectsSnapshot

    fun requestImport(): Boolean

    fun executeCatalog(command: ModelCatalogCommand): Boolean

    fun selectInstalled(metadata: InstalledCatalogModelMetadata): Boolean

    fun verifySelected(): Boolean

    fun removeSelected(): Boolean

    fun executeRecovery(command: ModelRecoveryCommand): Boolean
}
