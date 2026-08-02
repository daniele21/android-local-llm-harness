package io.github.daniele21.localllm.devicetest

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.daniele21.localllm.llamacpp.GgufInspectionResult
import io.github.daniele21.localllm.llamacpp.LlamaCppBridge
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class GgufInspectionDeviceTest {
    @Test
    fun ggufCanBeInspectedWithoutLoadingTheModel() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val relativePath = InstrumentationRegistry.getArguments()
            .getString("modelRelativePath")
            ?.takeIf { it.isNotBlank() }
            ?: "files/e2e/model.gguf"
        val modelFile = resolveModelFile(context, relativePath)

        when (val result = LlamaCppBridge().inspectGguf(modelFile)) {
            is GgufInspectionResult.Success -> {
                assertTrue("GGUF version must be positive", result.metadata.version > 0u)
                assertTrue("GGUF tensor count must be positive", result.metadata.tensorCount > 0)
                assertTrue("GGUF data offset must be positive", result.metadata.dataOffset > 0uL)
                println(
                    "LOCAL_LLM_E2E inspection " +
                        "version=${result.metadata.version} " +
                        "architecture=${result.metadata.architecture} " +
                        "tensorCount=${result.metadata.tensorCount} " +
                        "fileType=${result.metadata.fileType}",
                )
            }
            is GgufInspectionResult.Failure -> throw AssertionError(
                "GGUF inspection failed: ${result.error.code}: ${result.error.message}",
            )
        }
    }

    private fun resolveModelFile(context: Context, relativePath: String): File {
        require(relativePath.isNotBlank() && !File(relativePath).isAbsolute) {
            "modelRelativePath must be relative to the application data directory"
        }
        val dataRoot = context.dataDir.canonicalFile
        val modelFile = File(dataRoot, relativePath).canonicalFile
        require(modelFile.path.startsWith(dataRoot.path + File.separator)) {
            "modelRelativePath escapes the application data directory"
        }
        require(modelFile.isFile && modelFile.canRead()) {
            "Model file is missing or unreadable: ${modelFile.path}"
        }
        return modelFile
    }
}
