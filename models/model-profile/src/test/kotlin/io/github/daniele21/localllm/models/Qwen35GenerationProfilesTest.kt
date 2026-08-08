package io.github.daniele21.localllm.models

import io.github.daniele21.localllm.contracts.ThinkingMode
import org.junit.Assert.assertEquals
import org.junit.Test

class Qwen35GenerationProfilesTest {
    @Test
    fun profilesResolveOfficialSamplerBaselinesDeterministically() {
        Qwen35ModelTier.entries.forEach { tier ->
            val profiles = Qwen35GenerationProfiles.forTier(tier)
            assertEquals(Qwen35GenerationProfileId.entries.toSet(), profiles.map { it.id }.toSet())
            val text = profiles.single { it.id == Qwen35GenerationProfileId.QWEN35_TEXT_QUALITY }.defaults
            assertEquals(ThinkingMode.DISABLED, text.thinkingMode)
            assertEquals(1f, text.temperature)
            assertEquals(1f, text.topP)
            assertEquals(20, text.topK)
            assertEquals(0f, text.minP)
            assertEquals(2f, text.presencePenalty)
            assertEquals(1f, text.repeatPenalty)
            val thinking = profiles.single { it.id == Qwen35GenerationProfileId.QWEN35_THINKING }.defaults
            assertEquals(ThinkingMode.ENABLED, thinking.thinkingMode)
            assertEquals(1f, thinking.temperature)
            assertEquals(0.95f, thinking.topP)
            assertEquals(1.5f, thinking.presencePenalty)
            val precise = profiles.single { it.id == Qwen35GenerationProfileId.QWEN35_PRECISE }.defaults
            assertEquals(ThinkingMode.ENABLED, precise.thinkingMode)
            assertEquals(0.6f, precise.temperature)
            assertEquals(0f, precise.presencePenalty)
        }
    }
}
