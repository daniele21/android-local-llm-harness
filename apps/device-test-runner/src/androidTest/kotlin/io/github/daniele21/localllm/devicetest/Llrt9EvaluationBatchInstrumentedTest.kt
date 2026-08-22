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
class Llrt9EvaluationBatchInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val config by lazy { Llrt9EvidenceConfig.fromInstrumentation() }

    @Test
    fun recordsSerialVsNativeBatchEvidence() {
        val harness = buildHarness()
        val runtime = harness.runtime
        try {
            val prepared = runtime.prepare(harness.applicationId, harness.useCaseId)
            assertTrue("LLRT-9 model preparation failed", prepared.ready)
            assertEquals(config.digest, prepared.modelDigest)

            val before = deviceSnapshot()
            val serialFirst = config.sampleIndex % 2 == 0
            val first =
                if (serialFirst) runSerial(runtime, harness) else runNativeBatch(runtime, harness)
            val afterFirst = deviceSnapshot()
            val second =
                if (serialFirst) runNativeBatch(runtime, harness) else runSerial(runtime, harness)
            val afterSecond = deviceSnapshot()
            val serial = if (serialFirst) first else second
            val batch = if (serialFirst) second else first
            val afterSerial = if (serialFirst) afterFirst else afterSecond
            val afterBatch = if (serialFirst) afterSecond else afterFirst
            val measurementOrder = if (serialFirst) "SERIAL_FIRST" else "BATCH_FIRST"

            assertEquals("Native batch attribution drifted", serial.prompts, batch.prompts)
            assertEquals("Native batch output count drifted", serial.outputs.size, batch.outputs.size)
            assertEquals(
                "Native batch output-token attribution drifted",
                serial.outputTokens,
                batch.outputTokens,
            )
            val serialDigests = serial.outputs.map(::sha256)
            val batchDigests = batch.outputs.map(::sha256)
            assertEquals(
                "LLRT-9 deterministic output mismatch between serial and native batch",
                serialDigests,
                batchDigests,
            )
            assertTrue("Runtime leaked evaluation sessions", eventually { runtime.runtimeSnapshot().activeSessions == 0 })

            emitEvidence(
                serial,
                batch,
                serialDigests,
                before,
                afterSerial,
                afterBatch,
                measurementOrder,
            )
            assertTrue("Idle model was not unloaded", runtime.unloadIdleModel())
            assertFalse(runtime.memoryResourceSnapshot().modelLoaded)
        } finally {
            runtime.close()
        }
    }

    private fun runSerial(runtime: RuntimeOrchestrator, harness: Llrt9Harness): MeasuredMode {
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
        assertTrue("Serial sessions were not released", eventually { runtime.runtimeSnapshot().activeSessions == 0 })
        return MeasuredMode(config.prompts, outputs, metrics.map { it.metrics.outputTokens ?: 0 }, elapsedMs(started))
    }

    private fun runNativeBatch(runtime: RuntimeOrchestrator, harness: Llrt9Harness): MeasuredMode {
        val sessions = config.prompts.indices.map {
            runtime.createSession(
                harness.applicationId,
                harness.useCaseId,
                SessionOptions(kind = SessionKind.STATELESS),
            )
        }
        val requestIds = config.prompts.indices.map { RequestId("llrt9-batch-${config.sampleIndex}-$it-${UUID.randomUUID()}") }
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
                batchId = RequestId("llrt9-batch-${config.sampleIndex}-${UUID.randomUUID()}"),
                requests = requests,
            ),
        ) { result ->
            outcome.compareAndSet(null, result)
            terminal.countDown()
        }
        assertTrue(
            "Native evaluation batch did not terminate within ${config.timeoutSeconds} seconds",
            terminal.await(config.timeoutSeconds, TimeUnit.SECONDS),
        )
        val elapsed = elapsedMs(started)
        val completed = outcome.get() as? RuntimeEvaluationBatchOutcome.Completed
            ?: throw AssertionError("Native evaluation batch failed: ${outcome.get()}")
        assertEquals(requestIds, completed.cases.map(RuntimeEvaluationBatchCaseResult::requestId))
        val outputs = completed.cases.map { case ->
            when (case) {
                is RuntimeEvaluationBatchCaseResult.Completed -> case.output
                is RuntimeEvaluationBatchCaseResult.Cancelled -> throw AssertionError("Unexpected LLRT-9 case cancellation")
            }
        }
        val outputTokens = completed.cases.map { it.metrics.outputTokens ?: 0 }
        sessions.asReversed().forEach(runtime::closeSession)
        assertTrue("Batch sessions were not released", eventually { runtime.runtimeSnapshot().activeSessions == 0 })
        return MeasuredMode(config.prompts, outputs, outputTokens, elapsed)
    }

    private fun generateAndAwait(
        runtime: RuntimeOrchestrator,
        harness: Llrt9Harness,
        session: SessionId,
        prompt: String,
        requestSuffix: String,
    ): GenerationEvent.Completed {
        val terminal = CountDownLatch(1)
        val event = AtomicReference<GenerationEvent>()
        runtime.generate(
            GenerationRequest(
                requestId = RequestId("llrt9-$requestSuffix-${UUID.randomUUID()}"),
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
            "Serial generation did not terminate within ${config.timeoutSeconds} seconds",
            terminal.await(config.timeoutSeconds, TimeUnit.SECONDS),
        )
        return when (val terminalEvent = event.get()) {
            is GenerationEvent.Completed -> terminalEvent
            is GenerationEvent.Failed -> throw AssertionError("Serial generation failed: ${terminalEvent.error.code}")
            else -> throw AssertionError("Serial generation ended without a terminal event")
        }
    }

    private fun buildHarness(): Llrt9Harness {
        val sourceModel = config.resolveModelFile(context)
        val applicationId = ApplicationId("llrt9-evidence")
        val useCaseId = UseCaseId("serial-vs-native-batch")
        val runtimeProfile = Qwen35RuntimeTuningProfiles.candidateForTier(config.tier)
        val generationProfile = Qwen35GenerationProfiles.forTier(config.tier)
            .single { it.id == Qwen35GenerationProfileId.QWEN35_TEXT_QUALITY }
        val generationDefaults = generationProfile.defaults.copy(
            maxOutputTokens = config.maxOutputTokens,
            seed = config.seed,
            seedPolicy = SeedPolicy.Fixed(config.seed),
        )
        val artifact = GgufArtifact(
            digest = config.digest,
            fileName = sourceModel.name,
            sizeBytes = sourceModel.length(),
            architecture = "qwen35",
            quantization = "Q4_K_M",
            source = ArtifactSource.Imported("LLRT-9 physical evidence fixture"),
        )
        val modelProfile = GgufModelProfile(
            id = "llrt9-${config.tier.name.lowercase()}",
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
            id = "llrt9-use-case",
            modelProfileId = modelProfile.id,
            systemPromptVersion = "llrt9-v1",
            generationDefaults = generationDefaults,
            outputMode = OutputMode.TEXT,
            cachePolicy = UseCaseCachePolicy(0, false, false, false),
            healthSuiteId = "llrt9-health",
        )
        val resolved = ResolvedUseCase(
            binding = AppModelBinding(applicationId, useCaseId, useCase.id),
            useCase = useCase,
            model = modelProfile,
        )
        val modelStore = FileSystemModelStore(File(context.noBackupFilesDir, "llrt9-evidence-store"))
        modelStore.import(sourceModel, artifact)
        val nativeLibraryDir = File(context.applicationInfo.nativeLibraryDir)
        require(nativeLibraryDir.isDirectory) { "Native library directory is unavailable" }
        return Llrt9Harness(
            runtime = RuntimeOrchestrator(
                registry = Llrt9SingleBindingRegistry(resolved),
                modelStore = modelStore,
                backend = LlamaCppInferenceBackend(nativeLibraryDir),
            ),
            applicationId = applicationId,
            useCaseId = useCaseId,
        )
    }

    private fun emitEvidence(
        serial: MeasuredMode,
        batch: MeasuredMode,
        serialDigests: List<String>,
        before: Llrt9DeviceSnapshot,
        afterSerial: Llrt9DeviceSnapshot,
        afterBatch: Llrt9DeviceSnapshot,
        measurementOrder: String,
    ) {
        val evidence = JSONObject()
            .put("schemaVersion", 7)
            .put("evidenceType", "LLRT9_SERIAL_VS_NATIVE_BATCH")
            .put("sampleIndex", config.sampleIndex)
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
            .put("measurementOrder", measurementOrder)
            .put("batchSize", config.batchSize)
            .put("microBatchSize", config.microBatchSize)
            .put("cpuThreads", config.cpuThreads)
            .put("batchThreads", config.batchThreads)
            .put("maxOutputTokens", config.maxOutputTokens)
            .put("seedPolicy", "FIXED")
            .put("generationSeed", config.seed)
            .put("thinkingMode", "DISABLED")
            .put("promptDigests", JSONArray(config.prompts.map(::sha256)))
            .put("serialOutputDigests", JSONArray(serialDigests))
            .put("batchOutputDigests", JSONArray(batch.outputs.map(::sha256)))
            .put("serialOutputTokensPerCase", JSONArray(serial.outputTokens))
            .put("batchOutputTokensPerCase", JSONArray(batch.outputTokens))
            .put("outputsMatch", serialDigests == batch.outputs.map(::sha256))
            .put("serialElapsedMs", serial.elapsedMs)
            .put("batchElapsedMs", batch.elapsedMs)
            .put("speedup", serial.elapsedMs.toDouble() / batch.elapsedMs.coerceAtLeast(1L))
            .put("serialOutputTokens", serial.outputTokens.sum())
            .put("batchOutputTokens", batch.outputTokens.sum())
            .put("processPssKbBefore", before.processPssKb)
            .put("processPssKbAfterSerial", afterSerial.processPssKb)
            .put("processPssKbAfterBatch", afterBatch.processPssKb)
            .put("availableMemoryBytesBefore", before.availableMemoryBytes)
            .put("availableMemoryBytesAfterSerial", afterSerial.availableMemoryBytes)
            .put("availableMemoryBytesAfterBatch", afterBatch.availableMemoryBytes)
            .put("thermalStatusBefore", before.thermalStatus)
            .put("thermalStatusAfterSerial", afterSerial.thermalStatus)
            .put("thermalStatusAfterBatch", afterBatch.thermalStatus)
        emitStatus("LOCAL_LLM_LLRT9_JSON $evidence")
    }

    private fun deviceSnapshot(): Llrt9DeviceSnapshot {
        val processMemory = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
        val memoryInfo = ActivityManager.MemoryInfo().also {
            context.getSystemService(ActivityManager::class.java).getMemoryInfo(it)
        }
        val powerManager = context.getSystemService(PowerManager::class.java)
        return Llrt9DeviceSnapshot(processMemory.totalPss, memoryInfo.availMem, powerManager.currentThermalStatus)
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

private data class Llrt9Harness(val runtime: RuntimeOrchestrator, val applicationId: ApplicationId, val useCaseId: UseCaseId)

private data class MeasuredMode(val prompts: List<String>, val outputs: List<String>, val outputTokens: List<Int>, val elapsedMs: Long)

private data class Llrt9DeviceSnapshot(val processPssKb: Int, val availableMemoryBytes: Long, val thermalStatus: Int)

private class Llrt9SingleBindingRegistry(private val resolved: ResolvedUseCase) : ModelProfileRegistry {
    override fun resolve(applicationId: ApplicationId, useCaseId: UseCaseId): ResolvedUseCase =
        resolved.takeIf { it.binding.applicationId == applicationId && it.binding.useCaseId == useCaseId }
            ?: error("No LLRT-9 model binding for application=${applicationId.value}, useCase=${useCaseId.value}")
}

private data class Llrt9EvidenceConfig(
    val modelRelativePath: String,
    val digest: ModelDigest,
    val tier: Qwen35ModelTier,
    val width: Int,
    val contextSize: Int,
    val batchSize: Int,
    val microBatchSize: Int,
    val cpuThreads: Int,
    val batchThreads: Int,
    val maxOutputTokens: Int,
    val seed: Long,
    val timeoutSeconds: Long,
    val sampleIndex: Int,
    val harnessCommit: String,
) {
    val prompts: List<String> = SYNTHETIC_PROMPTS.take(width)

    init {
        require(width in 2..4) { "LLRT-9 batch width must be 2..4" }
        require(contextSize > 0 && contextSize % 256 == 0) { "LLRT-9 context must be a positive multiple of 256" }
        require(batchSize >= width) { "LLRT-9 batch size must cover the sequence width" }
        require(microBatchSize in 1..batchSize) { "LLRT-9 micro-batch must be within batch size" }
        require(cpuThreads > 0 && batchThreads > 0) { "LLRT-9 thread counts must be positive" }
        require(maxOutputTokens > 0) { "LLRT-9 output budget must be positive" }
        require(seed >= 0) { "LLRT-9 seed must be non-negative" }
        require(timeoutSeconds > 0) { "LLRT-9 timeout must be positive" }
        require(sampleIndex >= 0) { "LLRT-9 sample index must be non-negative" }
        require(harnessCommit.matches(Regex("[0-9a-f]{40}"))) { "LLRT-9 harness commit must be an exact SHA" }
    }

    fun resolveModelFile(context: Context): File = File(context.filesDir, modelRelativePath).canonicalFile.also {
        require(it.isFile && it.canRead()) { "LLRT-9 model file is unavailable: $it" }
    }

    companion object {
        private val SYNTHETIC_PROMPTS = listOf(
            "Reply with one short sentence: What is 2 + 2?",
            "Reply with one short sentence: Name the largest planet in the Solar System.",
            "Reply with one short sentence: What color do blue and yellow make?",
            "Reply with one short sentence: What is the capital of France?",
        )

        fun fromInstrumentation(): Llrt9EvidenceConfig {
            val args = InstrumentationRegistry.getArguments()
            val tier = when (required(args, "modelTier")) {
                "0.8b" -> Qwen35ModelTier.B0_8
                "2b" -> Qwen35ModelTier.B2
                else -> error("modelTier must be 0.8b or 2b")
            }
            return Llrt9EvidenceConfig(
                modelRelativePath = required(args, "modelRelativePath"),
                digest = ModelDigest(required(args, "modelSha256")),
                tier = tier,
                width = required(args, "batchWidth").toInt(),
                contextSize = required(args, "contextSize").toInt(),
                batchSize = required(args, "batchSize").toInt(),
                microBatchSize = required(args, "microBatchSize").toInt(),
                cpuThreads = required(args, "cpuThreads").toInt(),
                batchThreads = required(args, "batchThreads").toInt(),
                maxOutputTokens = required(args, "maxOutputTokens").toInt(),
                seed = required(args, "generationSeed").toLong(),
                timeoutSeconds = required(args, "timeoutSeconds").toLong(),
                sampleIndex = required(args, "sampleIndex").toInt(),
                harnessCommit = required(args, "harnessCommit"),
            )
        }

        private fun required(args: Bundle, name: String): String =
            requireNotNull(args.getString(name)).takeIf(String::isNotBlank) ?: error("Missing instrumentation argument: $name")
    }
}
