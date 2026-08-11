package io.github.daniele21.localllm.phonetest

internal enum class PlaygroundMarkdownInlineStyle {
    PLAIN,
    BOLD,
    ITALIC,
    BOLD_ITALIC,
    CODE,
    STRIKETHROUGH,
    LINK,
}

internal data class PlaygroundMarkdownInline(
    val text: String,
    val style: PlaygroundMarkdownInlineStyle = PlaygroundMarkdownInlineStyle.PLAIN,
    val destination: String? = null,
)

internal sealed interface PlaygroundMarkdownBlock {
    data class Paragraph(val inline: List<PlaygroundMarkdownInline>) : PlaygroundMarkdownBlock

    data class Heading(val level: Int, val inline: List<PlaygroundMarkdownInline>) : PlaygroundMarkdownBlock

    data class ListItem(val marker: String, val ordered: Boolean, val inline: List<PlaygroundMarkdownInline>) : PlaygroundMarkdownBlock

    data class Quote(val inline: List<PlaygroundMarkdownInline>) : PlaygroundMarkdownBlock

    data class Code(val language: String?, val text: String) : PlaygroundMarkdownBlock
}

/**
 * Small dependency-free renderer model for model output. The two parsing entry points are intentionally
 * branch-heavy because they recognize a bounded Markdown subset in a single pass. Keeping that branching
 * here avoids leaking Markdown concerns into the Compose presentation layer; behavior is covered by unit tests.
 */
internal object PlaygroundMarkdownParser {
    private val heading = Regex("^(#{1,4})\\s+(.+)$")
    private val bullet = Regex("^\\s*[-+*]\\s+(.+)$")
    private val ordered = Regex("^\\s*(\\d+[.)])\\s+(.+)$")
    private val quote = Regex("^\\s*>\\s?(.*)$")

    @Suppress("CyclomaticComplexMethod")
    fun parse(source: String): List<PlaygroundMarkdownBlock> {
        if (source.isBlank()) return emptyList()
        val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val blocks = mutableListOf<PlaygroundMarkdownBlock>()
        val paragraph = mutableListOf<String>()
        var index = 0

        fun flushParagraph() {
            if (paragraph.isEmpty()) return
            blocks += PlaygroundMarkdownBlock.Paragraph(parseInline(paragraph.joinToString("\n")))
            paragraph.clear()
        }

        while (index < lines.size) {
            val line = lines[index]
            when {
                line.trimStart().startsWith("```") -> {
                    flushParagraph()
                    val opening = line.trimStart()
                    val language = opening.removePrefix("```").trim().ifBlank { null }
                    index += 1
                    val code = mutableListOf<String>()
                    while (index < lines.size && !lines[index].trimStart().startsWith("```")) {
                        code += lines[index]
                        index += 1
                    }
                    if (index < lines.size) index += 1
                    blocks += PlaygroundMarkdownBlock.Code(language = language, text = code.joinToString("\n"))
                    continue
                }

                line.isBlank() -> flushParagraph()

                heading.matches(line) -> {
                    flushParagraph()
                    val match = requireNotNull(heading.matchEntire(line))
                    blocks += PlaygroundMarkdownBlock.Heading(
                        level = match.groupValues[1].length,
                        inline = parseInline(match.groupValues[2]),
                    )
                }

                bullet.matches(line) -> {
                    flushParagraph()
                    val match = requireNotNull(bullet.matchEntire(line))
                    blocks += PlaygroundMarkdownBlock.ListItem(
                        marker = "•",
                        ordered = false,
                        inline = parseInline(match.groupValues[1]),
                    )
                }

                ordered.matches(line) -> {
                    flushParagraph()
                    val match = requireNotNull(ordered.matchEntire(line))
                    blocks += PlaygroundMarkdownBlock.ListItem(
                        marker = match.groupValues[1],
                        ordered = true,
                        inline = parseInline(match.groupValues[2]),
                    )
                }

                quote.matches(line) -> {
                    flushParagraph()
                    val match = requireNotNull(quote.matchEntire(line))
                    blocks += PlaygroundMarkdownBlock.Quote(parseInline(match.groupValues[1]))
                }

                else -> paragraph += line
            }
            index += 1
        }
        flushParagraph()
        return blocks
    }

    @Suppress("CyclomaticComplexMethod")
    fun parseInline(source: String): List<PlaygroundMarkdownInline> {
        if (source.isEmpty()) return emptyList()
        val output = mutableListOf<PlaygroundMarkdownInline>()
        val plain = StringBuilder()
        var index = 0

        fun flushPlain() {
            if (plain.isEmpty()) return
            output += PlaygroundMarkdownInline(plain.toString())
            plain.clear()
        }

        fun delimited(marker: String, style: PlaygroundMarkdownInlineStyle): Boolean {
            if (!source.startsWith(marker, index)) return false
            val contentStart = index + marker.length
            val close = source.indexOf(marker, contentStart)
            if (close <= contentStart) return false
            flushPlain()
            output += PlaygroundMarkdownInline(source.substring(contentStart, close), style)
            index = close + marker.length
            return true
        }

        while (index < source.length) {
            when {
                source[index] == '\\' && index + 1 < source.length -> {
                    plain.append(source[index + 1])
                    index += 2
                }

                source.startsWith("[", index) -> {
                    val labelEnd = source.indexOf(']', index + 1)
                    val destinationStart = if (labelEnd >= 0 && labelEnd + 1 < source.length && source[labelEnd + 1] == '(') {
                        labelEnd + 2
                    } else {
                        -1
                    }
                    val destinationEnd = if (destinationStart >= 0) source.indexOf(')', destinationStart) else -1
                    if (labelEnd > index + 1 && destinationStart >= 0 && destinationEnd > destinationStart) {
                        flushPlain()
                        output += PlaygroundMarkdownInline(
                            text = source.substring(index + 1, labelEnd),
                            style = PlaygroundMarkdownInlineStyle.LINK,
                            destination = source.substring(destinationStart, destinationEnd),
                        )
                        index = destinationEnd + 1
                    } else {
                        plain.append(source[index])
                        index += 1
                    }
                }

                delimited("***", PlaygroundMarkdownInlineStyle.BOLD_ITALIC) -> Unit

                delimited("___", PlaygroundMarkdownInlineStyle.BOLD_ITALIC) -> Unit

                delimited("**", PlaygroundMarkdownInlineStyle.BOLD) -> Unit

                delimited("__", PlaygroundMarkdownInlineStyle.BOLD) -> Unit

                delimited("~~", PlaygroundMarkdownInlineStyle.STRIKETHROUGH) -> Unit

                delimited("`", PlaygroundMarkdownInlineStyle.CODE) -> Unit

                delimited("*", PlaygroundMarkdownInlineStyle.ITALIC) -> Unit

                delimited("_", PlaygroundMarkdownInlineStyle.ITALIC) -> Unit

                else -> {
                    plain.append(source[index])
                    index += 1
                }
            }
        }
        flushPlain()
        return mergePlain(output)
    }

    private fun mergePlain(source: List<PlaygroundMarkdownInline>): List<PlaygroundMarkdownInline> {
        if (source.size < 2) return source
        val merged = mutableListOf<PlaygroundMarkdownInline>()
        source.forEach { token ->
            val previous = merged.lastOrNull()
            if (
                previous?.style == PlaygroundMarkdownInlineStyle.PLAIN &&
                token.style == PlaygroundMarkdownInlineStyle.PLAIN
            ) {
                merged[merged.lastIndex] = previous.copy(text = previous.text + token.text)
            } else {
                merged += token
            }
        }
        return merged
    }
}
