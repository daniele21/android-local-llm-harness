package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.GenerationContentType
import io.github.daniele21.localllm.contracts.ThinkingMode
import io.github.daniele21.localllm.models.ReasoningStreamProtocol

internal data class ParsedGenerationChunk(
    val contentType: GenerationContentType,
    val text: String,
)

/**
 * Splits model output into reasoning and answer channels without relying on UI-side regexes.
 *
 * Qwen3.5's thinking chat template pre-fills the assistant `<think>` opener in the prompt,
 * therefore a thinking-enabled stream starts in [GenerationContentType.REASONING] and moves
 * to [GenerationContentType.ANSWER] only after the generated `</think>` marker. Markers may be
 * split across arbitrary native streaming chunks, so a short suffix is retained until it can
 * be classified safely.
 */
internal class ReasoningStreamParser(
    thinkingMode: ThinkingMode,
    private val protocol: ReasoningStreamProtocol,
) {
    private var state = if (thinkingMode == ThinkingMode.ENABLED && protocol == ReasoningStreamProtocol.QWEN35_THINK_TAGS) {
        GenerationContentType.REASONING
    } else {
        GenerationContentType.ANSWER
    }
    private var pending = ""
    private var reasoningClosed = state == GenerationContentType.ANSWER

    fun accept(text: String): List<ParsedGenerationChunk> {
        if (text.isEmpty()) return emptyList()
        if (state == GenerationContentType.ANSWER) {
            return listOf(ParsedGenerationChunk(GenerationContentType.ANSWER, text))
        }

        pending += text
        return parseReasoningBuffer()
    }

    fun finish(): List<ParsedGenerationChunk> {
        if (pending.isEmpty()) return emptyList()
        val tail = ParsedGenerationChunk(state, pending)
        pending = ""
        return listOf(tail)
    }

    fun hasClosedReasoning(): Boolean = reasoningClosed

    private fun parseReasoningBuffer(): List<ParsedGenerationChunk> {
        val parsed = mutableListOf<ParsedGenerationChunk>()
        while (pending.isNotEmpty() && state == GenerationContentType.REASONING) {
            val marker = nextCompleteMarker(pending)
            if (marker != null) {
                if (marker.index > 0) {
                    parsed += ParsedGenerationChunk(GenerationContentType.REASONING, pending.substring(0, marker.index))
                }
                pending = pending.substring(marker.index + marker.value.length)
                if (marker.value == CLOSE_THINK) {
                    state = GenerationContentType.ANSWER
                    reasoningClosed = true
                    if (pending.isNotEmpty()) {
                        parsed += ParsedGenerationChunk(GenerationContentType.ANSWER, pending)
                        pending = ""
                    }
                }
                continue
            }

            val heldCharacters = longestPossibleMarkerPrefixSuffix(pending)
            val safeLength = pending.length - heldCharacters
            if (safeLength > 0) {
                parsed += ParsedGenerationChunk(GenerationContentType.REASONING, pending.substring(0, safeLength))
                pending = pending.substring(safeLength)
            }
            break
        }
        return parsed
    }

    private fun nextCompleteMarker(value: String): MarkerMatch? {
        val openingIndex = value.indexOf(OPEN_THINK)
        val closingIndex = value.indexOf(CLOSE_THINK)
        return when {
            openingIndex < 0 && closingIndex < 0 -> null
            openingIndex >= 0 && (closingIndex < 0 || openingIndex < closingIndex) -> MarkerMatch(openingIndex, OPEN_THINK)
            else -> MarkerMatch(closingIndex, CLOSE_THINK)
        }
    }

    private fun longestPossibleMarkerPrefixSuffix(value: String): Int =
        maxOf(prefixSuffixLength(value, OPEN_THINK), prefixSuffixLength(value, CLOSE_THINK))

    private fun prefixSuffixLength(value: String, marker: String): Int {
        val maximum = minOf(value.length, marker.length - 1)
        for (length in maximum downTo 1) {
            if (value.regionMatches(value.length - length, marker, 0, length)) return length
        }
        return 0
    }

    private data class MarkerMatch(val index: Int, val value: String)

    private companion object {
        const val OPEN_THINK = "<think>"
        const val CLOSE_THINK = "</think>"
    }
}
