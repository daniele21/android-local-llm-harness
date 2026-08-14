package io.github.daniele21.localllm.console.document

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream

/**
 * OMB-0A normalized-layout export spike.
 *
 * The final OMBRA exporter will own placeholders, destination handling and independent output
 * verification. This spike keeps the writer deliberately narrow: deterministic page geometry,
 * system sans-serif text, bounded input and no attempt to preserve source-PDF objects or layout.
 */
internal class OmbraPdfWriterSpike {
    fun write(
        pages: List<String>,
        output: OutputStream,
        maxCharacters: Int = DEFAULT_MAX_CHARACTERS,
    ) {
        require(pages.isNotEmpty()) { "At least one page is required" }
        require(pages.size <= MAX_PAGES) { "Too many pages" }
        require(maxCharacters > 0) { "maxCharacters must be positive" }

        val totalCharacters = pages.sumOf(String::length)
        require(totalCharacters <= maxCharacters) { "Document text exceeds the spike bound" }

        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                textSize = TEXT_SIZE_POINTS
            }

        PdfDocument().use { document ->
            pages.forEachIndexed { pageIndex, text ->
                requireSupportedGlyphs(text, paint)
                val pageInfo =
                    PdfDocument.PageInfo.Builder(
                        PAGE_WIDTH_POINTS,
                        PAGE_HEIGHT_POINTS,
                        pageIndex + 1,
                    ).create()
                val page = document.startPage(pageInfo)
                try {
                    drawNormalizedText(
                        text = text,
                        paint = paint,
                        canvas = page.canvas,
                    )
                } finally {
                    document.finishPage(page)
                }
            }
            document.writeTo(output)
        }
    }

    private fun drawNormalizedText(
        text: String,
        paint: Paint,
        canvas: android.graphics.Canvas,
    ) {
        var y = TOP_MARGIN_POINTS + TEXT_SIZE_POINTS
        text.lineSequence().forEach { sourceLine ->
            wrapLine(sourceLine, paint).forEach { line ->
                if (y > PAGE_HEIGHT_POINTS - BOTTOM_MARGIN_POINTS) {
                    error("Page text exceeds the normalized-layout spike page height")
                }
                canvas.drawText(line, LEFT_MARGIN_POINTS, y, paint)
                y += LINE_HEIGHT_POINTS
            }
        }
    }

    private fun wrapLine(source: String, paint: Paint): List<String> {
        if (source.isEmpty()) return listOf("")

        val availableWidth = PAGE_WIDTH_POINTS - LEFT_MARGIN_POINTS - RIGHT_MARGIN_POINTS
        val lines = mutableListOf<String>()
        var remaining = source
        while (remaining.isNotEmpty()) {
            val count = paint.breakText(remaining, true, availableWidth, null).coerceAtLeast(1)
            if (count >= remaining.length) {
                lines += remaining
                break
            }

            val candidate = remaining.take(count)
            val breakAt = candidate.lastIndexOf(' ').takeIf { it > 0 } ?: count
            lines += remaining.take(breakAt).trimEnd()
            remaining = remaining.drop(breakAt).trimStart()
        }
        return lines
    }

    private fun requireSupportedGlyphs(text: String, paint: Paint) {
        text.codePoints().forEach { codePoint ->
            val value = String(Character.toChars(codePoint))
            require(value == "\n" || value == "\r" || paint.hasGlyph(value)) {
                "Unsupported glyph U+${codePoint.toString(16).uppercase()}"
            }
        }
    }

    private companion object {
        const val PAGE_WIDTH_POINTS = 595
        const val PAGE_HEIGHT_POINTS = 842
        const val LEFT_MARGIN_POINTS = 48f
        const val RIGHT_MARGIN_POINTS = 48f
        const val TOP_MARGIN_POINTS = 48f
        const val BOTTOM_MARGIN_POINTS = 48f
        const val TEXT_SIZE_POINTS = 11f
        const val LINE_HEIGHT_POINTS = 15f
        const val MAX_PAGES = 200
        const val DEFAULT_MAX_CHARACTERS = 1_000_000
    }
}
