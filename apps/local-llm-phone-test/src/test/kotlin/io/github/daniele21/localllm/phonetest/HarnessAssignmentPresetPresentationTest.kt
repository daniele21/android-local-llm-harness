package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.models.PresetCreationSource
import io.github.daniele21.localllm.models.PresetLifecycleState
import org.junit.Assert.assertEquals
import org.junit.Test

class HarnessAssignmentPresetPresentationTest {
    @Test
    fun `preset origin uses task language`() {
        assertEquals("Suggested", preset(PresetCreationSource.SUGGESTED).originLabel())
        assertEquals("Custom", preset(PresetCreationSource.CUSTOM).originLabel())
    }

    private fun preset(source: PresetCreationSource): HarnessPresetSummary = HarnessPresetSummary(
        presetId = "balanced",
        revision = 1,
        displayName = "Balanced",
        description = "Balanced preset",
        source = source,
        lifecycleState = PresetLifecycleState.PUBLISHED,
        modelProfileId = "qwen35-2b-q4",
        contextTokens = 4_096,
        isDefault = true,
    )
}
