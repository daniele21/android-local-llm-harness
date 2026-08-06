package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.GenerationEvent
import io.github.daniele21.localllm.contracts.GenerationListener
import io.github.daniele21.localllm.contracts.GenerationRequest
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.ModelLoadKind
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
import io.github.daniele21.localllm.observability.store.InMemoryTelemetryRepository
import io.github.daniele21.localllm.store.ModelStore
import io.github.daniele21.localllm.store.ModelStoreSnapshot
import io.github.daniele21.localllm.store.StoredModel
import io.github.daniele21.localllm.store.VerificationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RuntimeLoadClassificationTest {
    @Test
    fun `first session is cold and following session is warm`() {
        val fixture = LoadClassificationFixture()

        val firstSession = fixture.runtime.createSession(fixture.applicationId, fixture.useCaseId)
        val first = fixture.generate(firstSession, "cold")
        fixture.runtime.closeSession(firstSession)

        val secondSession = fixture.runtime.createSession(fixture.applicationId, fixture.useCaseId)
        val second = fixture.generate(secondSession, "warm")

        assertEquals(ModelLoadKind.COLD, first.metrics.modelLoadKind)
        assertEquals(25L, first.metrics.modelLoadMs)
        assertEquals(ModelLoadKind.WARM, second.metrics.modelLoadKind)
        assertNull(second.metrics.modelLoadMs)
        assertEquals(1, fixture.backend.loadCalls)
        assertEquals(ModelLoadKind.COLD, fixture.repository.findRun(RequestId("cold"))?.modelLoadKind)
        assertEquals(ModelLoadKind.WARM, fixture.repository.findRun(RequestId("warm"))?.modelLoadKind)
        fixture.close(secondSession)
    }
}

private class LoadClassificationFixture {
    val applicationId = ApplicationId("app")
    val useCaseId = UseCaseId("assistant")
    private val digest = ModelDigest("a".repeat(64))
    private val modelFile = File.createTempFile("load-classification", ".gguf").apply {
        writeText("model")
        deleteOnExit()
    }
    val backend = LoadClassificationBackend()
    val repository = InMemoryTelemetryRepository()
    private val resolved = resolvedUseCase()
    private val registry = object : ModelProfileRegistry {
        override fun resolve(applicationId: ApplicationId, useCaseId: UseCaseId): ResolvedUseCase = resolved
    }
    val runtime = RuntimeOrchestrator(
        registry = registry,
        modelStore = LoadClassificationStore(modelFile, digest),
        backend = backend,
        telemetryRepository = repository,
    )

    fun generate(sessionId: SessionId, id: String): GenerationEvent.Completed {
        val terminal = CountDownLatch(1)
        var result: GenerationEvent.Completed? = null
        runtime.generate(
            GenerationRequest(
                requestId = RequestId(id),
                sessionId = sessionId,
                applicationId = applicationId,
                useCaseId = useCaseId,
                input = "prompt",
            ),
            GenerationListener { event ->
                if (event is GenerationEvent.Completed) {
                    result = event
                    terminal.countDown()
                }
            },
        )
        check(terminal.await(2, TimeUnit.SECONDS)) { "Generation did not complete" }
        return requireNotNull(result)
    }

    fun close(sessionId: SessionId) {
        runtime.closeSession(sessionId)
        runtime.close()
        modelFile.delete()
    }

    private fun resolvedUseCase(): ResolvedUseCase {
        val model = GgufModelProfile(
            id = "profile",
            artifact = GgufArtifact(
                digest = digest,
                fileName = "model.gguf",
                sizeBytes = modelFile.length(),
                architecture = "qwen2",
                quantization = "Q4_K_M",
                source = ArtifactSource.Imported("test"),
            ),
            contextSize = 512,
            batchSize = 128,
            microBatchSize = 64,
            cpuThreads = 2,
            batchThreads = 2,
            gpuLayers = 0,
        )
        val useCase = UseCaseProfile(
            id = "assistant-use-case",
            modelProfileId = model.id,
            systemPromptVersion = "v1",
            generationDefaults = GenerationDefaults(8, 0f, 1f, 0, 0L),
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

private class LoadClassificationStore(private val file: File, private val digest: ModelDigest) : ModelStore {
    override fun find(digest: ModelDigest): StoredModel? =
        if (digest == this.digest) StoredModel(digest, file, file.length(), verified = true) else null

    override fun import(source: File, artifact: GgufArtifact): StoredModel = error("Not used")

    override fun verify(digest: ModelDigest): VerificationResult = VerificationResult(true, digest, "valid")

    override fun remove(digest: ModelDigest): Boolean = false

    override fun snapshot(): ModelStoreSnapshot = ModelStoreSnapshot(1, file.length(), listOf(requireNotNull(find(digest))))
}

private data class LoadClassificationModel(
    override val digest: ModelDigest,
    override val profileId: String,
    override val loadDurationMs: Long = 25L,
) : BackendModelHandle

private data class LoadClassificationContext(override val model: BackendModelHandle, override val contextSize: Int) : BackendContextHandle

private class LoadClassificationBackend : InferenceBackend {
    override val id: String = "classification"
    var loadCalls = 0

    override fun initialize() = Unit

    override fun shutdown() = Unit

    override fun loadModel(storedModel: StoredModel, profile: GgufModelProfile): BackendModelHandle {
        loadCalls += 1
        return LoadClassificationModel(storedModel.digest, profile.id)
    }

    override fun unloadModel(model: BackendModelHandle) = Unit

    override fun modelCapabilities(model: BackendModelHandle) = BackendModelCapabilities(4_096, true)

    override fun planPrompt(model: BackendModelHandle, request: BackendPromptPlanningRequest) = fakePromptPlan(request)

    override fun createContext(
        model: BackendModelHandle,
        profile: GgufModelProfile,
        configuration: BackendContextConfiguration,
    ): BackendContextHandle = LoadClassificationContext(model, configuration.contextSize)

    override fun releaseContext(context: BackendContextHandle) = Unit

    override fun generate(
        context: BackendContextHandle,
        request: BackendGenerationRequest,
        onChunk: (text: String, generatedTokens: Int) -> Boolean,
    ): BackendGenerationOutcome {
        onChunk("ok", 1)
        return BackendGenerationOutcome.Completed(
            BackendGenerationMetrics(
                inputTokens = 1,
                outputTokens = 1,
                promptDurationMs = 2,
                generationDurationMs = 3,
            ),
        )
    }

    override fun cancel(requestId: String): Boolean = true
}
