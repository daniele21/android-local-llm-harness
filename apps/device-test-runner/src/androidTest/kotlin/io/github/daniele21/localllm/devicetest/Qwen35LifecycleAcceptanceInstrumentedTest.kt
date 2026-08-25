package io.github.daniele21.localllm.devicetest

import android.content.Context
import android.os.Build
import android.os.Debug
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
import io.github.daniele21.localllm.runtime.RuntimeMemoryAction
import io.github.daniele21.localllm.runtime.RuntimeMemoryPressure
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
class Qwen35LifecycleAcceptanceInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val config = LifecycleAcceptanceConfig.fromInstrumentation()

    @Test
    fun lowMemoryDuringActiveGenerationCancelsAndReleasesEverything() {
        val harness = buildHarness(config.runtime.lowMemoryOutputTokens)
        val runtime = harness.runtime
        try {
            val prepared = runtime.prepare(harness.primary.applicationId, harness.primary.useCaseId)
            assertTrue("Primary prepare failed: ${prepared.detail}", prepared.ready)
            assertEquals(config.primary.digest, prepared.modelDigest)

            val session = runtime.createSession(harness.primary.applicationId, harness.primary.useCaseId)
            val firstDelta = CountDownLatch(1)
            val terminal = CountDownLatch(1)
            val terminalEvent = AtomicReference<GenerationEvent>()
            runtime.generate(
                request(
                    binding = harness.primary,
                    session = session,
                    prompt = config.prompts.lowMemoryPrompt,
                ),
                GenerationListener { event ->
                    if (event is GenerationEvent.TextDelta) firstDelta.countDown()
                    if (event is GenerationEvent.Completed || event is GenerationEvent.Failed) {
                        terminalEvent.compareAndSet(null, event)
                        terminal.countDown()
                    }
                },
            )

            assertTrue(
                "No streaming delta arrived before low-memory pressure",
                firstDelta.await(config.runtime.timeoutSeconds, TimeUnit.SECONDS),
            )
            val pssBeforeKb = totalPssKb()
            val pressureResult = runtime.handleMemoryPressure(RuntimeMemoryPressure.LOW_MEMORY)
            assertEquals(RuntimeMemoryAction.CANCEL_AND_RELEASE_ALL, pressureResult.action)
            assertTrue(
                "Low-memory cancellation did not reach a terminal event",
                terminal.await(config.runtime.timeoutSeconds, TimeUnit.SECONDS),
            )
            val terminalResult = terminalEvent.get()
            assertTrue(
                "Expected cancellation after LOW_MEMORY but got ${terminalResult?.javaClass?.simpleName}",
                terminalResult is GenerationEvent.Failed && terminalResult.error is LocalLlmError.Cancelled,
            )
            assertTrue(
                "LOW_MEMORY did not release runtime resources",
                eventually(config.runtime.timeoutSeconds) {
                    val resources = runtime.memoryResourceSnapshot()
                    !resources.modelLoaded &&
                        resources.activeSessions == 0 &&
                        !resources.activeGeneration &&
                        resources.queuedGenerations == 0
                },
            )

            printEvidence(
                scenario = "LOW_MEMORY_ACTIVE_GENERATION",
                values = mapOf(
                    "primaryModelDigest" to config.primary.digest.sha256,
                    "maxOutputTokens" to config.runtime.lowMemoryOutputTokens,
                    "action" to pressureResult.action.name,
                    "cancelledRequests" to pressureResult.cancelledRequests,
                    "initialModelUnloaded" to pressureResult.modelUnloaded,
                    "initialUnloadDeferred" to pressureResult.deferred,
                    "pssBeforeKb" to pssBeforeKb,
                    "pssAfterKb" to totalPssKb(),
                ),
            )
        } finally {
            runtime.close()
        }
    }

    @Test
    fun switchesBetweenReferenceModelsWithoutResidencyLeak() {
        val harness = buildHarness(config.runtime.switchOutputTokens)
        val runtime = harness.runtime
        try {
            prepareAndAssert(runtime, harness.primary, config.primary.digest)
            val primarySession = runtime.createSession(harness.primary.applicationId, harness.primary.useCaseId)
            generateAndAwait(runtime, harness.primary, primarySession, config.prompts.switchPrompt)
            closeSessionOnly(runtime, primarySession)
            val primaryPssKb = totalPssKb()

            prepareAndAssert(runtime, harness.secondary, config.secondary.digest)
            assertEquals(config.secondary.digest, runtime.runtimeSnapshot().loadedModel)
            assertEquals(0, runtime.runtimeSnapshot().activeSessions)
            val secondarySession = runtime.createSession(harness.secondary.applicationId, harness.secondary.useCaseId)
            generateAndAwait(runtime, harness.secondary, secondarySession, config.prompts.switchPrompt)
            closeSessionOnly(runtime, secondarySession)
            val secondaryPssKb = totalPssKb()

            prepareAndAssert(runtime, harness.primary, config.primary.digest)
            assertEquals(config.primary.digest, runtime.runtimeSnapshot().loadedModel)
            assertEquals(0, runtime.runtimeSnapshot().activeSessions)
            val primaryReloadPssKb = totalPssKb()

            assertTrue("Final idle model unload failed", runtime.unloadIdleModel())
            assertFalse(runtime.memoryResourceSnapshot().modelLoaded)

            printEvidence(
                scenario = "REFERENCE_MODEL_SWITCH",
                values = mapOf(
                    "primaryModelDigest" to config.primary.digest.sha256,
                    "secondaryModelDigest" to config.secondary.digest.sha256,
                    "maxOutputTokens" to config.runtime.switchOutputTokens,
                    "primaryPssKb" to primaryPssKb,
                    "secondaryPssKb" to secondaryPssKb,
                    "primaryReloadPssKb" to primaryReloadPssKb,
                    "finalModelLoaded" to runtime.memoryResourceSnapshot().modelLoaded,
                ),
            )
        } finally {
            runtime.close()
        }
    }

    private fun buildHarness(maxOutputTokens: Int): LifecycleHarness {
        val primaryApplicationId = ApplicationId("q35-lifecycle-primary")
        val primaryUseCaseId = UseCaseId("generation")
        val secondaryApplicationId = ApplicationId("q35-lifecycle-secondary")
        val secondaryUseCaseId = UseCaseId("generation")
        val primaryResolved = resolvedUseCase(
            applicationId = primaryApplicationId,
            useCaseId = primaryUseCaseId,
            modelId = "q35-lifecycle-primary-model",
            useCaseProfileId = "q35-lifecycle-primary-use-case",
            model = config.primary,
            maxOutputTokens = maxOutputTokens,
        )
        val secondaryResolved = resolvedUseCase(
            applicationId = secondaryApplicationId,
            useCaseId = secondaryUseCaseId,
            modelId = "q35-lifecycle-secondary-model",
            useCaseProfileId = "q35-lifecycle-secondary-use-case",
            model = config.secondary,
            maxOutputTokens = maxOutputTokens,
        )

        val storeRoot = File(context.noBackupFilesDir, "q35-lifecycle-acceptance")
        val modelStore = FileSystemModelStore(storeRoot)
        importModel(modelStore, config.primary)
        importModel(modelStore, config.secondary)

        val nativeLibraryDir = File(context.applicationInfo.nativeLibraryDir)
        require(nativeLibraryDir.isDirectory) {
            "Native library directory is unavailable: ${nativeLibraryDir.path}"
        }
        return LifecycleHarness(
            runtime = RuntimeOrchestrator(
                registry = MultiBindingRegistry(listOf(primaryResolved, secondaryResolved)),
                modelStore = modelStore,
                backend = LlamaCppInferenceBackend(nativeLibraryDir),
            ),
            primary = LifecycleBinding(primaryApplicationId, primaryUseCaseId),
            secondary = LifecycleBinding(secondaryApplicationId, secondaryUseCaseId),
        )
    }

    private fun resolvedUseCase(
        applicationId: ApplicationId,
        useCaseId: UseCaseId,
        modelId: String,
        useCaseProfileId: String,
        model: LifecycleModelArguments,
        maxOutputTokens: Int,
    ): ResolvedUseCase {
        val source = model.resolveModelFile(context)
        val artifact = artifactFor(source, model)
        val profile = GgufModelProfile(
            id = modelId,
            artifact = artifact,
            contextSize = config.runtime.contextTokens,
            batchSize = config.runtime.batchSize,
            microBatchSize = config.runtime.microBatchSize,
            cpuThreads = model.cpuThreads,
            batchThreads = config.runtime.batchThreads,
            gpuLayers = 0,
        )
        val useCase = UseCaseProfile(
            id = useCaseProfileId,
            modelProfileId = modelId,
            systemPromptVersion = "q35-lifecycle-acceptance-v1",
            generationDefaults = GenerationDefaults(
                maxOutputTokens = maxOutputTokens,
                temperature = 0f,
                topP = 1f,
                topK = 0,
                seed = 42,
            ),
            outputMode = OutputMode.TEXT,
            cachePolicy = UseCaseCachePolicy(0, false, false, false),
            healthSuiteId = "q35-lifecycle-acceptance-health",
        )
        return ResolvedUseCase(
            binding = AppModelBinding(applicationId, useCaseId, useCase.id),
            useCase = useCase,
            model = profile,
        )
    }

    private fun importModel(store: FileSystemModelStore, model: LifecycleModelArguments) {
        val source = model.resolveModelFile(context)
        store.import(source, artifactFor(source, model))
    }

    private fun artifactFor(source: File, model: LifecycleModelArguments) = GgufArtifact(
        digest = model.digest,
        fileName = source.name,
        sizeBytes = source.length(),
        architecture = "qwen35",
        quantization = "Q4_K_M",
        source = ArtifactSource.Imported(model.relativePath),
    )

    private fun prepareAndAssert(runtime: RuntimeOrchestrator, binding: LifecycleBinding, expectedDigest: ModelDigest) {
        val prepared = runtime.prepare(binding.applicationId, binding.useCaseId)
        assertTrue("Prepare failed: ${prepared.detail}", prepared.ready)
        assertEquals(expectedDigest, prepared.modelDigest)
    }

    private fun generateAndAwait(runtime: RuntimeOrchestrator, binding: LifecycleBinding, session: SessionId, prompt: String) {
        val terminal = CountDownLatch(1)
        val terminalEvent = AtomicReference<GenerationEvent>()
        runtime.generate(
            request(binding, session, prompt),
            GenerationListener { event ->
                if (event is GenerationEvent.Completed || event is GenerationEvent.Failed) {
                    terminalEvent.compareAndSet(null, event)
                    terminal.countDown()
                }
            },
        )
        assertTrue(
            "Generation did not terminate within ${config.runtime.timeoutSeconds}s",
            terminal.await(config.runtime.timeoutSeconds, TimeUnit.SECONDS),
        )
        when (val result = terminalEvent.get()) {
            is GenerationEvent.Completed -> assertTrue("Generation output was empty", result.output.isNotBlank())
            is GenerationEvent.Failed -> throw AssertionError("Generation failed: ${result.error}")
            else -> throw AssertionError("Generation ended without a terminal event")
        }
    }

    private fun request(binding: LifecycleBinding, session: SessionId, prompt: String) = GenerationRequest(
        requestId = RequestId(UUID.randomUUID().toString()),
        sessionId = session,
        applicationId = binding.applicationId,
        useCaseId = binding.useCaseId,
        input = prompt,
    )

    private fun closeSessionOnly(runtime: RuntimeOrchestrator, session: SessionId) {
        runtime.closeSession(session)
        assertTrue(
            "Session context was not released",
            eventually(config.runtime.timeoutSeconds) { runtime.runtimeSnapshot().activeSessions == 0 },
        )
    }

    private fun eventually(timeoutSeconds: Long, predicate: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        while (System.nanoTime() < deadline) {
            if (predicate()) return true
            Thread.sleep(25)
        }
        return predicate()
    }

    private fun totalPssKb(): Int {
        val memoryInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memoryInfo)
        return memoryInfo.totalPss
    }

    private fun printEvidence(scenario: String, values: Map<String, Any>) {
        val json = JSONObject()
            .put("schemaVersion", 1)
            .put("scenario", scenario)
            .put("harnessCommit", config.evidence.harnessCommit)
            .put("backendRevision", config.evidence.backendRevision)
            .put("deviceModel", Build.MODEL)
            .put("androidRelease", Build.VERSION.RELEASE)
            .put("sdkInt", Build.VERSION.SDK_INT)
            .put("abi", Build.SUPPORTED_ABIS.firstOrNull().orEmpty())
            .put("contextTokens", config.runtime.contextTokens)
            .put("batchSize", config.runtime.batchSize)
            .put("microBatchSize", config.runtime.microBatchSize)
            .put("batchThreads", config.runtime.batchThreads)
        values.forEach { (key, value) -> json.put(key, value) }
        println("LOCAL_LLM_Q35_LIFECYCLE_JSON $json")
    }
}

private data class LifecycleHarness(val runtime: RuntimeOrchestrator, val primary: LifecycleBinding, val secondary: LifecycleBinding)

private data class LifecycleBinding(val applicationId: ApplicationId, val useCaseId: UseCaseId)

private class MultiBindingRegistry(resolved: List<ResolvedUseCase>) : ModelProfileRegistry {
    private val entries = resolved.associateBy { it.binding.applicationId to it.binding.useCaseId }

    override fun resolve(applicationId: ApplicationId, useCaseId: UseCaseId): ResolvedUseCase = entries[applicationId to useCaseId]
        ?: error("Unknown binding ${applicationId.value}/${useCaseId.value}")
}

private data class LifecycleAcceptanceConfig(
    val primary: LifecycleModelArguments,
    val secondary: LifecycleModelArguments,
    val runtime: LifecycleRuntimeArguments,
    val prompts: LifecyclePromptArguments,
    val evidence: LifecycleEvidenceIdentity,
) {
    companion object {
        fun fromInstrumentation(): LifecycleAcceptanceConfig {
            val args = InstrumentationRegistry.getArguments()
            fun required(name: String): String = args.getString(name)?.takeIf(String::isNotBlank)
                ?: error("Missing required instrumentation argument: $name")
            fun positiveInt(name: String, default: Int): Int =
                (args.getString(name) ?: default.toString()).toInt().also { require(it > 0) { "$name must be positive" } }
            fun positiveLong(name: String, default: Long): Long =
                (args.getString(name) ?: default.toString()).toLong().also { require(it > 0) { "$name must be positive" } }

            return LifecycleAcceptanceConfig(
                primary = LifecycleModelArguments(
                    relativePath = args.getString("primaryModelRelativePath") ?: "files/e2e/qwen35-08b.gguf",
                    digest = ModelDigest(required("primaryModelSha256").lowercase()),
                    cpuThreads = positiveInt("primaryCpuThreads", 2),
                ),
                secondary = LifecycleModelArguments(
                    relativePath = args.getString("secondaryModelRelativePath") ?: "files/e2e/qwen35-2b.gguf",
                    digest = ModelDigest(required("secondaryModelSha256").lowercase()),
                    cpuThreads = positiveInt("secondaryCpuThreads", 4),
                ),
                runtime = LifecycleRuntimeArguments(
                    contextTokens = positiveInt("contextTokens", 2_048),
                    batchSize = positiveInt("batchSize", 128),
                    microBatchSize = positiveInt("microBatchSize", 64),
                    batchThreads = positiveInt("batchThreads", 4),
                    switchOutputTokens = positiveInt("switchOutputTokens", 8),
                    lowMemoryOutputTokens = positiveInt("lowMemoryOutputTokens", 256),
                    timeoutSeconds = positiveLong("timeoutSeconds", 900),
                ),
                prompts = LifecyclePromptArguments(
                    switchPrompt = args.getString("switchPrompt") ?: "Reply with READY.",
                    lowMemoryPrompt = args.getString("lowMemoryPrompt")
                        ?: "Write a numbered list from 1 to 1000 and do not stop early.",
                ),
                evidence = LifecycleEvidenceIdentity(
                    harnessCommit = required("harnessCommit"),
                    backendRevision = required("backendRevision"),
                ),
            )
        }
    }
}

private data class LifecycleRuntimeArguments(
    val contextTokens: Int,
    val batchSize: Int,
    val microBatchSize: Int,
    val batchThreads: Int,
    val switchOutputTokens: Int,
    val lowMemoryOutputTokens: Int,
    val timeoutSeconds: Long,
)

private data class LifecyclePromptArguments(val switchPrompt: String, val lowMemoryPrompt: String)

private data class LifecycleEvidenceIdentity(val harnessCommit: String, val backendRevision: String)

private data class LifecycleModelArguments(val relativePath: String, val digest: ModelDigest, val cpuThreads: Int) {
    fun resolveModelFile(context: Context): File {
        require(relativePath.isNotBlank() && !File(relativePath).isAbsolute) {
            "Model path must be relative to the application data directory"
        }
        val root = context.dataDir.canonicalFile
        val file = File(root, relativePath).canonicalFile
        require(file.path.startsWith(root.path + File.separator)) {
            "Model path escapes the application data directory"
        }
        require(file.isFile && file.canRead()) { "Model file is missing or unreadable: ${file.path}" }
        return file
    }
}
