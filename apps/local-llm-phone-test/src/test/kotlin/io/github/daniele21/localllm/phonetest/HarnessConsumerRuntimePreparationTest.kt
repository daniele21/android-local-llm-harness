package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.models.AppModelBinding
import io.github.daniele21.localllm.models.ArtifactSource
import io.github.daniele21.localllm.models.GenerationDefaults
import io.github.daniele21.localllm.models.GgufArtifact
import io.github.daniele21.localllm.models.GgufModelProfile
import io.github.daniele21.localllm.models.OutputMode
import io.github.daniele21.localllm.models.ResolvedUseCase
import io.github.daniele21.localllm.models.UseCaseCachePolicy
import io.github.daniele21.localllm.models.UseCaseProfile
import io.github.daniele21.localllm.runtime.BackendContextConfiguration
import io.github.daniele21.localllm.runtime.BackendContextHandle
import io.github.daniele21.localllm.runtime.BackendGenerationOutcome
import io.github.daniele21.localllm.runtime.BackendGenerationRequest
import io.github.daniele21.localllm.runtime.BackendModelCapabilities
import io.github.daniele21.localllm.runtime.BackendModelHandle
import io.github.daniele21.localllm.runtime.BackendModelSource
import io.github.daniele21.localllm.runtime.BackendPromptPlan
import io.github.daniele21.localllm.runtime.BackendPromptPlanningRequest
import io.github.daniele21.localllm.runtime.InferenceBackend
import io.github.daniele21.localllm.runtime.RuntimeOrchestrator
import io.github.daniele21.localllm.runtime.UseCaseActivationId
import io.github.daniele21.localllm.store.ModelStore
import io.github.daniele21.localllm.store.ModelStoreSnapshot
import io.github.daniele21.localllm.store.StoredModel
import io.github.daniele21.localllm.store.VerificationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HarnessConsumerRuntimePreparationTest {
    @Test
    fun `consumer prepare cold loads exact activation model then reuses it`() {
        val fixture = ConsumerRuntimePreparationFixture()
        fixture.activate(fixture.activationA, fixture.resolvedA)

        val first = fixture.client.prepare(fixture.applicationId, fixture.useCaseId)
        val second = fixture.client.prepare(fixture.applicationId, fixture.useCaseId)

        assertTrue(first.ready)
        assertTrue(second.ready)
        assertEquals(fixture.digestA, first.modelDigest)
        assertEquals(listOf(fixture.digestA), fixture.backend.loadedDigests)
        assertEquals(1, fixture.store.verificationCallsByDigest.getValue(fixture.digestA))
        assertEquals(fixture.digestA, fixture.client.runtimeSnapshot().loadedModel)
        fixture.close()
    }

    @Test
    fun `consumer prepare blocks activation model switch until session cleanup then switches exactly`() {
        val fixture = ConsumerRuntimePreparationFixture()
        fixture.activate(fixture.activationA, fixture.resolvedA)
        assertTrue(fixture.client.prepare(fixture.applicationId, fixture.useCaseId).ready)
        val session = fixture.client.createSession(fixture.applicationId, fixture.useCaseId)

        fixture.activate(fixture.activationB, fixture.resolvedB)
        val blocked = fixture.client.prepare(fixture.applicationId, fixture.useCaseId)

        assertFalse(blocked.ready)
        assertTrue(blocked.detail.contains("Cannot switch model"))
        assertEquals(listOf(fixture.digestA), fixture.backend.loadedDigests)
        assertEquals(0, fixture.backend.unloadCalls)

        fixture.client.closeSession(session)
        val switched = fixture.client.prepare(fixture.applicationId, fixture.useCaseId)

        assertTrue(switched.ready)
        assertEquals(fixture.digestB, switched.modelDigest)
        assertEquals(listOf(fixture.digestA, fixture.digestB), fixture.backend.loadedDigests)
        assertEquals(1, fixture.backend.unloadCalls)
        assertEquals(fixture.digestB, fixture.client.runtimeSnapshot().loadedModel)
        fixture.close()
    }

    @Test
    fun `consumer prepare fails closed when exact activation model is not installed`() {
        val fixture = ConsumerRuntimePreparationFixture()
        fixture.activate(fixture.activationMissing, fixture.resolvedMissing)

        val missing = fixture.client.prepare(fixture.applicationId, fixture.useCaseId)

        assertFalse(missing.ready)
        assertEquals(null, missing.modelDigest)
        assertTrue(fixture.backend.loadedDigests.isEmpty())
        assertFalse(fixture.store.verificationCallsByDigest.containsKey(fixture.digestMissing))
        assertEquals(null, fixture.client.runtimeSnapshot().loadedModel)
        fixture.close()
    }

    @Test
    fun `consumer prepare fails closed after activation binding is released without reloading`() {
        val fixture = ConsumerRuntimePreparationFixture()
        fixture.activate(fixture.activationA, fixture.resolvedA)
        assertTrue(fixture.client.prepare(fixture.applicationId, fixture.useCaseId).ready)

        fixture.registry.removeActivationBinding(fixture.activationA)
        val released = fixture.client.prepare(fixture.applicationId, fixture.useCaseId)

        assertFalse(released.ready)
        assertTrue(released.detail.contains("active Harness control-plane activation"))
        assertEquals(listOf(fixture.digestA), fixture.backend.loadedDigests)
        assertEquals(fixture.digestA, fixture.client.runtimeSnapshot().loadedModel)
        fixture.close()
    }
}

private class ConsumerRuntimePreparationFixture {
    val applicationId = ApplicationId("consumer.runtime.preparation")
    val useCaseId = UseCaseId("document-pii-detection")
    val activationA = UseCaseActivationId("activation-a")
    val activationB = UseCaseActivationId("activation-b")
    val activationMissing = UseCaseActivationId("activation-missing")
    val digestA = ModelDigest("a".repeat(64))
    val digestB = ModelDigest("b".repeat(64))
    val digestMissing = ModelDigest("c".repeat(64))

    private val modelFile = File.createTempFile("consumer-runtime-preparation", ".gguf").apply {
        writeText("model")
        deleteOnExit()
    }
    val registry = HarnessPhoneBindingRegistry()
    val store = ConsumerRuntimePreparationModelStore(modelFile, setOf(digestA, digestB))
    val backend = ConsumerRuntimePreparationBackend()
    val resolvedA = resolved("profile-a", digestA)
    val resolvedB = resolved("profile-b", digestB)
    val resolvedMissing = resolved("profile-missing", digestMissing)

    private var runtime: RuntimeOrchestrator? = null
    val client = HarnessSharedRuntimeClient(
        activeClient = { runtime },
        prepareClient = {
            runtime ?: RuntimeOrchestrator(registry, store, backend).also { runtime = it }
        },
    )

    fun activate(activationId: UseCaseActivationId, resolved: ResolvedUseCase) {
        registry.installActivationBinding(
            activationId = activationId,
            applicationId = applicationId,
            useCaseId = useCaseId,
            resolved = resolved,
        )
    }

    fun close() {
        runtime?.close()
        modelFile.delete()
    }

    private fun resolved(profileId: String, digest: ModelDigest): ResolvedUseCase {
        val model = GgufModelProfile(
            id = profileId,
            artifact = GgufArtifact(
                digest = digest,
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
            id = "consumer-runtime-preparation-use-case",
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

private class ConsumerRuntimePreparationModelStore(private val file: File, private val available: Set<ModelDigest>) : ModelStore {
    val verificationCallsByDigest = mutableMapOf<ModelDigest, Int>()

    override fun find(digest: ModelDigest): StoredModel? =
        digest.takeIf { it in available }?.let { StoredModel(it, file, file.length(), verified = false) }

    override fun import(source: File, artifact: GgufArtifact): StoredModel = error("Runtime preparation must not import model bytes")

    override fun verify(digest: ModelDigest): VerificationResult {
        verificationCallsByDigest[digest] = verificationCallsByDigest.getOrDefault(digest, 0) + 1
        return VerificationResult(true, digest, "valid")
    }

    override fun remove(digest: ModelDigest): Boolean = error("Runtime preparation must not remove model bytes")

    override fun snapshot(): ModelStoreSnapshot = ModelStoreSnapshot(0, 0, emptyList())
}

private data class ConsumerRuntimePreparationModel(
    override val digest: ModelDigest,
    override val profileId: String,
    override val loadDurationMs: Long = 1,
) : BackendModelHandle

private class ConsumerRuntimePreparationBackend : InferenceBackend {
    override val id: String = "consumer-runtime-preparation"
    val loadedDigests = mutableListOf<ModelDigest>()
    var unloadCalls = 0

    override fun initialize() = Unit

    override fun shutdown() = Unit

    override fun loadModel(source: BackendModelSource, profile: GgufModelProfile): BackendModelHandle {
        loadedDigests += source.digest
        return ConsumerRuntimePreparationModel(source.digest, profile.id)
    }

    override fun unloadModel(model: BackendModelHandle) {
        unloadCalls += 1
    }

    override fun modelCapabilities(model: BackendModelHandle) = BackendModelCapabilities(
        maximumContextTokens = 4_096,
        supportsGrammar = true,
    )

    override fun planPrompt(model: BackendModelHandle, request: BackendPromptPlanningRequest): BackendPromptPlan =
        error("Generation is outside CRV runtime preparation coverage")

    override fun createContext(
        model: BackendModelHandle,
        profile: GgufModelProfile,
        configuration: BackendContextConfiguration,
    ): BackendContextHandle = error("Generation is outside CRV runtime preparation coverage")

    override fun releaseContext(context: BackendContextHandle) = Unit

    override fun generate(
        context: BackendContextHandle,
        request: BackendGenerationRequest,
        onChunk: (text: String, generatedTokens: Int) -> Boolean,
    ): BackendGenerationOutcome = error("Generation is outside CRV runtime preparation coverage")

    override fun cancel(requestId: String): Boolean = false
}
