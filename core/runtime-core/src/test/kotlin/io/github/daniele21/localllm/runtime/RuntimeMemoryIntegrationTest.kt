package io.github.daniele21.localllm.runtime

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
import io.github.daniele21.localllm.store.ModelStore
import io.github.daniele21.localllm.store.ModelStoreSnapshot
import io.github.daniele21.localllm.store.StoredModel
import io.github.daniele21.localllm.store.VerificationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RuntimeMemoryIntegrationTest {
    @Test
    fun `ui hidden unloads idle warm model`() {
        val fixture = MemoryRuntimeFixture()
        assertTrue(fixture.runtime.prepare(fixture.applicationId, fixture.useCaseId).ready)

        val result = fixture.runtime.handleMemoryPressure(RuntimeMemoryPressure.UI_HIDDEN)

        assertEquals(RuntimeMemoryAction.UNLOAD_IDLE_MODEL, result.action)
        assertTrue(result.modelUnloaded)
        assertFalse(result.deferred)
        assertEquals(1, fixture.backend.unloadCalls)
        assertNull(fixture.runtime.runtimeSnapshot().loadedModel)
        fixture.close()
    }

    @Test
    fun `background pressure preserves active session`() {
        val fixture = MemoryRuntimeFixture()
        fixture.runtime.createSession(fixture.applicationId, fixture.useCaseId)

        val result = fixture.runtime.handleMemoryPressure(RuntimeMemoryPressure.BACKGROUND)

        assertEquals(RuntimeMemoryAction.NONE, result.action)
        assertFalse(result.modelUnloaded)
        assertEquals(0, fixture.backend.unloadCalls)
        assertEquals(1, fixture.runtime.runtimeSnapshot().activeSessions)
        fixture.close()
    }

    @Test
    fun `low memory cancels active and queued requests then releases all resources`() {
        val fixture = MemoryRuntimeFixture()
        val session = fixture.runtime.createSession(fixture.applicationId, fixture.useCaseId)
        fixture.backend.blockGeneration = CountDownLatch(1)
        val activeEvents = Collections.synchronizedList(mutableListOf<GenerationEvent>())
        val queuedEvents = Collections.synchronizedList(mutableListOf<GenerationEvent>())
        val terminals = CountDownLatch(2)

        fixture.runtime.generate(
            fixture.request("active", session),
            terminalListener(activeEvents, terminals),
        )
        assertTrue(fixture.backend.generationStarted.await(2, TimeUnit.SECONDS))
        fixture.runtime.generate(
            fixture.request("queued", session),
            terminalListener(queuedEvents, terminals),
        )

        val result = fixture.runtime.handleMemoryPressure(RuntimeMemoryPressure.LOW_MEMORY)

        assertEquals(RuntimeMemoryAction.CANCEL_AND_RELEASE_ALL, result.action)
        assertEquals(2, result.cancelledRequests)
        assertFalse(result.modelUnloaded)
        assertTrue(result.deferred)
        assertTrue(terminals.await(2, TimeUnit.SECONDS))
        assertTrue(eventually { fixture.runtime.runtimeSnapshot().activeSessions == 0 })
        assertTrue(eventually { fixture.runtime.runtimeSnapshot().loadedModel == null })
        assertEquals(1, fixture.backend.cancelCalls)
        assertEquals(1, fixture.backend.releaseCalls)
        assertEquals(1, fixture.backend.unloadCalls)
        assertTrue((activeEvents.last() as GenerationEvent.Failed).error is LocalLlmError.Cancelled)
        assertTrue((queuedEvents.last() as GenerationEvent.Failed).error is LocalLlmError.Cancelled)
        assertFalse(queuedEvents.any { it is GenerationEvent.Started })
        fixture.close()
    }

    private fun terminalListener(events: MutableList<GenerationEvent>, terminals: CountDownLatch): GenerationListener =
        GenerationListener { event ->
            events += event
            if (event is GenerationEvent.Completed || event is GenerationEvent.Failed) {
                terminals.countDown()
            }
        }

    private fun eventually(timeoutMs: Long = 2_000, condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (System.nanoTime() < deadline) {
            if (condition()) return true
            Thread.sleep(10)
        }
        return condition()
    }
}

private class MemoryRuntimeFixture {
    val applicationId = ApplicationId("memory-app")
    val useCaseId = UseCaseId("memory-use-case")
    private val digest = ModelDigest("e".repeat(64))
    private val modelFile = File.createTempFile("memory-runtime-model", ".gguf").apply {
        writeText("model")
        deleteOnExit()
    }
    val backend = MemoryFakeBackend()
    val runtime = RuntimeOrchestrator(
        registry = MemoryRegistry(resolvedUseCase()),
        modelStore = MemoryModelStore(modelFile),
        backend = backend,
    )

    fun request(id: String, sessionId: SessionId): GenerationRequest = GenerationRequest(
        requestId = RequestId(id),
        sessionId = sessionId,
        applicationId = applicationId,
        useCaseId = useCaseId,
        input = "prompt",
    )

    fun close() {
        backend.blockGeneration?.countDown()
        runtime.close()
        modelFile.delete()
    }

    private fun resolvedUseCase(): ResolvedUseCase {
        val model = GgufModelProfile(
            id = "memory-profile",
            artifact = GgufArtifact(
                digest = digest,
                fileName = "memory.gguf",
                sizeBytes = modelFile.length(),
                architecture = "qwen2",
                quantization = "Q4_K_M",
                source = ArtifactSource.Imported("memory"),
            ),
            contextSize = 512,
            batchSize = 128,
            microBatchSize = 64,
            cpuThreads = 2,
            batchThreads = 2,
            gpuLayers = 0,
        )
        val useCase = UseCaseProfile(
            id = "memory-use-case-profile",
            modelProfileId = model.id,
            systemPromptVersion = "v1",
            generationDefaults = GenerationDefaults(8, 0f, seed = 7),
            outputMode = OutputMode.TEXT,
            cachePolicy = UseCaseCachePolicy(0, false, false, false),
            healthSuiteId = "health",
        )
        return ResolvedUseCase(
            binding = AppModelBinding(applicationId, useCaseId, useCase.id),
            useCase = useCase,
            model = model,
        )
    }
}

private class MemoryRegistry(private val resolved: ResolvedUseCase) : ModelProfileRegistry {
    override fun resolve(applicationId: ApplicationId, useCaseId: UseCaseId): ResolvedUseCase = resolved
}

private class MemoryModelStore(private val file: File) : ModelStore {
    override fun find(digest: ModelDigest): StoredModel = StoredModel(
        digest = digest,
        file = file,
        sizeBytes = file.length(),
        verified = true,
    )

    override fun import(source: File, artifact: GgufArtifact): StoredModel = error("Not used")

    override fun verify(digest: ModelDigest): VerificationResult = VerificationResult(true, digest, "valid")

    override fun remove(digest: ModelDigest): Boolean = false

    override fun snapshot(): ModelStoreSnapshot = ModelStoreSnapshot(0, 0, emptyList())
}

private data class MemoryBackendModel(
    override val digest: ModelDigest,
    override val profileId: String,
    override val loadDurationMs: Long = 1,
) : BackendModelHandle

private data class MemoryBackendContext(override val model: BackendModelHandle, override val contextSize: Int) : BackendContextHandle

private class MemoryFakeBackend : InferenceBackend {
    override val id: String = "memory-fake"
    var unloadCalls: Int = 0
    var releaseCalls: Int = 0
    var cancelCalls: Int = 0
    var blockGeneration: CountDownLatch? = null
    val generationStarted = CountDownLatch(1)

    override fun initialize() = Unit

    override fun shutdown() = Unit

    override fun loadModel(storedModel: StoredModel, profile: GgufModelProfile): BackendModelHandle =
        MemoryBackendModel(storedModel.digest, profile.id)

    override fun unloadModel(model: BackendModelHandle) {
        unloadCalls += 1
    }

    override fun modelCapabilities(model: BackendModelHandle) = BackendModelCapabilities(4_096, true)

    override fun planPrompt(model: BackendModelHandle, request: BackendPromptPlanningRequest) = fakePromptPlan(request)

    override fun createContext(
        model: BackendModelHandle,
        profile: GgufModelProfile,
        configuration: BackendContextConfiguration,
    ): BackendContextHandle = MemoryBackendContext(model, configuration.contextSize)

    override fun releaseContext(context: BackendContextHandle) {
        releaseCalls += 1
    }

    override fun generate(
        context: BackendContextHandle,
        request: BackendGenerationRequest,
        onChunk: (text: String, generatedTokens: Int) -> Boolean,
    ): BackendGenerationOutcome {
        generationStarted.countDown()
        blockGeneration?.await()
        return if (onChunk("result", 1)) {
            BackendGenerationOutcome.Completed(BackendGenerationMetrics(1, 1, 1, 1))
        } else {
            BackendGenerationOutcome.Cancelled(BackendGenerationMetrics(1, 0, 1, 1))
        }
    }

    override fun cancel(requestId: String): Boolean {
        cancelCalls += 1
        blockGeneration?.countDown()
        return true
    }
}
