package io.github.daniele21.localllm.console.document

internal data class OmbraPdfPageText(val pageIndex: Int, val text: String) {
    init {
        require(pageIndex >= 0) { "pageIndex must be non-negative" }
    }
}

internal data class OmbraPdfReadResult(val pageCount: Int, val pages: List<OmbraPdfPageText>, val truncated: Boolean) {
    init {
        require(pageCount >= 0) { "pageCount must be non-negative" }
        require(pages.all { page -> page.pageIndex < pageCount }) { "Returned PDF page is outside document bounds" }
        require(pages.map { page -> page.pageIndex }.distinct().size == pages.size) { "Duplicate PDF page index" }
    }
}

/** Pure deterministic mapping from page text into stable OMBRA source segments. */
internal object OmbraPdfSegmenter {
    fun segment(pages: List<OmbraPdfPageText>): List<DocumentSegment> = buildList {
        pages.sortedBy(OmbraPdfPageText::pageIndex).forEach { page ->
            normalizePage(page.text).forEachIndexed { blockIndex, block ->
                require(blockIndex < MAX_BLOCKS_PER_PAGE) { "PDF page exceeds stable block identity range" }
                add(
                    DocumentSegment(
                        id = SegmentId.fromIndices(page.pageIndex, blockIndex),
                        pageIndex = page.pageIndex,
                        blockIndex = blockIndex,
                        normalizedText = block,
                    ),
                )
            }
        }
    }

    private fun normalizePage(text: String): List<String> {
        require(text.none(::isUnsupportedControl)) { "Extracted PDF text contains unsupported control characters" }
        val normalizedLines =
            text.replace("\r\n", "\n").replace('\r', '\n').lineSequence().map(String::trimEnd).toList()
        val blocks = mutableListOf<String>()
        val current = mutableListOf<String>()

        fun flush() {
            val block = current.joinToString("\n").trim()
            if (block.isNotEmpty()) blocks += block
            current.clear()
        }

        normalizedLines.forEach { line ->
            if (line.isBlank()) {
                flush()
            } else {
                current += line
            }
        }
        flush()
        return blocks
    }

    private fun isUnsupportedControl(character: Char): Boolean = character == '\u0000' ||
        (Character.isISOControl(character) && character != '\n' && character != '\r' && character != '\t')

    private const val MAX_BLOCKS_PER_PAGE = 9_999
}
