package io.github.daniele21.localllm.models

import io.github.daniele21.localllm.contracts.ThinkingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Qwen35GenerationProfilesTest {
    @Test
    fun existingTiersKeepTheirValidatedSamplerBaselines() {
        listOf(Qwen35ModelTier.B0_8, Qwen35ModelTier.B2).forEach { tier ->
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
            assertEquals(ReasoningStreamProtocol.QWEN35_THINK_TAGS, text.reasoningStreamProtocol)
        }
    }

    @Test
    fun fourBitFourBProfilesMatchUnslothGuidance() {
        val profiles = Qwen35GenerationProfiles.forTier(Qwen35ModelTier.B4)
        assertEquals(Qwen35GenerationProfileId.entries.toSet(), profiles.map { it.id }.toSet())

        assertSampler(
            profiles,
            Qwen35GenerationProfileId.QWEN35_TEXT_FAST,
            ThinkingMode.DISABLED,
            temperature = 0.7f,
            topP = 0.8f,
            presencePenalty = 1.5f,
        )
        assertSampler(
            profiles,
            Qwen35GenerationProfileId.QWEN35_TEXT_QUALITY,
            ThinkingMode.DISABLED,
            temperature = 1f,
            topP = 0.95f,
            presencePenalty = 1.5f,
        )
        assertSampler(
            profiles,
            Qwen35GenerationProfileId.QWEN35_THINKING,
            ThinkingMode.ENABLED,
            temperature = 1f,
            topP = 0.95f,
            presencePenalty = 1.5f,
        )
        assertSampler(
            profiles,
            Qwen35GenerationProfileId.QWEN35_PRECISE,
            ThinkingMode.ENABLED,
            temperature = 0.6f,
            topP = 0.95f,
            presencePenalty = 0f,
        )
        assertSampler(
            profiles,
            Qwen35GenerationProfileId.QWEN35_JSON,
            ThinkingMode.DISABLED,
            temperature = 0.7f,
            topP = 0.8f,
            presencePenalty = 1.5f,
        )
    }

    @Test
    fun `thinking profiles always reserve output for final answer`() {
        val expected = mapOf(
            Qwen35ModelTier.B0_8 to (192 to 256),
            Qwen35ModelTier.B2 to (384 to 512),
            Qwen35ModelTier.B4 to (384 to 512),
        )

        expected.forEach { (tier, budgets) ->
            val thinking = Qwen35GenerationProfiles.forTier(tier)
                .single { it.id == Qwen35GenerationProfileId.QWEN35_THINKING }
                .defaults
            assertEquals(budgets.first, thinking.guardPolicy.thinkingTokenBudget)
            assertEquals(budgets.second, thinking.guardPolicy.answerReserveTokens)
            assertTrue(thinking.guardPolicy.thinkingTokenBudget < thinking.maxOutputTokens)
            assertTrue(thinking.guardPolicy.answerReserveTokens < thinking.maxOutputTokens)
            assertTrue(
                thinking.guardPolicy.thinkingTokenBudget + thinking.guardPolicy.answerReserveTokens <=
                    thinking.maxOutputTokens,
            )
        }
    }

    @Test
    fun `Qwen thinking protocol declares generated close transition`() {
        val protocol = ReasoningStreamProtocol.QWEN35_THINK_TAGS

        assertEquals("</think>", protocol.closeMarker)
        assertEquals("</think>\n\n", protocol.forcedCloseText)
    }

    private fun assertSampler(
        profiles: List<Qwen35GenerationProfile>,
        id: Qwen35GenerationProfileId,
        thinkingMode: ThinkingMode,
        temperature: Float,
        topP: Float,
        presencePenalty: Float,
    ) {
        val defaults = profiles.single { it.id == id }.defaults
        assertEquals(thinkingMode, defaults.thinkingMode)
        assertEquals(temperature, defaults.temperature)
        assertEquals(topP, defaults.topP)
        assertEquals(20, defaults.topK)
        assertEquals(0f, defaults.minP)
        assertEquals(presencePenalty, defaults.presencePenalty)
        assertEquals(1f, defaults.repeatPenalty)
        assertEquals(ReasoningStreamProtocol.QWEN35_THINK_TAGS, defaults.reasoningStreamProtocol)
    }
}
