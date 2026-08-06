package io.github.daniele21.localllm.phonetest

internal data class ModelEffectsSnapshot(
    val distribution: PhoneModelDistributionState,
    val selectedModel: ImportedPhoneModel?,
    val loadedDigest: String?,
)

/**
 * Activity-scoped boundary around model import, catalog and model-management effects.
 *
 * The ViewModel retains only this interface. Android launchers, controllers, executors and the
 * process-scoped runtime graph remain owned by the Activity and must be detached on disposal.
 */
internal interface ModelEffects {
    fun snapshot(): ModelEffectsSnapshot

    fun requestImport(): Boolean

    fun refresh(): Boolean

    fun download(stableId: String): Boolean

    fun cancelDownload(stableId: String): Boolean

    fun install(stableId: String): Boolean

    fun verifyInstalled(stableId: String): Boolean

    fun requestCatalogRemoval(stableId: String): Boolean

    fun cancelCatalogRemoval(stableId: String): Boolean

    fun confirmCatalogRemoval(stableId: String): Boolean

    fun selectInstalled(metadata: InstalledCatalogModelMetadata): Boolean

    fun verifySelected(): Boolean

    fun removeSelected(): Boolean
}
