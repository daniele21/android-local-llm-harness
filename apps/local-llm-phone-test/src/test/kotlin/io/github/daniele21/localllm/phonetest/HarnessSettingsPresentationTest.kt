package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ModelDigest
import org.junit.Assert.assertEquals
import org.junit.Test

class HarnessSettingsPresentationTest {
    @Test
    fun `storage summary does not infer total usage when no model is selected`() {
        assertEquals("No selection", settingsSelectedModelStorageLabel(null))
    }

    @Test
    fun `storage summary reports selection without presenting selected size as total usage`() {
        val model = ImportedPhoneModel(
            digest = ModelDigest("a".repeat(64)),
            fileName = "qwen.gguf",
            sizeBytes = 512L * 1_048_576L,
            architecture = "qwen35",
            quantization = "Q4_K_M",
        )

        assertEquals("Selected", settingsSelectedModelStorageLabel(model))
    }
}
