package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ModelDigest
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
import java.util.concurrent.atomic.AtomicInteger

class RuntimeModelResidencyIntegrationTest {
    @Test
    fun `failed load leaves runtime non resident and retry can succeed`() {
        val backend = DeterministicFakeInferenceBackend().apply {
            loadFailure = FakeBackendFailure("LOAD_FAILED", "synthetic load failure")
        }
        val fixture = ModelResidencyRuntimeFixture(backend)

        val first = fixture.runtime.prepare(fixture.applicationId, fixture.primaryUseCaseId)

        assertFalse(first.ready)
        assertNull(fixture.runtime.runtimeSnapshot().loadedModel)
        assertFalse(fixture.runtime.memoryResourceSnapshot().modelLoaded)
        assertEquals(1, backend.loadCalls)

        backend.loadFailure = null
        val retry = fixture.runtime.prepare(fixture.applicationId, fixture.primaryUseCaseId)

        assertTrue(retry.ready)
        assertEquals(fixture.primaryDigest, fixture.runtime.runtimeSnapshot().loadedModel)
        assertTrue(fixture.runtime.memoryResourceSnapshot().modelLoaded)
        assertEquals(2, backend.loadCalls)
        assertEquals(1, backend.initializeCalls)
        fixture.close()
    }

    @Test
    fun `failed unload keeps the same model resident and repeated unload retries`() {
        val delegate = DeterministicFakeInferenceBackend()
        val backend = UnloadFailOnceBackend(delegate)
        val fixture = ModelResidencyRuntimeFixture(backend)
        assertTrue(fixture.runtime.prepare(fixture.applicationId, fixture.primaryUseCaseId).ready)

        val first = runCatching { fixture.runtime.unloadIdleModel() }

        assertTrue(first.isFailure)
        assertEquals(fixture.primaryDigest, fixture.runtime.runtimeSnapshot().loadedModel)
        assertTrue(fixture.runtime.memoryResourceSnapshot().modelLoaded)
        assertEquals(1, backend.unloadAttempts.get())
        assertEquals(0, delegate.unloadCalls)

        assertTrue(fixture.runtime.unloadIdleModel())

        assertNull(fixture.runtime.runtimeSnapshot().loadedModel)
        assertFalse(fixture.runtime.memoryResourceSnapshot().modelLoaded)
        assertEquals(2, backend.unloadAttempts.get())
        assertEquals(1, delegate.unloadCalls)
        fixture.close()
    }

    @Test
    fun `model switch unloads previous resident model before loading replacement`() {
        val delegate = DeterministicFakeInferenceBackend()
        val backend = RecordingResidencyBackend(delegate)
        val fixture = ModelResidencyRuntimeFixture(backend)

        assertTrue(fixture.runtime.prepare(fixture.applicationId, fixture.primaryUseCaseId).ready)
        assertTrue(fixture.runtime.prepare(fixture.applicationId, fixture.secondaryUseCaseId).ready)

        assertEquals(
            listOf(
                "load:${fixture.primaryDigest.sha256}",
                "unload:${fixture.primaryDigest.sha256}",
                "load:${fixture.secondaryDigest.sha256}",
            ),
            backend.events,
        )
        assertEquals(fixture.secondaryDigest, fixture.runtime.runtimeSnapshot().loadedModel)
        assertEquals(2, delegate.loadCalls)
        assertEquals(1, delegate.unloadCalls)
        fixture.close()
    }

    @Test
    fun `runtime close unloads resident model before backend shutdown and stays idempotent`() {
        val delegate = DeterministicFakeInferenceBackend()
        val backend = RecordingResidencyBackend(delegate)
        val fixture = ModelResidencyRuntimeFixture(backend)
        assertTrue(fixture.runtime.prepare(fixture.applicationId, fixture.primaryUseCaseId).ready)

        fixture.runtime.close()
        fixture.runtime.close()

        assertNull(fixture.runtime.runtimeSnapshot().loadedModel)
        assertEquals(
            listOf(
                "load:${fixture.primaryDigest.sha256}",
                "unload:${fixture.primaryDigest.sha256}",
                "shutdown",
            ),
            backend.events,
        )
        assertEquals(1, delegate.unloadCalls)
        assertEquals(1, delegate.shutdownCalls)
        fixture.deleteModelFile()
    }
}

private class UnloadFailOnceBackend(
    private val delegate: DeterministicFakeInferenceBackend,
) : InferenceBackend by delegate {
    val unloadAttempts = AtomicInteger(0)

    override fun unloadModel(model: BackendModelHandle) {
        if (unloadAttempts.incrementAndGet() == 1) {
            throw BackendException("UNLOAD_FAILED", "synthetic unload failure")
        }
        delegate.unloadModel(model)
    }
}

private class RecordingResidencyBackend(
    private val delegate: DeterministicFakeInferenceBackend,
) : InferenceBackend by delegate {
    val events = mutableListOf<String>()

    override fun loadModel(source: BackendModelSource, profile: GgufModelProfile): BackendModelHandle {
        events += "load:${source.digest.sha256}"
        return delegate.loadModel(source, profile)
    }

    override fun unloadModel(model: BackendModelHandle) {
        events += "unload:${model.digest.sha256}"
        delegate.unloadModel(model)
    }

    override fun shutdown() {
        events += "shutdown"
        delegate.shutdown()
    }
}

private class ModelResidencyRuntimeFixture(backend: InferenceBackend) {
    val applicationId = ApplicationId("residency-app")
    val primaryUseCaseId = UseCaseId("primary")
    val secondaryUseCaseId = UseCaseId("secondary")
    val primaryDigest = ModelDigest("a".repeat(64))
    val secondaryDigest = ModelDigest("b".repeat(64))
    private val modelFile = File.createTempFile("residency-model", ".gguf").apply {
        writeText("model")
        deleteOnExit()
    }
    private val primary = resolvedUseCase(primaryUseCaseId, "residency-profile-a", primaryDigest)
    private val secondary = resolvedUseCase(secondaryUseCaseId, "residency-profile-b", secondaryDigest)
    private val registry = object : ModelProfileRegistry {
        override fun resolve(applicationId: ApplicationId, useCaseId: UseCaseId): ResolvedUseCase =
            when (useCaseId) {
                primaryUseCaseId -> primary
                secondaryUseCaseId -> secondary
                else -> error("Unknown use case ${useCaseId.value}")
            }
    }
    val runtime = RuntimeOrchestrator(
        registry = registry,
        modelStore = ResidencyModelStore(modelFile, setOf(primaryDigest, secondaryDigest)),
        backend = backend,
    )

    fun close() {
        runtime.close()
        deleteModelFile()
    }

    fun deleteModelFile() {
        modelFile.delete()
    }

    private fun resolvedUseCase(useCaseId: UseCaseId, profileId: String, digest: ModelDigest): ResolvedUseCase {
        val model = GgufModelProfile(
            id = profileId,
            artifact = GgufArtifact(
                digest = digest,
                fileName = "$profileId.gguf",
                sizeBytes = modelFile.length(),
                architecture = "qwen2",
                quantization = "Q4_K_M",
                source = ArtifactSource.Imported("residency-test"),
            ),
            contextSize = 512,
            batchSize = 128,
            microBatchSize = 64,
            cpuThreads = 2,
            batchThreads = 2,
            gpuLayers = 0,
        )
        val useCase = UseCaseProfile(
            id = "$profileId-use-case",
            modelProfileId = profileId,
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

private class ResidencyModelStore(
    private val file: File,
    private val digests: Set<ModelDigest>,
) : ModelStore {
    override fun find(digest: ModelDigest): StoredModel? = if (digest in digests) {
        StoredModel(digest, file, file.length(), verified = true)
    } else {
        null
    }

    override fun import(source: File, artifact: GgufArtifact): StoredModel = error("Not used")

    override fun verify(digest: ModelDigest): VerificationResult = VerificationResult(true, digest, "valid")

    override fun remove(digest: ModelDigest): Boolean = false

    override fun snapshot(): ModelStoreSnapshot = ModelStoreSnapshot(0, 0, emptyList())
}
