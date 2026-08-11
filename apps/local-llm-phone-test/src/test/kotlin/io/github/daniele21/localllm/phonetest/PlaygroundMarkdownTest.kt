package io.github.daniele21.localllm.phonetest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaygroundMarkdownTest {
    @Test
    fun `bold markers become styled content instead of visible syntax`() {
        val blocks = PlaygroundMarkdownParser.parse(
            "Approximately **6,371 kilometers** from the center.",
        )
        val paragraph = blocks.single() as PlaygroundMarkdownBlock.Paragraph

        assertEquals(
            listOf(
                PlaygroundMarkdownInline("Approximately "),
                PlaygroundMarkdownInline("6,371 kilometers", PlaygroundMarkdownInlineStyle.BOLD),
                PlaygroundMarkdownInline(" from the center."),
            ),
            paragraph.inline,
        )
    }

    @Test
    fun `common inline markdown keeps semantic styles`() {
        val parts = PlaygroundMarkdownParser.parseInline(
            "Use *care*, `local.gguf`, ~~remote~~ and **bold**.",
        )

        assertTrue(parts.any { it.text == "care" && it.style == PlaygroundMarkdownInlineStyle.ITALIC })
        assertTrue(parts.any { it.text == "local.gguf" && it.style == PlaygroundMarkdownInlineStyle.CODE })
        assertTrue(parts.any { it.text == "remote" && it.style == PlaygroundMarkdownInlineStyle.STRIKETHROUGH })
        assertTrue(parts.any { it.text == "bold" && it.style == PlaygroundMarkdownInlineStyle.BOLD })
    }

    @Test
    fun `headings lists quotes and code fences become distinct blocks`() {
        val blocks = PlaygroundMarkdownParser.parse(
            """
            ## Result

            - first
            1. second
            > local only

            ```kotlin
            val answer = 42
            ```
            """.trimIndent(),
        )

        assertTrue(blocks[0] is PlaygroundMarkdownBlock.Heading)
        assertTrue(blocks[1] is PlaygroundMarkdownBlock.ListItem)
        assertTrue(blocks[2] is PlaygroundMarkdownBlock.ListItem)
        assertTrue(blocks[3] is PlaygroundMarkdownBlock.Quote)
        val code = blocks[4] as PlaygroundMarkdownBlock.Code
        assertEquals("kotlin", code.language)
        assertEquals("val answer = 42", code.text)
    }

    @Test
    fun `unclosed markdown remains readable literal text`() {
        val parts = PlaygroundMarkdownParser.parseInline("answer **still streaming")

        assertEquals("answer **still streaming", parts.joinToString("") { it.text })
        assertTrue(parts.all { it.style == PlaygroundMarkdownInlineStyle.PLAIN })
    }

    @Test
    fun `escaped markdown markers stay literal`() {
        val parts = PlaygroundMarkdownParser.parseInline("\\*literal\\* and \\`code\\`")

        assertEquals("*literal* and `code`", parts.joinToString("") { it.text })
        assertTrue(parts.all { it.style == PlaygroundMarkdownInlineStyle.PLAIN })
    }
}
