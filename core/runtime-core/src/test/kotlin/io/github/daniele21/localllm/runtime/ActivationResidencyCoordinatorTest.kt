package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.UseCaseId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ActivationResidencyCoordinatorTest {
    private val firstDigest = ModelDigest("a".repeat(64))
    private val secondDigest = ModelDigest("b".repeat(64))

    @Test
    fun `compatible activations share one protected model`() {
        val coordinator = coordinator()

        val first = coordinator.acquire(request("owner-a", firstDigest), retainModelWarmMs = 30_000)
        val second = coordinator.acquire(request("owner-b", firstDigest), retainModelWarmMs = 60_000)

        assertTrue(first is ActivationResidencyResult.Success)
        assertTrue(second is ActivationResidencyResult.Success)
        assertTrue(coordinator.protects(firstDigest))
        assertEquals(2, coordinator.activeLeaseCount(firstDigest))
    }

    @Test
    fun `exclusive consumer activation rejects duplicate owner application use case`() {
        val coordinator = coordinator()

        val first = coordinator.acquireExclusiveUseCase(request("owner-a", firstDigest), retainModelWarmMs = 30_000)
        val duplicate = coordinator.acquireExclusiveUseCase(request("owner-a", firstDigest), retainModelWarmMs = 30_000)
        val otherOwner = coordinator.acquireExclusiveUseCase(request("owner-b", firstDigest), retainModelWarmMs = 30_000)

        assertTrue(first is ActivationResidencyResult.Success)
        duplicate as ActivationResidencyResult.Failure
        assertEquals(ActivationResidencyFailure.USE_CASE_ALREADY_ACTIVE, duplicate.reason)
        assertTrue(otherOwner is ActivationResidencyResult.Success)
        assertEquals(2, coordinator.activeLeaseCount(firstDigest))
    }

    @Test
    fun `different model activation fails while resident target is protected`() {
        val coordinator = coordinator()
        coordinator.acquire(request("owner-a", firstDigest), retainModelWarmMs = 30_000)

        val result = coordinator.acquire(request("owner-b", secondDigest), retainModelWarmMs = 30_000)

        result as ActivationResidencyResult.Failure
        assertEquals(ActivationResidencyFailure.MODEL_CONFLICT, result.reason)
        assertEquals(firstDigest, result.conflict?.protectedModelDigest)
        assertEquals(secondDigest, result.conflict?.requestedModelDigest)
        assertEquals(1, result.conflict?.activeLeaseCount)
        assertFalse(coordinator.canActivate(secondDigest))
    }

    @Test
    fun `releasing one shared lease keeps model protected without starting warm retention`() {
        val coordinator = coordinator()
        val first =
            coordinator.acquire(request("owner-a", firstDigest), retainModelWarmMs = 30_000) as ActivationResidencyResult.Success
        coordinator.acquire(request("owner-b", firstDigest), retainModelWarmMs = 60_000)

        val result =
            coordinator.release(first.value.activationId, first.value.ownerId) as ActivationResidencyResult.Success

        assertTrue(coordinator.protects(firstDigest))
        assertEquals(1, result.value.remainingLeaseCount)
        assertTrue(result.value.warmRetentionByModelMs.isEmpty())
    }

    @Test
    fun `final release removes protection and starts resolved warm retention`() {
        val coordinator = coordinator()
        val acquired =
            coordinator.acquire(request("owner-a", firstDigest), retainModelWarmMs = 45_000) as ActivationResidencyResult.Success

        val result =
            coordinator.release(acquired.value.activationId, acquired.value.ownerId) as ActivationResidencyResult.Success

        assertFalse(coordinator.protects(firstDigest))
        assertEquals(0, result.value.remainingLeaseCount)
        assertEquals(45_000L, result.value.warmRetentionByModelMs[firstDigest])
    }

    @Test
    fun `owner death keeps shared model protected until final owner disappears`() {
        val coordinator = coordinator()
        coordinator.acquire(request("owner-a", firstDigest), retainModelWarmMs = 30_000)
        coordinator.acquire(request("owner-b", firstDigest), retainModelWarmMs = 60_000)

        val firstRelease = coordinator.releaseAll(ActivationOwnerId("owner-a"))
        val finalRelease = coordinator.releaseAll(ActivationOwnerId("owner-b"))

        assertTrue(firstRelease.warmRetentionByModelMs.isEmpty())
        assertEquals(60_000L, finalRelease.warmRetentionByModelMs[firstDigest])
        assertFalse(coordinator.protects(firstDigest))
    }

    private fun coordinator(): ActivationResidencyCoordinator {
        val sequence = AtomicInteger()
        return ActivationResidencyCoordinator(
            UseCaseActivationLeaseRegistry(
                idFactory = ActivationIdFactory {
                    UseCaseActivationId("activation-${sequence.incrementAndGet()}")
                },
            ),
        )
    }

    private fun request(ownerId: String, digest: ModelDigest): UseCaseActivationRequest = UseCaseActivationRequest(
        ownerId = ActivationOwnerId(ownerId),
        applicationId = ApplicationId("application-$ownerId"),
        useCaseId = UseCaseId("document-analysis"),
        preset = InferencePresetRef(InferencePresetId("balanced"), 3),
        modelDigest = digest,
        acquiredAtEpochMs = 1_000,
        useCaseRevision = 2,
        bindingRevision = 7,
    )
}
