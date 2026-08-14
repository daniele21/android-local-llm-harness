package io.github.daniele21.localllm.console.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OmbraPdfSegmenterTest {
    @Test
    fun normalizesLineEndingsAndCreatesStableBlocks() {
        val segments =
            OmbraPdfSegmenter.segment(
                listOf(
                    OmbraPdfPageText(
                        pageIndex = 0,
                        text = "First line  \r\nSecond line\r\n\r\nThird block\n",
                    ),
                ),
            )

        assertEquals(2, segments.size)
        assertEquals("p0001-b0001", segments[0].id.value)
        assertEquals("First line\nSecond line", segments[0].normalizedText)
        assertEquals("p0001-b0002", segments[1].id.value)
        assertEquals("Third block", segments[1].normalizedText)
    }

    @Test
    fun ordersPagesDeterministicallyAndSkipsBlankPages() {
        val segments =
            OmbraPdfSegmenter.segment(
                listOf(
                    OmbraPdfPageText(pageIndex = 2, text = "Page three"),
                    OmbraPdfPageText(pageIndex = 0, text = "  \n\t"),
                    OmbraPdfPageText(pageIndex = 1, text = "Page two"),
                ),
            )

        assertEquals(listOf(1, 2), segments.map { segment -> segment.pageIndex })
        assertEquals(listOf("Page two", "Page three"), segments.map { segment -> segment.normalizedText })
    }

    @Test
    fun rejectsUnsupportedControlCharactersFailClosed() {
        val result = runCatching { OmbraPdfSegmenter.segment(listOf(OmbraPdfPageText(0, "safe\u0000unsafe"))) }

        assertTrue(result.isFailure)
    }
}
