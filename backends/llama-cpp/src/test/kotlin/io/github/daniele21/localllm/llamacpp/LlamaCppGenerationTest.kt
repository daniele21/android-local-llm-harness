package io.github.daniele21.localllm.llamacpp

import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.models.ArtifactSource
import io.github.daniele21.localllm.models.GgufArtifact
import io.github.daniele21.localllm.models.GgufModelProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Base64

class LlamaCppGenerationTest {
    @Test
    fun `context creation forwards exact model profile`() {
        val nativeApi = FakeNativeGenerationApi(contextCreation = arrayOf("ok", "13"))
        val model = testModel()
        val profile = testProfile()

        val result = LlamaCppGenerationBridge(nativeApi).createContext(model, profile)

        assertEquals(
            ContextCreationResult.Success(
                LoadedNativeContext(NativeContextHandle(13), model),
            ),
            result,
        )
        assertEquals(
            listOf(7L, 1024, 256, 128, 3, 4, true),
            nativeApi.lastContextCreation,
        )
    }

    @Test
    fun `invalid context handle fails closed`() {
        val result = LlamaCppGenerationBridge(
            FakeNativeGenerationApi(contextCreation = arrayOf("ok", "0")),
        ).createContext(testModel(), testProfile())

        assertTrue(result is ContextCreationResult.Failure)
        assertEquals(
            GenerationNativeErrorCode.NATIVE_PROTOCOL,
            (result as ContextCreationResult.Failure).error.code,
        )
    }

    @Test
    fun `generation decodes utf8 output and metrics`() {
        val output = "Risposta locale 🌍"
        val nativeApi = FakeNativeGenerationApi(
            generation = arrayOf(
                "ok",
                Base64.getEncoder().encodeToString(output.toByteArray(Charsets.UTF_8)),
                "12",
                "4",
                "20",
                "40",
            ),
        )
        val context = testContext()
        val config = testConfig()

        val result = LlamaCppGenerationBridge(nativeApi).generate(context, "Prompt", config)

        assertEquals(
            NativeGenerationResult.Success(
                output = output,
                metrics = NativeGenerationMetrics(
                    inputTokens = 12,
                    outputTokens = 4,
                    promptDurationMs = 20,
                    generationDurationMs = 40,
                ),
            ),
            result,
        )
        assertEquals(
            listOf(11L, "Prompt", 32, 0.2f, 0.9f, 20, 123L),
            nativeApi.lastGeneration,
        )
    }

    @Test
    fun `malformed generation payload fails closed`() {
        val nativeApi = FakeNativeGenerationApi(
            generation = arrayOf("ok", "not-base64", "1", "1", "1", "1"),
        )

        val result = LlamaCppGenerationBridge(nativeApi).generate(
            testContext(),
            "Prompt",
            testConfig(),
        )

        assertTrue(result is NativeGenerationResult.Failure)
        assertEquals(
            GenerationNativeErrorCode.NATIVE_PROTOCOL,
            (result as NativeGenerationResult.Failure).error.code,
        )
    }

    @Test
    fun `invalid config is rejected before JNI`() {
        val nativeApi = FakeNativeGenerationApi()
        val result = LlamaCppGenerationBridge(nativeApi).generate(
            testContext(),
            " ",
            testConfig(),
        )

        assertTrue(result is NativeGenerationResult.Failure)
        assertEquals(
            GenerationNativeErrorCode.INVALID_ARGUMENT,
            (result as NativeGenerationResult.Failure).error.code,
        )
        assertFalse(nativeApi.generateCalled)
    }

    @Test
    fun `native generation error retains structured code`() {
        val nativeApi = FakeNativeGenerationApi(
            generation = arrayOf("error", "CONTEXT_OVERFLOW", "Requested context is too large"),
        )

        val result = LlamaCppGenerationBridge(nativeApi).generate(
            testContext(),
            "Prompt",
            testConfig(),
        )

        assertEquals(
            NativeGenerationResult.Failure(
                GenerationNativeError(
                    GenerationNativeErrorCode.CONTEXT_OVERFLOW,
                    "Requested context is too large",
                ),
            ),
            result,
        )
    }

    @Test
    fun `context release forwards native handle`() {
        val nativeApi = FakeNativeGenerationApi(contextRelease = arrayOf("ok"))
        val result = LlamaCppGenerationBridge(nativeApi).releaseContext(testContext())

        assertEquals(GenerationNativeOperationResult.Success, result)
        assertEquals(11L, nativeApi.releasedContextHandle)
    }

    private fun testModel(): LoadedNativeModel = LoadedNativeModel(
        handle = NativeModelHandle(7),
        profileId = "profile",
        digest = ModelDigest("sha256:test"),
        file = File("model.gguf"),
        loadDurationMs = 1,
    )

    private fun testContext(): LoadedNativeContext = LoadedNativeContext(
        handle = NativeContextHandle(11),
        model = testModel(),
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
        contextSize = 1024,
        batchSize = 256,
        microBatchSize = 128,
        cpuThreads = 3,
        batchThreads = 4,
        gpuLayers = 0,
        flashAttention = true,
    )

    private fun testConfig(): NativeGenerationConfig = NativeGenerationConfig(
        maxOutputTokens = 32,
        temperature = 0.2f,
        topP = 0.9f,
        topK = 20,
        seed = 123,
    )
}

private class FakeNativeGenerationApi(
    private val contextCreation: Array<String> = arrayOf("ok", "1"),
    private val contextRelease: Array<String> = arrayOf("ok"),
    private val generation: Array<String> = arrayOf("ok", "", "0", "0", "0", "0"),
) : NativeLlamaGenerationApi {
    var lastContextCreation: List<Any>? = null
    var releasedContextHandle: Long? = null
    var lastGeneration: List<Any>? = null
    var generateCalled: Boolean = false

    override fun createContext(
        modelHandle: Long,
        contextSize: Int,
        batchSize: Int,
        microBatchSize: Int,
        threads: Int,
        batchThreads: Int,
        flashAttention: Boolean,
    ): Array<String> {
        lastContextCreation = listOf(
            modelHandle,
            contextSize,
            batchSize,
            microBatchSize,
            threads,
            batchThreads,
            flashAttention,
        )
        return contextCreation
    }

    override fun releaseContext(contextHandle: Long): Array<String> {
        releasedContextHandle = contextHandle
        return contextRelease
    }

    override fun generate(
        contextHandle: Long,
        prompt: String,
        maxOutputTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        seed: Long,
    ): Array<String> {
        generateCalled = true
        lastGeneration = listOf(
            contextHandle,
            prompt,
            maxOutputTokens,
            temperature,
            topP,
            topK,
            seed,
        )
        return generation
    }
}
