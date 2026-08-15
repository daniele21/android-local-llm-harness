package io.github.daniele21.localllm.console.analysis

import io.github.daniele21.localllm.console.application.OmbraAnalysisRequest
import io.github.daniele21.localllm.console.application.OmbraOperationId
import io.github.daniele21.localllm.console.document.DocumentSegment
import io.github.daniele21.localllm.console.document.SegmentId
import io.github.daniele21.localllm.console.document.SourceRange
import io.github.daniele21.localllm.console.pii.PiiDefinition
import io.github.daniele21.localllm.console.pii.PiiDefinitionSource
import io.github.daniele21.localllm.console.pii.PiiTypeId
import io.github.daniele21.localllm.contracts.ConsumerLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OmbraSequentialAnalysisClientTest {
    private val email =
        PiiDefinition(
            id = PiiTypeId.parse("email"),
            label = "Email",
            definition = "Personal email address",
            source = PiiDefinitionSource.BUILT_IN,
        )

    @Test
    fun `valid structured result round trips into source validated finding`() {
        val fake =
            FakeChunkClient(generousLimits()) { _, onResult ->
                onResult(
                    Result.success(
                        """{"schemaVersion":1,"findings":[{"typeId":"email","surface":"alice@example.test","segmentId":"p0001-b0001"}]}""",
                    ),
                )
            }
        val client = client(fake)
        var result: Result<List<ValidatedFinding>>? = null

        client.analyze(
            operationId = OmbraOperationId(1),
            request = OmbraAnalysisRequest(listOf(segment("Contact alice@example.test")), listOf(email)),
            onResult = { result = it },
        )

        val finding = requireNotNull(result).getOrThrow().single()
        assertEquals(email.id, finding.typeId)
        assertEquals("alice@example.test", finding.surface)
        assertEquals(SourceRange(8, 26), finding.occurrences.single().range)
        assertEquals(listOf(0), fake.generatedOrdinals)
        assertEquals(1, fake.closeCount)
    }

    @Test
    fun `planned chunks are generated sequentially in ordinal order`() {
        val source = "0123456789".repeat(100)
        val fake =
            FakeChunkClient(fragmentingLimits()) { _, onResult ->
                onResult(Result.success("""{"schemaVersion":1,"findings":[]}"""))
            }
        val client = client(fake)
        var result: Result<List<ValidatedFinding>>? = null

        client.analyze(
            operationId = OmbraOperationId(2),
            request = OmbraAnalysisRequest(listOf(segment(source)), listOf(email)),
            onResult = { result = it },
        )

        assertTrue(requireNotNull(result).getOrThrow().isEmpty())
        assertTrue(fake.generatedOrdinals.size > 1)
        assertEquals(fake.generatedOrdinals.indices.toList(), fake.generatedOrdinals)
        assertEquals(1, fake.closeCount)
    }

    @Test
    fun `invalid source candidate fails closed without exposing partial findings`() {
        val fake =
            FakeChunkClient(generousLimits()) { _, onResult ->
                onResult(
                    Result.success(
                        """{"schemaVersion":1,"findings":[{"typeId":"email","surface":"invented@example.test","segmentId":"p0001-b0001"}]}""",
                    ),
                )
            }
        val client = client(fake)
        var result: Result<List<ValidatedFinding>>? = null

        client.analyze(
            operationId = OmbraOperationId(3),
            request = OmbraAnalysisRequest(listOf(segment("No email here")), listOf(email)),
            onResult = { result = it },
        )

        val failure = requireNotNull(result).exceptionOrNull() as OmbraAnalysisException
        assertEquals(OmbraAnalysisFailureCode.INVALID_FINDINGS, failure.code)
        assertEquals(1, failure.invalidFindingCount)
        assertEquals(1, fake.closeCount)
    }

    @Test
    fun `later chunk failure discards prior successful chunk work`() {
        var generationCount = 0
        val fake =
            FakeChunkClient(fragmentingLimits()) { _, onResult ->
                generationCount += 1
                if (generationCount == 1) {
                    onResult(Result.success("""{"schemaVersion":1,"findings":[]}"""))
                } else {
                    onResult(Result.failure(OmbraAnalysisChunkException(OmbraAnalysisChunkFailureCode.GENERATION_FAILED)))
                }
            }
        val client = client(fake)
        var result: Result<List<ValidatedFinding>>? = null

        client.analyze(
            operationId = OmbraOperationId(4),
            request = OmbraAnalysisRequest(listOf(segment("x".repeat(2_000))), listOf(email)),
            onResult = { result = it },
        )

        val failure = requireNotNull(result).exceptionOrNull() as OmbraAnalysisException
        assertEquals(OmbraAnalysisFailureCode.CHUNK_FAILED, failure.code)
        assertTrue(generationCount > 1)
        assertEquals(1, fake.closeCount)
    }

    @Test
    fun `disconnect is preserved as typed content free failure`() {
        val fake =
            FakeChunkClient(generousLimits()) { _, onResult ->
                onResult(Result.failure(OmbraAnalysisChunkException(OmbraAnalysisChunkFailureCode.DISCONNECTED)))
            }
        val client = client(fake)
        var result: Result<List<ValidatedFinding>>? = null

        client.analyze(
            operationId = OmbraOperationId(5),
            request = OmbraAnalysisRequest(listOf(segment("Text")), listOf(email)),
            onResult = { result = it },
        )

        val failure = requireNotNull(result).exceptionOrNull() as OmbraAnalysisException
        assertEquals(OmbraAnalysisFailureCode.DISCONNECTED, failure.code)
        assertEquals(1, fake.closeCount)
    }

    @Test
    fun `cancellation waits for chunk client acknowledgement and ignores late generation callback`() {
        var generationCallback: ((Result<String>) -> Unit)? = null
        val fake =
            FakeChunkClient(generousLimits()) { _, onResult ->
                generationCallback = onResult
            }
        val client = client(fake)
        var analysisResult: Result<List<ValidatedFinding>>? = null
        var cancelled = false
        val operationId = OmbraOperationId(6)

        client.analyze(
            operationId = operationId,
            request = OmbraAnalysisRequest(listOf(segment("Text")), listOf(email)),
            onResult = { analysisResult = it },
        )
        client.cancel(operationId) { cancelled = true }
        requireNotNull(generationCallback).invoke(Result.success("""{"schemaVersion":1,"findings":[]}"""))

        assertTrue(cancelled)
        assertNull(analysisResult)
        assertEquals(1, fake.cancelCount)
        assertEquals(1, fake.closeCount)
    }

    @Test
    fun `invalid structured output fails before findings reach the application port`() {
        val fake =
            FakeChunkClient(generousLimits()) { _, onResult ->
                onResult(Result.success("not-json"))
            }
        val client = client(fake)
        var result: Result<List<ValidatedFinding>>? = null

        client.analyze(
            operationId = OmbraOperationId(7),
            request = OmbraAnalysisRequest(listOf(segment("Text")), listOf(email)),
            onResult = { result = it },
        )

        val failure = requireNotNull(result).exceptionOrNull() as OmbraAnalysisException
        assertEquals(OmbraAnalysisFailureCode.INVALID_STRUCTURED_RESULT, failure.code)
        assertEquals(1, fake.closeCount)
    }

    private fun client(fake: FakeChunkClient): OmbraSequentialAnalysisClient =
        OmbraSequentialAnalysisClient(
            chunkClient = fake,
            planner = OmbraAnalysisChunkPlanner(OmbraAnalysisPlanningPolicy(templateOverheadCharacters = 0)),
        )

    private fun segment(text: String): DocumentSegment = DocumentSegment(
        id = SegmentId.fromIndices(0, 0),
        pageIndex = 0,
        blockIndex = 0,
        normalizedText = text,
    )

    private fun generousLimits(): ConsumerLimits = ConsumerLimits(20_000, 1, 20_000)

    private fun fragmentingLimits(): ConsumerLimits {
        val minimum =
            OmbraAnalysisProtocol.instruction.length +
                OmbraAnalysisDataSerializer.serialize(
                    definitions = listOf(email),
                    segments = listOf(OmbraAnalysisSegmentData("p0001-b0001-f0001", "x")),
                ).length
        return ConsumerLimits(
            maxInputCharacters = minimum + 80,
            maxConversationMessages = 1,
            maxJsonSchemaCharacters = OmbraAnalysisProtocol.outputJsonSchema.length,
        )
    }

    private class FakeChunkClient(
        private val limits: ConsumerLimits,
        private val generateBehavior: (OmbraStructuredChunkRequest, (Result<String>) -> Unit) -> Unit,
    ) : OmbraAnalysisChunkClient {
        val generatedOrdinals = mutableListOf<Int>()
        var cancelCount: Int = 0
        var closeCount: Int = 0

        override fun prepare(operationId: OmbraOperationId, onResult: (Result<ConsumerLimits>) -> Unit) {
            onResult(Result.success(limits))
        }

        override fun generate(operationId: OmbraOperationId, request: OmbraStructuredChunkRequest, onResult: (Result<String>) -> Unit) {
            generatedOrdinals += request.ordinal
            generateBehavior(request, onResult)
        }

        override fun cancel(operationId: OmbraOperationId, onCancelled: () -> Unit) {
            cancelCount += 1
            onCancelled()
        }

        override fun close(operationId: OmbraOperationId) {
            closeCount += 1
        }
    }
}
