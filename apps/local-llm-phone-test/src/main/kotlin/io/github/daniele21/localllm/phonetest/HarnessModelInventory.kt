package io.github.daniele21.localllm.phonetest

internal enum class HarnessModelOrigin {
    CATALOG,
    IMPORTED,
    RUNTIME,
}

internal enum class HarnessModelLifecycle {
    INCOMPATIBLE,
    READY_TO_DOWNLOAD,
    DOWNLOADING,
    VERIFIED_READY_TO_INSTALL,
    INSTALLING,
    INSTALLED,
    SELECTED,
    LOADED,
    CANCELLED,
    FAILED,
    DEGRADED,
}

internal enum class HarnessModelDegradation {
    LOADED_MODEL_NOT_IN_INVENTORY,
    LOADED_MODEL_DIFFERS_FROM_SELECTION,
}

internal data class HarnessModelInventoryItem(
    val stableId: String,
    val displayName: String,
    val origin: HarnessModelOrigin,
    val digest: String? = null,
    val lifecycle: HarnessModelLifecycle,
    val compatible: Boolean = true,
    val installed: Boolean = false,
    val selected: Boolean = false,
    val loaded: Boolean = false,
    val detail: String? = null,
    val degradation: HarnessModelDegradation? = null,
)

internal data class HarnessModelInventoryState(
    val items: List<HarnessModelInventoryItem> = emptyList(),
    val selectedDigest: String? = null,
    val loadedDigest: String? = null,
) {
    val installedCount: Int
        get() = items.count(HarnessModelInventoryItem::installed)

    val activeOperationCount: Int
        get() = items.count {
            it.lifecycle == HarnessModelLifecycle.DOWNLOADING ||
                it.lifecycle == HarnessModelLifecycle.INSTALLING
        }

    val degradedCount: Int
        get() = items.count { it.lifecycle == HarnessModelLifecycle.DEGRADED }
}

internal object HarnessModelInventoryReconciler {
    fun reconcile(
        distribution: PhoneModelDistributionState,
        selectedModel: ImportedPhoneModel?,
        loadedDigest: String? = null,
    ): HarnessModelInventoryState {
        val selectedDigest = selectedModel?.digest?.sha256
        val catalogItems = distribution.models.map { model ->
            val digest = model.installedModel?.digest?.sha256
            val selected = digest != null && digest == selectedDigest
            val loaded = digest != null && digest == loadedDigest
            val mismatch = loaded && selectedDigest != null && selectedDigest != loadedDigest
            HarnessModelInventoryItem(
                stableId = model.stableId,
                displayName = model.displayName,
                origin = HarnessModelOrigin.CATALOG,
                digest = digest,
                lifecycle = when {
                    mismatch -> HarnessModelLifecycle.DEGRADED
                    loaded -> HarnessModelLifecycle.LOADED
                    selected -> HarnessModelLifecycle.SELECTED
                    else -> model.status.toLifecycle()
                },
                compatible = model.compatible,
                installed = model.installedModel != null,
                selected = selected,
                loaded = loaded,
                detail = model.detail,
                degradation = if (mismatch) {
                    HarnessModelDegradation.LOADED_MODEL_DIFFERS_FROM_SELECTION
                } else {
                    null
                },
            )
        }.toMutableList()

        val selectedRepresented = selectedDigest != null && catalogItems.any { it.digest == selectedDigest }
        if (selectedModel != null && !selectedRepresented) {
            val loaded = selectedDigest == loadedDigest
            catalogItems += HarnessModelInventoryItem(
                stableId = "imported::$selectedDigest",
                displayName = selectedModel.fileName,
                origin = HarnessModelOrigin.IMPORTED,
                digest = selectedDigest,
                lifecycle = if (loaded) HarnessModelLifecycle.LOADED else HarnessModelLifecycle.SELECTED,
                installed = true,
                selected = true,
                loaded = loaded,
                detail = "Imported GGUF selected for this application",
            )
        }

        val loadedRepresented = loadedDigest != null && catalogItems.any { it.digest == loadedDigest }
        if (loadedDigest != null && !loadedRepresented) {
            catalogItems += HarnessModelInventoryItem(
                stableId = "runtime::$loadedDigest",
                displayName = "Runtime-owned model",
                origin = HarnessModelOrigin.RUNTIME,
                digest = loadedDigest,
                lifecycle = HarnessModelLifecycle.DEGRADED,
                loaded = true,
                detail = "The runtime owns a model that is absent from the current inventory",
                degradation = HarnessModelDegradation.LOADED_MODEL_NOT_IN_INVENTORY,
            )
        }

        return HarnessModelInventoryState(
            items = catalogItems,
            selectedDigest = selectedDigest,
            loadedDigest = loadedDigest,
        )
    }

    private fun PhoneCatalogModelStatus.toLifecycle(): HarnessModelLifecycle = when (this) {
        PhoneCatalogModelStatus.INCOMPATIBLE -> HarnessModelLifecycle.INCOMPATIBLE
        PhoneCatalogModelStatus.READY_TO_DOWNLOAD -> HarnessModelLifecycle.READY_TO_DOWNLOAD
        PhoneCatalogModelStatus.DOWNLOADING -> HarnessModelLifecycle.DOWNLOADING
        PhoneCatalogModelStatus.VERIFIED_READY_TO_INSTALL -> HarnessModelLifecycle.VERIFIED_READY_TO_INSTALL
        PhoneCatalogModelStatus.INSTALLING -> HarnessModelLifecycle.INSTALLING
        PhoneCatalogModelStatus.INSTALLED -> HarnessModelLifecycle.INSTALLED
        PhoneCatalogModelStatus.CANCELLED -> HarnessModelLifecycle.CANCELLED
        PhoneCatalogModelStatus.FAILED -> HarnessModelLifecycle.FAILED
    }
}
