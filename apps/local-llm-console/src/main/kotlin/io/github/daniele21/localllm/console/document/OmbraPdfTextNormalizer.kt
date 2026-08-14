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
 * AndroidX may return very fine-grained text elements, including individual characters, and may
 * suffix those fragments with CR/LF terminators even when their bounds place them on the same
 * visual line. Treating either the element or its trailing terminator as a line therefore corrupts
 * otherwise valid text. This normalizer removes only fragment-edge line terminators, reconstructs
 * visual lines from geometry and infers only clearly visible inter-word gaps. If geometry is
 * unavailable or ambiguous, it preserves the API stream order without inventing separators.
 * OMB-2 will own the production extraction contract.
 */
@OptIn(ExperimentalPdfApi::class)
internal object OmbraPdfTextNormalizer {
    fun normalize(contents: List<PdfPageTextContent>): String {
        val nonEmpty =
            contents.mapNotNull { content ->
                val text = normalizeFragmentText(content.text)
                if (text.isEmpty()) null else content to text
            }
        if (nonEmpty.isEmpty()) return ""
        if (nonEmpty.any { (content, _) -> content.bounds.size != 1 }) {
            return nonEmpty.joinToString(separator = "") { (_, text) -> text }
        }

        val fragments =
            nonEmpty.map { (content, text) ->
                TextFragment(
                    text = text,
                    bounds = RectF(content.bounds.single()),
                )
            }
        val lines = groupIntoVisualLines(fragments)
        val typicalCharacterWidth = medianCharacterWidth(fragments)
        return lines.joinToString(separator = "\n") { line ->
            reconstructLine(line, typicalCharacterWidth)
        }
    }

    private fun normalizeFragmentText(text: String): String = text.trimEnd('\r', '\n')

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

    private data class TextFragment(val text: String, val bounds: RectF)

    private const val MIN_VERTICAL_OVERLAP_RATIO = 0.5f
    private const val MAX_CENTER_DELTA_RATIO = 0.35f
    private const val WORD_GAP_RATIO = 0.35f
}
