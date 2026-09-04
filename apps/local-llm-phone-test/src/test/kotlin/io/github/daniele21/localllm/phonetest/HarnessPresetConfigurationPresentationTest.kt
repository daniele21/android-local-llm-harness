package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.models.PresetCreationSource
import io.github.daniele21.localllm.models.PresetLifecycleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessPresetConfigurationPresentationTest {
    @Test
    fun `PII model options use exact ombra runtime profile identities`() {
        val options = harnessPresetModelOptions(HarnessSharedRuntimeBindings.ombraUseCaseId.value)

        assertTrue(options.isNotEmpty())
        assertTrue(options.all { it.modelProfileId.endsWith("-ombra-pii") })
        assertTrue(options.any { it.modelId == "qwen35-08b-q4-k-m" })
        assertTrue(options.any { it.modelId == "qwen35-2b-q4-k-m" })
    }

    @Test
    fun `console model options use exact shared console runtime profile identities`() {
        val options = harnessPresetModelOptions(HarnessSharedRuntimeBindings.consoleUseCaseId.value)

        assertTrue(options.isNotEmpty())
        assertTrue(options.all { it.modelProfileId.endsWith("-shared-console") })
    }

    @Test
    fun `stale explicit runtime profile is rejected while automatic remains valid`() {
        val useCaseId = HarnessSharedRuntimeBindings.ombraUseCaseId.value

        assertTrue(isHarnessPresetModelSelectionValid(useCaseId, null))
        assertFalse(isHarnessPresetModelSelectionValid(useCaseId, "qwen35-obsolete-profile-ombra-pii"))
    }

    @Test
    fun `automatic model target exposes tier dependent generation values`() {
        val summary = harnessPresetConfigurationSummary(
            useCaseId = HarnessSharedRuntimeBindings.ombraUseCaseId.value,
            preset = preset(inferencePresetId = "qwen35-text-quality"),
            selectedModelProfileId = null,
        )

        assertTrue(summary.available)
        assertEquals("Automatic compatible local model", summary.value("Model target"))
        assertEquals("qwen35-text-quality · v3", summary.value("Inference profile"))
        assertEquals("512 (0.8B) / 768 (2B)", summary.value("Max output tokens"))
        assertEquals("4096", summary.value("Context tokens"))
        assertEquals("1 min", summary.value("Warm retention"))
        assertEquals("Disabled", summary.value("Prefix snapshot"))
    }

    @Test
    fun `specific model target resolves one tier without silent fallback`() {
        val option = harnessPresetModelOptions(HarnessSharedRuntimeBindings.ombraUseCaseId.value)
            .single { it.modelId == "qwen35-2b-q4-k-m" }

        val summary = harnessPresetConfigurationSummary(
            useCaseId = HarnessSharedRuntimeBindings.ombraUseCaseId.value,
            preset = preset(inferencePresetId = "qwen35-text-quality"),
            selectedModelProfileId = option.modelProfileId,
        )

        assertTrue(summary.available)
        assertEquals(option.displayName, summary.value("Model target"))
        assertEquals("768", summary.value("Max output tokens"))
    }

    @Test
    fun `unknown explicit model target makes effective configuration unavailable`() {
        val summary = harnessPresetConfigurationSummary(
            useCaseId = HarnessSharedRuntimeBindings.ombraUseCaseId.value,
            preset = preset(inferencePresetId = "qwen35-json"),
            selectedModelProfileId = "unknown-profile-ombra-pii",
        )

        assertFalse(summary.available)
        assertTrue(summary.unavailableReason.orEmpty().contains("not available"))
    }

    private fun preset(inferencePresetId: String): HarnessPresetSummary = HarnessPresetSummary(
        presetId = "base",
        revision = 1,
        displayName = "Base",
        description = "Base preset",
        source = PresetCreationSource.SUGGESTED,
        lifecycleState = PresetLifecycleState.PUBLISHED,
        modelProfileId = null,
        contextTokens = 4_096,
        isDefault = true,
        inferencePresetId = inferencePresetId,
        inferencePresetRevision = 3,
        retainModelWarmMs = 60_000,
        reuseStatelessContext = false,
        enablePrefixSnapshot = false,
        enableDeterministicResultCache = false,
    )

    private fun HarnessPresetConfigurationSummary.value(label: String): String = rows.single { it.label == label }.value
}
