package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.GenerationInput
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.models.ArtifactSource
import io.github.daniele21.localllm.models.GgufArtifact
import io.github.daniele21.localllm.models.GgufModelProfile
import io.github.daniele21.localllm.store.StoredModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DeterministicFakeInferenceBackendTest {
    @Test
    fun `fake exposes deterministic lifecycle streaming cancellation and failure controls`() {
        val backend = DeterministicFakeInferenceBackend()
        val profile = profile()
        val stored = StoredModel(profile.artifact.digest, File("fake.gguf"), 4, verified = true)

        backend.initialize()
        val model = backend.loadModel(stored, profile)
        val plan = backend.planPrompt(
            model,
            BackendPromptPlanningRequest(
                input = GenerationInput.RawCompletion("hello"),
                systemPrompt = null,
                chatTemplatePolicy = profile.chatTemplatePolicy,
            ),
        )
        val context = backend.createContext(model, profile, BackendContextConfiguration(512))
        val chunks = mutableListOf<String>()
        val outcome = backend.generate(
            context,
            generationRequest(plan.prompt),
        ) { text, _ ->
            chunks += text
            true
        }

        assertTrue(outcome is BackendGenerationOutcome.Completed)
        assertEquals(listOf("deterministic ", "response"), chunks)
        assertTrue(backend.cancel("request-1"))
        backend.releaseContext(context)
        backend.unloadModel(model)
        backend.shutdown()

        assertEquals(1, backend.initializeCalls)
        assertEquals(1, backend.loadCalls)
        assertEquals(1, backend.createContextCalls)
        assertEquals(1, backend.generateCalls)
        assertEquals(1, backend.cancelCalls)
        assertEquals(1, backend.releaseContextCalls)
        assertEquals(1, backend.unloadCalls)
        assertEquals(1, backend.shutdownCalls)

        backend.generationFailure = FakeBackendFailure("TEST_FAILURE", "controlled failure")
        val secondModel = backend.loadModel(stored, profile)
        val secondContext = backend.createContext(secondModel, profile, BackendContextConfiguration(512))
        val failure = runCatching {
            backend.generate(secondContext, generationRequest("prompt")) { _, _ -> true }
        }.exceptionOrNull()
        assertTrue(failure is BackendException)
        assertEquals("TEST_FAILURE", (failure as BackendException).code)
    }

    private fun generationRequest(prompt: String) = BackendGenerationRequest(
        requestId = "request-1",
        prompt = prompt,
        maxOutputTokens = 8,
        temperature = 0f,
        topP = 1f,
        topK = 0,
        repeatPenalty = 1f,
        repeatLastN = 0,
        seed = 42,
    )

    private fun profile(): GgufModelProfile {
        val digest = ModelDigest("a".repeat(64))
        return GgufModelProfile(
            id = "fake-profile",
            artifact = GgufArtifact(
                digest = digest,
                fileName = "fake.gguf",
                sizeBytes = 4,
                architecture = "qwen35",
                quantization = "Q4_K_M",
                source = ArtifactSource.Imported("fake"),
            ),
            contextSize = 512,
            batchSize = 64,
            microBatchSize = 32,
            cpuThreads = 2,
            batchThreads = 2,
            gpuLayers = 0,
        )
    }
}
