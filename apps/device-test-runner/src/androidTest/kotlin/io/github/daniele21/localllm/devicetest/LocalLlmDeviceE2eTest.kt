package io.github.daniele21.localllm.devicetest

import android.content.Context
import android.os.Debug
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.GenerationEvent
import io.github.daniele21.localllm.contracts.GenerationListener
import io.github.daniele21.localllm.contracts.GenerationRequest
import io.github.daniele21.localllm.contracts.LocalLlmError
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.models.AppModelBinding
import io.github.daniele21.localllm.models.ArtifactSource
import io.github.daniele21.localllm.models.GenerationDefaults
import io.github.daniele21.localllm.models.GgufArtifact
import io.github.daniele21.localllm.models.GgufModelProfile
import io.github.daniele21.localllm.models.ModelProfileRegistry
import io.github.daniele21.localllm.models.OutputMode
import io.github.daniele21.localllm.models.ResolvedUseCase
import io.github.daniele21.localllm.models.UseCaseCachePolicy
import io.github.daniele21.localllm.models.UseCaseProfile
import io.github.daniele21.localllm.runtime.LlamaCppInferenceBackend
import io.github.daniele21.localllm.runtime.RuntimeOrchestrator
import io.github.daniele21.localllm.store.FileSystemModelStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class LocalLlmDeviceE2eTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val config = DeviceTestConfig.fromInstrumentation()

    @Test
    fun fullLifecycleGeneratesAndReleasesResources() {
        val harness = buildHarness(config.maxOutputTokens)
        val runtime = harness.runtime

        try {
            val prepared = runtime.prepare(harness.applicationId, harness.useCaseId)
            assertTrue("Prepare failed: ${prepared.detail}", prepared.ready)
            assertEquals(config.modelDigest, prepared.modelDigest)

            val session = runtime.createSession(harness.applicationId, harness.useCaseId)
            val completed = generateAndAwait(runtime, harness, session, config.prompt)

            assertTrue("Generation returned an empty output", completed.output.isNotBlank())
            assertTrue(completed.metrics.inputTokens > 0)
            assertTrue(completed.metrics.outputTokens > 0)
            assertNotNull(completed.metrics.timeToFirstTokenMs)

            closeSessionAndUnload(runtime, session)
            assertFalse(runtime.memoryResourceSnapshot().modelLoaded)

            println(
                "LOCAL_LLM_E2E generation " +
                    "inputTokens=${completed.metrics.inputTokens} " +
                    "outputTokens=${completed.metrics.outputTokens} " +
                    "ttftMs=${completed.metrics.timeToFirstTokenMs} " +
                    "totalMs=${completed.metrics.totalMs} " +
                    "decodeTokensPerSecond=${completed.metrics.decodeTokensPerSecond}",
            )
        } finally {
            runtime.close()
        }
    }

    @Test
    fun activeGenerationCanBeCancelled() {
        assumeTrue("Cancellation validation disabled by instrumentation argument", config.cancellationEnabled)

        val harness = buildHarness(config.cancellationMaxOutputTokens)
        val runtime = harness.runtime

        try {
            val prepared = runtime.prepare(harness.applicationId, harness.useCaseId)
            assertTrue("Prepare failed: ${prepared.detail}", prepared.ready)

            val session = runtime.createSession(harness.applicationId, harness.useCaseId)
            val firstDelta = CountDownLatch(1)
            val terminal = CountDownLatch(1)
            val terminalEvent = AtomicReference<GenerationEvent>()
            val events = Collections.synchronizedList(mutableListOf<GenerationEvent>())

            val handle = runtime.generate(
                request(harness, session, config.cancellationPrompt),
                GenerationListener { event ->
                    events += event
                    if (event is GenerationEvent.TextDelta) firstDelta.countDown()
                    if (event is GenerationEvent.Completed || event is GenerationEvent.Failed) {
                        terminalEvent.compareAndSet(null, event)
                        terminal.countDown()
                    }
                },
            )

            assertTrue(
                "The model produced no streaming delta before the cancellation timeout",
                firstDelta.await(config.timeoutSeconds, TimeUnit.SECONDS),
            )
            handle.cancel()
            assertTrue(
                "Cancellation did not reach a terminal event",
                terminal.await(config.timeoutSeconds, TimeUnit.SECONDS),
            )

            val result = terminalEvent.get()
            assertTrue(
                "Expected a cancelled failure but received ${result?.javaClass?.simpleName}; " +
                    "the model may have completed before cancellation",
                result is GenerationEvent.Failed && result.error is LocalLlmError.Cancelled,
            )
            assertTrue(events.any { it is GenerationEvent.Started })
            assertTrue(events.any { it is GenerationEvent.TextDelta })

            closeSessionAndUnload(runtime, session)
            println("LOCAL_LLM_E2E cancellation terminal=cancelled")
        } finally {
            runtime.close()
        }
    }

    @Test
    fun repeatedLifecycleStaysInsideConfiguredPssBudget() {
        assumeTrue(
            "Memory stability validation requires memoryRepeatCount >= 2",
            config.memoryRepeatCount >= 2,
        )

        val pssSamplesKb = mutableListOf<Int>()
        repeat(config.memoryRepeatCount) { iteration ->
            val harness = buildHarness(config.memoryOutputTokens)
            val runtime = harness.runtime
            try {
                val prepared = runtime.prepare(harness.applicationId, harness.useCaseId)
                assertTrue("Prepare failed on iteration $iteration: ${prepared.detail}", prepared.ready)
                val session = runtime.createSession(harness.applicationId, harness.useCaseId)
                generateAndAwait(runtime, harness, session, config.memoryPrompt)
                closeSessionAndUnload(runtime, session)
            } finally {
                runtime.close()
            }

            Runtime.getRuntime().gc()
            Thread.sleep(config.memorySettleMillis)
            pssSamplesKb += totalPssKb()
        }

        val growthKb = pssSamplesKb.last() - pssSamplesKb.first()
        assertTrue(
            "PSS grew by ${growthKb}KB across ${config.memoryRepeatCount} cycles; " +
                "budget=${config.maxPssGrowthKb}KB, samples=$pssSamplesKb",
            growthKb <= config.maxPssGrowthKb,
        )
        println("LOCAL_LLM_E2E memory pssSamplesKb=$pssSamplesKb growthKb=$growthKb")
    }

    private fun buildHarness(maxOutputTokens: Int): DeviceHarness {
        val sourceModel = config.resolveModelFile(context)
        val applicationId = ApplicationId("device-e2e")
        val useCaseId = UseCaseId("generation")
        val modelProfileId = "device-e2e-model"
        val useCaseProfileId = "device-e2e-use-case"

        val artifact = GgufArtifact(
            digest = config.modelDigest,
            fileName = sourceModel.name,
            sizeBytes = sourceModel.length(),
            architecture = config.modelArchitecture,
            quantization = config.modelQuantization,
            source = ArtifactSource.Imported(config.modelRelativePath),
        )
        val modelProfile = GgufModelProfile(
            id = modelProfileId,
            artifact = artifact,
            contextSize = config.contextSize,
            batchSize = config.batchSize,
            microBatchSize = config.microBatchSize,
            cpuThreads = config.cpuThreads,
            batchThreads = config.cpuThreads,
            gpuLayers = 0,
        )
        val useCase = UseCaseProfile(
            id = useCaseProfileId,
            modelProfileId = modelProfileId,
            systemPromptVersion = "device-e2e-v1",
            generationDefaults = GenerationDefaults(
                maxOutputTokens = maxOutputTokens,
                temperature = 0f,
                topP = 1f,
                topK = 0,
                seed = 42,
            ),
            outputMode = OutputMode.TEXT,
            cachePolicy = UseCaseCachePolicy(0, false, false, false),
            healthSuiteId = "device-e2e-health",
        )
        val resolved = ResolvedUseCase(
            binding = AppModelBinding(applicationId, useCaseId, useCase.id),
            useCase = useCase,
            model = modelProfile,
        )

        val storeRoot = File(context.noBackupFilesDir, "local-llm-device-e2e")
        val modelStore = FileSystemModelStore(storeRoot)
        modelStore.import(sourceModel, artifact)

        val nativeLibraryDir = File(context.applicationInfo.nativeLibraryDir)
        require(nativeLibraryDir.isDirectory) {
            "Native library directory is unavailable: ${nativeLibraryDir.path}"
        }

        return DeviceHarness(
            runtime = RuntimeOrchestrator(
                registry = SingleBindingRegistry(resolved),
                modelStore = modelStore,
                backend = LlamaCppInferenceBackend(nativeLibraryDir),
            ),
            applicationId = applicationId,
            useCaseId = useCaseId,
        )
    }

    private fun generateAndAwait(
        runtime: RuntimeOrchestrator,
        harness: DeviceHarness,
        session: SessionId,
        prompt: String,
    ): GenerationEvent.Completed {
        val terminal = CountDownLatch(1)
        val terminalEvent = AtomicReference<GenerationEvent>()

        runtime.generate(
            request(harness, session, prompt),
            GenerationListener { event ->
                if (event is GenerationEvent.Completed || event is GenerationEvent.Failed) {
                    terminalEvent.compareAndSet(null, event)
                    terminal.countDown()
                }
            },
        )

        assertTrue(
            "Generation did not complete within ${config.timeoutSeconds} seconds",
            terminal.await(config.timeoutSeconds, TimeUnit.SECONDS),
        )
        return when (val result = terminalEvent.get()) {
            is GenerationEvent.Completed -> result
            is GenerationEvent.Failed -> throw AssertionError(
                "Generation failed with ${result.error::class.simpleName}: ${result.error.message}",
            )
            else -> throw AssertionError("Generation completed without a terminal event")
        }
    }

    private fun request(
        harness: DeviceHarness,
        session: SessionId,
        prompt: String,
    ): GenerationRequest = GenerationRequest(
        requestId = RequestId(UUID.randomUUID().toString()),
        sessionId = session,
        applicationId = harness.applicationId,
        useCaseId = harness.useCaseId,
        input = prompt,
    )

    private fun closeSessionAndUnload(runtime: RuntimeOrchestrator, session: SessionId) {
        runtime.closeSession(session)
        assertTrue(
            "Session context was not released",
            eventually(config.timeoutSeconds) { runtime.runtimeSnapshot().activeSessions == 0 },
        )
        assertTrue("Idle model was not unloaded", runtime.unloadIdleModel())
    }

    private fun eventually(timeoutSeconds: Long, condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        while (System.nanoTime() < deadline) {
            if (condition()) return true
            Thread.sleep(25)
        }
        return condition()
    }

    private fun totalPssKb(): Int {
        val memoryInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memoryInfo)
        return memoryInfo.totalPss
    }
}

private data class DeviceHarness(
    val runtime: RuntimeOrchestrator,
    val applicationId: ApplicationId,
    val useCaseId: UseCaseId,
)

private class SingleBindingRegistry(
    private val resolved: ResolvedUseCase,
) : ModelProfileRegistry {
    override fun resolve(applicationId: ApplicationId, useCaseId: UseCaseId): ResolvedUseCase {
        require(applicationId == resolved.binding.applicationId) {
            "Unknown applicationId ${applicationId.value}"
        }
        require(useCaseId == resolved.binding.useCaseId) {
            "Unknown useCaseId ${useCaseId.value}"
        }
        return resolved
    }
}

private data class DeviceTestConfig(
    val modelRelativePath: String,
    val modelDigest: ModelDigest,
    val modelArchitecture: String,
    val modelQuantization: String,
    val prompt: String,
    val maxOutputTokens: Int,
    val contextSize: Int,
    val batchSize: Int,
    val microBatchSize: Int,
    val cpuThreads: Int,
    val timeoutSeconds: Long,
    val cancellationEnabled: Boolean,
    val cancellationPrompt: String,
    val cancellationMaxOutputTokens: Int,
    val memoryRepeatCount: Int,
    val memoryPrompt: String,
    val memoryOutputTokens: Int,
    val memorySettleMillis: Long,
    val maxPssGrowthKb: Int,
) {
    fun resolveModelFile(context: Context): File {
        require(modelRelativePath.isNotBlank() && !File(modelRelativePath).isAbsolute) {
            "modelRelativePath must be a non-empty path relative to the application data directory"
        }
        val dataRoot = context.dataDir.canonicalFile
        val modelFile = File(dataRoot, modelRelativePath).canonicalFile
        require(modelFile.path.startsWith(dataRoot.path + File.separator)) {
            "modelRelativePath escapes the application data directory"
        }
        require(modelFile.isFile && modelFile.canRead()) {
            "Model file is missing or unreadable: ${modelFile.path}"
        }
        return modelFile
    }

    companion object {
        fun fromInstrumentation(): DeviceTestConfig {
            val arguments = InstrumentationRegistry.getArguments()

            fun string(name: String, default: String): String =
                arguments.getString(name)?.takeIf { it.isNotBlank() } ?: default

            fun requiredString(name: String): String =
                arguments.getString(name)?.takeIf { it.isNotBlank() }
                    ?: error("Missing required instrumentation argument: $name")

            fun positiveInt(name: String, default: Int): Int =
                string(name, default.toString()).toInt().also {
                    require(it > 0) { "$name must be positive" }
                }

            fun nonNegativeInt(name: String, default: Int): Int =
                string(name, default.toString()).toInt().also {
                    require(it >= 0) { "$name must not be negative" }
                }

            fun positiveLong(name: String, default: Long): Long =
                string(name, default.toString()).toLong().also {
                    require(it > 0) { "$name must be positive" }
                }

            fun decoded(name: String, default: String): String {
                val encoded = arguments.getString(name)?.takeIf { it.isNotBlank() } ?: return default
                return Base64.decode(encoded, Base64.DEFAULT).toString(Charsets.UTF_8)
            }

            val availableProcessors = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            return DeviceTestConfig(
                modelRelativePath = string("modelRelativePath", "files/e2e/model.gguf"),
                modelDigest = ModelDigest(requiredString("modelSha256").lowercase()),
                modelArchitecture = string("modelArchitecture", "unknown"),
                modelQuantization = string("modelQuantization", "unknown"),
                prompt = decoded("promptBase64", "Reply with the single word READY."),
                maxOutputTokens = positiveInt("maxOutputTokens", 32),
                contextSize = positiveInt("contextSize", 512),
                batchSize = positiveInt("batchSize", 128),
                microBatchSize = positiveInt("microBatchSize", 64),
                cpuThreads = positiveInt("cpuThreads", availableProcessors.coerceAtMost(4)),
                timeoutSeconds = positiveLong("timeoutSeconds", 120),
                cancellationEnabled = string("cancellationEnabled", "true").toBooleanStrict(),
                cancellationPrompt = decoded(
                    "cancellationPromptBase64",
                    "Write a numbered list from 1 to 1000. Continue until every number is written.",
                ),
                cancellationMaxOutputTokens = positiveInt("cancellationMaxOutputTokens", 512),
                memoryRepeatCount = nonNegativeInt("memoryRepeatCount", 0),
                memoryPrompt = decoded("memoryPromptBase64", "Reply with the single word READY."),
                memoryOutputTokens = positiveInt("memoryOutputTokens", 16),
                memorySettleMillis = positiveLong("memorySettleMillis", 750),
                maxPssGrowthKb = nonNegativeInt("maxPssGrowthKb", 131_072),
            )
        }
    }
}
