package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ConfigurationErrorCode
import io.github.daniele21.localllm.contracts.GenerationEvent
import io.github.daniele21.localllm.contracts.GenerationListener
import io.github.daniele21.localllm.contracts.GenerationRequest
import io.github.daniele21.localllm.contracts.LocalLlmError
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.SessionOptions
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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class RuntimeMemoryAdmissionIntegrationTest {
    @Test
    fun `memory rejection fails before native context creation`() {
        val fixture = MemoryAdmissionRuntimeFixture(
            planner = memoryPlanner(
                observationSource = RuntimeMemoryObservationSource {
                    RuntimeMemoryObservation(availableMemoryBytes = 256, lowMemory = false)
                },
                peakBytes = 1_024,
            ),
        )
        val session = fixture.runtime.createSession(fixture.applicationId, fixture.useCaseId)
        val events = Collections.synchronizedList(mutableListOf<GenerationEvent>())
        val terminal = CountDownLatch(1)

        fixture.runtime.generate(
            fixture.request("memory-reject", session),
            GenerationListener { event ->
                events += event
                if (event is GenerationEvent.Failed || event is GenerationEvent.Completed) terminal.countDown()
            },
        )

        assertTrue(terminal.await(2, TimeUnit.SECONDS))
        val failure = events.last() as GenerationEvent.Failed
        val error = failure.error as LocalLlmError.Configuration
        assertEquals(ConfigurationErrorCode.MEMORY_BUDGET_EXCEEDED, error.reason)
        assertEquals(0, fixture.backend.createContextCalls)
        assertEquals(0, fixture.backend.generateCalls)
        fixture.close()
    }

    @Test
    fun `reused conversational context does not rerun memory admission`() {
        val observations = AtomicInteger(0)
        val fixture = MemoryAdmissionRuntimeFixture(
            planner = memoryPlanner(
                observationSource = RuntimeMemoryObservationSource {
                    if (observations.incrementAndGet() == 1) {
                        RuntimeMemoryObservation(availableMemoryBytes = 8_192, lowMemory = false)
                    } else {
                        null
                    }
                },
                peakBytes = 128,
            ),
        )
        val session = fixture.runtime.createSession(
            fixture.applicationId,
            fixture.useCaseId,
            SessionOptions(kind = SessionKind.CONVERSATIONAL),
        )

        assertTrue(fixture.generateAndAwait("first", session) is GenerationEvent.Completed)
        assertTrue(fixture.generateAndAwait("second", session) is GenerationEvent.Completed)
        assertEquals(1, observations.get())
        assertEquals(1, fixture.backend.createContextCalls)
        assertEquals(2, fixture.backend.generateCalls)
        fixture.close()
    }

    private fun memoryPlanner(observationSource: RuntimeMemoryObservationSource, peakBytes: Long): MemoryAwareContextPlanner =
        MemoryAwareContextPlanner(
            observationSource = observationSource,
            costEstimator = ContextMemoryCostEstimator { modelProfileId, contextTokens ->
                MemoryCostEstimate(
                    residentBytes = peakBytes / 2,
                    peakIncrementalBytes = peakBytes,
                    source = MemoryCostSource.CANDIDATE,
                    profileId = "$modelProfileId-$contextTokens",
                )
            },
            admissionController = MemoryAdmissionController(
                RuntimeMemoryBudget(
                    minimumAvailableBytes = 128,
                    safetyReserveBytes = 128,
                    maxResidentContexts = 4,
                ),
            ),
        )
}

private class MemoryAdmissionRuntimeFixture(planner: MemoryAwareContextPlanner) {
    val applicationId = ApplicationId("memory-app")
    val useCaseId = UseCaseId("memory-use-case")
    private val digest = ModelDigest("e".repeat(64))
    private val modelFile = File.createTempFile("memory-admission-model", ".gguf").apply {
        writeText("model")
        deleteOnExit()
    }
    val backend = DeterministicFakeInferenceBackend()
    private val resolved = resolvedUseCase()
    private val registry = object : ModelProfileRegistry {
        override fun resolve(applicationId: ApplicationId, useCaseId: UseCaseId): ResolvedUseCase = resolved
    }
    val runtime = RuntimeOrchestrator(
        registry = registry,
        modelStore = MemoryAdmissionModelStore(modelFile, digest),
        backend = backend,
        memoryAwareContextPlanner = planner,
    )

    fun request(id: String, sessionId: SessionId): GenerationRequest = GenerationRequest(
        requestId = RequestId(id),
        sessionId = sessionId,
        applicationId = applicationId,
        useCaseId = useCaseId,
        input = "prompt",
    )

    fun generateAndAwait(id: String, sessionId: SessionId): GenerationEvent {
        val terminal = CountDownLatch(1)
        var terminalEvent: GenerationEvent? = null
        runtime.generate(
            request(id, sessionId),
            GenerationListener { event ->
                if (event is GenerationEvent.Completed || event is GenerationEvent.Failed) {
                    terminalEvent = event
                    terminal.countDown()
                }
            },
        )
        check(terminal.await(2, TimeUnit.SECONDS)) { "Generation did not reach a terminal event" }
        return checkNotNull(terminalEvent)
    }

    fun close() {
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
                source = ArtifactSource.Imported("memory-test"),
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

private class MemoryAdmissionModelStore(private val file: File, private val digest: ModelDigest) : ModelStore {
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
