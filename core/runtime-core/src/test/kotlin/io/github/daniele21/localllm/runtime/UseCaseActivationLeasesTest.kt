package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.UseCaseId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UseCaseActivationLeasesTest {
    @Test
    fun `activation pins resolved execution revision identity`() {
        val registry = registry("activation-1")

        val lease = (registry.acquire(request()) as ActivationLeaseResult.Success).value

        assertEquals(UseCaseActivationId("activation-1"), lease.activationId)
        assertEquals(PRESET, lease.preset)
        assertEquals(3, lease.presetRevision)
        assertEquals(4, lease.useCaseRevision)
        assertEquals(7, lease.bindingRevision)
        assertEquals(MODEL, lease.modelDigest)
    }

    @Test
    fun `one owner cannot release another owners activation`() {
        val registry = registry("activation-1")
        val lease = (registry.acquire(request(ownerId = OWNER_A)) as ActivationLeaseResult.Success).value

        val release = registry.release(lease.activationId, OWNER_B)

        assertEquals(ActivationLeaseFailure.NOT_OWNED, (release as ActivationLeaseResult.Failure).reason)
        assertEquals(1, registry.activeCount)
    }

    @Test
    fun `connection death cleanup releases only owned activations`() {
        val ids = ArrayDeque(listOf("activation-a", "activation-b"))
        val registry = UseCaseActivationLeaseRegistry(ActivationIdFactory { UseCaseActivationId(ids.removeFirst()) })
        registry.acquire(request(ownerId = OWNER_A))
        registry.acquire(request(ownerId = OWNER_B))

        val released = registry.releaseAll(OWNER_A)

        assertEquals(listOf(UseCaseActivationId("activation-a")), released.map { it.activationId })
        assertTrue(registry.activeForOwner(OWNER_A).isEmpty())
        assertEquals(1, registry.activeForOwner(OWNER_B).size)
    }

    @Test
    fun `multiple owners may hold leases for the same resolved model`() {
        val ids = ArrayDeque(listOf("activation-a", "activation-b"))
        val registry = UseCaseActivationLeaseRegistry(ActivationIdFactory { UseCaseActivationId(ids.removeFirst()) })
        registry.acquire(request(ownerId = OWNER_A))
        registry.acquire(request(ownerId = OWNER_B))

        assertEquals(2, registry.activeForModel(MODEL).size)
    }

    @Test
    fun `bounded lease registry rejects activation at capacity`() {
        val ids = ArrayDeque(listOf("activation-a", "activation-b"))
        val registry = UseCaseActivationLeaseRegistry(
            idFactory = ActivationIdFactory { UseCaseActivationId(ids.removeFirst()) },
            maxActiveLeases = 1,
        )
        registry.acquire(request(ownerId = OWNER_A))

        val second = registry.acquire(request(ownerId = OWNER_B))

        assertEquals(ActivationLeaseFailure.CAPACITY_REACHED, (second as ActivationLeaseResult.Failure).reason)
        assertEquals(1, registry.activeCount)
    }

    @Test
    fun `activation id collision fails without replacing the existing lease`() {
        val registry = registry("same-id")
        registry.acquire(request(ownerId = OWNER_A))

        val duplicate = registry.acquire(request(ownerId = OWNER_B))

        assertEquals(ActivationLeaseFailure.ID_COLLISION, (duplicate as ActivationLeaseResult.Failure).reason)
        assertEquals(OWNER_A, registry.find(UseCaseActivationId("same-id"))?.ownerId)
    }

    private fun registry(id: String): UseCaseActivationLeaseRegistry =
        UseCaseActivationLeaseRegistry(ActivationIdFactory { UseCaseActivationId(id) })

    private fun request(ownerId: ActivationOwnerId = OWNER_A): UseCaseActivationRequest = UseCaseActivationRequest(
        ownerId = ownerId,
        applicationId = ApplicationId("redactguard"),
        useCaseId = UseCaseId("document-pii-detection"),
        preset = PRESET,
        modelDigest = MODEL,
        acquiredAtEpochMs = 100,
        useCaseRevision = 4,
        bindingRevision = 7,
    )

    private companion object {
        val OWNER_A = ActivationOwnerId("owner-a")
        val OWNER_B = ActivationOwnerId("owner-b")
        val PRESET = InferencePresetRef(InferencePresetId("quality"), 3)
        val MODEL = ModelDigest("a".repeat(64))
    }
}
