package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ConsumerActivationId
import io.github.daniele21.localllm.contracts.ConsumerControlPlaneErrorCode
import io.github.daniele21.localllm.contracts.ConsumerPreparationAction
import io.github.daniele21.localllm.contracts.ConsumerRuntimePhase
import io.github.daniele21.localllm.contracts.ConsumerRuntimeReadinessResult
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.RuntimeSnapshot
import io.github.daniele21.localllm.contracts.RuntimeState
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.runtime.ActivationIdFactory
import io.github.daniele21.localllm.runtime.ActivationOwnerId
import io.github.daniele21.localllm.runtime.ActivationResidencyCoordinator
import io.github.daniele21.localllm.runtime.ActivationResidencyResult
import io.github.daniele21.localllm.runtime.UseCaseActivationId
import io.github.daniele21.localllm.runtime.UseCaseActivationLeaseRegistry
import io.github.daniele21.localllm.runtime.UseCaseActivationRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessConsumerRuntimeReadinessHostTest {
    private val applicationId = ApplicationId("consumer-app")
    private val useCaseId = UseCaseId("document-pii-detection")
    private val ownerId = ActivationOwnerId("owner-1")
    private val activationId = UseCaseActivationId("activation-1")
    private val modelDigest = ModelDigest("a".repeat(64))
    private val otherDigest = ModelDigest("b".repeat(64))
    private var runtime: RuntimeSnapshot? = null
    private val residency = ActivationResidencyCoordinator(
        UseCaseActivationLeaseRegistry(ActivationIdFactory { activationId }),
    )
    private val host = HarnessConsumerRuntimeReadinessHost(residency) { runtime }

    init {
        val acquired = residency.acquireExclusiveUseCase(
            request =
            UseCaseActivationRequest(
                ownerId = ownerId,
                applicationId = applicationId,
                useCaseId = useCaseId,
                preset = InferencePresetRef(InferencePresetId("preset"), 1),
                modelDigest = modelDigest,
                acquiredAtEpochMs = 1L,
                useCaseRevision = 1,
                bindingRevision = 1,
            ),
            retainModelWarmMs = 0L,
        )
        check(acquired is ActivationResidencyResult.Success)
    }

    @Test
    fun `absent runtime is idle and observation has no preparation side effect`() {
        val result = available()

        assertEquals(ConsumerRuntimePhase.IDLE, result.readiness.phase)
        assertEquals(ConsumerPreparationAction.NONE, result.readiness.preparationAction)
        assertEquals(null, runtime)
    }

    @Test
    fun `preparing action follows source-backed resident model state`() {
        runtime = snapshot(RuntimeState.PREPARING, null)
        assertEquals(ConsumerPreparationAction.LOADING, available().readiness.preparationAction)

        runtime = snapshot(RuntimeState.PREPARING, modelDigest)
        assertEquals(ConsumerPreparationAction.REUSING, available().readiness.preparationAction)

        runtime = snapshot(RuntimeState.PREPARING, otherDigest)
        assertEquals(ConsumerPreparationAction.SWITCHING, available().readiness.preparationAction)
    }

    @Test
    fun `ready and generating require the exact activation model`() {
        runtime = snapshot(RuntimeState.READY, otherDigest)
        assertEquals(ConsumerRuntimePhase.IDLE, available().readiness.phase)

        runtime = snapshot(RuntimeState.READY, modelDigest)
        assertEquals(ConsumerRuntimePhase.READY, available().readiness.phase)

        runtime = snapshot(RuntimeState.GENERATING, modelDigest)
        assertEquals(ConsumerRuntimePhase.GENERATING, available().readiness.phase)
    }

    @Test
    fun `wrong owner or application cannot observe activation runtime`() {
        val wrongOwner = host.runtimeReadiness("owner-2", applicationId, ConsumerActivationId(activationId.value))
        val wrongApplication = host.runtimeReadiness(
            ownerId.value,
            ApplicationId("other-app"),
            ConsumerActivationId(activationId.value),
        )

        assertTrue(wrongOwner is ConsumerRuntimeReadinessResult.Rejected)
        assertTrue(wrongApplication is ConsumerRuntimeReadinessResult.Rejected)
        assertEquals(
            ConsumerControlPlaneErrorCode.INVALID_REQUEST,
            (wrongOwner as ConsumerRuntimeReadinessResult.Rejected).failure.code,
        )
    }

    private fun available(): ConsumerRuntimeReadinessResult.Available =
        host.runtimeReadiness(ownerId.value, applicationId, ConsumerActivationId(activationId.value))
            as ConsumerRuntimeReadinessResult.Available

    private fun snapshot(state: RuntimeState, loadedModel: ModelDigest?) = RuntimeSnapshot(
        state = state,
        loadedModel = loadedModel,
        activeSessions = 0,
        queuedRequests = 0,
    )
}
