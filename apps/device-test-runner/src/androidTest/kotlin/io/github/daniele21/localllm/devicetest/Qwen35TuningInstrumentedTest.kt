package io.github.daniele21.localllm.devicetest

import android.app.ActivityManager
import android.app.Instrumentation
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Debug
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.GenerationEvent
import io.github.daniele21.localllm.contracts.GenerationListener
import io.github.daniele21.localllm.contracts.GenerationRequest
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.ThinkingMode
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.models.AppModelBinding
import io.github.daniele21.localllm.models.ArtifactSource
import io.github.daniele21.localllm.models.GgufArtifact
import io.github.daniele21.localllm.models.GgufModelProfile
import io.github.daniele21.localllm.models.ModelProfileRegistry
import io.github.daniele21.localllm.models.OutputMode
import io.github.daniele21.localllm.models.Qwen35GenerationProfileId
import io.github.daniele21.localllm.models.Qwen35GenerationProfiles
import io.github.daniele21.localllm.models.Qwen35ModelTier
import io.github.daniele21.localllm.models.Qwen35RuntimeTuningProfiles
import io.github.daniele21.localllm.models.ResolvedUseCase
import io.github.daniele21.localllm.models.UseCaseCachePolicy
import io.github.daniele21.localllm.models.UseCaseProfile
import io.github.daniele21.localllm.runtime.LlamaCppInferenceBackend
import io.github.daniele21.localllm.runtime.RuntimeOrchestrator
import io.github.daniele21.localllm.store.FileSystemModelStore
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class Qwen35TuningInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val config = Qwen35TuningConfig.fromInstrumentation()

    @Test
    fun recordsColdAndWarmEvidence() {
        val harness = buildHarness()
        val runtime = harness.runtime
        try {
            val cold = runMeasuredGeneration(runtime, harness, sampleIndex = 0)
            assertEquals("Expected a cold first tuning run", "COLD", cold.completed.metrics.modelLoadKind.name)
            emitEvidence(cold)

            repeat(config.warmRepetitions) { index ->
                val warm = runMeasuredGeneration(runtime, harness, sampleIndex = index + 1)
                assertEquals("Expected a warm tuning run", "WARM", warm.completed.metrics.modelLoadKind.name)
                emitEvidence(warm)
            }

            assertTrue("Idle model was not unloaded", runtime.unloadIdleModel())
            assertFalse(runtime.memoryResourceSnapshot().modelLoaded)
        } finally {
            runtime.close()
        }
    }

    private fun runMeasuredGeneration(runtime: RuntimeOrchestrator, harness: Qwen35TuningHarness, sampleIndex: Int): MeasuredGeneration {
        val before = deviceSnapshot()
        val session = runtime.createSession(harness.applicationId, harness.useCaseId)
        val completed = generateAndAwait(runtime, harness, session)
        closeSession(runtime, session)
        return MeasuredGeneration(
            completed = completed,
            sampleIndex = sampleIndex,
            before = before,
            after = deviceSnapshot(),
        )
    }

    private fun buildHarness(): Qwen35TuningHarness {
        val sourceModel = config.resolveModelFile(context)
        val applicationId = ApplicationId("qwen35-tuning")
        val useCaseId = UseCaseId("generation-${config.caseId}")
        val modelProfileId = "qwen35-tuning-model-${config.caseId}"
        val useCaseProfileId = "qwen35-tuning-use-case-${config.caseId}"
        val runtimeProfile = Qwen35RuntimeTuningProfiles.candidateForTier(config.tier)
        val generationProfileId = if (config.thinkingMode == ThinkingMode.ENABLED) {
            Qwen35GenerationProfileId.QWEN35_THINKING
        } else {
            Qwen35GenerationProfileId.QWEN35_TEXT_QUALITY
        }
        val generationProfile = Qwen35GenerationProfiles.forTier(config.tier)
            .single { it.id == generationProfileId }
        val artifact = GgufArtifact(
            digest = config.digest,
            fileName = sourceModel.name,
            sizeBytes = sourceModel.length(),
            architecture = "qwen35",
            quantization = "Q4_K_M",
            source = ArtifactSource.Imported("qwen35 tuning fixture"),
        )
        val modelProfile = GgufModelProfile(
            id = modelProfileId,
            artifact = artifact,
            contextSize = config.contextSize,
            batchSize = config.batchSize,
            microBatchSize = config.microBatchSize,
            cpuThreads = config.cpuThreads,
            batchThreads = config.batchThreads,
            gpuLayers = 0,
            useMmap = true,
            useMlock = false,
            flashAttention = false,
            runtimeCapabilities = runtimeProfile.runtimeCapabilities(),
        )
        val useCase = UseCaseProfile(
            id = useCaseProfileId,
            modelProfileId = modelProfileId,
            systemPromptVersion = "qwen35-tuning-v1",
            generationDefaults = generationProfile.defaults.copy(maxOutputTokens = config.maxOutputTokens),
            outputMode = OutputMode.TEXT,
            cachePolicy = UseCaseCachePolicy(0, false, false, false),
            healthSuiteId = "qwen35-tuning-health",
        )
        val resolved = ResolvedUseCase(
            binding = AppModelBinding(applicationId, useCaseId, useCase.id),
            useCase = useCase,
            model = modelProfile,
        )
        val modelStore = FileSystemModelStore(File(context.noBackupFilesDir, "qwen35-tuning-store"))
        modelStore.import(sourceModel, artifact)
        val nativeLibraryDir = File(context.applicationInfo.nativeLibraryDir)
        require(nativeLibraryDir.isDirectory) { "Native library directory is unavailable" }
        return Qwen35TuningHarness(
            runtime = RuntimeOrchestrator(
                registry = Qwen35SingleBindingRegistry(resolved),
                modelStore = modelStore,
                backend = LlamaCppInferenceBackend(nativeLibraryDir),
            ),
            applicationId = applicationId,
            useCaseId = useCaseId,
            runtimeProfileId = runtimeProfile.id,
            generationProfileId = generationProfile.id.name,
        )
    }

    private fun generateAndAwait(
        runtime: RuntimeOrchestrator,
        harness: Qwen35TuningHarness,
        session: SessionId,
    ): GenerationEvent.Completed {
        val terminal = CountDownLatch(1)
        val terminalEvent = AtomicReference<GenerationEvent>()
        runtime.generate(
            GenerationRequest(
                requestId = RequestId(UUID.randomUUID().toString()),
                sessionId = session,
                applicationId = harness.applicationId,
                useCaseId = harness.useCaseId,
                input = config.prompt,
            ),
            GenerationListener { event ->
                if (event is GenerationEvent.Completed || event is GenerationEvent.Failed) {
                    terminalEvent.compareAndSet(null, event)
                    terminal.countDown()
                }
            },
        )
        assertTrue(
            "Generation did not terminate within ${config.timeoutSeconds} seconds",
            terminal.await(config.timeoutSeconds, TimeUnit.SECONDS),
        )
        return when (val result = terminalEvent.get()) {
            is GenerationEvent.Completed -> result
            is GenerationEvent.Failed -> throw AssertionError("Qwen3.5 tuning generation failed: ${result.error.code}")
            else -> throw AssertionError("Qwen3.5 tuning generation ended without a terminal event")
        }
    }

    private fun closeSession(runtime: RuntimeOrchestrator, session: SessionId) {
        runtime.closeSession(session)
        assertTrue(
            "Session context was not released",
            eventually { runtime.runtimeSnapshot().activeSessions == 0 },
        )
    }

    private fun eventually(condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(config.timeoutSeconds)
        while (System.nanoTime() < deadline) {
            if (condition()) return true
            Thread.sleep(25)
        }
        return condition()
    }

    private fun emitEvidence(measured: MeasuredGeneration) {
        val line = "LOCAL_LLM_TUNING_JSON ${evidence(measured)}"
        val status = Bundle().apply {
            putString(Instrumentation.REPORT_KEY_STREAMRESULT, "$line\n")
        }
        InstrumentationRegistry.getInstrumentation().sendStatus(0, status)
    }

    private fun evidence(measured: MeasuredGeneration): JSONObject {
        val completed = measured.completed
        val inputTokens = completed.metrics.inputTokens
        val prefillMs = completed.metrics.prefillMs
        val prefillTokensPerSecond = if (inputTokens != null && prefillMs != null && prefillMs > 0) {
            inputTokens * 1_000.0 / prefillMs
        } else {
            null
        }
        return JSONObject()
            .put("schemaVersion", EVIDENCE_SCHEMA_VERSION)
            .put("tuningCaseId", config.caseId)
            .put("sampleIndex", measured.sampleIndex)
            .put("warmRepetitionsRequested", config.warmRepetitions)
            .put("modelDigest", config.digest.sha256)
            .put("modelTier", config.tier.name)
            .put("architecture", "qwen35")
            .put("quantization", "Q4_K_M")
            .put("backendRevision", Qwen35RuntimeTuningProfiles.LLAMA_CPP_REVISION)
            .put("harnessCommit", config.harnessCommit)
            .put("runtimeProfileId", config.runtimeProfileId)
            .put("runtimeProfileVersion", Qwen35RuntimeTuningProfiles.VERSION)
            .put("generationProfileId", config.generationProfileId)
            .put("generationProfileVersion", Qwen35GenerationProfiles.VERSION)
            .put("contextTokens", config.contextSize)
            .put("cpuThreads", config.cpuThreads)
            .put("batchThreads", config.batchThreads)
            .put("batchSize", config.batchSize)
            .put("microBatchSize", config.microBatchSize)
            .put("thinkingMode", config.thinkingMode.name)
            .put("modelLoadKind", completed.metrics.modelLoadKind.name)
            .put("deviceModel", Build.MODEL)
            .put("androidRelease", Build.VERSION.RELEASE)
            .put("sdkInt", Build.VERSION.SDK_INT)
            .put("abi", Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown")
            .put("ttftMs", completed.metrics.timeToFirstTokenMs)
            .put("prefillMs", completed.metrics.prefillMs)
            .put("decodeMs", completed.metrics.decodeMs)
            .put("totalMs", completed.metrics.totalMs)
            .put("inputTokens", completed.metrics.inputTokens)
            .put("outputTokens", completed.metrics.outputTokens)
            .put("prefillTokensPerSecond", prefillTokensPerSecond)
            .put("decodeTokensPerSecond", completed.metrics.decodeTokensPerSecond)
            .put("stopReason", completed.metrics.stopReason.name)
            .put("processPssBeforeKb", measured.before.processPssKb)
            .put("processPssAfterKb", measured.after.processPssKb)
            .put("processPssKb", maxOf(measured.before.processPssKb, measured.after.processPssKb))
            .put("availableMemoryBeforeBytes", measured.before.availableMemoryBytes)
            .put("availableMemoryAfterBytes", measured.after.availableMemoryBytes)
            .put("availableMemoryBytes", minOf(measured.before.availableMemoryBytes, measured.after.availableMemoryBytes))
            .put("thermalStatusBefore", measured.before.thermalStatus)
            .put("thermalStatusAfter", measured.after.thermalStatus)
            .put("thermalStatus", maxOf(measured.before.thermalStatus, measured.after.thermalStatus))
    }

    private fun deviceSnapshot(): DeviceSnapshot {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val debugMemory = Debug.MemoryInfo().also(Debug::getMemoryInfo)
        return DeviceSnapshot(
            processPssKb = debugMemory.totalPss,
            availableMemoryBytes = memoryInfo.availMem,
            thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) powerManager.currentThermalStatus else -1,
        )
    }

    private companion object {
        const val EVIDENCE_SCHEMA_VERSION = 2
    }
}

private data class MeasuredGeneration(
    val completed: GenerationEvent.Completed,
    val sampleIndex: Int,
    val before: DeviceSnapshot,
    val after: DeviceSnapshot,
)

private data class DeviceSnapshot(val processPssKb: Int, val availableMemoryBytes: Long, val thermalStatus: Int)

private data class Qwen35TuningHarness(
    val runtime: RuntimeOrchestrator,
    val applicationId: ApplicationId,
    val useCaseId: UseCaseId,
    val runtimeProfileId: String,
    val generationProfileId: String,
)

private class Qwen35SingleBindingRegistry(private val resolved: ResolvedUseCase) : ModelProfileRegistry {
    override fun resolve(applicationId: ApplicationId, useCaseId: UseCaseId): ResolvedUseCase {
        require(applicationId == resolved.binding.applicationId) { "Unknown application" }
        require(useCaseId == resolved.binding.useCaseId) { "Unknown use case" }
        return resolved
    }
}

private data class Qwen35TuningConfig(
    val relativePath: String,
    val digest: ModelDigest,
    val tier: Qwen35ModelTier,
    val contextSize: Int,
    val batchSize: Int,
    val microBatchSize: Int,
    val cpuThreads: Int,
    val batchThreads: Int,
    val maxOutputTokens: Int,
    val warmRepetitions: Int,
    val thinkingMode: ThinkingMode,
    val caseId: String,
    val harnessCommit: String,
    val prompt: String,
    val timeoutSeconds: Long,
) {
    val runtimeProfileId: String
        get() = Qwen35RuntimeTuningProfiles.candidateForTier(tier).id

    val generationProfileId: String
        get() = if (thinkingMode == ThinkingMode.ENABLED) {
            Qwen35GenerationProfileId.QWEN35_THINKING.name
        } else {
            Qwen35GenerationProfileId.QWEN35_TEXT_QUALITY.name
        }

    fun resolveModelFile(context: Context): File {
        require(relativePath.isNotBlank() && !File(relativePath).isAbsolute) {
            "modelRelativePath must be relative to app data"
        }
        val dataRoot = context.dataDir.canonicalFile
        val modelFile = File(dataRoot, relativePath).canonicalFile
        require(modelFile.path.startsWith(dataRoot.path + File.separator)) { "modelRelativePath escapes app data" }
        require(modelFile.isFile && modelFile.canRead()) { "Model file is missing or unreadable" }
        return modelFile
    }

    companion object {
        fun fromInstrumentation(): Qwen35TuningConfig {
            val arguments = InstrumentationRegistry.getArguments()
            val processors = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            fun string(name: String, default: String): String = arguments.getString(name)?.takeIf(String::isNotBlank) ?: default
            fun required(name: String): String = arguments.getString(name)?.takeIf(String::isNotBlank)
                ?: error("Missing required instrumentation argument: $name")
            fun positiveInt(name: String, default: Int): Int = string(name, default.toString()).toInt().also {
                require(it > 0) { "$name must be positive" }
            }
            val tier = when (required("modelTier").lowercase()) {
                "0.8b" -> Qwen35ModelTier.B0_8
                "2b" -> Qwen35ModelTier.B2
                else -> error("modelTier must be 0.8b or 2b")
            }
            val contextSize = positiveInt("contextSize", 2_048)
            require(contextSize in Qwen35RuntimeTuningProfiles.APPROVED_CONTEXT_TIERS) {
                "contextSize must be an approved Qwen3.5 tier"
            }
            val warmRepetitions = positiveInt("warmRepetitions", 3)
            require(warmRepetitions >= 3) { "warmRepetitions must be at least 3 for tuning evidence" }
            return Qwen35TuningConfig(
                relativePath = string("modelRelativePath", "files/e2e/model.gguf"),
                digest = ModelDigest(required("modelSha256").lowercase()),
                tier = tier,
                contextSize = contextSize,
                batchSize = positiveInt("batchSize", 128),
                microBatchSize = positiveInt("microBatchSize", 64),
                cpuThreads = positiveInt("cpuThreads", processors.coerceAtMost(4)),
                batchThreads = positiveInt("batchThreads", processors.coerceAtMost(4)),
                maxOutputTokens = positiveInt("maxOutputTokens", 64),
                warmRepetitions = warmRepetitions,
                thinkingMode = when (string("thinkingMode", "DISABLED").uppercase()) {
                    "ENABLED" -> ThinkingMode.ENABLED
                    "DISABLED" -> ThinkingMode.DISABLED
                    else -> error("thinkingMode must be ENABLED or DISABLED")
                },
                caseId = required("tuningCaseId"),
                harnessCommit = required("harnessCommit"),
                prompt = string("prompt", "How much is the Earth radius?"),
                timeoutSeconds = string("timeoutSeconds", "600").toLong().also {
                    require(it > 0) { "timeoutSeconds must be positive" }
                },
            )
        }
    }
}
