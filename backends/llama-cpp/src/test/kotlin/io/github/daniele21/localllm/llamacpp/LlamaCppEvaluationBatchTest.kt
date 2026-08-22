package io.github.daniele21.localllm.llamacpp

import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.models.ArtifactSource
import io.github.daniele21.localllm.models.GgufArtifact
import io.github.daniele21.localllm.models.GgufModelProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LlamaCppEvaluationBatchTest {
    @Test
    fun `evaluation context creation forwards dedicated multi-sequence configuration`() {
        val nativeApi = FakeNativeEvaluationBatchApi(contextCreation = arrayOf("ok", "31"))
        val model = testModel()

        val result = LlamaCppEvaluationBatchBridge(nativeApi).createContext(
            model = model,
            profile = testProfile(),
            perSequenceContextSize = 2_048,
            maxSequences = 4,
        )

        assertEquals(
            EvaluationContextCreationResult.Success(
                LoadedNativeEvaluationContext(NativeEvaluationContextHandle(31), model, 2_048, 4),
            ),
            result,
        )
        assertEquals(
            listOf(7L, 2_048, 4, 256, 128, 3, 4, NativeFlashAttentionMode.ENABLED.nativeValue, null, null),
            nativeApi.lastContextCreation,
        )
    }

    @Test
    fun `unaligned evaluation context fails before JNI`() {
        val nativeApi = FakeNativeEvaluationBatchApi()

        val result = LlamaCppEvaluationBatchBridge(nativeApi).createContext(
            testModel(),
            testProfile(),
            perSequenceContextSize = 2_100,
            maxSequences = 2,
        )

        assertTrue(result is EvaluationContextCreationResult.Failure)
        assertEquals(
            GenerationNativeErrorCode.INVALID_ARGUMENT,
            (result as EvaluationContextCreationResult.Failure).error.code,
        )
        assertNull(nativeApi.lastContextCreation)
    }

    @Test
    fun `batch generation preserves per-case configuration and ordered attribution`() {
        val nativeApi = FakeNativeEvaluationBatchApi(
            generation = arrayOf(
                "ok",
                "2",
                "case-a",
                "COMPLETED",
                "alpha",
                "10",
                "2",
                "4",
                "6",
                "END_OF_GENERATION",
                "case-b",
                "CANCELLED",
                "",
                "8",
                "1",
                "3",
                "5",
                "UNKNOWN",
            ),
        )
        val context = testContext(maxSequences = 4)
        val cases = listOf(
            testCase("case-a", "prompt-a", seed = 11, stopTokenIds = intArrayOf(7), stopSequences = listOf("STOP")),
            testCase("case-b", "prompt-b", seed = 12, outputConstraintType = "JSON"),
        )

        val result = LlamaCppEvaluationBatchBridge(nativeApi).generate(context, cases)

        assertTrue(result is NativeEvaluationBatchResult.Completed)
        val completed = result as NativeEvaluationBatchResult.Completed
        assertEquals(listOf("case-a", "case-b"), completed.cases.map(NativeEvaluationBatchCaseResult::requestId))
        assertEquals(NativeEvaluationBatchCaseStatus.COMPLETED, completed.cases[0].status)
        assertEquals(NativeEvaluationBatchCaseStatus.CANCELLED, completed.cases[1].status)
        assertEquals("alpha", completed.cases[0].output)
        assertEquals(10, completed.cases[0].metrics.inputTokens)
        assertEquals(12L, nativeApi.lastSeeds?.get(1))
        assertEquals("JSON", nativeApi.lastOutputConstraintTypes?.get(1))
        assertEquals(listOf(7), nativeApi.lastStopTokenIds?.get(0)?.toList())
        assertEquals(listOf("STOP"), nativeApi.lastStopSequences?.get(0)?.toList())
    }

    @Test
    fun `reordered native results fail closed`() {
        val nativeApi = FakeNativeEvaluationBatchApi(
            generation = arrayOf(
                "ok",
                "2",
                "case-b",
                "COMPLETED",
                "b",
                "1",
                "1",
                "1",
                "1",
                "MAX_OUTPUT_TOKENS",
                "case-a",
                "COMPLETED",
                "a",
                "1",
                "1",
                "1",
                "1",
                "MAX_OUTPUT_TOKENS",
            ),
        )

        val result = LlamaCppEvaluationBatchBridge(nativeApi).generate(
            testContext(),
            listOf(testCase("case-a", "a"), testCase("case-b", "b")),
        )

        assertTrue(result is NativeEvaluationBatchResult.Failure)
        assertEquals(
            GenerationNativeErrorCode.NATIVE_PROTOCOL,
            (result as NativeEvaluationBatchResult.Failure).error.code,
        )
    }

    @Test
    fun `reasoning transition fails before evaluation JNI`() {
        val nativeApi = FakeNativeEvaluationBatchApi()
        val reasoningCase = testCase("case-a", "a").copy(
            config = testCase("case-a", "a").config.copy(
                reasoningMaxTokens = 4,
                reasoningCloseMarker = "</think>",
                reasoningForcedCloseText = "</think>",
            ),
        )

        val result = LlamaCppEvaluationBatchBridge(nativeApi).generate(
            testContext(),
            listOf(reasoningCase, testCase("case-b", "b")),
        )

        assertTrue(result is NativeEvaluationBatchResult.Failure)
        assertNull(nativeApi.lastRequestIds)
    }

    @Test
    fun `context release and cancellation use dedicated native API`() {
        val nativeApi = FakeNativeEvaluationBatchApi(cancel = arrayOf("ok", "true"))
        val bridge = LlamaCppEvaluationBatchBridge(nativeApi)

        assertEquals(GenerationNativeOperationResult.Success, bridge.releaseContext(testContext()))
        assertEquals(41L, nativeApi.releasedContextHandle)
        assertEquals(EvaluationBatchCancelResult.Accepted(true), bridge.cancel("case-a"))
        assertEquals("case-a", nativeApi.cancelledRequestId)
    }

    private fun testCase(
        requestId: String,
        prompt: String,
        seed: Long = 9,
        outputConstraintType: String = "TEXT",
        stopTokenIds: IntArray = intArrayOf(),
        stopSequences: List<String> = emptyList(),
    ): NativeEvaluationBatchCase = NativeEvaluationBatchCase(
        requestId = requestId,
        prompt = prompt,
        config = NativeGenerationConfig(
            maxOutputTokens = 16,
            temperature = 0f,
            topP = 1f,
            topK = 0,
            repeatPenalty = 1f,
            repeatLastN = 0,
            seed = seed,
            outputConstraintType = outputConstraintType,
            stopTokenIds = stopTokenIds,
            stopSequences = stopSequences,
        ),
    )

    private fun testContext(maxSequences: Int = 2): LoadedNativeEvaluationContext = LoadedNativeEvaluationContext(
        handle = NativeEvaluationContextHandle(41),
        model = testModel(),
        perSequenceContextSize = 2_048,
        maxSequences = maxSequences,
    )

    private fun testModel(): LoadedNativeModel = LoadedNativeModel(
        handle = NativeModelHandle(7),
        profileId = "profile",
        digest = ModelDigest("sha256:test"),
        file = File("model.gguf"),
        loadDurationMs = 1,
    )

    private fun testProfile(): GgufModelProfile = GgufModelProfile(
        id = "profile",
        artifact = GgufArtifact(
            digest = ModelDigest("sha256:test"),
            fileName = "model.gguf",
            sizeBytes = 1,
            architecture = "qwen2",
            quantization = "Q4_K_M",
            source = ArtifactSource.Imported("test"),
        ),
        contextSize = 2_048,
        batchSize = 256,
        microBatchSize = 128,
        cpuThreads = 3,
        batchThreads = 4,
        gpuLayers = 0,
        flashAttention = true,
    )
}

private class FakeNativeEvaluationBatchApi(
    private val contextCreation: Array<String> = arrayOf("ok", "31"),
    private val contextRelease: Array<String> = arrayOf("ok"),
    private val generation: Array<String> = arrayOf("error", "INTERNAL", "not configured"),
    private val cancel: Array<String> = arrayOf("ok", "false"),
) : NativeLlamaEvaluationBatchApi {
    var lastContextCreation: List<Any?>? = null
    var releasedContextHandle: Long? = null
    var lastRequestIds: Array<String>? = null
    var lastSeeds: LongArray? = null
    var lastOutputConstraintTypes: Array<String>? = null
    var lastStopTokenIds: Array<IntArray>? = null
    var lastStopSequences: Array<Array<String>>? = null
    var cancelledRequestId: String? = null

    override fun createEvaluationContext(
        modelHandle: Long,
        perSequenceContextSize: Int,
        maxSequences: Int,
        batchSize: Int,
        microBatchSize: Int,
        threads: Int,
        batchThreads: Int,
        flashAttentionMode: Int,
        kvCacheTypeK: String?,
        kvCacheTypeV: String?,
    ): Array<String> {
        lastContextCreation = listOf(
            modelHandle,
            perSequenceContextSize,
            maxSequences,
            batchSize,
            microBatchSize,
            threads,
            batchThreads,
            flashAttentionMode,
            kvCacheTypeK,
            kvCacheTypeV,
        )
        return contextCreation
    }

    override fun releaseEvaluationContext(contextHandle: Long): Array<String> {
        releasedContextHandle = contextHandle
        return contextRelease
    }

    override fun generateEvaluationBatch(
        contextHandle: Long,
        requestIds: Array<String>,
        prompts: Array<String>,
        maxOutputTokens: IntArray,
        temperatures: FloatArray,
        topPs: FloatArray,
        topKs: IntArray,
        minPs: FloatArray,
        presencePenalties: FloatArray,
        repeatPenalties: FloatArray,
        repeatLastNs: IntArray,
        seeds: LongArray,
        outputConstraintTypes: Array<String>,
        outputSchemas: Array<String>,
        stopTokenIds: Array<IntArray>,
        stopSequences: Array<Array<String>>,
    ): Array<String> {
        lastRequestIds = requestIds
        lastSeeds = seeds
        lastOutputConstraintTypes = outputConstraintTypes
        lastStopTokenIds = stopTokenIds
        lastStopSequences = stopSequences
        return generation
    }

    override fun cancelEvaluationCase(requestId: String): Array<String> {
        cancelledRequestId = requestId
        return cancel
    }
}
