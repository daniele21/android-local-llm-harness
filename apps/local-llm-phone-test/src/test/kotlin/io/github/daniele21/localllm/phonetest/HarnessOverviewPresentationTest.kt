package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ModelDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessOverviewPresentationTest {
    @Test
    fun `no model produces choose-model action and unavailable evidence`() {
        val presentation = harnessOverviewPresentation(
            state = HarnessUiState(),
            diagnostics = DiagnosticsUiState(null, emptyList(), emptyList()),
            processPss = null,
            thermalStatus = null,
        )

        assertEquals(HarnessOverviewPrimaryAction.CHOOSE_MODEL, presentation.primaryAction)
        assertEquals("Not selected", presentation.selectedModelStatus)
        assertEquals("Unavailable", presentation.runtimeValue)
        assertEquals("Unavailable", presentation.processPss)
        assertEquals("Unavailable", presentation.thermalStatus)
        assertFalse(presentation.resourceEvidenceAvailable)
        assertEquals("Not run", presentation.healthValue)
        assertEquals("Not run", presentation.latestRunStatus)
    }

    @Test
    fun `selected model is not presented as resident without residency evidence`() {
        val model = importedModel("a")
        val state = HarnessUiState(importedModel = model)

        val presentation = harnessOverviewPresentation(
            state = state,
            diagnostics = DiagnosticsUiState(null, emptyList(), emptyList()),
            processPss = null,
            thermalStatus = null,
        )

        assertEquals(HarnessOverviewPrimaryAction.RUN_PROMPT, presentation.primaryAction)
        assertEquals("Selected", presentation.selectedModelStatus)
        assertEquals("Not resident", presentation.residencyStatus)
        assertFalse(presentation.residencyPositive)
    }

    @Test
    fun `selected and loaded model is explicitly resident`() {
        val model = importedModel("b")
        val digest = model.digest.sha256
        val state = HarnessUiState(
            importedModel = model,
            modelInventory = HarnessModelInventoryState(
                items = listOf(
                    HarnessModelInventoryItem(
                        stableId = "model@1",
                        displayName = "Qwen test",
                        origin = HarnessModelOrigin.CATALOG,
                        digest = digest,
                        lifecycle = HarnessModelLifecycle.LOADED,
                        installed = true,
                        selected = true,
                        loaded = true,
                    ),
                ),
                selectedDigest = digest,
                loadedDigest = digest,
            ),
        )

        val presentation = harnessOverviewPresentation(
            state = state,
            diagnostics = DiagnosticsUiState(null, emptyList(), emptyList()),
            processPss = "1.2 GiB",
            thermalStatus = "LIGHT",
        )

        assertEquals("Qwen test", presentation.heroTitle)
        assertEquals("Qwen test", presentation.selectedModelValue)
        assertEquals("In memory", presentation.selectedModelStatus)
        assertEquals("Resident", presentation.residencyStatus)
        assertTrue(presentation.residencyPositive)
        assertEquals("1.2 GiB", presentation.processPss)
        assertEquals("LIGHT", presentation.thermalStatus)
        assertTrue(presentation.resourceEvidenceAvailable)
    }

    @Test
    fun `runtime mismatch becomes recovery action instead of ready state`() {
        val selected = importedModel("c")
        val loadedDigest = ModelDigest("d".repeat(64)).sha256
        val state = HarnessUiState(
            importedModel = selected,
            modelInventory = HarnessModelInventoryState(
                items = listOf(
                    HarnessModelInventoryItem(
                        stableId = "runtime::$loadedDigest",
                        displayName = "Runtime-owned model",
                        origin = HarnessModelOrigin.RUNTIME,
                        digest = loadedDigest,
                        lifecycle = HarnessModelLifecycle.DEGRADED,
                        loaded = true,
                        degradation = HarnessModelDegradation.LOADED_MODEL_NOT_IN_INVENTORY,
                    ),
                ),
                selectedDigest = selected.digest.sha256,
                loadedDigest = loadedDigest,
            ),
        )

        val presentation = harnessOverviewPresentation(
            state = state,
            diagnostics = DiagnosticsUiState(null, emptyList(), emptyList()),
            processPss = null,
            thermalStatus = null,
        )

        assertEquals(HarnessOverviewPrimaryAction.RESOLVE_MODEL_STATE, presentation.primaryAction)
        assertEquals("Needs attention", presentation.selectedModelStatus)
        assertEquals("Mismatch", presentation.residencyStatus)
    }

    @Test
    fun `unrelated degraded inventory item does not invalidate selected model`() {
        val selected = importedModel("e")
        val selectedDigest = selected.digest.sha256
        val degradedDigest = ModelDigest("f".repeat(64)).sha256
        val state = HarnessUiState(
            importedModel = selected,
            modelInventory = HarnessModelInventoryState(
                items = listOf(
                    HarnessModelInventoryItem(
                        stableId = "model@selected",
                        displayName = "Selected Qwen",
                        origin = HarnessModelOrigin.CATALOG,
                        digest = selectedDigest,
                        lifecycle = HarnessModelLifecycle.INSTALLED,
                        installed = true,
                        selected = true,
                    ),
                    HarnessModelInventoryItem(
                        stableId = "orphan@$degradedDigest",
                        displayName = "Unrelated degraded model",
                        origin = HarnessModelOrigin.RUNTIME,
                        digest = degradedDigest,
                        lifecycle = HarnessModelLifecycle.DEGRADED,
                        degradation = HarnessModelDegradation.LOADED_MODEL_NOT_IN_INVENTORY,
                    ),
                ),
                selectedDigest = selectedDigest,
            ),
        )

        val presentation = harnessOverviewPresentation(
            state = state,
            diagnostics = DiagnosticsUiState(null, emptyList(), emptyList()),
            processPss = null,
            thermalStatus = null,
        )

        assertEquals(HarnessOverviewPrimaryAction.RUN_PROMPT, presentation.primaryAction)
        assertEquals("Selected", presentation.selectedModelStatus)
        assertEquals("Selected Qwen", presentation.heroTitle)
    }

    @Test
    fun `loaded model without a selection is identified as other model resident`() {
        val loadedDigest = ModelDigest("1".repeat(64)).sha256
        val state = HarnessUiState(
            modelInventory = HarnessModelInventoryState(loadedDigest = loadedDigest),
        )

        val presentation = harnessOverviewPresentation(
            state = state,
            diagnostics = DiagnosticsUiState(null, emptyList(), emptyList()),
            processPss = null,
            thermalStatus = null,
        )

        assertEquals(HarnessOverviewPrimaryAction.CHOOSE_MODEL, presentation.primaryAction)
        assertEquals("Other model resident", presentation.residencyStatus)
        assertFalse(presentation.residencyPositive)
    }

    private fun importedModel(seed: String): ImportedPhoneModel = ImportedPhoneModel(
        digest = ModelDigest(seed.repeat(64)),
        fileName = "qwen.gguf",
        sizeBytes = 1_024L,
        architecture = "qwen35",
        quantization = "Q4_K_M",
    )
}
