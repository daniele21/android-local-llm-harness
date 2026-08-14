package io.github.daniele21.localllm.console.document

import android.graphics.RectF
import androidx.pdf.ExperimentalPdfApi
import androidx.pdf.content.PdfPageTextContent
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * OMB-0 geometry-aware normalization for AndroidX PDF text fragments.
 *
 * AndroidX exposes a continuous text stream in viewing order plus zero or more line bounds. Some
 * generated PDFs return very fine-grained CR/LF-delimited text even when the geometry still places
 * that content on one visual line. This normalizer expands text into line fragments only when the
 * text-line count and bounds count agree, treats internal line terminators as extraction artifacts
 * when one bound proves a single visual line, and otherwise preserves the API stream conservatively.
 * OMB-2 will own the production extraction contract and its malformed/ambiguous-content policy.
 */
@OptIn(ExperimentalPdfApi::class)
internal object OmbraPdfTextNormalizer {
    fun normalize(contents: List<PdfPageTextContent>): String {
        val expanded = contents.flatMap(::expandContent)
        if (expanded.isEmpty()) return ""

        val bounded = expanded.filter { fragment -> fragment.bounds != null }
        if (bounded.size != expanded.size) {
            return expanded.joinToString(separator = "") { fragment -> fragment.text }
        }

        val fragments = bounded.map { fragment -> TextFragment(fragment.text, requireNotNull(fragment.bounds)) }
        val lines = groupIntoVisualLines(fragments)
        val typicalCharacterWidth = medianCharacterWidth(fragments)
        return lines.joinToString(separator = "\n") { line ->
            reconstructLine(line, typicalCharacterWidth)
        }
    }

    private fun expandContent(content: PdfPageTextContent): List<PendingFragment> {
        val normalized = content.text.replace("\r\n", "\n").replace('\r', '\n')
        val textLines = normalized.split('\n').filter { line -> line.isNotEmpty() }
        if (textLines.isEmpty()) return emptyList()

        return when {
            content.bounds.isEmpty() -> listOf(PendingFragment(textLines.joinToString(separator = "\n"), null))
            content.bounds.size == textLines.size ->
                textLines.zip(content.bounds) { text, bounds -> PendingFragment(text, RectF(bounds)) }
            content.bounds.size == 1 ->
                listOf(PendingFragment(textLines.joinToString(separator = ""), RectF(content.bounds.single())))
            else -> listOf(PendingFragment(textLines.joinToString(separator = "\n"), null))
        }
    }

    private fun groupIntoVisualLines(fragments: List<TextFragment>): List<List<TextFragment>> {
        val sorted =
            fragments.sortedWith(
                compareBy<TextFragment> { fragment -> fragment.bounds.centerY() }
                    .thenBy { fragment -> fragment.bounds.left },
            )
        val lines = mutableListOf<MutableList<TextFragment>>()
        sorted.forEach { fragment ->
            val currentLine = lines.lastOrNull()
            if (currentLine == null || !isSameVisualLine(currentLine.first().bounds, fragment.bounds)) {
                lines += mutableListOf(fragment)
            } else {
                currentLine += fragment
            }
        }
        return lines.map { line -> line.sortedBy { fragment -> fragment.bounds.left } }
    }

    private fun isSameVisualLine(reference: RectF, candidate: RectF): Boolean {
        val overlap = min(reference.bottom, candidate.bottom) - max(reference.top, candidate.top)
        val minimumHeight = min(reference.height(), candidate.height()).coerceAtLeast(1f)
        if (overlap >= minimumHeight * MIN_VERTICAL_OVERLAP_RATIO) return true

        val centerDelta = abs(reference.centerY() - candidate.centerY())
        return centerDelta <= minimumHeight * MAX_CENTER_DELTA_RATIO
    }

    private fun reconstructLine(line: List<TextFragment>, typicalCharacterWidth: Float): String {
        val builder = StringBuilder()
        line.forEachIndexed { index, fragment ->
            if (index > 0) {
                val previous = line[index - 1]
                val horizontalGap = fragment.bounds.left - previous.bounds.right
                if (shouldInsertSpace(previous.text, fragment.text, horizontalGap, typicalCharacterWidth)) {
                    builder.append(' ')
                }
            }
            builder.append(fragment.text)
        }
        return builder.toString()
    }

    private fun shouldInsertSpace(previousText: String, currentText: String, horizontalGap: Float, typicalCharacterWidth: Float): Boolean {
        if (previousText.lastOrNull()?.isWhitespace() == true) return false
        if (currentText.firstOrNull()?.isWhitespace() == true) return false
        if (typicalCharacterWidth <= 0f) return false
        return horizontalGap > typicalCharacterWidth * WORD_GAP_RATIO
    }

    private fun medianCharacterWidth(fragments: List<TextFragment>): Float {
        val widths =
            fragments
                .mapNotNull { fragment ->
                    val visibleCharacters = fragment.text.count { character -> !character.isWhitespace() }
                    if (visibleCharacters == 0 || fragment.bounds.width() <= 0f) {
                        null
                    } else {
                        fragment.bounds.width() / visibleCharacters
                    }
                }.sorted()
        if (widths.isEmpty()) return 0f
        val middle = widths.size / 2
        return if (widths.size % 2 == 0) {
            (widths[middle - 1] + widths[middle]) / 2f
        } else {
            widths[middle]
        }
    }

    private data class PendingFragment(val text: String, val bounds: RectF?)

    private data class TextFragment(val text: String, val bounds: RectF)

    private const val MIN_VERTICAL_OVERLAP_RATIO = 0.5f
    private const val MAX_CENTER_DELTA_RATIO = 0.35f
    private const val WORD_GAP_RATIO = 0.35f
}
