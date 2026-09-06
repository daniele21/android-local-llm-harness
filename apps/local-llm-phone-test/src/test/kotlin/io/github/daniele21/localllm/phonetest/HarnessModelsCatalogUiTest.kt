package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.ui.designsystem.HarnessStatusTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessModelsCatalogUiTest {
    @Test
    fun catalogExposesTheThreeSupportedQwen35SizeGroups() {
        assertEquals(
            listOf("Qwen3.5 · 0.8B", "Qwen3.5 · 2B", "Qwen3.5 · 4B · 4-bit only"),
            ModelsSizeFilter.entries.mapNotNull(ModelsSizeFilter::groupLabel),
        )
    }

    @Test
    fun curatedStartingPointsAreExplicitPerSizeTier() {
        assertEquals("qwen35-08b-q4-k-m", ModelsSizeFilter.B08.suggestedModelId)
        assertEquals("qwen35-2b-q4-k-m", ModelsSizeFilter.B2.suggestedModelId)
        assertEquals("qwen35-4b-ud-q4-k-xl", ModelsSizeFilter.B4.suggestedModelId)
        assertEquals(null, ModelsSizeFilter.ALL.suggestedModelId)
    }

    @Test
    fun suggestedModelIsPresentedBeforeQuantizationAlternatives() {
        val alternatives = listOf(
            item("qwen35-4b-q4-k-m"),
            item("qwen35-4b-ud-q4-k-xl"),
            item("qwen35-4b-iq4-xs"),
        )

        val ordered = orderGroupItems(ModelsSizeFilter.B4, alternatives)

        assertEquals("qwen35-4b-ud-q4-k-xl", ordered.first().stableId)
        assertEquals(alternatives.map { it.stableId }.toSet(), ordered.map { it.stableId }.toSet())
    }

    @Test
    fun sizeFiltersMatchOnlyTheirQwen35ParameterGroup() {
        val compact = item("qwen35-08b-q4-k-m")
        val capable = item("qwen35-2b-q4-k-m")
        val fourB = item("qwen35-4b-ud-q4-k-xl")

        assertTrue(ModelsSizeFilter.B08.matches(compact))
        assertFalse(ModelsSizeFilter.B08.matches(capable))
        assertFalse(ModelsSizeFilter.B08.matches(fourB))
        assertTrue(ModelsSizeFilter.B2.matches(capable))
        assertFalse(ModelsSizeFilter.B2.matches(compact))
        assertFalse(ModelsSizeFilter.B2.matches(fourB))
        assertTrue(ModelsSizeFilter.B4.matches(fourB))
        assertFalse(ModelsSizeFilter.B4.matches(compact))
        assertFalse(ModelsSizeFilter.B4.matches(capable))
        assertTrue(ModelsSizeFilter.ALL.matches(compact))
        assertTrue(ModelsSizeFilter.ALL.matches(capable))
        assertTrue(ModelsSizeFilter.ALL.matches(fourB))
    }

    @Test
    fun availabilityFiltersSeparateInstalledAndNotInstalledModels() {
        val installed = item("qwen35-08b-q4-k-m", installed = true)
        val available = item("qwen35-2b-q4-k-m", installed = false)

        assertTrue(ModelsAvailabilityFilter.INSTALLED.matches(installed))
        assertFalse(ModelsAvailabilityFilter.INSTALLED.matches(available))
        assertTrue(ModelsAvailabilityFilter.AVAILABLE.matches(available))
        assertFalse(ModelsAvailabilityFilter.AVAILABLE.matches(installed))
        assertTrue(ModelsAvailabilityFilter.ALL.matches(installed))
        assertTrue(ModelsAvailabilityFilter.ALL.matches(available))
    }

    @Test
    fun modelStatusLabelsDescribeUserFacingLifecycleState() {
        val expected = mapOf(
            HarnessModelLifecycle.READY_TO_DOWNLOAD to "AVAILABLE",
            HarnessModelLifecycle.VERIFIED_READY_TO_INSTALL to "READY TO INSTALL",
            HarnessModelLifecycle.INSTALLED to "INSTALLED",
            HarnessModelLifecycle.SELECTED to "SELECTED",
            HarnessModelLifecycle.LOADED to "IN MEMORY",
            HarnessModelLifecycle.CANCELLED to "DOWNLOAD STOPPED",
            HarnessModelLifecycle.FAILED to "NEEDS ATTENTION",
            HarnessModelLifecycle.DEGRADED to "NEEDS RECOVERY",
            HarnessModelLifecycle.INCOMPATIBLE to "NOT COMPATIBLE",
        )

        expected.forEach { (lifecycle, label) ->
            assertEquals(label, modelCardStatusLabel(item("model", lifecycle = lifecycle), loading = false))
        }
        assertEquals("LOADING", modelCardStatusLabel(item("model"), loading = true))
    }

    @Test
    fun emptyStateExplainsTheActiveFilterWithoutLosingLoadedModelContext() {
        assertEquals(
            "No 4B installed models match this filter.",
            modelsEmptyStateDetail(ModelsAvailabilityFilter.INSTALLED, ModelsSizeFilter.B4, activeModelPresent = false),
        )
        assertEquals(
            "No other 2B models match this filter.",
            modelsEmptyStateDetail(ModelsAvailabilityFilter.ALL, ModelsSizeFilter.B2, activeModelPresent = true),
        )
    }

    @Test
    fun modelFailuresKeepErrorSeverity() {
        assertEquals(HarnessStatusTone.ERROR, HarnessModelLifecycle.FAILED.statusTone())
        assertEquals(HarnessStatusTone.WARNING, HarnessModelLifecycle.DEGRADED.statusTone())
        assertEquals(HarnessStatusTone.WARNING, HarnessModelLifecycle.INCOMPATIBLE.statusTone())
    }

    private fun item(
        stableId: String,
        installed: Boolean = false,
        lifecycle: HarnessModelLifecycle = if (installed) HarnessModelLifecycle.INSTALLED else HarnessModelLifecycle.READY_TO_DOWNLOAD,
    ): HarnessModelInventoryItem = HarnessModelInventoryItem(
        stableId = stableId,
        displayName = stableId,
        origin = HarnessModelOrigin.CATALOG,
        lifecycle = lifecycle,
        installed = installed,
    )
}
