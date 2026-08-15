package io.github.daniele21.localllm.console.analysis

import io.github.daniele21.localllm.console.document.DocumentSegment
import io.github.daniele21.localllm.console.document.SegmentId
import io.github.daniele21.localllm.console.pii.PiiDefinition
import io.github.daniele21.localllm.console.pii.PiiDefinitionSource
import io.github.daniele21.localllm.console.pii.PiiTypeId
import io.github.daniele21.localllm.contracts.ConsumerLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OmbraAnalysisChunkPlannerTest {
    private val definition =
        PiiDefinition(
            id = PiiTypeId.parse("email"),
            label = "Email",
            definition = "Personal email address",
            source = PiiDefinitionSource.BUILT_IN,
        )

    @Test
    fun `whole blocks stay ordered when they fit`() {
        val segments = listOf(segment(0, "first block"), segment(1, "second block"))
        val result = planner(templateReserve = 0).plan(segments, listOf(definition), generousLimits())

        val planned = result as OmbraChunkPlanResult.Planned
        assertEquals(1, planned.chunks.size)
        assertEquals(listOf("p0001-b0001", "p0001-b0002"), planned.chunks.single().segments.map { it.segmentId })
        assertEquals(listOf("first block", "second block"), planned.chunks.single().segments.map { it.text })
    }

    @Test
    fun `oversized block splits into stable fragments without losing text`() {
        val source = "0123456789".repeat(80)
        val segment = segment(0, source)
        val minimum =
            OmbraAnalysisProtocol.instruction.length +
                OmbraAnalysisDataSerializer.serialize(
                    listOf(definition),
                    listOf(OmbraAnalysisSegmentData("p0001-b0001-f0001", "x")),
                ).length
        val result =
            planner(templateReserve = 0).plan(
                listOf(segment),
                listOf(definition),
                ConsumerLimits(
                    maxInputCharacters = minimum + 120,
                    maxConversationMessages = 1,
                    maxJsonSchemaCharacters = OmbraAnalysisProtocol.outputJsonSchema.length,
                ),
            )

        val planned = result as OmbraChunkPlanResult.Planned
        assertTrue(planned.chunks.size > 1)
        val fragments = planned.chunks.flatMap { it.segments }
        assertEquals(source, fragments.joinToString(separator = "") { it.text })
        assertEquals("p0001-b0001-f0001", fragments.first().segmentId)
        fragments.forEachIndexed { index, fragment ->
            assertEquals("p0001-b0001-f${(index + 1).toString().padStart(4, '0')}", fragment.segmentId)
        }
    }

    @Test
    fun `fragmentation never splits a surrogate pair`() {
        val source = "A😀B😀C😀D😀E".repeat(30)
        val segment = segment(0, source)
        val minimum =
            OmbraAnalysisProtocol.instruction.length +
                OmbraAnalysisDataSerializer.serialize(
                    listOf(definition),
                    listOf(OmbraAnalysisSegmentData("p0001-b0001-f0001", "x")),
                ).length
        val result =
            planner(templateReserve = 0).plan(
                listOf(segment),
                listOf(definition),
                ConsumerLimits(minimum + 40, 1, OmbraAnalysisProtocol.outputJsonSchema.length),
            )

        val fragments = (result as OmbraChunkPlanResult.Planned).chunks.flatMap { it.segments }
        assertEquals(source, fragments.joinToString(separator = "") { it.text })
        assertFalse(fragments.any { it.text.lastOrNull()?.isHighSurrogate() == true })
        assertFalse(fragments.any { it.text.firstOrNull()?.isLowSurrogate() == true })
    }

    @Test
    fun `schema limit is rejected before document planning`() {
        val result =
            planner(templateReserve = 0).plan(
                listOf(segment(0, "text")),
                listOf(definition),
                ConsumerLimits(10_000, 1, OmbraAnalysisProtocol.outputJsonSchema.length - 1),
            )

        assertEquals(OmbraChunkPlanResult.Rejected(OmbraChunkPlanFailureCode.JSON_SCHEMA_LIMIT_EXCEEDED), result)
    }

    @Test
    fun `fixed instruction and definition overhead cannot be silently truncated`() {
        val result =
            planner(templateReserve = 64).plan(
                listOf(segment(0, "text")),
                listOf(definition),
                ConsumerLimits(100, 1, 10_000),
            )

        assertEquals(OmbraChunkPlanResult.Rejected(OmbraChunkPlanFailureCode.INPUT_OVERHEAD_EXCEEDS_LIMIT), result)
    }

    private fun planner(templateReserve: Int): OmbraAnalysisChunkPlanner =
        OmbraAnalysisChunkPlanner(OmbraAnalysisPlanningPolicy(templateOverheadCharacters = templateReserve))

    private fun generousLimits(): ConsumerLimits = ConsumerLimits(20_000, 1, 20_000)

    private fun segment(blockIndex: Int, text: String): DocumentSegment =
        DocumentSegment(
            id = SegmentId.fromIndices(0, blockIndex),
            pageIndex = 0,
            blockIndex = blockIndex,
            normalizedText = text,
        )
}
