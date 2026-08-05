package io.github.daniele21.localllm.llamacpp

import io.github.daniele21.localllm.install.GgufArtifactInspectionFailureCode
import io.github.daniele21.localllm.install.GgufArtifactInspectionResult
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LlamaCppGgufArtifactInspectorTest {
    @Test
    fun mapsSuccessfulMetadataWithoutBackendDetails() {
        val file = Files.createTempFile("gguf-inspection", ".gguf").toFile()
        val inspector =
            LlamaCppGgufArtifactInspector(
                LlamaCppBridge(
                    FakeNativeApi(
                        arrayOf(
                            "ok",
                            "3",
                            "32",
                            "64",
                            "4",
                            "2",
                            "qwen35",
                            "Qwen",
                            "15",
                        ),
                    ),
                ),
            )

        val result = inspector.inspect(file)

        result as GgufArtifactInspectionResult.Success
        assertEquals(3u, result.metadata.version)
        assertEquals("qwen35", result.metadata.architecture)
        assertEquals("Qwen", result.metadata.name)
        assertEquals(15L, result.metadata.fileType)
    }

    @Test
    fun mapsParseFailureToStableInstallationFailure() {
        val file = Files.createTempFile("gguf-inspection", ".gguf").toFile()
        val inspector =
            LlamaCppGgufArtifactInspector(
                LlamaCppBridge(
                    FakeNativeApi(
                        arrayOf(
                            "error",
                            "PARSE_FAILED",
                            "/private/path backend detail",
                        ),
                    ),
                ),
            )

        val result = inspector.inspect(file)

        result as GgufArtifactInspectionResult.Failure
        assertEquals(GgufArtifactInspectionFailureCode.INVALID_GGUF, result.code)
    }

    private class FakeNativeApi(private val inspection: Array<String>) : NativeLlamaApi {
        override fun runtimeVersion(): String = "test"

        override fun isLlamaCppLinked(): Boolean = true

        override fun supportsMmap(): Boolean = true

        override fun initialize(nativeLibraryDir: String): Array<String> = arrayOf("ok", "0")

        override fun shutdown(): Array<String> = arrayOf("ok")

        override fun loadModel(path: String, nGpuLayers: Int, useMmap: Boolean, useMlock: Boolean): Array<String> =
            arrayOf("error", "INTERNAL", "unused")

        override fun unloadModel(modelHandle: Long): Array<String> = arrayOf("ok")

        override fun inspectGguf(path: String): Array<String> = inspection
    }
}
