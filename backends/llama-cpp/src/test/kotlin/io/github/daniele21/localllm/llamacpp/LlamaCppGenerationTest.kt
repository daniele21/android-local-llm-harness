package io.github.daniele21.localllm.llamacpp

import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.models.ArtifactSource
import io.github.daniele21.localllm.models.GgufArtifact
import io.github.daniele21.localllm.models.GgufModelProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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
}

private class FakeNativeGenerationApi(
    private val contextCreation: Array<String> = arrayOf("ok", "1"),
    private val contextRelease: Array<String> = arrayOf("ok"),
) : NativeLlamaGenerationApi {
    var lastContextCreation: List<Any>? = null
    var releasedContextHandle: Long? = null

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
}
