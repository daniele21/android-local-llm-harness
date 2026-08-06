package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ModelDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessModelInventoryTest {
    @Test
    fun `catalog lifecycle is preserved before installation`() {
        val state = HarnessModelInventoryReconciler.reconcile(
            distribution = distribution(
                catalogModel("compatible", PhoneCatalogModelStatus.READY_TO_DOWNLOAD),
                catalogModel(
                    "incompatible",
                    PhoneCatalogModelStatus.INCOMPATIBLE,
                    compatible = false,
                ),
                catalogModel("downloading", PhoneCatalogModelStatus.DOWNLOADING),
            ),
            selectedModel = null,
        )

        assertEquals(
            listOf(
                HarnessModelLifecycle.READY_TO_DOWNLOAD,
                HarnessModelLifecycle.INCOMPATIBLE,
                HarnessModelLifecycle.DOWNLOADING,
            ),
            state.items.map(HarnessModelInventoryItem::lifecycle),
        )
        assertEquals(1, state.activeOperationCount)
        assertEquals(0, state.installedCount)
        assertEquals(0, state.degradedCount)
    }

    @Test
    fun `installed catalog model becomes selected then loaded`() {
        val digest = digest('a')
        val metadata = installedMetadata("catalog", digest)
        val selected = metadata.asImportedPhoneModel()
        val distribution = distribution(
            catalogModel(
                stableId = "catalog",
                status = PhoneCatalogModelStatus.INSTALLED,
                installed = metadata,
            ),
        )

        val selectedState = HarnessModelInventoryReconciler.reconcile(distribution, selected)
        val loadedState = HarnessModelInventoryReconciler.reconcile(
            distribution = distribution,
            selectedModel = selected,
            loadedDigest = digest.sha256,
        )

        assertEquals(HarnessModelLifecycle.SELECTED, selectedState.items.single().lifecycle)
        assertTrue(selectedState.items.single().selected)
        assertFalse(selectedState.items.single().loaded)
        assertEquals(HarnessModelLifecycle.LOADED, loadedState.items.single().lifecycle)
        assertTrue(loadedState.items.single().selected)
        assertTrue(loadedState.items.single().loaded)
        assertEquals(1, loadedState.installedCount)
    }

    @Test
    fun `external imported selection remains a valid inventory item`() {
        val imported = importedModel(digest('b'), "external.gguf")

        val state = HarnessModelInventoryReconciler.reconcile(
            distribution = distribution(),
            selectedModel = imported,
        )

        val item = state.items.single()
        assertEquals(HarnessModelOrigin.IMPORTED, item.origin)
        assertEquals(HarnessModelLifecycle.SELECTED, item.lifecycle)
        assertTrue(item.installed)
        assertTrue(item.selected)
        assertNull(item.degradation)
        assertEquals(imported.digest.sha256, state.selectedDigest)
    }

    @Test
    fun `runtime ownership absent from inventory is degraded explicitly`() {
        val loadedDigest = digest('c').sha256

        val state = HarnessModelInventoryReconciler.reconcile(
            distribution = distribution(),
            selectedModel = null,
            loadedDigest = loadedDigest,
        )

        val item = state.items.single()
        assertEquals(HarnessModelOrigin.RUNTIME, item.origin)
        assertEquals(HarnessModelLifecycle.DEGRADED, item.lifecycle)
        assertEquals(HarnessModelDegradation.LOADED_MODEL_NOT_IN_INVENTORY, item.degradation)
        assertEquals(1, state.degradedCount)
    }

    @Test
    fun `runtime ownership differing from selection is degraded without losing selection`() {
        val selectedDigest = digest('d')
        val loadedDigest = digest('e')
        val selectedMetadata = installedMetadata("selected", selectedDigest)
        val loadedMetadata = installedMetadata("loaded", loadedDigest)
        val state = HarnessModelInventoryReconciler.reconcile(
            distribution = distribution(
                catalogModel("selected", PhoneCatalogModelStatus.INSTALLED, installed = selectedMetadata),
                catalogModel("loaded", PhoneCatalogModelStatus.INSTALLED, installed = loadedMetadata),
            ),
            selectedModel = selectedMetadata.asImportedPhoneModel(),
            loadedDigest = loadedDigest.sha256,
        )

        val selectedItem = state.items.first { it.stableId == "selected" }
        val loadedItem = state.items.first { it.stableId == "loaded" }
        assertEquals(HarnessModelLifecycle.SELECTED, selectedItem.lifecycle)
        assertTrue(selectedItem.selected)
        assertEquals(HarnessModelLifecycle.DEGRADED, loadedItem.lifecycle)
        assertTrue(loadedItem.loaded)
        assertEquals(
            HarnessModelDegradation.LOADED_MODEL_DIFFERS_FROM_SELECTION,
            loadedItem.degradation,
        )
    }

    @Test
    fun `verified and installing states are counted independently`() {
        val state = HarnessModelInventoryReconciler.reconcile(
            distribution = distribution(
                catalogModel("verified", PhoneCatalogModelStatus.VERIFIED_READY_TO_INSTALL),
                catalogModel("installing", PhoneCatalogModelStatus.INSTALLING),
                catalogModel("failed", PhoneCatalogModelStatus.FAILED),
            ),
            selectedModel = null,
        )

        assertEquals(1, state.activeOperationCount)
        assertEquals(HarnessModelLifecycle.VERIFIED_READY_TO_INSTALL, state.items[0].lifecycle)
        assertEquals(HarnessModelLifecycle.INSTALLING, state.items[1].lifecycle)
        assertEquals(HarnessModelLifecycle.FAILED, state.items[2].lifecycle)
    }

    private fun distribution(vararg models: PhoneCatalogModelUi): PhoneModelDistributionState = PhoneModelDistributionState(
        catalogStatus = PhoneCatalogLoadStatus.READY,
        models = models.toList(),
        operationActive = models.any {
            it.status == PhoneCatalogModelStatus.DOWNLOADING ||
                it.status == PhoneCatalogModelStatus.INSTALLING
        },
    )

    private fun catalogModel(
        stableId: String,
        status: PhoneCatalogModelStatus,
        compatible: Boolean = true,
        installed: InstalledCatalogModelMetadata? = null,
    ): PhoneCatalogModelUi = PhoneCatalogModelUi(
        stableId = stableId,
        displayName = stableId.replaceFirstChar(Char::uppercase),
        description = "Test model",
        fileName = "$stableId.gguf",
        sizeBytes = 1_024L,
        architecture = "qwen2",
        quantization = "Q4_K_M",
        profileKey = "profile-$stableId",
        licenseName = "Apache-2.0",
        status = status,
        compatible = compatible,
        compatibilityReasons = emptyList(),
        compatibilityWarnings = emptyList(),
        installedModel = installed,
    )

    private fun installedMetadata(stableId: String, digest: ModelDigest): InstalledCatalogModelMetadata = InstalledCatalogModelMetadata(
        digest = digest,
        modelId = stableId,
        version = "1.0.0",
        displayName = stableId,
        profileKey = "profile-$stableId",
        applicationId = "play-internal-phone-test",
        useCaseId = "manual-inference-playground",
        fileName = "$stableId.gguf",
        sizeBytes = 1_024L,
        architecture = "qwen2",
        quantization = "Q4_K_M",
        installedAtEpochMs = 1L,
    )

    private fun importedModel(digest: ModelDigest, fileName: String): ImportedPhoneModel = ImportedPhoneModel(
        digest = digest,
        fileName = fileName,
        sizeBytes = 1_024L,
        architecture = "qwen2",
        quantization = "Q4_K_M",
    )

    private fun digest(character: Char): ModelDigest = ModelDigest(character.toString().repeat(64))
}
