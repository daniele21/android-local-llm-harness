package io.github.daniele21.localllm.console.document

import java.util.Locale

/** Android-independent document identity used by the OMBRA domain. */
@JvmInline
internal value class SegmentId private constructor(val value: String) {
    companion object {
        private val VALUE_PATTERN = Regex("^p[0-9]{4}-b[0-9]{4}$")

        fun fromIndices(pageIndex: Int, blockIndex: Int): SegmentId {
            require(pageIndex >= 0) { "pageIndex must be non-negative" }
            require(blockIndex >= 0) { "blockIndex must be non-negative" }
            require(pageIndex < MAX_INDEX) { "pageIndex exceeds stable ID range" }
            require(blockIndex < MAX_INDEX) { "blockIndex exceeds stable ID range" }
            return SegmentId(
                String.format(
                    Locale.ROOT,
                    "p%04d-b%04d",
                    pageIndex + 1,
                    blockIndex + 1,
                ),
            )
        }

        fun parse(value: String): SegmentId {
            require(VALUE_PATTERN.matches(value)) { "Invalid segment ID" }
            return SegmentId(value)
        }

        private const val MAX_INDEX = 9_999
    }
}

/** Document metadata kept in task memory; the display name may itself contain PII. */
internal data class DocumentDescriptor(val displayName: String, val pageCount: Int) {
    init {
        require(displayName.isNotBlank()) { "displayName must not be blank" }
        require(pageCount > 0) { "pageCount must be positive" }
    }

    override fun toString(): String = "DocumentDescriptor(pageCount=$pageCount, displayName=<redacted>)"
}

/**
 * Stable normalized source unit consumed by analysis and redaction.
 *
 * Android Uri values, PDF parser objects and Binder/native types deliberately cannot enter this
 * model. The text is intentionally omitted from [toString] so ordinary logging cannot expose PDF
 * content by accident.
 */
internal data class DocumentSegment(val id: SegmentId, val pageIndex: Int, val blockIndex: Int, val normalizedText: String) {
    init {
        require(pageIndex >= 0) { "pageIndex must be non-negative" }
        require(blockIndex >= 0) { "blockIndex must be non-negative" }
        require(id == SegmentId.fromIndices(pageIndex, blockIndex)) {
            "Segment ID must match page/block indices"
        }
        require(normalizedText.isNotBlank()) { "normalizedText must not be blank" }
        require(normalizedText.none(::isUnsupportedControl)) {
            "normalizedText contains unsupported control characters"
        }
    }

    override fun toString(): String = "DocumentSegment(id=$id, pageIndex=$pageIndex, blockIndex=$blockIndex, normalizedText=<redacted>)"

    private fun isUnsupportedControl(character: Char): Boolean = character == '\u0000' ||
        (Character.isISOControl(character) && character != '\n' && character != '\t')
}
