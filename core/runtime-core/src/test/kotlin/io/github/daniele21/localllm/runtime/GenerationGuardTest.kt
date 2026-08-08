package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.StopReason
import io.github.daniele21.localllm.contracts.ThinkingMode
import io.github.daniele21.localllm.models.GenerationGuardPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationGuardTest {
    @Test
    fun `thinking budget has a typed stop reason`() {
        val guard = GenerationGuard(ThinkingMode.ENABLED, policy(thinkingBudget = 8))
        assertNull(guard.observe("reasoning", 7))
        assertEquals(StopReason.GENERATION_GUARD_THINKING_BUDGET, guard.observe(" more", 8))
    }

    @Test
    fun `closing thinking disables the thinking guard`() {
        val guard = GenerationGuard(ThinkingMode.ENABLED, policy(thinkingBudget = 4))
        assertNull(guard.observe("analysis </thi", 3))
        assertNull(guard.observe("nk> final answer", 8))
        assertNull(guard.observe(" continues", 40))
    }

    @Test
    fun `repetition detection is independent from chunk boundaries`() {
        val repeated = "loop pattern with enough entropy "
        val whole = GenerationGuard(ThinkingMode.ENABLED, policy(thinkingBudget = 500, activation = 4))
        val split = GenerationGuard(ThinkingMode.ENABLED, policy(thinkingBudget = 500, activation = 4))
        val wholeReason = whole.observe(repeated.repeat(4), 4)
        val parts = listOf(repeated.take(13), repeated.drop(13) + repeated, repeated.repeat(2))
        var splitReason: StopReason? = null
        parts.forEachIndexed { index, part -> splitReason = split.observe(part, index + 2) ?: splitReason }
        assertEquals(StopReason.GENERATION_GUARD_REPETITION, wholeReason)
        assertEquals(wholeReason, splitReason)
    }

    @Test
    fun `guard retains only bounded output`() {
        val guard = GenerationGuard(ThinkingMode.ENABLED, policy(thinkingBudget = 10_000, window = 512))
        repeat(100) { guard.observe("abcdefghij", it + 1) }
        assertTrue(guard.retainedCharacters() <= 512)
    }

    @Test
    fun `disabled and non thinking modes never guard`() {
        val enabled = policy(thinkingBudget = 1)
        assertNull(GenerationGuard(ThinkingMode.DISABLED, enabled).observe("x", 100))
        assertNull(GenerationGuard(ThinkingMode.ENABLED, GenerationGuardPolicy.disabled()).observe("x", 100))
    }

    private fun policy(thinkingBudget: Int, activation: Int = 4, window: Int = 512) = GenerationGuardPolicy(
        enabled = true,
        thinkingTokenBudget = thinkingBudget,
        repetitionActivationTokens = activation,
        observationWindowChars = window,
        minPatternChars = 24,
        maxPatternChars = 64,
        repetitionOccurrences = 4,
    )
}
