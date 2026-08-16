package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.GenerationEvent
import io.github.daniele21.localllm.contracts.GenerationListener
import io.github.daniele21.localllm.contracts.GenerationRequest
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RuntimeShutdownLifecycleTest {
    @Test
    fun `close during active generation finalizes model and backend after cancellation drains`() {
        val fixture = ShutdownRuntimeFixture()
        val session = fixture.runtime.createSession(fixture.applicationId, fixture.useCaseId)
        val terminal = CountDownLatch(1)

        fixture.runtime.generate(
            fixture.request(session),
            GenerationListener { event ->
                if (event is GenerationEvent.Completed || event is GenerationEvent.Failed) terminal.countDown()
            },
        )
        assertTrue(fixture.backend.generationStarted.await(2, TimeUnit.SECONDS))

        fixture.runtime.close()

        assertEquals(1, fixture.backend.cancelCalls)
        assertEquals(0, fixture.backend.unloadCalls)
        assertEquals(0, fixture.backend.shutdownCalls)

        fixture.backend.releaseGeneration.countDown()

        assertTrue(terminal.await(2, TimeUnit.SECONDS))
        assertTrue(eventually { fixture.backend.releaseCalls == 1 })
        assertTrue(eventually { fixture.backend.unloadCalls == 1 })
        assertTrue(eventually { fixture.backend.shutdownCalls == 1 })
        assertTrue(eventually { fixture.runtime.runtimeSnapshot().activeSessions == 0 })
        assertNull(fixture.runtime.runtimeSnapshot().loadedModel)
        fixture.modelFile.delete()
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

private class ShutdownRuntimeFixture {
    val applicationId = ApplicationId("shutdown-app")
    val useCaseId = UseCaseId("shutdown-use-case")
    private val digest = ModelDigest("d".repeat(64))
    val modelFile = File.createTempFile("shutdown-runtime-model", ".gguf").apply {
        writeText("model")
        deleteOnExit()
    }
    val backend = BlockingShutdownBackend()
    private val resolved = resolvedUseCase()
    private val registry = object : ModelProfileRegistry {
        override fun resolve(applicationId: ApplicationId, useCaseId: UseCaseId): ResolvedUseCase = resolved
    }
    val runtime = RuntimeOrchestrator(
        registry = registry,
        modelStore = ShutdownModelStore(modelFile, digest),
        backend = backend,
    )

    fun request(sessionId: SessionId): GenerationRequest = GenerationRequest(
        requestId = RequestId("shutdown-request"),
        sessionId = sessionId,
        applicationId = applicationId,
        useCaseId = useCaseId,
        input = "prompt",
    )

    private fun resolvedUseCase(): ResolvedUseCase {
        val model = GgufModelProfile(
            id = "shutdown-profile",
            artifact = GgufArtifact(
                digest = digest,
                fileName = "shutdown.gguf",
                sizeBytes = modelFile.length(),
                architecture = "qwen2",
                quantization = "Q4_K_M",
                source = ArtifactSource.Imported("shutdown-test"),
            ),
            contextSize = 512,
            batchSize = 128,
            microBatchSize = 64,
            cpuThreads = 2,
            batchThreads = 2,
            gpuLayers = 0,
        )
        val useCase = UseCaseProfile(
            id = "shutdown-use-case-profile",
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

private class ShutdownModelStore(private val file: File, private val digest: ModelDigest) : ModelStore {
    override fun find(digest: ModelDigest): StoredModel? = if (digest == this.digest) {
        StoredModel(digest, file, file.length(), verified = true)
    } else {
        null
    }

    override fun import(source: File, artifact: GgufArtifact): StoredModel = error("Not used")

    override fun verify(digest: ModelDigest): VerificationResult = VerificationResult(true, digest, "valid")

    override fun remove(digest: ModelDigest): Boolean = false

    override fun snapshot(): ModelStoreSnapshot = ModelStoreSnapshot(0, 0, emptyList())
}

private data class ShutdownModelHandle(
    override val digest: ModelDigest,
    override val profileId: String,
    override val loadDurationMs: Long = 1,
) : BackendModelHandle

private data class ShutdownContextHandle(override val model: BackendModelHandle, override val contextSize: Int) : BackendContextHandle

private class BlockingShutdownBackend : InferenceBackend {
    override val id: String = "shutdown-fake"
    val generationStarted = CountDownLatch(1)
    val releaseGeneration = CountDownLatch(1)
    var cancelCalls = 0
    var releaseCalls = 0
    var unloadCalls = 0
    var shutdownCalls = 0

    override fun initialize() = Unit

    override fun shutdown() {
        shutdownCalls += 1
    }

    override fun loadModel(storedModel: StoredModel, profile: GgufModelProfile): BackendModelHandle =
        ShutdownModelHandle(storedModel.digest, profile.id)

    override fun unloadModel(model: BackendModelHandle) {
        unloadCalls += 1
    }

    override fun modelCapabilities(model: BackendModelHandle) = BackendModelCapabilities(4_096, true)

    override fun planPrompt(model: BackendModelHandle, request: BackendPromptPlanningRequest) = fakePromptPlan(request)

    override fun createContext(
        model: BackendModelHandle,
        profile: GgufModelProfile,
        configuration: BackendContextConfiguration,
    ): BackendContextHandle = ShutdownContextHandle(model, configuration.contextSize)

    override fun releaseContext(context: BackendContextHandle) {
        releaseCalls += 1
    }

    override fun generate(
        context: BackendContextHandle,
        request: BackendGenerationRequest,
        onChunk: (text: String, generatedTokens: Int) -> Boolean,
    ): BackendGenerationOutcome {
        generationStarted.countDown()
        awaitReleaseIgnoringInterrupts()
        return if (onChunk("result", 1)) {
            BackendGenerationOutcome.Completed(BackendGenerationMetrics(1, 1, 1, 1))
        } else {
            BackendGenerationOutcome.Cancelled(BackendGenerationMetrics(1, 0, 1, 1))
        }
    }

    override fun cancel(requestId: String): Boolean {
        cancelCalls += 1
        return true
    }

    private fun awaitReleaseIgnoringInterrupts() {
        while (releaseGeneration.count > 0) {
            try {
                releaseGeneration.await()
            } catch (_: InterruptedException) {
                // Keep the backend call alive so the test proves deferred cleanup after close() returns.
            }
        }
    }
}
