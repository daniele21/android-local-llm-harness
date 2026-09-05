package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.audit.InferenceAuditOrigin
import io.github.daniele21.localllm.audit.InferenceAuditOriginKind
import io.github.daniele21.localllm.audit.InferenceAuditResult
import io.github.daniele21.localllm.audit.store.InMemoryInferenceAuditRepository
import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.GenerationEvent
import io.github.daniele21.localllm.contracts.GenerationListener
import io.github.daniele21.localllm.contracts.GenerationRequest
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class InferenceAuditRuntimePromptBridgeTest {
    @Test
    fun `runtime rendered prompt is durably captured before Prepared is forwarded`() {
        val bridge = OneShotInferenceAuditEffectivePromptBridge()
        val repository = InMemoryInferenceAuditRepository()
        val fixture = PromptBridgeRuntimeFixture(bridge)
        val client = InferenceAuditLocalLlmClient(
            delegate = fixture.runtime,
            auditRepository = repository,
            effectivePromptResolver = bridge,
            originResolver = InferenceAuditOriginResolver { request ->
                InferenceAuditOrigin(
                    kind = InferenceAuditOriginKind.HARNEX_INTERNAL,
                    applicationId = request.applicationId,
                    useCaseId = request.useCaseId,
                )
            },
        )
        val sessionId = client.createSession(fixture.applicationId, fixture.useCaseId)
        val request = GenerationRequest(
            requestId = RequestId("prompt-bridge-request"),
            sessionId = sessionId,
            applicationId = fixture.applicationId,
            useCaseId = fixture.useCaseId,
            input = "user secret",
        )
        val terminal = CountDownLatch(1)
        val preparedWasDurable = AtomicBoolean(false)

        client.generate(
            request,
            GenerationListener { event ->
                if (event is GenerationEvent.Prepared) {
                    val record = (repository.find(request.requestId) as InferenceAuditResult.Success).value
                    preparedWasDurable.set(record?.prepared?.effectivePrompt == fixture.backend.lastRenderedPrompt)
                }
                if (event is GenerationEvent.Completed || event is GenerationEvent.Failed) {
                    terminal.countDown()
                }
            },
        )

        assertTrue(terminal.await(2, TimeUnit.SECONDS))
        val record = (repository.find(request.requestId) as InferenceAuditResult.Success).value
        assertTrue(preparedWasDurable.get())
        assertEquals(fixture.backend.lastRenderedPrompt, record?.prepared?.effectivePrompt)
        assertEquals("user secret", record?.prepared?.effectivePrompt)
        assertNull(bridge.consume(request.requestId))
        fixture.close()
    }

    @Test
    fun `bridge ignores unarmed prompts and fails closed if Prepared consumes before runtime publication`() {
        val bridge = OneShotInferenceAuditEffectivePromptBridge()
        val requestId = RequestId("one-shot")

        bridge.publish(requestId, "must-not-be-stored")
        assertNull(bridge.consume(requestId))

        bridge.expect(requestId)
        val failure = runCatching { bridge.consume(requestId) }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertNull(bridge.consume(requestId))
    }
}

private class PromptBridgeRuntimeFixture(bridge: InferenceAuditEffectivePromptSink) {
    val applicationId = ApplicationId("prompt-bridge-app")
    val useCaseId = UseCaseId("prompt-bridge-use-case")
    private val digest = ModelDigest("d".repeat(64))
    private val modelFile = File.createTempFile("prompt-bridge-model", ".gguf").apply {
        writeText("model")
        deleteOnExit()
    }
    private val resolved = resolvedUseCase()
    private val registry = object : ModelProfileRegistry {
        override fun resolve(applicationId: ApplicationId, useCaseId: UseCaseId): ResolvedUseCase {
            require(applicationId == this@PromptBridgeRuntimeFixture.applicationId)
            require(useCaseId == this@PromptBridgeRuntimeFixture.useCaseId)
            return resolved
        }
    }
    private val store = PromptBridgeModelStore(modelFile, digest)
    val backend = PromptBridgeInferenceBackend()
    val runtime = RuntimeOrchestrator(
        registry = registry,
        modelStore = store,
        backend = backend,
        effectivePromptSink = bridge,
    )

    fun close() {
        runtime.close()
        modelFile.delete()
    }

    private fun resolvedUseCase(): ResolvedUseCase {
        val model = GgufModelProfile(
            id = "prompt-bridge-model",
            artifact = GgufArtifact(
                digest = digest,
                fileName = "prompt-bridge.gguf",
                sizeBytes = modelFile.length(),
                architecture = "qwen2",
                quantization = "Q4_K_M",
                source = ArtifactSource.Imported("prompt-bridge-test"),
            ),
            contextSize = 512,
            batchSize = 128,
            microBatchSize = 64,
            cpuThreads = 2,
            batchThreads = 2,
            gpuLayers = 0,
        )
        val useCase = UseCaseProfile(
            id = "prompt-bridge-use-case-profile",
            modelProfileId = model.id,
            systemPromptVersion = "v1",
            generationDefaults = GenerationDefaults(
                maxOutputTokens = 8,
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

private class PromptBridgeModelStore(private val file: File, private val digest: ModelDigest) : ModelStore {
    override fun find(digest: ModelDigest): StoredModel? =
        if (digest == this.digest) StoredModel(digest, file, file.length(), verified = false) else null

    override fun import(source: File, artifact: GgufArtifact): StoredModel = error("Not used")

    override fun verify(digest: ModelDigest): VerificationResult = VerificationResult(true, digest, "valid")

    override fun remove(digest: ModelDigest): Boolean = false

    override fun snapshot(): ModelStoreSnapshot = ModelStoreSnapshot(0, 0, emptyList())
}

private data class PromptBridgeModelHandle(
    override val digest: ModelDigest,
    override val profileId: String,
    override val loadDurationMs: Long = 1,
) : BackendModelHandle

private data class PromptBridgeContextHandle(override val model: BackendModelHandle, override val contextSize: Int) : BackendContextHandle

private class PromptBridgeInferenceBackend : InferenceBackend {
    override val id: String = "prompt-bridge-fake"
    var lastRenderedPrompt: String? = null
        private set

    override fun initialize() = Unit

    override fun shutdown() = Unit

    override fun loadModel(source: BackendModelSource, profile: GgufModelProfile): BackendModelHandle =
        PromptBridgeModelHandle(source.digest, profile.id)

    override fun unloadModel(model: BackendModelHandle) = Unit

    override fun modelCapabilities(model: BackendModelHandle): BackendModelCapabilities = BackendModelCapabilities(4_096, true)

    override fun planPrompt(model: BackendModelHandle, request: BackendPromptPlanningRequest): BackendPromptPlan =
        fakePromptPlan(request).also { lastRenderedPrompt = it.prompt }

    override fun createContext(
        model: BackendModelHandle,
        profile: GgufModelProfile,
        configuration: BackendContextConfiguration,
    ): BackendContextHandle = PromptBridgeContextHandle(model, configuration.contextSize)

    override fun releaseContext(context: BackendContextHandle) = Unit

    override fun generate(
        context: BackendContextHandle,
        request: BackendGenerationRequest,
        onChunk: (text: String, generatedTokens: Int) -> Boolean,
    ): BackendGenerationOutcome {
        onChunk("answer", 1)
        return BackendGenerationOutcome.Completed(
            BackendGenerationMetrics(
                inputTokens = 2,
                outputTokens = 1,
                promptDurationMs = 1,
                generationDurationMs = 1,
            ),
        )
    }

    override fun cancel(requestId: String): Boolean = true
}
