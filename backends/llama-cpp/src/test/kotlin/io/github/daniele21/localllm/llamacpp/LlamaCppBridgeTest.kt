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

class LlamaCppBridgeTest {
    @Test
    fun `runtime status exposes pinned native capabilities`() {
        val nativeApi = FakeNativeLlamaApi(
            version = "b9637 (aedb2a5e)",
            linked = true,
            mmap = true,
        )

        val status = LlamaCppBridge(nativeApi).inspect(testProfile())

        assertTrue(status.linked)
        assertTrue(status.supportsMmap)
        assertEquals("b9637 (aedb2a5e)", status.runtimeVersion)
        assertEquals("test-profile", status.modelProfileId)
        assertEquals("Pinned llama.cpp CPU backend linked", status.detail)
    }

    @Test
    fun `valid native response is decoded into typed GGUF metadata`() {
        val model = temporaryFile()
        val nativeApi = FakeNativeLlamaApi(
            inspection = arrayOf(
                "ok",
                "3",
                "32",
                "224",
                "3",
                "0",
                "qwen2",
                "fixture-model",
                "15",
            ),
        )

        val result = LlamaCppBridge(nativeApi).inspectGguf(model)

        assertTrue(result is GgufInspectionResult.Success)
        val metadata = (result as GgufInspectionResult.Success).metadata
        assertEquals(3u, metadata.version)
        assertEquals(32uL, metadata.alignment)
        assertEquals(224uL, metadata.dataOffset)
        assertEquals(3L, metadata.keyValueCount)
        assertEquals(0L, metadata.tensorCount)
        assertEquals("qwen2", metadata.architecture)
        assertEquals("fixture-model", metadata.name)
        assertEquals(15L, metadata.fileType)
        assertEquals(model.absolutePath, nativeApi.lastInspectedPath)
    }

    @Test
    fun `optional native metadata is represented as null`() {
        val model = temporaryFile()
        val nativeApi = FakeNativeLlamaApi(
            inspection = arrayOf("ok", "3", "32", "0", "0", "0", "", "", ""),
        )

        val result = LlamaCppBridge(nativeApi).inspectGguf(model) as GgufInspectionResult.Success

        assertEquals(null, result.metadata.architecture)
        assertEquals(null, result.metadata.name)
        assertEquals(null, result.metadata.fileType)
    }

    @Test
    fun `native failures retain their structured error code`() {
        val model = temporaryFile()
        val nativeApi = FakeNativeLlamaApi(
            inspection = arrayOf("error", "INVALID_MAGIC", "File does not start with GGUF magic bytes"),
        )

        val result = LlamaCppBridge(nativeApi).inspectGguf(model)

        assertEquals(
            GgufInspectionResult.Failure(
                GgufInspectionError(
                    GgufInspectionErrorCode.INVALID_MAGIC,
                    "File does not start with GGUF magic bytes",
                ),
            ),
            result,
        )
    }

    @Test
    fun `malformed native responses fail closed`() {
        val model = temporaryFile()
        val nativeApi = FakeNativeLlamaApi(inspection = arrayOf("ok", "not-a-version"))

        val result = LlamaCppBridge(nativeApi).inspectGguf(model)

        assertTrue(result is GgufInspectionResult.Failure)
        assertEquals(
            GgufInspectionErrorCode.NATIVE_PROTOCOL,
            (result as GgufInspectionResult.Failure).error.code,
        )
    }

    @Test
    fun `missing files are rejected before JNI is called`() {
        val nativeApi = FakeNativeLlamaApi()
        val missing = File(System.getProperty("java.io.tmpdir"), "missing-${System.nanoTime()}.gguf")

        val result = LlamaCppBridge(nativeApi).inspectGguf(missing)

        assertTrue(result is GgufInspectionResult.Failure)
        assertEquals(
            GgufInspectionErrorCode.FILE_NOT_FOUND,
            (result as GgufInspectionResult.Failure).error.code,
        )
        assertFalse(nativeApi.inspectCalled)
    }

    private fun temporaryFile(): File = File.createTempFile("gguf-bridge-test", ".gguf").apply {
        deleteOnExit()
    }

    private fun testProfile(): GgufModelProfile = GgufModelProfile(
        id = "test-profile",
        artifact = GgufArtifact(
            digest = ModelDigest("sha256:test"),
            fileName = "test.gguf",
            sizeBytes = 1,
            architecture = "qwen2",
            quantization = "Q4_K_M",
            source = ArtifactSource.Imported("test"),
        ),
        contextSize = 512,
        batchSize = 128,
        microBatchSize = 64,
        cpuThreads = 2,
        batchThreads = 2,
        gpuLayers = 0,
    )
}

private class FakeNativeLlamaApi(
    private val version: String = "test-version",
    private val linked: Boolean = true,
    private val mmap: Boolean = true,
    private val inspection: Array<String> = arrayOf("error", "PARSE_FAILED", "not configured"),
) : NativeLlamaApi {
    var inspectCalled: Boolean = false
    var lastInspectedPath: String? = null

    override fun runtimeVersion(): String = version
    override fun isLlamaCppLinked(): Boolean = linked
    override fun supportsMmap(): Boolean = mmap

    override fun inspectGguf(path: String): Array<String> {
        inspectCalled = true
        lastInspectedPath = path
        return inspection
    }
}
