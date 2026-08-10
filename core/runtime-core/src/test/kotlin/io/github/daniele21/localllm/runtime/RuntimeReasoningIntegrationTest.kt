package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.GenerationContentType
import io.github.daniele21.localllm.contracts.GenerationEvent
import io.github.daniele21.localllm.contracts.GenerationListener
import io.github.daniele21.localllm.contracts.GenerationRequest
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.ThinkingMode
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.models.AppModelBinding
import io.github.daniele21.localllm.models.ArtifactSource
import io.github.daniele21.localllm.models.GenerationDefaults
import io.github.daniele21.localllm.models.GenerationGuardPolicy
import io.github.daniele21.localllm.models.GgufArtifact
import io.github.daniele21.localllm.models.GgufModelProfile
import io.github.daniele21.localllm.models.ModelProfileRegistry
import io.github.daniele21.localllm.models.OutputMode
import io.github.daniele21.localllm.models.ReasoningStreamProtocol
import io.github.daniele21.localllm.models.ResolvedUseCase
import io.github.daniele21.localllm.models.UseCaseCachePolicy
import io.github.daniele21.localllm.models.UseCaseProfile
import io.github.daniele21.localllm.store.ModelStore
import io.github.daniele21.localllm.store.ModelStoreSnapshot
import io.github.daniele21.localllm.store.StoredModel
import io.github.daniele21.localllm.store.VerificationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RuntimeReasoningIntegrationTest {
    @Test
    fun `Qwen reasoning is streamed separately and native budget preserves final answer`() {
        val fixture = ReasoningRuntimeFixture()
        try {
            val session = fixture.runtime.createSession(fixture.applicationId, fixture.useCaseId)
            val events = Collections.synchronizedList(mutableListOf<GenerationEvent>())
            val terminal = CountDownLatch(1)

            fixture.runtime.generate(
                fixture.request(session),
                GenerationListener { event ->
                    events += event
                    if (event is GenerationEvent.Completed || event is GenerationEvent.Failed) terminal.countDown()
                },
            )

            assertTrue(terminal.await(2, TimeUnit.SECONDS))
            val request = requireNotNull(fixture.backend.lastGenerationRequest)
            val control = requireNotNull(request.reasoningControl)
            assertEquals(4, control.maxReasoningTokens)
            assertEquals("</think>", control.closeMarker)
            assertEquals("</think>\n\n", control.forcedCloseText)

            val deltas = events.filterIsInstance<GenerationEvent.TextDelta>()
            assertEquals(
                listOf(GenerationContentType.REASONING, GenerationContentType.ANSWER),
                deltas.map(GenerationEvent.TextDelta::contentType).distinct(),
            )
            assertFalse(deltas.any { "</think>" in it.text || "<think>" in it.text })

            val completed = events.last() as GenerationEvent.Completed
            assertEquals("analysis", completed.reasoningOutput)
            assertEquals("\n\nfinal answer", completed.answerOutput)
            assertEquals("analysis</think>\n\nfinal answer", completed.output)
            assertEquals(4, completed.metrics.reasoningTokens)
            assertEquals(3, completed.metrics.answerTokens)
            assertNotNull(completed.metrics.timeToFirstAnswerMs)
        } finally {
            fixture.close()
        }
    }
}

private class ReasoningRuntimeFixture {
    val applicationId = ApplicationId("reasoning-app")
    val useCaseId = UseCaseId("reasoning-use-case")
    private val digest = ModelDigest("c".repeat(64))
    private val modelFile = File.createTempFile("reasoning-runtime", ".gguf").apply {
        writeText("model")
        deleteOnExit()
    }
    private val artifact = GgufArtifact(
        digest = digest,
        fileName = "reasoning.gguf",
        sizeBytes = modelFile.length(),
        architecture = "qwen2",
        quantization = "Q4_K_M",
        source = ArtifactSource.Imported("reasoning-test"),
    )
    private val model = GgufModelProfile(
        id = "reasoning-model",
        artifact = artifact,
        contextSize = 1_024,
        batchSize = 128,
        microBatchSize = 64,
        cpuThreads = 2,
        batchThreads = 2,
        gpuLayers = 0,
    )
    private val useCase = UseCaseProfile(
        id = "reasoning-profile",
        modelProfileId = model.id,
        systemPromptVersion = "v1",
        generationDefaults = GenerationDefaults(
            maxOutputTokens = 16,
            temperature = 1f,
            topP = 0.95f,
            topK = 20,
            thinkingMode = ThinkingMode.ENABLED,
            guardPolicy = GenerationGuardPolicy(
                enabled = true,
                thinkingTokenBudget = 4,
                repetitionActivationTokens = 8,
                observationWindowChars = 512,
                minPatternChars = 24,
                maxPatternChars = 64,
                repetitionOccurrences = 4,
                answerReserveTokens = 8,
            ),
            reasoningStreamProtocol = ReasoningStreamProtocol.QWEN35_THINK_TAGS,
        ),
        outputMode = OutputMode.TEXT,
        cachePolicy = UseCaseCachePolicy(0, false, false, false),
        healthSuiteId = "reasoning-health",
    )
    private val resolved = ResolvedUseCase(
        binding = AppModelBinding(applicationId, useCaseId, useCase.id),
        useCase = useCase,
        model = model,
    )
    private val store = ReasoningModelStore(modelFile, digest)
    val backend = ReasoningBackend()
    val runtime = RuntimeOrchestrator(
        registry = ReasoningRegistry(resolved),
        modelStore = store,
        backend = backend,
    )

    fun request(sessionId: SessionId) = GenerationRequest(
        requestId = RequestId("reasoning-request"),
        sessionId = sessionId,
        applicationId = applicationId,
        useCaseId = useCaseId,
        input = "Explain briefly.",
    )

    fun close() {
        runtime.close()
        modelFile.delete()
    }
}

private class ReasoningRegistry(private val resolved: ResolvedUseCase) : ModelProfileRegistry {
    override fun resolve(applicationId: ApplicationId, useCaseId: UseCaseId): ResolvedUseCase = resolved.also {
        require(applicationId == it.binding.applicationId)
        require(useCaseId == it.binding.useCaseId)
    }
}

private class ReasoningModelStore(private val modelFile: File, private val digest: ModelDigest) : ModelStore {
    override fun find(digest: ModelDigest): StoredModel? = digest.takeIf { it == this.digest }?.let {
        StoredModel(it, modelFile, modelFile.length(), verified = true)
    }

    override fun import(source: File, artifact: GgufArtifact): StoredModel = error("Not used")

    override fun verify(digest: ModelDigest): VerificationResult = VerificationResult(
        valid = digest == this.digest,
        actualDigest = digest,
        detail = "valid",
    )

    override fun remove(digest: ModelDigest): Boolean = false

    override fun snapshot(): ModelStoreSnapshot = ModelStoreSnapshot(0, 0, emptyList())
}

private data class ReasoningBackendModel(
    override val digest: ModelDigest,
    override val profileId: String,
    override val loadDurationMs: Long = 1,
) : BackendModelHandle

private data class ReasoningBackendContext(
    override val model: BackendModelHandle,
    override val contextSize: Int,
) : BackendContextHandle

private class ReasoningBackend : InferenceBackend {
    override val id: String = "reasoning-backend"

    @Volatile
    var lastGenerationRequest: BackendGenerationRequest? = null

    override fun initialize() = Unit

    override fun shutdown() = Unit

    override fun loadModel(storedModel: StoredModel, profile: GgufModelProfile): BackendModelHandle =
        ReasoningBackendModel(storedModel.digest, profile.id)

    override fun unloadModel(model: BackendModelHandle) = Unit

    override fun modelCapabilities(model: BackendModelHandle) = BackendModelCapabilities(
        maximumContextTokens = 4_096,
        supportsGrammar = true,
        supportsReasoningTransition = true,
    )

    override fun planPrompt(model: BackendModelHandle, request: BackendPromptPlanningRequest): BackendPromptPlan =
        fakePromptPlan(request)

    override fun createContext(
        model: BackendModelHandle,
        profile: GgufModelProfile,
        configuration: BackendContextConfiguration,
    ): BackendContextHandle = ReasoningBackendContext(model, configuration.contextSize)

    override fun releaseContext(context: BackendContextHandle) = Unit

    override fun generate(
        context: BackendContextHandle,
        request: BackendGenerationRequest,
        onChunk: (text: String, generatedTokens: Int) -> Boolean,
    ): BackendGenerationOutcome {
        lastGenerationRequest = request
        check(onChunk("analysis</thi", 3))
        check(onChunk("nk>\n\nfinal answer", 7))
        return BackendGenerationOutcome.Completed(
            BackendGenerationMetrics(
                inputTokens = 4,
                outputTokens = 7,
                promptDurationMs = 2,
                generationDurationMs = 8,
                reasoningTokens = 4,
                answerTokens = 3,
            ),
        )
    }

    override fun cancel(requestId: String): Boolean = false
}
