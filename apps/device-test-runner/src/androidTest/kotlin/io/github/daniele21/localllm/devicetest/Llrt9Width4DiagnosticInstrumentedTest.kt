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
import io.github.daniele21.localllm.contracts.SeedPolicy
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.SessionOptions
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
import io.github.daniele21.localllm.runtime.RuntimeEvaluationBatchCaseResult
import io.github.daniele21.localllm.runtime.RuntimeEvaluationBatchOutcome
import io.github.daniele21.localllm.runtime.RuntimeEvaluationBatchRequest
import io.github.daniele21.localllm.runtime.RuntimeOrchestrator
import io.github.daniele21.localllm.store.FileSystemModelStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class Llrt9Width4DiagnosticInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val config by lazy { Llrt9Width4DiagnosticConfig.fromInstrumentation() }

    @Test
    fun recordsWidth4Diagnostic() {
        val harness = buildHarness()
        val runtime = harness.runtime
        try {
            val prepared = runtime.prepare(harness.applicationId, harness.useCaseId)
            assertTrue("LLRT-9 diagnostic model preparation failed", prepared.ready)
            assertEquals(config.digest, prepared.modelDigest)

            val before = deviceSnapshot()
            val serial = runSerial(runtime, harness)
            val afterSerial = deviceSnapshot()
            val batch = runNativeBatch(runtime, harness)
            val afterBatch = deviceSnapshot()

            assertEquals("LLRT-9 diagnostic prompt attribution drifted", serial.prompts, batch.prompts)
            assertEquals("LLRT-9 diagnostic output count drifted", serial.outputs.size, batch.outputs.size)
            assertTrue("Runtime leaked LLRT-9 diagnostic sessions", eventually { runtime.runtimeSnapshot().activeSessions == 0 })

            emitDiagnostic(serial, batch, before, afterSerial, afterBatch)

            assertTrue("Idle model was not unloaded after LLRT-9 diagnostic", runtime.unloadIdleModel())
            assertFalse(runtime.memoryResourceSnapshot().modelLoaded)
        } finally {
            runtime.close()
        }
    }

    private fun runSerial(runtime: RuntimeOrchestrator, harness: Llrt9DiagnosticHarness): Llrt9DiagnosticMeasuredMode {
        val started = System.nanoTime()
        val outputs = ArrayList<String>(config.width)
        val metrics = ArrayList<GenerationEvent.Completed>(config.width)
        config.prompts.forEachIndexed { index, prompt ->
            val session = runtime.createSession(
                harness.applicationId,
                harness.useCaseId,
                SessionOptions(kind = SessionKind.STATELESS),
            )
            try {
                val completed = generateAndAwait(runtime, harness, session, prompt, "serial-$index")
                outputs += completed.answerOutput
                metrics += completed
            } finally {
                runtime.closeSession(session)
            }
        }
        assertTrue("Serial diagnostic sessions were not released", eventually { runtime.runtimeSnapshot().activeSessions == 0 })
        return Llrt9DiagnosticMeasuredMode(config.prompts, outputs, metrics.map { it.metrics.outputTokens ?: 0 }, elapsedMs(started))
    }

    private fun runNativeBatch(runtime: RuntimeOrchestrator, harness: Llrt9DiagnosticHarness): Llrt9DiagnosticMeasuredMode {
        val sessions = config.prompts.indices.map {
            runtime.createSession(
                harness.applicationId,
                harness.useCaseId,
                SessionOptions(kind = SessionKind.STATELESS),
            )
        }
        val requestIds = config.prompts.indices.map {
            RequestId("llrt9-diagnostic-${config.diagnosticCase}-$it-${UUID.randomUUID()}")
        }
        val requests = config.prompts.indices.map { index ->
            GenerationRequest(
                requestId = requestIds[index],
                sessionId = sessions[index],
                applicationId = harness.applicationId,
                useCaseId = harness.useCaseId,
                input = config.prompts[index],
            )
        }
        val terminal = CountDownLatch(1)
        val outcome = AtomicReference<RuntimeEvaluationBatchOutcome>()
        val started = System.nanoTime()
        runtime.generateEvaluationBatch(
            RuntimeEvaluationBatchRequest(
                batchId = RequestId("llrt9-diagnostic-${config.diagnosticCase}-${UUID.randomUUID()}"),
                requests = requests,
            ),
        ) { result ->
            outcome.compareAndSet(null, result)
            terminal.countDown()
        }
        assertTrue(
            "Native diagnostic batch did not terminate within ${config.timeoutSeconds} seconds",
            terminal.await(config.timeoutSeconds, TimeUnit.SECONDS),
        )
        val elapsed = elapsedMs(started)
        val completed = outcome.get() as? RuntimeEvaluationBatchOutcome.Completed
            ?: throw AssertionError("Native diagnostic batch failed: ${outcome.get()}")
        assertEquals(requestIds, completed.cases.map(RuntimeEvaluationBatchCaseResult::requestId))
        val outputs = completed.cases.map { case ->
            when (case) {
                is RuntimeEvaluationBatchCaseResult.Completed -> case.output
                is RuntimeEvaluationBatchCaseResult.Cancelled -> throw AssertionError("Unexpected LLRT-9 diagnostic cancellation")
            }
        }
        val outputTokens = completed.cases.map { it.metrics.outputTokens ?: 0 }
        sessions.asReversed().forEach(runtime::closeSession)
        assertTrue("Batch diagnostic sessions were not released", eventually { runtime.runtimeSnapshot().activeSessions == 0 })
        return Llrt9DiagnosticMeasuredMode(config.prompts, outputs, outputTokens, elapsed)
    }

    private fun generateAndAwait(
        runtime: RuntimeOrchestrator,
        harness: Llrt9DiagnosticHarness,
        session: SessionId,
        prompt: String,
        requestSuffix: String,
    ): GenerationEvent.Completed {
        val terminal = CountDownLatch(1)
        val event = AtomicReference<GenerationEvent>()
        runtime.generate(
            GenerationRequest(
                requestId = RequestId("llrt9-diagnostic-$requestSuffix-${UUID.randomUUID()}"),
                sessionId = session,
                applicationId = harness.applicationId,
                useCaseId = harness.useCaseId,
                input = prompt,
            ),
            GenerationListener { generationEvent ->
                if (generationEvent is GenerationEvent.Completed || generationEvent is GenerationEvent.Failed) {
                    event.compareAndSet(null, generationEvent)
                    terminal.countDown()
                }
            },
        )
        assertTrue(
            "Serial diagnostic generation did not terminate within ${config.timeoutSeconds} seconds",
            terminal.await(config.timeoutSeconds, TimeUnit.SECONDS),
        )
        return when (val terminalEvent = event.get()) {
            is GenerationEvent.Completed -> terminalEvent
            is GenerationEvent.Failed -> throw AssertionError("Serial diagnostic generation failed: ${terminalEvent.error.code}")
            else -> throw AssertionError("Serial diagnostic generation ended without a terminal event")
        }
    }

    private fun buildHarness(): Llrt9DiagnosticHarness {
        val sourceModel = config.resolveModelFile(context)
        val applicationId = ApplicationId("llrt9-width4-diagnostic")
        val useCaseId = UseCaseId("serial-vs-native-batch-diagnostic")
        val runtimeProfile = Qwen35RuntimeTuningProfiles.candidateForTier(config.tier)
        val generationProfile = Qwen35GenerationProfiles.forTier(config.tier)
            .single { it.id == Qwen35GenerationProfileId.QWEN35_TEXT_QUALITY }
        val generationDefaults = generationProfile.defaults.copy(
            maxOutputTokens = config.maxOutputTokens,
            temperature = if (config.samplingMode == Llrt9DiagnosticSamplingMode.GREEDY) 0f else generationProfile.defaults.temperature,
            seed = config.seed,
            seedPolicy = SeedPolicy.Fixed(config.seed),
        )
        val artifact = GgufArtifact(
            digest = config.digest,
            fileName = sourceModel.name,
            sizeBytes = sourceModel.length(),
            architecture = "qwen35",
            quantization = "Q4_K_M",
            source = ArtifactSource.Imported("LLRT-9 width=4 diagnostic fixture"),
        )
        val modelProfile = GgufModelProfile(
            id = "llrt9-width4-diagnostic-${config.tier.name.lowercase()}",
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
            id = "llrt9-width4-diagnostic-use-case",
            modelProfileId = modelProfile.id,
            systemPromptVersion = "llrt9-width4-diagnostic-v1",
            generationDefaults = generationDefaults,
            outputMode = OutputMode.TEXT,
            cachePolicy = UseCaseCachePolicy(0, false, false, false),
            healthSuiteId = "llrt9-width4-diagnostic-health",
        )
        val resolved = ResolvedUseCase(
            binding = AppModelBinding(applicationId, useCaseId, useCase.id),
            useCase = useCase,
            model = modelProfile,
        )
        val modelStore = FileSystemModelStore(File(context.noBackupFilesDir, "llrt9-width4-diagnostic-store"))
        modelStore.import(sourceModel, artifact)
        val nativeLibraryDir = File(context.applicationInfo.nativeLibraryDir)
        require(nativeLibraryDir.isDirectory) { "Native library directory is unavailable" }
        return Llrt9DiagnosticHarness(
            runtime = RuntimeOrchestrator(
                registry = Llrt9DiagnosticSingleBindingRegistry(resolved),
                modelStore = modelStore,
                backend = LlamaCppInferenceBackend(nativeLibraryDir),
            ),
            applicationId = applicationId,
            useCaseId = useCaseId,
        )
    }

    private fun emitDiagnostic(
        serial: Llrt9DiagnosticMeasuredMode,
        batch: Llrt9DiagnosticMeasuredMode,
        before: Llrt9DiagnosticDeviceSnapshot,
        afterSerial: Llrt9DiagnosticDeviceSnapshot,
        afterBatch: Llrt9DiagnosticDeviceSnapshot,
    ) {
        val serialDigests = serial.outputs.map(::sha256)
        val batchDigests = batch.outputs.map(::sha256)
        val matchingSlots = serialDigests.indices.map { serialDigests[it] == batchDigests[it] }
        val diagnostic = JSONObject()
            .put("schemaVersion", 1)
            .put("evidenceType", "LLRT9_WIDTH4_DIAGNOSTIC")
            .put("diagnosticCase", config.diagnosticCase)
            .put("samplingMode", config.samplingMode.wireValue)
            .put("modelDigest", config.digest.sha256)
            .put("modelTier", config.tier.name)
            .put("architecture", "qwen35")
            .put("quantization", "Q4_K_M")
            .put("backendRevision", Qwen35RuntimeTuningProfiles.LLAMA_CPP_REVISION)
            .put("harnessCommit", config.harnessCommit)
            .put("deviceModel", Build.MODEL)
            .put("androidRelease", Build.VERSION.RELEASE)
            .put("sdkInt", Build.VERSION.SDK_INT)
            .put("abi", Build.SUPPORTED_ABIS.firstOrNull().orEmpty())
            .put("contextTokensPerSequence", config.contextSize)
            .put("aggregateContextTokens", config.contextSize * config.width)
            .put("batchWidth", config.width)
            .put("measurementOrder", "SERIAL_FIRST")
            .put("batchSize", config.batchSize)
            .put("microBatchSize", config.microBatchSize)
            .put("cpuThreads", config.cpuThreads)
            .put("batchThreads", config.batchThreads)
            .put("maxOutputTokens", config.maxOutputTokens)
            .put("seedPolicy", "FIXED")
            .put("generationSeed", config.seed)
            .put("thinkingMode", "DISABLED")
            .put("promptSourceIndices", JSONArray(config.promptOrder))
            .put("promptDigests", JSONArray(config.prompts.map(::sha256)))
            .put("serialOutputDigests", JSONArray(serialDigests))
            .put("batchOutputDigests", JSONArray(batchDigests))
            .put("serialOutputTokensPerCase", JSONArray(serial.outputTokens))
            .put("batchOutputTokensPerCase", JSONArray(batch.outputTokens))
            .put("matchingSlots", JSONArray(matchingSlots))
            .put("outputsMatch", serialDigests == batchDigests)
            .put("outputTokensMatch", serial.outputTokens == batch.outputTokens)
            .put("serialElapsedMs", serial.elapsedMs)
            .put("batchElapsedMs", batch.elapsedMs)
            .put("speedup", serial.elapsedMs.toDouble() / batch.elapsedMs.coerceAtLeast(1L))
            .put("processPssKbBefore", before.processPssKb)
            .put("processPssKbAfterSerial", afterSerial.processPssKb)
            .put("processPssKbAfterBatch", afterBatch.processPssKb)
            .put("availableMemoryBytesBefore", before.availableMemoryBytes)
            .put("availableMemoryBytesAfterSerial", afterSerial.availableMemoryBytes)
            .put("availableMemoryBytesAfterBatch", afterBatch.availableMemoryBytes)
            .put("thermalStatusBefore", before.thermalStatus)
            .put("thermalStatusAfterSerial", afterSerial.thermalStatus)
            .put("thermalStatusAfterBatch", afterBatch.thermalStatus)
        emitStatus("LOCAL_LLM_LLRT9_DIAGNOSTIC_JSON $diagnostic")
    }

    private fun deviceSnapshot(): Llrt9DiagnosticDeviceSnapshot {
        val processMemory = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
        val memoryInfo = ActivityManager.MemoryInfo().also {
            context.getSystemService(ActivityManager::class.java).getMemoryInfo(it)
        }
        val powerManager = context.getSystemService(PowerManager::class.java)
        return Llrt9DiagnosticDeviceSnapshot(processMemory.totalPss, memoryInfo.availMem, powerManager.currentThermalStatus)
    }

    private fun eventually(condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(config.timeoutSeconds)
        while (System.nanoTime() < deadline) {
            if (condition()) return true
            Thread.sleep(25)
        }
        return condition()
    }

    private fun emitStatus(line: String) {
        val status = Bundle().apply { putString(Instrumentation.REPORT_KEY_STREAMRESULT, "$line\n") }
        InstrumentationRegistry.getInstrumentation().sendStatus(0, status)
    }

    private fun elapsedMs(started: Long): Long = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

private data class Llrt9DiagnosticHarness(
    val runtime: RuntimeOrchestrator,
    val applicationId: ApplicationId,
    val useCaseId: UseCaseId,
)

private data class Llrt9DiagnosticMeasuredMode(
    val prompts: List<String>,
    val outputs: List<String>,
    val outputTokens: List<Int>,
    val elapsedMs: Long,
)

private data class Llrt9DiagnosticDeviceSnapshot(
    val processPssKb: Int,
    val availableMemoryBytes: Long,
    val thermalStatus: Int,
)

private class Llrt9DiagnosticSingleBindingRegistry(private val resolved: ResolvedUseCase) : ModelProfileRegistry {
    override fun resolve(applicationId: ApplicationId, useCaseId: UseCaseId): ResolvedUseCase =
        resolved.takeIf { it.binding.applicationId == applicationId && it.binding.useCaseId == useCaseId }
            ?: error("No LLRT-9 diagnostic binding for application=${applicationId.value}, useCase=${useCaseId.value}")
}

private enum class Llrt9DiagnosticSamplingMode(val wireValue: String) {
    QUALITY("quality"),
    GREEDY("greedy"),
}

private data class Llrt9Width4DiagnosticConfig(
    val modelRelativePath: String,
    val digest: ModelDigest,
    val tier: Qwen35ModelTier,
    val contextSize: Int,
    val batchSize: Int,
    val microBatchSize: Int,
    val cpuThreads: Int,
    val batchThreads: Int,
    val maxOutputTokens: Int,
    val seed: Long,
    val timeoutSeconds: Long,
    val harnessCommit: String,
    val diagnosticCase: String,
    val promptOrder: List<Int>,
    val samplingMode: Llrt9DiagnosticSamplingMode,
) {
    val width: Int = 4
    val prompts: List<String> = promptOrder.map(SYNTHETIC_PROMPTS::get)

    init {
        require(contextSize > 0 && contextSize % 256 == 0) { "LLRT-9 diagnostic context must be a positive multiple of 256" }
        require(batchSize >= width) { "LLRT-9 diagnostic batch size must cover width=4" }
        require(microBatchSize in 1..batchSize) { "LLRT-9 diagnostic micro-batch must be within batch size" }
        require(cpuThreads > 0 && batchThreads > 0) { "LLRT-9 diagnostic thread counts must be positive" }
        require(maxOutputTokens > 0) { "LLRT-9 diagnostic output budget must be positive" }
        require(seed >= 0) { "LLRT-9 diagnostic seed must be non-negative" }
        require(timeoutSeconds > 0) { "LLRT-9 diagnostic timeout must be positive" }
        require(harnessCommit.matches(Regex("[0-9a-f]{40}"))) { "LLRT-9 diagnostic harness commit must be an exact SHA" }
        require(diagnosticCase.matches(Regex("[a-z0-9-]+"))) { "LLRT-9 diagnostic case must be a stable slug" }
        require(promptOrder.size == width && promptOrder.toSet() == (0 until width).toSet()) {
            "LLRT-9 diagnostic prompt order must be a permutation of 0,1,2,3"
        }
    }

    fun resolveModelFile(context: Context): File = File(context.filesDir, modelRelativePath).canonicalFile.also {
        require(it.isFile && it.canRead()) { "LLRT-9 diagnostic model file is unavailable: $it" }
    }

    companion object {
        private val SYNTHETIC_PROMPTS = listOf(
            "Reply with one short sentence: What is 2 + 2?",
            "Reply with one short sentence: Name the largest planet in the Solar System.",
            "Reply with one short sentence: What color do blue and yellow make?",
            "Reply with one short sentence: What is the capital of France?",
        )

        fun fromInstrumentation(): Llrt9Width4DiagnosticConfig {
            val args = InstrumentationRegistry.getArguments()
            val tier = when (required(args, "modelTier")) {
                "0.8b" -> Qwen35ModelTier.B0_8
                "2b" -> Qwen35ModelTier.B2
                else -> error("modelTier must be 0.8b or 2b")
            }
            val samplingMode = when (required(args, "samplingMode")) {
                "quality" -> Llrt9DiagnosticSamplingMode.QUALITY
                "greedy" -> Llrt9DiagnosticSamplingMode.GREEDY
                else -> error("samplingMode must be quality or greedy")
            }
            val promptOrder = required(args, "promptOrder").split(',').map(String::toInt)
            return Llrt9Width4DiagnosticConfig(
                modelRelativePath = required(args, "modelRelativePath"),
                digest = ModelDigest(required(args, "modelSha256")),
                tier = tier,
                contextSize = required(args, "contextSize").toInt(),
                batchSize = required(args, "batchSize").toInt(),
                microBatchSize = required(args, "microBatchSize").toInt(),
                cpuThreads = required(args, "cpuThreads").toInt(),
                batchThreads = required(args, "batchThreads").toInt(),
                maxOutputTokens = required(args, "maxOutputTokens").toInt(),
                seed = required(args, "generationSeed").toLong(),
                timeoutSeconds = required(args, "timeoutSeconds").toLong(),
                harnessCommit = required(args, "harnessCommit"),
                diagnosticCase = required(args, "diagnosticCase"),
                promptOrder = promptOrder,
                samplingMode = samplingMode,
            )
        }

        private fun required(args: Bundle, name: String): String =
            requireNotNull(args.getString(name)).takeIf(String::isNotBlank) ?: error("Missing instrumentation argument: $name")
    }
}
