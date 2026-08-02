package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.GenerationEvent
import io.github.daniele21.localllm.contracts.GenerationListener
import io.github.daniele21.localllm.contracts.GenerationRequest
import io.github.daniele21.localllm.contracts.LocalLlmError
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.RequestId
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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RuntimeOrchestratorTest {
    @Test
    fun `prepare verifies once and reuses loaded model`() {
        val fixture = RuntimeFixture()

        val first = fixture.runtime.prepare(fixture.applicationId, fixture.useCaseId)
        val second = fixture.runtime.prepare(fixture.applicationId, fixture.useCaseId)

        assertTrue(first.ready)
        assertTrue(second.ready)
        assertEquals(fixture.digest, first.modelDigest)
        assertEquals(1, fixture.store.verificationCalls)
        assertEquals(1, fixture.backend.initializeCalls)
        assertEquals(1, fixture.backend.loadCalls)
        fixture.close()
    }

    @Test
    fun `generation emits ordered streaming events and complete metrics`() {
        val fixture = RuntimeFixture()
        val session = fixture.runtime.createSession(fixture.applicationId, fixture.useCaseId)
        val events = Collections.synchronizedList(mutableListOf<GenerationEvent>())
        val terminal = CountDownLatch(1)

        fixture.runtime.generate(
            fixture.request("generation", session),
            GenerationListener { event ->
                events += event
                if (event is GenerationEvent.Completed || event is GenerationEvent.Failed) terminal.countDown()
            },
        )

        assertTrue(terminal.await(2, TimeUnit.SECONDS))
        assertEquals(
            listOf("Queued", "Started", "TextDelta", "TextDelta", "Completed"),
            events.map { it::class.simpleName },
        )
        val completed = events.last() as GenerationEvent.Completed
        assertEquals("hello world", completed.output)
        assertEquals(2, completed.metrics.inputTokens)
        assertEquals(2, completed.metrics.outputTokens)
        assertEquals(200.0, completed.metrics.decodeTokensPerSecond!!, 0.001)
        fixture.close()
    }

    @Test
    fun `queued cancellation never starts backend generation`() {
        val fixture = RuntimeFixture()
        val session = fixture.runtime.createSession(fixture.applicationId, fixture.useCaseId)
        fixture.backend.blockGeneration = CountDownLatch(1)
        fixture.runtime.generate(
            fixture.request("active", session),
            GenerationListener {},
        )
        assertTrue(fixture.backend.generationStarted.await(2, TimeUnit.SECONDS))

        val events = Collections.synchronizedList(mutableListOf<GenerationEvent>())
        val terminal = CountDownLatch(1)
        val queued = fixture.runtime.generate(
            fixture.request("queued", session),
            GenerationListener { event ->
                events += event
                if (event is GenerationEvent.Failed) terminal.countDown()
            },
        )
        queued.cancel()

        assertTrue(terminal.await(2, TimeUnit.SECONDS))
        assertTrue(events.first() is GenerationEvent.Queued)
        assertTrue((events.last() as GenerationEvent.Failed).error is LocalLlmError.Cancelled)
        assertFalse(events.any { it is GenerationEvent.Started })
        fixture.backend.blockGeneration?.countDown()
        fixture.close()
    }

    @Test
    fun `running cancellation delegates to backend and returns cancelled terminal`() {
        val fixture = RuntimeFixture()
        val session = fixture.runtime.createSession(fixture.applicationId, fixture.useCaseId)
        fixture.backend.blockGeneration = CountDownLatch(1)
        val terminal = CountDownLatch(1)
        val events = Collections.synchronizedList(mutableListOf<GenerationEvent>())

        val handle = fixture.runtime.generate(
            fixture.request("running", session),
            GenerationListener { event ->
                events += event
                if (event is GenerationEvent.Failed) terminal.countDown()
            },
        )
        assertTrue(fixture.backend.generationStarted.await(2, TimeUnit.SECONDS))
        handle.cancel()

        assertTrue(terminal.await(2, TimeUnit.SECONDS))
        assertEquals(1, fixture.backend.cancelCalls)
        assertTrue((events.last() as GenerationEvent.Failed).error is LocalLlmError.Cancelled)
        fixture.close()
    }

    @Test
    fun `closing session defers context release until active request terminates`() {
        val fixture = RuntimeFixture()
        val session = fixture.runtime.createSession(fixture.applicationId, fixture.useCaseId)
        fixture.backend.blockGeneration = CountDownLatch(1)
        val terminal = CountDownLatch(1)

        fixture.runtime.generate(
            fixture.request("deferred-close", session),
            GenerationListener { event ->
                if (event is GenerationEvent.Completed || event is GenerationEvent.Failed) terminal.countDown()
            },
        )
        assertTrue(fixture.backend.generationStarted.await(2, TimeUnit.SECONDS))
        fixture.runtime.closeSession(session)
        assertEquals(0, fixture.backend.releaseCalls)

        fixture.backend.blockGeneration?.countDown()
        assertTrue(terminal.await(2, TimeUnit.SECONDS))
        assertTrue(eventually { fixture.backend.releaseCalls == 1 })
        fixture.close()
    }

    @Test
    fun `model cannot switch while session is active`() {
        val fixture = RuntimeFixture()
        fixture.runtime.createSession(fixture.applicationId, fixture.useCaseId)

        val result = fixture.runtime.prepare(fixture.applicationId, fixture.secondUseCaseId)

        assertFalse(result.ready)
        assertTrue(result.detail.contains("Cannot switch model"))
        assertEquals(1, fixture.backend.loadCalls)
        fixture.close()
    }

    @Test
    fun `idle model can be unloaded after session close`() {
        val fixture = RuntimeFixture()
        val session = fixture.runtime.createSession(fixture.applicationId, fixture.useCaseId)
        fixture.runtime.closeSession(session)

        assertTrue(eventually { fixture.runtime.runtimeSnapshot().activeSessions == 0 })
        assertTrue(fixture.runtime.unloadIdleModel())
        assertEquals(1, fixture.backend.unloadCalls)
        fixture.close()
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

private class RuntimeFixture {
    val applicationId = ApplicationId("app")
    val useCaseId = UseCaseId("primary")
    val secondUseCaseId = UseCaseId("secondary")
    val digest = ModelDigest("a".repeat(64))
    private val secondDigest = ModelDigest("b".repeat(64))
    private val modelFile = File.createTempFile("runtime-model", ".gguf").apply {
        writeText("model")
        deleteOnExit()
    }
    val store = FakeRuntimeModelStore(modelFile, setOf(digest, secondDigest))
    val backend = FakeInferenceBackend()
    private val registry = FakeRuntimeRegistry(
        primary = resolved(useCaseId, "profile-a", digest),
        secondary = resolved(secondUseCaseId, "profile-b", secondDigest),
    )
    val runtime = RuntimeOrchestrator(registry, store, backend)

    fun request(id: String, sessionId: io.github.daniele21.localllm.contracts.SessionId): GenerationRequest = GenerationRequest(
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

    private fun resolved(useCaseId: UseCaseId, profileId: String, modelDigest: ModelDigest): ResolvedUseCase {
        val model = GgufModelProfile(
            id = profileId,
            artifact = GgufArtifact(
                digest = modelDigest,
                fileName = "$profileId.gguf",
                sizeBytes = modelFile.length(),
                architecture = "qwen2",
                quantization = "Q4_K_M",
                source = ArtifactSource.Imported(profileId),
            ),
            contextSize = 512,
            batchSize = 128,
            microBatchSize = 64,
            cpuThreads = 2,
            batchThreads = 2,
            gpuLayers = 0,
        )
        val useCase = UseCaseProfile(
            id = "use-case-${useCaseId.value}",
            modelProfileId = profileId,
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
            healthSuiteId = "health",
        )
        return ResolvedUseCase(
            binding = AppModelBinding(applicationId, useCaseId, useCase.id),
            useCase = useCase,
            model = model,
        )
    }
}

private class FakeRuntimeRegistry(private val primary: ResolvedUseCase, private val secondary: ResolvedUseCase) : ModelProfileRegistry {
    override fun resolve(applicationId: ApplicationId, useCaseId: UseCaseId): ResolvedUseCase =
        if (useCaseId == primary.binding.useCaseId) primary else secondary
}

private class FakeRuntimeModelStore(private val file: File, private val available: Set<ModelDigest>) : ModelStore {
    var verificationCalls: Int = 0

    override fun find(digest: ModelDigest): StoredModel? = if (digest in available) {
        StoredModel(digest, file, file.length(), verified = false)
    } else {
        null
    }

    override fun import(source: File, artifact: GgufArtifact): StoredModel = error("Not used")

    override fun verify(digest: ModelDigest): VerificationResult {
        verificationCalls += 1
        return VerificationResult(true, digest, "valid")
    }

    override fun remove(digest: ModelDigest): Boolean = false

    override fun snapshot(): ModelStoreSnapshot = ModelStoreSnapshot(0, 0, emptyList())
}

private data class FakeBackendModel(
    override val digest: ModelDigest,
    override val profileId: String,
    override val loadDurationMs: Long = 12,
) : BackendModelHandle

private data class FakeBackendContext(override val model: BackendModelHandle) : BackendContextHandle

private class FakeInferenceBackend : InferenceBackend {
    override val id: String = "fake"
    var initializeCalls: Int = 0
    var loadCalls: Int = 0
    var unloadCalls: Int = 0
    var releaseCalls: Int = 0
    var cancelCalls: Int = 0
    var blockGeneration: CountDownLatch? = null
    var generationStarted: CountDownLatch = CountDownLatch(1)

    override fun initialize() {
        initializeCalls += 1
    }

    override fun shutdown() = Unit

    override fun loadModel(storedModel: StoredModel, profile: GgufModelProfile): BackendModelHandle {
        loadCalls += 1
        return FakeBackendModel(storedModel.digest, profile.id)
    }

    override fun unloadModel(model: BackendModelHandle) {
        unloadCalls += 1
    }

    override fun createContext(model: BackendModelHandle, profile: GgufModelProfile): BackendContextHandle = FakeBackendContext(model)

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
        if (!onChunk("hello ", 1)) {
            return BackendGenerationOutcome.Cancelled(BackendGenerationMetrics(2, 0, 5, 1))
        }
        if (!onChunk("world", 2)) {
            return BackendGenerationOutcome.Cancelled(BackendGenerationMetrics(2, 1, 5, 5))
        }
        return BackendGenerationOutcome.Completed(
            BackendGenerationMetrics(
                inputTokens = 2,
                outputTokens = 2,
                promptDurationMs = 5,
                generationDurationMs = 10,
            ),
        )
    }

    override fun cancel(requestId: String): Boolean {
        cancelCalls += 1
        blockGeneration?.countDown()
        return true
    }
}
