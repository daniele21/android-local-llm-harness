package io.github.daniele21.localllm.devicetest

import android.app.Instrumentation
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.llamacpp.LlamaCppBridge
import io.github.daniele21.localllm.llamacpp.ModelLoadResult
import io.github.daniele21.localllm.llamacpp.NativeOperationResult
import io.github.daniele21.localllm.llamacpp.RuntimeInitializationResult
import io.github.daniele21.localllm.models.ArtifactSource
import io.github.daniele21.localllm.models.GgufArtifact
import io.github.daniele21.localllm.models.GgufModelProfile
import io.github.daniele21.localllm.models.Qwen35ModelTier
import io.github.daniele21.localllm.models.Qwen35RuntimeTuningProfiles
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class LlupModelLoadLatencyInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val arguments = InstrumentationRegistry.getArguments()

    @Test
    fun recordsModelLoadLatencyEvidence() {
        val config = Config.from(arguments)
        assertEquals(
            "LLUP evidence backend revision must match the compiled runtime policy",
            Qwen35RuntimeTuningProfiles.LLAMA_CPP_REVISION,
            config.backendRevision,
        )

        val modelFile = resolveModelFile(config.modelRelativePath)
        assertEquals("LLUP model digest mismatch", config.modelSha256, sha256(modelFile))
        val profile = profile(config, modelFile)
        val bridge = LlamaCppBridge()
        val nativeLibraryDir = File(context.applicationInfo.nativeLibraryDir)
        require(nativeLibraryDir.isDirectory) { "Native library directory is unavailable" }

        val initialization = bridge.initializeRuntime(nativeLibraryDir)
        assertTrue("llama.cpp runtime initialization failed: $initialization", initialization is RuntimeInitializationResult.Success)
        val runtimeVersion = bridge.inspect(profile).runtimeVersion

        try {
            repeat(config.repetitions) { sampleIndex ->
                val thermalBefore = currentThermalStatus()
                val loaded = bridge.loadModel(modelFile, profile)
                assertTrue("LLUP model load failed: $loaded", loaded is ModelLoadResult.Success)
                val model = (loaded as ModelLoadResult.Success).model
                try {
                    emitEvidence(
                        JSONObject()
                            .put("schemaVersion", 1)
                            .put("evidenceType", "LLUP_MODEL_LOAD_LATENCY")
                            .put("sampleIndex", sampleIndex)
                            .put("runtimeSourceCommit", config.runtimeSourceCommit)
                            .put("evidenceHarnessCommit", config.evidenceHarnessCommit)
                            .put("backendRevision", config.backendRevision)
                            .put("runtimeVersion", runtimeVersion)
                            .put("modelTier", config.tier.name)
                            .put("modelDigest", config.modelSha256)
                            .put("quantization", "Q4_K_M")
                            .put("loadDurationMs", model.loadDurationMs)
                            .put("deviceModel", Build.MODEL)
                            .put("androidRelease", Build.VERSION.RELEASE)
                            .put("sdkInt", Build.VERSION.SDK_INT)
                            .put("abi", Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown")
                            .put("thermalStatusBefore", thermalBefore)
                            .put("thermalStatusAfter", currentThermalStatus()),
                    )
                } finally {
                    assertEquals(NativeOperationResult.Success, bridge.unloadModel(model))
                }
            }
        } finally {
            assertEquals(NativeOperationResult.Success, bridge.shutdownRuntime())
        }
    }

    private fun resolveModelFile(relativePath: String): File {
        require(relativePath.isNotBlank() && !File(relativePath).isAbsolute) {
            "modelRelativePath must be relative to app data"
        }
        val dataRoot = context.dataDir.canonicalFile
        val modelFile = File(dataRoot, relativePath).canonicalFile
        require(modelFile.path.startsWith(dataRoot.path + File.separator)) { "modelRelativePath escapes app data" }
        require(modelFile.isFile && modelFile.canRead()) { "Model file is missing or unreadable" }
        return modelFile
    }

    private fun profile(config: Config, modelFile: File): GgufModelProfile {
        val artifact = GgufArtifact(
            digest = ModelDigest(config.modelSha256),
            fileName = modelFile.name,
            sizeBytes = modelFile.length(),
            architecture = "qwen35",
            quantization = "Q4_K_M",
            source = ArtifactSource.Imported("LLUP-50 model-load evidence"),
        )
        return GgufModelProfile(
            id = "llup50-load-${config.tier.name.lowercase()}",
            artifact = artifact,
            contextSize = 2_048,
            batchSize = 128,
            microBatchSize = 64,
            cpuThreads = config.cpuThreads,
            batchThreads = config.cpuThreads,
            gpuLayers = 0,
            useMmap = true,
            useMlock = false,
            flashAttention = false,
            runtimeCapabilities = Qwen35RuntimeTuningProfiles.candidateForTier(config.tier).runtimeCapabilities(),
        )
    }

    private fun currentThermalStatus(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return -1
        return (context.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus
    }

    private fun emitEvidence(payload: JSONObject) {
        val status = Bundle().apply {
            putString(Instrumentation.REPORT_KEY_STREAMRESULT, "LOCAL_LLM_LLUP_LOAD_JSON $payload\n")
        }
        InstrumentationRegistry.getInstrumentation().sendStatus(0, status)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private data class Config(
        val modelRelativePath: String,
        val modelSha256: String,
        val tier: Qwen35ModelTier,
        val cpuThreads: Int,
        val repetitions: Int,
        val runtimeSourceCommit: String,
        val evidenceHarnessCommit: String,
        val backendRevision: String,
    ) {
        companion object {
            fun from(arguments: Bundle): Config {
                fun required(name: String): String = arguments.getString(name)?.takeIf(String::isNotBlank)
                    ?: error("Missing required instrumentation argument: $name")
                val tier = when (required("modelTier").lowercase()) {
                    "0.8b" -> Qwen35ModelTier.B0_8
                    "2b" -> Qwen35ModelTier.B2
                    else -> error("modelTier must be 0.8b or 2b")
                }
                val repetitions = required("loadRepetitions").toInt()
                require(repetitions >= 3) { "loadRepetitions must be at least 3" }
                val cpuThreads = required("cpuThreads").toInt()
                require(cpuThreads > 0) { "cpuThreads must be positive" }
                return Config(
                    modelRelativePath = required("modelRelativePath"),
                    modelSha256 = required("modelSha256").lowercase(),
                    tier = tier,
                    cpuThreads = cpuThreads,
                    repetitions = repetitions,
                    runtimeSourceCommit = required("runtimeSourceCommit"),
                    evidenceHarnessCommit = required("evidenceHarnessCommit"),
                    backendRevision = required("backendRevision"),
                )
            }
        }
    }
}
