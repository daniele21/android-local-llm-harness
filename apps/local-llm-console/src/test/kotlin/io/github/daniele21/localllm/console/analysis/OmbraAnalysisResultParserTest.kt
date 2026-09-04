package io.github.daniele21.localllm.console.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OmbraAnalysisResultParserTest {
    private val parser = OmbraAnalysisResultParser()

    @Test
    fun `valid fixed-schema response parses escaped source surface`() {
        val result =
            parser.parse(
                """{"schemaVersion":1,"findings":[{"typeId":"email","surface":"alice\"x@example.test","segmentId":"p0001-b0001"}]}""",
            )

        val parsed = (result as OmbraAnalysisParseResult.Parsed).result
        assertEquals(1, parsed.findings.size)
        assertEquals("alice\"x@example.test", parsed.findings.single().surface)
    }

    @Test
    fun `extra top-level field is rejected`() {
        val result = parser.parse("""{"schemaVersion":1,"findings":[],"explanation":"no"}""")

        assertEquals(OmbraAnalysisParseResult.Rejected(OmbraAnalysisParseFailureCode.INVALID_SHAPE), result)
    }

    @Test
    fun `unsupported schema version is rejected`() {
        val result = parser.parse("""{"schemaVersion":2,"findings":[]}""")

        assertEquals(OmbraAnalysisParseResult.Rejected(OmbraAnalysisParseFailureCode.UNSUPPORTED_SCHEMA), result)
    }

    @Test
    fun `duplicate object key is rejected before semantic validation`() {
        val result = parser.parse("""{"schemaVersion":1,"schemaVersion":1,"findings":[]}""")

        assertEquals(OmbraAnalysisParseResult.Rejected(OmbraAnalysisParseFailureCode.INVALID_JSON), result)
    }

    @Test
    fun `trailing prose is rejected`() {
        val result = parser.parse("""{"schemaVersion":1,"findings":[]} explanation""")

        assertEquals(OmbraAnalysisParseResult.Rejected(OmbraAnalysisParseFailureCode.INVALID_JSON), result)
    }

    @Test
    fun `unpaired unicode surrogate is rejected`() {
        val result =
            parser.parse(
                """{"schemaVersion":1,"findings":[{"typeId":"email","surface":"\uD83D","segmentId":"p0001-b0001"}]}""",
            )

        assertEquals(OmbraAnalysisParseResult.Rejected(OmbraAnalysisParseFailureCode.INVALID_JSON), result)
    }

    @Test
    fun `valid surrogate pair is decoded`() {
        val result =
            parser.parse(
                """{"schemaVersion":1,"findings":[{"typeId":"email","surface":"\uD83D\uDE00","segmentId":"p0001-b0001"}]}""",
            )

        assertTrue(result is OmbraAnalysisParseResult.Parsed)
        assertEquals("😀", (result as OmbraAnalysisParseResult.Parsed).result.findings.single().surface)
    }

    @Test
    fun `escaped backslash remains literal data`() {
        val result =
            parser.parse(
                """{"schemaVersion":1,"findings":[{"typeId":"email","surface":"literal\\uD83D","segmentId":"p0001-b0001"}]}""",
            )

        assertTrue(result is OmbraAnalysisParseResult.Parsed)
        assertEquals("literal\\uD83D", (result as OmbraAnalysisParseResult.Parsed).result.findings.single().surface)
    }
}
