package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.GenerationContentType
import io.github.daniele21.localllm.contracts.ThinkingMode
import io.github.daniele21.localllm.models.ReasoningStreamProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningStreamParserTest {
    @Test
    fun `Qwen thinking stream starts as reasoning because opener is prefilled`() {
        val parser = qwenParser()

        assertEquals(
            listOf(ParsedGenerationChunk(GenerationContentType.REASONING, "step one")),
            parser.accept("step one"),
        )
        assertFalse(parser.hasClosedReasoning())
    }

    @Test
    fun `closing marker transitions from reasoning to answer`() {
        val parser = qwenParser()

        val chunks = parser.accept("reasoning</think>final answer")

        assertEquals(
            listOf(
                ParsedGenerationChunk(GenerationContentType.REASONING, "reasoning"),
                ParsedGenerationChunk(GenerationContentType.ANSWER, "final answer"),
            ),
            chunks,
        )
        assertTrue(parser.hasClosedReasoning())
    }

    @Test
    fun `Qwen transition newlines are removed from the final answer across chunks`() {
        val parser = qwenParser()

        val all = parser.accept("reasoning</think>\n") + parser.accept("\nfinal answer") + parser.finish()

        assertEquals("reasoning", all.filter { it.contentType == GenerationContentType.REASONING }.joinToString("") { it.text })
        assertEquals("final answer", all.filter { it.contentType == GenerationContentType.ANSWER }.joinToString("") { it.text })
    }

    @Test
    fun `closing marker can be split at every boundary`() {
        val marker = "</think>"
        for (boundary in 1 until marker.length) {
            val parser = qwenParser()
            val first = parser.accept("analysis" + marker.substring(0, boundary))
            val second = parser.accept(marker.substring(boundary) + "answer")
            val all = first + second + parser.finish()

            assertEquals("analysis", all.filter { it.contentType == GenerationContentType.REASONING }.joinToString("") { it.text })
            assertEquals("answer", all.filter { it.contentType == GenerationContentType.ANSWER }.joinToString("") { it.text })
            assertTrue("boundary=$boundary", parser.hasClosedReasoning())
        }
    }

    @Test
    fun `unexpected echoed opener is stripped even when split`() {
        val parser = qwenParser()

        val all = parser.accept("<thi") + parser.accept("nk>analysis</think>answer") + parser.finish()

        assertEquals("analysis", all.filter { it.contentType == GenerationContentType.REASONING }.joinToString("") { it.text })
        assertEquals("answer", all.filter { it.contentType == GenerationContentType.ANSWER }.joinToString("") { it.text })
    }

    @Test
    fun `unterminated thinking is flushed as reasoning and never mislabeled as answer`() {
        val parser = qwenParser()

        val all = parser.accept("unfinished </thi") + parser.finish()

        assertEquals("unfinished </thi", all.filter { it.contentType == GenerationContentType.REASONING }.joinToString("") { it.text })
        assertEquals("", all.filter { it.contentType == GenerationContentType.ANSWER }.joinToString("") { it.text })
        assertFalse(parser.hasClosedReasoning())
    }

    @Test
    fun `thinking disabled bypasses reasoning parsing`() {
        val parser = ReasoningStreamParser(ThinkingMode.DISABLED, ReasoningStreamProtocol.QWEN35_THINK_TAGS)

        assertEquals(
            listOf(ParsedGenerationChunk(GenerationContentType.ANSWER, "plain output </think> stays plain")),
            parser.accept("plain output </think> stays plain"),
        )
    }

    @Test
    fun `protocol none bypasses reasoning parsing`() {
        val parser = ReasoningStreamParser(ThinkingMode.ENABLED, ReasoningStreamProtocol.NONE)

        assertEquals(
            listOf(ParsedGenerationChunk(GenerationContentType.ANSWER, "model-specific text")),
            parser.accept("model-specific text"),
        )
    }

    @Test
    fun `unicode content survives arbitrary marker boundaries`() {
        val parser = qwenParser()

        val all = parser.accept("ragiono 🧠</th") + parser.accept("ink>risposta ✅") + parser.finish()

        assertEquals("ragiono 🧠", all.filter { it.contentType == GenerationContentType.REASONING }.joinToString("") { it.text })
        assertEquals("risposta ✅", all.filter { it.contentType == GenerationContentType.ANSWER }.joinToString("") { it.text })
    }

    private fun qwenParser() = ReasoningStreamParser(ThinkingMode.ENABLED, ReasoningStreamProtocol.QWEN35_THINK_TAGS)
}
