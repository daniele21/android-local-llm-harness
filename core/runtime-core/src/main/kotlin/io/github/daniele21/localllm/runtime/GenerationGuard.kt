package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.StopReason
import io.github.daniele21.localllm.contracts.ThinkingMode
import io.github.daniele21.localllm.models.GenerationGuardPolicy

internal class GenerationGuard(private val thinkingMode: ThinkingMode, private val policy: GenerationGuardPolicy) {
    private val observation = StringBuilder()
    private var thinkingClosed = false

    fun observe(text: String, generatedTokens: Int): StopReason? {
        if (!isActive()) return null
        appendBounded(text)
        return when {
            THINKING_CLOSE in observation -> {
                thinkingClosed = true
                null
            }

            generatedTokens >= policy.thinkingTokenBudget -> StopReason.GENERATION_GUARD_THINKING_BUDGET

            generatedTokens >= policy.repetitionActivationTokens && repeatedSuffix() ->
                StopReason.GENERATION_GUARD_REPETITION

            else -> null
        }
    }

    private fun isActive(): Boolean = policy.enabled && thinkingMode == ThinkingMode.ENABLED && !thinkingClosed

    internal fun retainedCharacters(): Int = observation.length

    private fun appendBounded(text: String) {
        observation.append(text)
        val overflow = observation.length - policy.observationWindowChars
        if (overflow > 0) observation.delete(0, overflow)
    }

    private fun repeatedSuffix(): Boolean {
        val normalized = observation.toString().lowercase().replace(WHITESPACE, " ")
        val maximumPattern = minOf(policy.maxPatternChars, normalized.length / policy.repetitionOccurrences)
        if (maximumPattern < policy.minPatternChars) return false
        for (length in policy.minPatternChars..maximumPattern) {
            val evidence = normalized.takeLast(length * policy.repetitionOccurrences)
            val pattern = evidence.take(length)
            if (!pattern.any(Char::isLetterOrDigit) || pattern.toSet().size < MIN_PATTERN_DIVERSITY) continue
            if (evidence == pattern.repeat(policy.repetitionOccurrences)) return true
        }
        return false
    }

    private companion object {
        const val THINKING_CLOSE = "</think>"
        const val MIN_PATTERN_DIVERSITY = 4
        val WHITESPACE = Regex("\\s+")
    }
}
