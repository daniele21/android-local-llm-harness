package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.models.GgufModelProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivationResidencyInferenceBackendTest {
    private val digest = ModelDigest("d".repeat(64))

    @Test
    fun `matching protected activation rejects physical backend unload`() {
        val coordinator = coordinator()
        val delegate = RecordingBackend()
        val backend = ActivationResidencyInferenceBackend(delegate, coordinator)
        val handle = ResidencyBackendTestModelHandle(digest, PROFILE_A)
        coordinator.acquire(request(PROFILE_A), retainModelWarmMs = 30_000)

        val failure = runCatching { backend.unloadModel(handle) }.exceptionOrNull()

        assertTrue(failure is BackendException)
        assertEquals(ActivationResidencyInferenceBackend.MODEL_PROTECTED_CODE, (failure as BackendException).code)
        assertFalse(delegate.unloaded)
    }

    @Test
    fun `same digest activation for another profile allows stale profile unload`() {
        val coordinator = coordinator()
        val delegate = RecordingBackend()
        val backend = ActivationResidencyInferenceBackend(delegate, coordinator)
        val staleHandle = ResidencyBackendTestModelHandle(digest, PROFILE_A)
        coordinator.acquire(request(PROFILE_B), retainModelWarmMs = 30_000)

        backend.unloadModel(staleHandle)

        assertTrue(delegate.unloaded)
    }

    @Test
    fun `final release allows physical backend unload`() {
        val coordinator = coordinator()
        val delegate = RecordingBackend()
        val backend = ActivationResidencyInferenceBackend(delegate, coordinator)
        val handle = ResidencyBackendTestModelHandle(digest, PROFILE_A)
        val acquired = coordinator.acquire(request(PROFILE_A), retainModelWarmMs = 30_000) as ActivationResidencyResult.Success
        coordinator.release(acquired.value.activationId, acquired.value.ownerId)

        backend.unloadModel(handle)

        assertTrue(delegate.unloaded)
    }

    private fun coordinator(): ActivationResidencyCoordinator = ActivationResidencyCoordinator(
        UseCaseActivationLeaseRegistry(ActivationIdFactory { UseCaseActivationId("activation-1") }),
    )

    private fun request(modelProfileId: String): UseCaseActivationRequest = UseCaseActivationRequest(
        ownerId = ActivationOwnerId("owner-a"),
        applicationId = ApplicationId("redactguard"),
        useCaseId = UseCaseId("document-pii-detection"),
        preset = InferencePresetRef(InferencePresetId("balanced"), 3),
        modelDigest = digest,
        modelProfileId = modelProfileId,
        acquiredAtEpochMs = 1_000,
        useCaseRevision = 2,
        bindingRevision = 7,
    )

    private companion object {
        const val PROFILE_A = "profile-a"
        const val PROFILE_B = "profile-b"
    }
}

private data class ResidencyBackendTestModelHandle(
    override val digest: ModelDigest,
    override val profileId: String,
    override val loadDurationMs: Long = 1,
) : BackendModelHandle

@Suppress("TooManyFunctions")
private class RecordingBackend : InferenceBackend {
    override val id = "recording"
    var unloaded = false

    override fun initialize() = Unit

    override fun shutdown() = Unit

    override fun loadModel(source: BackendModelSource, profile: GgufModelProfile): BackendModelHandle = error("Not used")

    override fun unloadModel(model: BackendModelHandle) {
        unloaded = true
    }

    override fun modelCapabilities(model: BackendModelHandle): BackendModelCapabilities = error("Not used")

    override fun planPrompt(model: BackendModelHandle, request: BackendPromptPlanningRequest): BackendPromptPlan = error("Not used")

    override fun createContext(
        model: BackendModelHandle,
        profile: GgufModelProfile,
        configuration: BackendContextConfiguration,
    ): BackendContextHandle = error("Not used")

    override fun releaseContext(context: BackendContextHandle) = Unit

    override fun generate(
        context: BackendContextHandle,
        request: BackendGenerationRequest,
        onChunk: (text: String, generatedTokens: Int) -> Boolean,
    ): BackendGenerationOutcome = error("Not used")

    override fun cancel(requestId: String): Boolean = false
}
