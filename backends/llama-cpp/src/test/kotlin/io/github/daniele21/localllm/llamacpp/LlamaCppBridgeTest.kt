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
import kotlin.io.path.createTempDirectory

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
    fun `runtime initialization exposes registered device count`() {
        val nativeDirectory = createTempDirectory("native-libraries").toFile()
        val nativeApi = FakeNativeLlamaApi(initialization = arrayOf("ok", "3"))

        val result = LlamaCppBridge(nativeApi).initializeRuntime(nativeDirectory)

        assertEquals(RuntimeInitializationResult.Success(deviceCount = 3), result)
        assertEquals(nativeDirectory.absolutePath, nativeApi.initializedDirectory)
        nativeDirectory.deleteRecursively()
    }

    @Test
    fun `invalid initialization response fails closed`() {
        val nativeDirectory = createTempDirectory("native-libraries").toFile()
        val nativeApi = FakeNativeLlamaApi(initialization = arrayOf("ok", "not-a-number"))

        val result = LlamaCppBridge(nativeApi).initializeRuntime(nativeDirectory)

        assertTrue(result is RuntimeInitializationResult.Failure)
        assertEquals(
            NativeRuntimeErrorCode.NATIVE_PROTOCOL,
            (result as RuntimeInitializationResult.Failure).error.code,
        )
        nativeDirectory.deleteRecursively()
    }

    @Test
    fun `model load forwards exact profile parameters and decodes handle`() {
        val model = temporaryFile()
        val nativeApi = FakeNativeLlamaApi(modelLoad = arrayOf("ok", "42", "123"))
        val profile = testProfile(gpuLayers = 7, useMmap = false, useMlock = true)

        val result = LlamaCppBridge(nativeApi).loadModel(model, profile)

        assertTrue(result is ModelLoadResult.Success)
        val loaded = (result as ModelLoadResult.Success).model
        assertEquals(42L, loaded.handle.value)
        assertEquals(123L, loaded.loadDurationMs)
        assertEquals(profile.id, loaded.profileId)
        assertEquals(profile.artifact.digest, loaded.digest)
        assertEquals(listOf(model.absolutePath, 7, false, true), nativeApi.lastModelLoad)
    }

    @Test
    fun `model unload and runtime shutdown use native handles`() {
        val modelFile = temporaryFile()
        val nativeApi = FakeNativeLlamaApi()
        val loaded = LoadedNativeModel(
            handle = NativeModelHandle(9),
            profileId = "profile",
            digest = ModelDigest("sha256:test"),
            file = modelFile,
            loadDurationMs = 1,
        )
        val bridge = LlamaCppBridge(nativeApi)

        assertEquals(NativeOperationResult.Success, bridge.unloadModel(loaded))
        assertEquals(9L, nativeApi.unloadedHandle)
        assertEquals(NativeOperationResult.Success, bridge.shutdownRuntime())
        assertTrue(nativeApi.shutdownCalled)
    }

    @Test
    fun `native model load failures retain structured error`() {
        val model = temporaryFile()
        val nativeApi = FakeNativeLlamaApi(
            modelLoad = arrayOf("error", "MODEL_LOAD_FAILED", "Model is incompatible"),
        )

        val result = LlamaCppBridge(nativeApi).loadModel(model, testProfile())

        assertEquals(
            ModelLoadResult.Failure(
                NativeRuntimeError(NativeRuntimeErrorCode.MODEL_LOAD_FAILED, "Model is incompatible"),
            ),
            result,
        )
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
                "262144",
                "24",
                "1024",
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
        assertEquals(262144L, metadata.contextLength)
        assertEquals(24L, metadata.blockCount)
        assertEquals(1024L, metadata.embeddingLength)
        assertEquals(model.absolutePath, nativeApi.lastInspectedPath)
    }

    @Test
    fun `optional native metadata is represented as null`() {
        val model = temporaryFile()
        val nativeApi = FakeNativeLlamaApi(
            inspection = arrayOf("ok", "3", "32", "0", "0", "0", "", "", "", "", "", ""),
        )

        val result = LlamaCppBridge(nativeApi).inspectGguf(model) as GgufInspectionResult.Success

        assertEquals(null, result.metadata.architecture)
        assertEquals(null, result.metadata.name)
        assertEquals(null, result.metadata.fileType)
        assertEquals(null, result.metadata.contextLength)
        assertEquals(null, result.metadata.blockCount)
        assertEquals(null, result.metadata.embeddingLength)
    }

    @Test
    fun `native inspection failures retain their structured error code`() {
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
    fun `malformed inspection responses fail closed`() {
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

    private fun testProfile(gpuLayers: Int = 0, useMmap: Boolean = true, useMlock: Boolean = false): GgufModelProfile = GgufModelProfile(
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
        gpuLayers = gpuLayers,
        useMmap = useMmap,
        useMlock = useMlock,
    )
}

private class FakeNativeLlamaApi(
    private val version: String = "test-version",
    private val linked: Boolean = true,
    private val mmap: Boolean = true,
    private val initialization: Array<String> = arrayOf("ok", "1"),
    private val modelLoad: Array<String> = arrayOf("ok", "1", "0"),
    private val modelUnload: Array<String> = arrayOf("ok"),
    private val shutdown: Array<String> = arrayOf("ok"),
    private val inspection: Array<String> = arrayOf("error", "PARSE_FAILED", "not configured"),
) : NativeLlamaApi {
    var initializedDirectory: String? = null
    var lastModelLoad: List<Any>? = null
    var unloadedHandle: Long? = null
    var shutdownCalled: Boolean = false
    var inspectCalled: Boolean = false
    var lastInspectedPath: String? = null

    override fun runtimeVersion(): String = version
    override fun isLlamaCppLinked(): Boolean = linked
    override fun supportsMmap(): Boolean = mmap

    override fun initialize(nativeLibraryDir: String): Array<String> {
        initializedDirectory = nativeLibraryDir
        return initialization
    }

    override fun shutdown(): Array<String> {
        shutdownCalled = true
        return shutdown
    }

    override fun loadModel(path: String, nGpuLayers: Int, useMmap: Boolean, useMlock: Boolean): Array<String> {
        lastModelLoad = listOf(path, nGpuLayers, useMmap, useMlock)
        return modelLoad
    }

    override fun unloadModel(modelHandle: Long): Array<String> {
        unloadedHandle = modelHandle
        return modelUnload
    }

    override fun inspectGguf(path: String): Array<String> {
        inspectCalled = true
        lastInspectedPath = path
        return inspection
    }
}
