package io.github.daniele21.localllm.phonetest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HarnessModelsCatalogPresentationTest {
    @Test
    fun `loading state is tied to the exact model being prepared`() {
        val model = catalogModel("qwen35-08b-q4-k-m", "Qwen3.5-0.8B-Q4_K_M.gguf")
        val state = HarnessUiState(
            controllerBusy = true,
            modelDistribution = PhoneModelDistributionState(models = listOf(model)),
        )
        val feedback = ModelActionFeedbackState(
            latest = "Loading ${model.fileName} into memory",
            history = listOf("Loading ${model.fileName} into memory"),
        )

        assertEquals(model.stableId, loadingStableId(state, feedback))
    }

    @Test
    fun `busy controller is not mislabeled as model loading`() {
        val model = catalogModel("qwen35-08b-q4-k-m", "Qwen3.5-0.8B-Q4_K_M.gguf")
        val state = HarnessUiState(
            controllerBusy = true,
            modelDistribution = PhoneModelDistributionState(models = listOf(model)),
        )

        assertNull(
            loadingStableId(
                state,
                ModelActionFeedbackState(latest = "Verifying installed model before loading"),
            ),
        )
    }

    @Test
    fun `card distinguishes installed selected loaded and transient loading`() {
        val installed = inventoryItem(HarnessModelLifecycle.INSTALLED)
        val selected = inventoryItem(HarnessModelLifecycle.SELECTED)
        val loaded = inventoryItem(HarnessModelLifecycle.LOADED)

        assertEquals("INSTALLED", modelCardStatusLabel(installed, loading = false))
        assertEquals("SELECTED", modelCardStatusLabel(selected, loading = false))
        assertEquals("LOADED", modelCardStatusLabel(loaded, loading = false))
        assertEquals("LOADING", modelCardStatusLabel(installed, loading = true))
    }

    private fun catalogModel(stableId: String, fileName: String) = PhoneCatalogModelUi(
        stableId = stableId,
        displayName = "Qwen 3.5 0.8B Q4_K_M",
        description = "test",
        fileName = fileName,
        sizeBytes = 532_000_000L,
        architecture = "qwen35",
        quantization = "Q4_K_M",
        profileKey = "qwen35-08b-q4-k-m",
        licenseName = "Apache-2.0",
        status = PhoneCatalogModelStatus.INSTALLED,
        compatible = true,
        compatibilityReasons = emptyList(),
        compatibilityWarnings = emptyList(),
    )

    private fun inventoryItem(lifecycle: HarnessModelLifecycle) = HarnessModelInventoryItem(
        stableId = "qwen35-08b-q4-k-m",
        displayName = "Qwen 3.5 0.8B Q4_K_M",
        origin = HarnessModelOrigin.CATALOG,
        lifecycle = lifecycle,
        installed = true,
        selected = lifecycle == HarnessModelLifecycle.SELECTED || lifecycle == HarnessModelLifecycle.LOADED,
        loaded = lifecycle == HarnessModelLifecycle.LOADED,
    )
}
