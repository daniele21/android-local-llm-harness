package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.GenerationOverrides
import io.github.daniele21.localllm.contracts.ThinkingMode
import io.github.daniele21.localllm.models.GenerationDefaults
import org.junit.Assert.assertEquals
import org.junit.Test

class Qwen35GenerationConfigurationTest {
    @Test
    fun neutralThinkingAndSamplerFieldsAreRepresentable() {
        val defaults = GenerationDefaults(
            maxOutputTokens = 512,
            temperature = 1f,
            topP = 0.95f,
            topK = 20,
            minP = 0f,
            presencePenalty = 1.5f,
            thinkingMode = ThinkingMode.ENABLED,
        )
        val override = GenerationOverrides(
            thinkingMode = ThinkingMode.DISABLED,
            minP = 0.1f,
            presencePenalty = 0.5f,
        )
        assertEquals(ThinkingMode.ENABLED, defaults.thinkingMode)
        assertEquals(0f, defaults.minP)
        assertEquals(1.5f, defaults.presencePenalty)
        assertEquals(ThinkingMode.DISABLED, override.thinkingMode)
        assertEquals(0.1f, override.minP)
        assertEquals(0.5f, override.presencePenalty)
    }
}
