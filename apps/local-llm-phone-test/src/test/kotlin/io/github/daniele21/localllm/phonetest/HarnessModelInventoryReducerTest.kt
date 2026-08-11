package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ModelDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessModelInventoryReducerTest {
    @Test
    fun `distribution and selection events converge on one inventory`() {
        val digest = ModelDigest("a".repeat(64))
        val metadata = metadata(digest)
        val distribution = PhoneModelDistributionState(
            catalogStatus = PhoneCatalogLoadStatus.READY,
            models = listOf(catalogModel(metadata)),
            message = "Catalog ready",
        )
        val selected = metadata.asImportedPhoneModel()

        val afterCatalog = HarnessUiReducer.reduce(
            HarnessUiState(),
            HarnessUiEvent.ModelDistributionChanged(distribution),
        )
        val afterSelection = HarnessUiReducer.reduce(
            afterCatalog,
            HarnessUiEvent.ModelChanged(selected),
        )

        assertEquals("Catalog ready", afterCatalog.operationStatus)
        assertEquals(HarnessModelLifecycle.INSTALLED, afterCatalog.modelInventory.items.single().lifecycle)
        assertEquals(HarnessModelLifecycle.SELECTED, afterSelection.modelInventory.items.single().lifecycle)
        assertEquals(digest.sha256, afterSelection.modelInventory.selectedDigest)
        assertFalse(afterSelection.removalConfirmationPending)
    }

    @Test
    fun `loaded ownership is preserved across catalog refreshes`() {
        val digest = ModelDigest("b".repeat(64))
        val metadata = metadata(digest)
        val selected = metadata.asImportedPhoneModel()
        val initial = HarnessUiState(
            importedModel = selected,
            modelDistribution = PhoneModelDistributionState(
                catalogStatus = PhoneCatalogLoadStatus.READY,
                models = listOf(catalogModel(metadata)),
            ),
        )
        val loaded = HarnessUiReducer.reduce(
            initial,
            HarnessUiEvent.LoadedModelChanged(digest.sha256),
        )
        val refreshed = HarnessUiReducer.reduce(
            loaded,
            HarnessUiEvent.ModelDistributionChanged(
                initial.modelDistribution.copy(message = "Refreshed"),
            ),
        )

        assertEquals(digest.sha256, refreshed.modelInventory.loadedDigest)
        assertEquals(HarnessModelLifecycle.LOADED, refreshed.modelInventory.items.single().lifecycle)
        assertTrue(refreshed.modelInventory.items.single().selected)
        assertTrue(refreshed.modelInventory.items.single().loaded)
    }

    @Test
    fun `clearing selection retains runtime mismatch evidence`() {
        val selectedDigest = ModelDigest("c".repeat(64))
        val loadedDigest = ModelDigest("d".repeat(64))
        val state = HarnessUiState(
            importedModel = importedModel(selectedDigest),
            modelDistribution = PhoneModelDistributionState(),
            modelInventory = HarnessModelInventoryReconciler.reconcile(
                distribution = PhoneModelDistributionState(),
                selectedModel = importedModel(selectedDigest),
                loadedDigest = loadedDigest.sha256,
            ),
            removalConfirmationPending = true,
        )

        val reduced = HarnessUiReducer.reduce(state, HarnessUiEvent.ModelChanged(null))

        assertNull(reduced.importedModel)
        assertNull(reduced.modelInventory.selectedDigest)
        assertEquals(loadedDigest.sha256, reduced.modelInventory.loadedDigest)
        assertEquals(HarnessModelLifecycle.DEGRADED, reduced.modelInventory.items.single().lifecycle)
        assertFalse(reduced.removalConfirmationPending)
    }

    private fun catalogModel(metadata: InstalledCatalogModelMetadata): PhoneCatalogModelUi = PhoneCatalogModelUi(
        stableId = "model@1.0.0",
        displayName = "Model",
        description = "Test model",
        fileName = metadata.fileName,
        sizeBytes = metadata.sizeBytes,
        architecture = metadata.architecture,
        quantization = metadata.quantization,
        profileKey = metadata.profileKey,
        licenseName = "Apache-2.0",
        status = PhoneCatalogModelStatus.INSTALLED,
        compatible = true,
        compatibilityReasons = emptyList(),
        compatibilityWarnings = emptyList(),
        installedModel = metadata,
    )

    private fun metadata(digest: ModelDigest): InstalledCatalogModelMetadata = InstalledCatalogModelMetadata(
        digest = digest,
        modelId = "model",
        version = "1.0.0",
        displayName = "Model",
        profileKey = "profile",
        applicationId = "play-internal-phone-test",
        useCaseId = "manual-inference-playground",
        fileName = "model.gguf",
        sizeBytes = 1_024L,
        architecture = "qwen35",
        quantization = "Q4_K_M",
        installedAtEpochMs = 1L,
    )

    private fun importedModel(digest: ModelDigest): ImportedPhoneModel = ImportedPhoneModel(
        digest = digest,
        fileName = "external.gguf",
        sizeBytes = 1_024L,
        architecture = "qwen35",
        quantization = "Q4_K_M",
    )
}
