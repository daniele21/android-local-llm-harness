package io.github.daniele21.localllm.models

import io.github.daniele21.localllm.contracts.ThinkingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
            assertEquals(ReasoningStreamProtocol.QWEN35_THINK_TAGS, text.reasoningStreamProtocol)
            val thinking = profiles.single { it.id == Qwen35GenerationProfileId.QWEN35_THINKING }.defaults
            assertEquals(ThinkingMode.ENABLED, thinking.thinkingMode)
            assertEquals(1f, thinking.temperature)
            assertEquals(0.95f, thinking.topP)
            assertEquals(1.5f, thinking.presencePenalty)
            assertEquals(ReasoningStreamProtocol.QWEN35_THINK_TAGS, thinking.reasoningStreamProtocol)
            val precise = profiles.single { it.id == Qwen35GenerationProfileId.QWEN35_PRECISE }.defaults
            assertEquals(ThinkingMode.ENABLED, precise.thinkingMode)
            assertEquals(0.6f, precise.temperature)
            assertEquals(0f, precise.presencePenalty)
        }
    }

    @Test
    fun `thinking profiles always reserve output for final answer`() {
        val expected = mapOf(
            Qwen35ModelTier.B0_8 to (192 to 256),
            Qwen35ModelTier.B2 to (384 to 512),
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
}
