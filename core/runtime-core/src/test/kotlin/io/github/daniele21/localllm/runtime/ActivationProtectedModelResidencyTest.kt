package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.UseCaseId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ActivationProtectedModelResidencyTest {
    private val digest = ModelDigest("c".repeat(64))

    @Test
    fun `normal unload cannot reserve model while activation protects its digest`() {
        val registry = UseCaseActivationLeaseRegistry(ActivationIdFactory { UseCaseActivationId("activation-1") })
        val activationResidency = ActivationResidencyCoordinator(registry)
        val lifecycle = ModelResidencyLifecycle(
            ModelResidencyProtection(activationResidency::protects),
        )
        val model = ResidentModel("profile-a", ActivationResidencyModelHandle(digest, "profile-a"))
        lifecycle.beginLoad(model.profileId, digest)
        lifecycle.loadSucceeded(model)
        activationResidency.acquire(request(), retainModelWarmMs = 30_000)

        assertNull(lifecycle.beginUnload())
        assertEquals(ModelResidencyState.RESIDENT, lifecycle.snapshot().state)
        assertSame(model, lifecycle.residentModelOrNull())
    }

    @Test
    fun `final activation release lets normal unload reserve the same resident handle`() {
        val registry = UseCaseActivationLeaseRegistry(ActivationIdFactory { UseCaseActivationId("activation-1") })
        val activationResidency = ActivationResidencyCoordinator(registry)
        val lifecycle = ModelResidencyLifecycle(
            ModelResidencyProtection(activationResidency::protects),
        )
        val model = ResidentModel("profile-a", ActivationResidencyModelHandle(digest, "profile-a"))
        lifecycle.beginLoad(model.profileId, digest)
        lifecycle.loadSucceeded(model)
        val acquired = activationResidency.acquire(request(), retainModelWarmMs = 30_000)
            as ActivationResidencyResult.Success

        activationResidency.release(acquired.value.activationId, acquired.value.ownerId)

        assertSame(model, lifecycle.beginUnload())
        assertEquals(ModelResidencyState.UNLOADING, lifecycle.snapshot().state)
    }

    private fun request(): UseCaseActivationRequest = UseCaseActivationRequest(
        ownerId = ActivationOwnerId("owner-a"),
        applicationId = ApplicationId("redactguard"),
        useCaseId = UseCaseId("document-pii-detection"),
        preset = InferencePresetRef(InferencePresetId("balanced"), 3),
        modelDigest = digest,
        acquiredAtEpochMs = 1_000,
        useCaseRevision = 2,
        bindingRevision = 7,
    )
}

private data class ActivationResidencyModelHandle(
    override val digest: ModelDigest,
    override val profileId: String,
    override val loadDurationMs: Long = 1,
) : BackendModelHandle
