package io.github.daniele21.localllm.phonetest

internal enum class HarnessModelOrigin {
    CATALOG,
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
    val sizeBytes: Long? = null,
    val architecture: String? = null,
    val quantization: String? = null,
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

    val installedBytes: Long
        get() = items.filter(HarnessModelInventoryItem::installed).sumOf { it.sizeBytes ?: 0L }

    val selectedItem: HarnessModelInventoryItem?
        get() = items.firstOrNull(HarnessModelInventoryItem::selected)

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
        val requestedSelectedDigest = selectedModel?.digest?.sha256
        val selectedDigest = requestedSelectedDigest?.takeIf { digest ->
            distribution.models.any { it.installedModel?.digest?.sha256 == digest }
        }
        val items = distribution.models
            .map { it.toInventoryItem(selectedDigest, loadedDigest) }
            .toMutableList()

        appendUnknownRuntimeOwnership(items, loadedDigest)

        return HarnessModelInventoryState(
            items = items,
            selectedDigest = selectedDigest,
            loadedDigest = loadedDigest,
        )
    }

    private fun PhoneCatalogModelUi.toInventoryItem(selectedDigest: String?, loadedDigest: String?): HarnessModelInventoryItem {
        val installedDigest = installedModel?.digest?.sha256
        val selected = installedDigest != null && installedDigest == selectedDigest
        val loaded = installedDigest != null && installedDigest == loadedDigest
        val mismatch = loaded && selectedDigest != null && selectedDigest != loadedDigest
        return HarnessModelInventoryItem(
            stableId = stableId,
            displayName = displayName,
            origin = HarnessModelOrigin.CATALOG,
            digest = installedDigest,
            sizeBytes = sizeBytes,
            architecture = architecture,
            quantization = quantization,
            lifecycle = lifecycle(mismatch, loaded, selected),
            compatible = compatible,
            installed = installedModel != null,
            selected = selected,
            loaded = loaded,
            detail = detail,
            degradation = mismatch.takeIf { it }?.let {
                HarnessModelDegradation.LOADED_MODEL_DIFFERS_FROM_SELECTION
            },
        )
    }

    private fun PhoneCatalogModelUi.lifecycle(mismatch: Boolean, loaded: Boolean, selected: Boolean): HarnessModelLifecycle = when {
        mismatch -> HarnessModelLifecycle.DEGRADED
        loaded -> HarnessModelLifecycle.LOADED
        selected -> HarnessModelLifecycle.SELECTED
        else -> status.toLifecycle()
    }

    private fun appendUnknownRuntimeOwnership(items: MutableList<HarnessModelInventoryItem>, loadedDigest: String?) {
        if (loadedDigest == null || items.represents(loadedDigest)) return
        items += HarnessModelInventoryItem(
            stableId = "runtime::$loadedDigest",
            displayName = "Runtime-owned model",
            origin = HarnessModelOrigin.RUNTIME,
            digest = loadedDigest,
            lifecycle = HarnessModelLifecycle.DEGRADED,
            loaded = true,
            detail = "The runtime owns a model that is absent from the current catalog inventory",
            degradation = HarnessModelDegradation.LOADED_MODEL_NOT_IN_INVENTORY,
        )
    }

    private fun List<HarnessModelInventoryItem>.represents(digest: String?): Boolean = digest != null && any { it.digest == digest }

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
