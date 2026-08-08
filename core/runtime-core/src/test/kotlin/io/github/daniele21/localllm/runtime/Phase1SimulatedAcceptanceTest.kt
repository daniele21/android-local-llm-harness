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
import io.github.daniele21.localllm.store.FileSystemModelStore
import io.github.daniele21.localllm.store.StoredModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class Phase1SimulatedAcceptanceTest {
    @Test
    fun `real store and simulated backend complete lifecycle and recover after cancellation`() {
        val fixture = SimulatedAcceptanceFixture()
        try {
            val imported = fixture.store.import(fixture.sourceModel, fixture.artifact)
            assertEquals(fixture.digest, imported.digest)
            assertTrue(imported.verified)
            assertTrue(fixture.store.verify(fixture.digest).valid)
            assertEquals(1, fixture.store.snapshot().modelCount)

            val prepared = fixture.runtime.prepare(fixture.applicationId, fixture.useCaseId)
            assertTrue(prepared.ready)
            assertEquals(fixture.digest, prepared.modelDigest)

            val session = fixture.runtime.createSession(fixture.applicationId, fixture.useCaseId)
            val successEvents = fixture.generateAndAwait("success", session)
            assertEquals(
                listOf("Queued", "Prepared", "Started", "TextDelta", "TextDelta", "Completed"),
                successEvents.map { it::class.simpleName },
            )
            assertEquals("simulated ready", (successEvents.last() as GenerationEvent.Completed).output)

            fixture.backend.blockNextGeneration()
            val cancelledEvents = Collections.synchronizedList(mutableListOf<GenerationEvent>())
            val cancelledTerminal = CountDownLatch(1)
            val handle = fixture.runtime.generate(
                fixture.request("cancelled", session),
                fixture.terminalListener(cancelledEvents, cancelledTerminal),
            )
            assertTrue(fixture.backend.awaitGenerationStart())
            handle.cancel()
            assertTrue(cancelledTerminal.await(2, TimeUnit.SECONDS))
            assertTrue((cancelledEvents.last() as GenerationEvent.Failed).error is LocalLlmError.Cancelled)
            assertEquals(1, fixture.backend.cancelCalls)

            fixture.backend.useSuccessfulGeneration()
            val recoveredEvents = fixture.generateAndAwait("recovered", session)
            assertTrue(recoveredEvents.last() is GenerationEvent.Completed)
            assertEquals("simulated ready", (recoveredEvents.last() as GenerationEvent.Completed).output)

            fixture.runtime.closeSession(session)
            assertTrue(eventually { fixture.runtime.runtimeSnapshot().activeSessions == 0 })
            assertEquals(3, fixture.backend.releaseContextCalls)

            val memoryResult = fixture.runtime.handleMemoryPressure(RuntimeMemoryPressure.UI_HIDDEN)
            assertEquals(RuntimeMemoryAction.UNLOAD_IDLE_MODEL, memoryResult.action)
            assertTrue(memoryResult.modelUnloaded)
            assertFalse(memoryResult.deferred)
            assertNull(fixture.runtime.runtimeSnapshot().loadedModel)

            assertTrue(fixture.runtime.prepare(fixture.applicationId, fixture.useCaseId).ready)
            assertEquals(2, fixture.backend.loadCalls)

            fixture.runtime.close()
            assertEquals(2, fixture.backend.unloadCalls)
            assertEquals(1, fixture.backend.shutdownCalls)
            assertEquals(0, fixture.runtime.runtimeSnapshot().activeSessions)
            assertEquals(0, fixture.runtime.runtimeSnapshot().queuedRequests)
        } finally {
            fixture.close()
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

private class SimulatedAcceptanceFixture {
    val applicationId = ApplicationId("simulated-app")
    val useCaseId = UseCaseId("simulated-generation")
    private val temporaryDirectory = Files.createTempDirectory("phase1-simulated-acceptance").toFile()
    val sourceModel = File(temporaryDirectory, "source.gguf").apply {
        writeBytes("simulated-gguf-payload".toByteArray())
    }
    val digest = sha256(sourceModel)
    val artifact = GgufArtifact(
        digest = digest,
        fileName = "simulated.gguf",
        sizeBytes = sourceModel.length(),
        architecture = "qwen2",
        quantization = "Q4_K_M",
        source = ArtifactSource.Imported("simulated acceptance model"),
    )
    val store = FileSystemModelStore(File(temporaryDirectory, "model-store"))
    val backend = SimulatedAcceptanceBackend()
    private val resolvedUseCase = resolvedUseCase()
    val runtime = RuntimeOrchestrator(
        registry = SimulatedAcceptanceRegistry(resolvedUseCase),
        modelStore = store,
        backend = backend,
    )

    fun request(id: String, sessionId: SessionId): GenerationRequest = GenerationRequest(
        requestId = RequestId(id),
        sessionId = sessionId,
        applicationId = applicationId,
        useCaseId = useCaseId,
        input = "Return a deterministic simulated response.",
    )

    fun generateAndAwait(id: String, sessionId: SessionId): List<GenerationEvent> {
        val events = Collections.synchronizedList(mutableListOf<GenerationEvent>())
        val terminal = CountDownLatch(1)
        runtime.generate(request(id, sessionId), terminalListener(events, terminal))
        check(terminal.await(2, TimeUnit.SECONDS)) { "Generation $id did not terminate" }
        return events.toList()
    }

    fun terminalListener(events: MutableList<GenerationEvent>, terminal: CountDownLatch): GenerationListener = GenerationListener { event ->
        events += event
        if (event is GenerationEvent.Completed || event is GenerationEvent.Failed) {
            terminal.countDown()
        }
    }

    fun close() {
        backend.releaseBlockedGeneration()
        runtime.close()
        temporaryDirectory.deleteRecursively()
    }

    private fun resolvedUseCase(): ResolvedUseCase {
        val model = GgufModelProfile(
            id = "simulated-model-profile",
            artifact = artifact,
            contextSize = 512,
            batchSize = 128,
            microBatchSize = 64,
            cpuThreads = 2,
            batchThreads = 2,
            gpuLayers = 0,
        )
        val useCase = UseCaseProfile(
            id = "simulated-use-case-profile",
            modelProfileId = model.id,
            systemPromptVersion = "v1",
            generationDefaults = GenerationDefaults(
                maxOutputTokens = 16,
                temperature = 0f,
                topP = 1f,
                topK = 0,
                seed = 42,
            ),
            outputMode = OutputMode.TEXT,
            cachePolicy = UseCaseCachePolicy(0, false, false, false),
            healthSuiteId = "simulated-health",
        )
        return ResolvedUseCase(
            binding = AppModelBinding(applicationId, useCaseId, useCase.id),
            useCase = useCase,
            model = model,
        )
    }

    private fun sha256(file: File): ModelDigest {
        val digestBytes = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        return ModelDigest(digestBytes.joinToString("") { byte -> "%02x".format(byte) })
    }
}

private class SimulatedAcceptanceRegistry(private val resolved: ResolvedUseCase) : ModelProfileRegistry {
    override fun resolve(applicationId: ApplicationId, useCaseId: UseCaseId): ResolvedUseCase {
        require(applicationId == resolved.binding.applicationId) { "Unknown application" }
        require(useCaseId == resolved.binding.useCaseId) { "Unknown use case" }
        return resolved
    }
}

private data class SimulatedAcceptanceModel(
    override val digest: ModelDigest,
    override val profileId: String,
    override val loadDurationMs: Long = 4,
) : BackendModelHandle

private data class SimulatedAcceptanceContext(override val model: BackendModelHandle, override val contextSize: Int) : BackendContextHandle

private class SimulatedAcceptanceBackend : InferenceBackend {
    override val id: String = "simulated-llama-backend"
    var loadCalls: Int = 0
    var unloadCalls: Int = 0
    var releaseContextCalls: Int = 0
    var shutdownCalls: Int = 0
    var cancelCalls: Int = 0

    @Volatile
    private var blockGeneration: Boolean = false

    @Volatile
    private var generationStarted = CountDownLatch(1)

    @Volatile
    private var generationRelease = CountDownLatch(0)

    private val cancellationRequested = AtomicBoolean(false)

    override fun initialize() = Unit

    override fun shutdown() {
        shutdownCalls += 1
    }

    override fun loadModel(storedModel: StoredModel, profile: GgufModelProfile): BackendModelHandle {
        loadCalls += 1
        return SimulatedAcceptanceModel(storedModel.digest, profile.id)
    }

    override fun unloadModel(model: BackendModelHandle) {
        unloadCalls += 1
    }

    override fun modelCapabilities(model: BackendModelHandle) = BackendModelCapabilities(4_096, true)

    override fun planPrompt(model: BackendModelHandle, request: BackendPromptPlanningRequest) = fakePromptPlan(request)

    override fun createContext(
        model: BackendModelHandle,
        profile: GgufModelProfile,
        configuration: BackendContextConfiguration,
    ): BackendContextHandle = SimulatedAcceptanceContext(model, configuration.contextSize)

    override fun releaseContext(context: BackendContextHandle) {
        releaseContextCalls += 1
    }

    override fun generate(
        context: BackendContextHandle,
        request: BackendGenerationRequest,
        onChunk: (text: String, generatedTokens: Int) -> Boolean,
    ): BackendGenerationOutcome {
        generationStarted.countDown()
        if (blockGeneration) {
            generationRelease.await(2, TimeUnit.SECONDS)
            if (cancellationRequested.get()) {
                return BackendGenerationOutcome.Cancelled(
                    BackendGenerationMetrics(4, 0, 2, 1),
                )
            }
        }
        if (!onChunk("simulated ", 1)) {
            return BackendGenerationOutcome.Cancelled(BackendGenerationMetrics(4, 0, 2, 1))
        }
        if (!onChunk("ready", 2)) {
            return BackendGenerationOutcome.Cancelled(BackendGenerationMetrics(4, 1, 2, 2))
        }
        return BackendGenerationOutcome.Completed(
            BackendGenerationMetrics(
                inputTokens = 4,
                outputTokens = 2,
                promptDurationMs = 2,
                generationDurationMs = 4,
            ),
        )
    }

    override fun cancel(requestId: String): Boolean {
        cancelCalls += 1
        cancellationRequested.set(true)
        generationRelease.countDown()
        return true
    }

    fun blockNextGeneration() {
        blockGeneration = true
        cancellationRequested.set(false)
        generationStarted = CountDownLatch(1)
        generationRelease = CountDownLatch(1)
    }

    fun useSuccessfulGeneration() {
        blockGeneration = false
        cancellationRequested.set(false)
        generationStarted = CountDownLatch(1)
        generationRelease = CountDownLatch(0)
    }

    fun awaitGenerationStart(): Boolean = generationStarted.await(2, TimeUnit.SECONDS)

    fun releaseBlockedGeneration() {
        generationRelease.countDown()
    }
}
