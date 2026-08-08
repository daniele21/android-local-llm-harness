package io.github.daniele21.localllm.llamacpp

import io.github.daniele21.localllm.contracts.ModelDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Base64

class LlamaCppStreamingTest {
    @Test
    fun `streaming decodes aggregated utf8 chunks and forwards config`() {
        val chunks = listOf("Ciao ", "mondo 🌍")
        val nativeApi = FakeNativeStreamingApi(
            chunks = chunks.mapIndexed { index, text ->
                Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8)) to index + 1
            },
            terminal = arrayOf("ok", "5", "2", "10", "20", "END_OF_GENERATION"),
        )
        val received = mutableListOf<NativeTextChunk>()

        val result = LlamaCppStreamingBridge(nativeApi).generate(
            context = testContext(),
            requestId = "request-1",
            prompt = "Prompt",
            config = testConfig(),
            listener = NativeStreamingListener { chunk ->
                received += chunk
                true
            },
        )

        assertEquals(
            NativeStreamingResult.Completed(
                NativeStreamingMetrics(5, 2, 10, 20, "END_OF_GENERATION"),
            ),
            result,
        )
        assertEquals(
            listOf(
                NativeTextChunk("Ciao ", 1),
                NativeTextChunk("mondo 🌍", 2),
            ),
            received,
        )
        assertEquals(
            listOf(11L, "request-1", "Prompt", 16, 0.1f, 0.95f, 40, 0f, 0f, 1.05f, 64, 77L, "TEXT"),
            nativeApi.lastGeneration,
        )
    }

    @Test
    fun `listener rejection propagates cancellation`() {
        val nativeApi = FakeNativeStreamingApi(
            chunks = listOf(Base64.getEncoder().encodeToString("first".toByteArray()) to 1),
            terminal = arrayOf("cancelled", "4", "1", "3", "5", "UNKNOWN"),
        )

        val result = LlamaCppStreamingBridge(nativeApi).generate(
            testContext(),
            "request-2",
            "Prompt",
            testConfig(),
            NativeStreamingListener { false },
        )

        assertEquals(
            NativeStreamingResult.Cancelled(
                NativeStreamingMetrics(4, 1, 3, 5, "UNKNOWN"),
            ),
            result,
        )
        assertFalse(nativeApi.lastCallbackAccepted)
    }

    @Test
    fun `invalid base64 chunk rejects callback safely`() {
        val nativeApi = FakeNativeStreamingApi(
            chunks = listOf("invalid%%%" to 1),
            terminal = arrayOf("cancelled", "1", "0", "1", "1", "UNKNOWN"),
        )
        var listenerCalled = false

        val result = LlamaCppStreamingBridge(nativeApi).generate(
            testContext(),
            "request-3",
            "Prompt",
            testConfig(),
            NativeStreamingListener {
                listenerCalled = true
                true
            },
        )

        assertTrue(result is NativeStreamingResult.Cancelled)
        assertFalse(listenerCalled)
        assertFalse(nativeApi.lastCallbackAccepted)
    }

    @Test
    fun `explicit cancellation reports whether request was running`() {
        val nativeApi = FakeNativeStreamingApi(cancel = arrayOf("ok", "true"))

        val result = LlamaCppStreamingBridge(nativeApi).cancel("request-4")

        assertEquals(StreamingCancelResult.Accepted(wasRunning = true), result)
        assertEquals("request-4", nativeApi.cancelledRequestId)
    }

    @Test
    fun `malformed cancellation state fails closed`() {
        val result = LlamaCppStreamingBridge(
            FakeNativeStreamingApi(cancel = arrayOf("ok", "maybe")),
        ).cancel("request-5")

        assertTrue(result is StreamingCancelResult.Failure)
        assertEquals(
            StreamingNativeErrorCode.NATIVE_PROTOCOL,
            (result as StreamingCancelResult.Failure).error.code,
        )
    }

    @Test
    fun `invalid request is rejected before JNI`() {
        val nativeApi = FakeNativeStreamingApi()

        val result = LlamaCppStreamingBridge(nativeApi).generate(
            testContext(),
            " ",
            "Prompt",
            testConfig(),
            NativeStreamingListener { true },
        )

        assertTrue(result is NativeStreamingResult.Failure)
        assertEquals(
            StreamingNativeErrorCode.INVALID_ARGUMENT,
            (result as NativeStreamingResult.Failure).error.code,
        )
        assertFalse(nativeApi.generateCalled)
    }

    @Test
    fun `enabled repeat penalty requires a positive token window before JNI`() {
        val nativeApi = FakeNativeStreamingApi()

        val result = LlamaCppStreamingBridge(nativeApi).generate(
            testContext(),
            "request-invalid-repeat",
            "Prompt",
            testConfig().copy(repeatPenalty = 1.05f, repeatLastN = 0),
            NativeStreamingListener { true },
        )

        assertTrue(result is NativeStreamingResult.Failure)
        assertEquals(
            StreamingNativeErrorCode.INVALID_ARGUMENT,
            (result as NativeStreamingResult.Failure).error.code,
        )
        assertFalse(nativeApi.generateCalled)
    }

    private fun testContext(): LoadedNativeContext = LoadedNativeContext(
        handle = NativeContextHandle(11),
        model = LoadedNativeModel(
            handle = NativeModelHandle(7),
            profileId = "profile",
            digest = ModelDigest("sha256:test"),
            file = File("model.gguf"),
            loadDurationMs = 1,
        ),
    )

    private fun testConfig(): NativeGenerationConfig = NativeGenerationConfig(
        maxOutputTokens = 16,
        temperature = 0.1f,
        topP = 0.95f,
        topK = 40,
        repeatPenalty = 1.05f,
        repeatLastN = 64,
        seed = 77,
    )
}

private class FakeNativeStreamingApi(
    private val chunks: List<Pair<String, Int>> = emptyList(),
    private val terminal: Array<String> = arrayOf("ok", "0", "0", "0", "0", "UNKNOWN"),
    private val cancel: Array<String> = arrayOf("ok", "false"),
) : NativeLlamaStreamingApi {
    var generateCalled: Boolean = false
    var lastGeneration: List<Any>? = null
    var lastCallbackAccepted: Boolean = true
    var cancelledRequestId: String? = null

    override fun generateStreaming(
        contextHandle: Long,
        requestId: String,
        prompt: String,
        maxOutputTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        minP: Float,
        presencePenalty: Float,
        repeatPenalty: Float,
        repeatLastN: Int,
        seed: Long,
        outputConstraintType: String,
        outputSchema: String?,
        stopTokenIds: IntArray,
        stopSequences: Array<String>,
        callback: NativeStreamingCallback,
    ): Array<String> {
        generateCalled = true
        lastGeneration = listOf(
            contextHandle,
            requestId,
            prompt,
            maxOutputTokens,
            temperature,
            topP,
            topK,
            minP,
            presencePenalty,
            repeatPenalty,
            repeatLastN,
            seed,
            outputConstraintType,
        )
        for ((text, generatedTokens) in chunks) {
            lastCallbackAccepted = callback.onChunk(text, generatedTokens)
            if (!lastCallbackAccepted) break
        }
        return terminal
    }

    override fun cancel(requestId: String): Array<String> {
        cancelledRequestId = requestId
        return cancel
    }
}
