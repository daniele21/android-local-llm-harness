package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ConsumerCapabilityResult
import io.github.daniele21.localllm.contracts.ConsumerExecutionIdentity
import io.github.daniele21.localllm.contracts.ConsumerFailure
import io.github.daniele21.localllm.contracts.ConsumerGenerationEvent
import io.github.daniele21.localllm.contracts.ConsumerGenerationHandle
import io.github.daniele21.localllm.contracts.ConsumerGenerationInput
import io.github.daniele21.localllm.contracts.ConsumerGenerationListener
import io.github.daniele21.localllm.contracts.ConsumerGenerationRequest
import io.github.daniele21.localllm.contracts.ConsumerGenerationStartResult
import io.github.daniele21.localllm.contracts.ConsumerLocalLlmClient
import io.github.daniele21.localllm.contracts.ConsumerLogicalJobRequestId
import io.github.daniele21.localllm.contracts.ConsumerLogicalJobSubmitRequest
import io.github.daniele21.localllm.contracts.ConsumerOutputConstraint
import io.github.daniele21.localllm.contracts.ConsumerOutputConstraintKind
import io.github.daniele21.localllm.contracts.ConsumerPrepareRequest
import io.github.daniele21.localllm.contracts.ConsumerPrepareResult
import io.github.daniele21.localllm.contracts.ConsumerPreparedId
import io.github.daniele21.localllm.contracts.ConsumerSessionResult
import io.github.daniele21.localllm.contracts.ConsumerErrorCode
import io.github.daniele21.localllm.contracts.EffectiveConsumerReasoningMode
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.transport.binder.contract.ClientTokenParcel
import io.github.daniele21.localllm.transport.binder.contract.toConsumerLogicalJobWire
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostLogicalJobCoordinatorTest {
    private val useCaseId = UseCaseId("document-pii-detection")
    private val expectedExecution = execution("capability-a", 1)

    @Test
    fun `runtime prepared identity mismatch fails logical job before running`() {
        val registry = HostLogicalJobRegistry(
            maxJobs = 4,
            runtimeSessionId = HostRuntimeSessionId("runtime-A"),
            idFactory = { HostLogicalJobId("job-1") },
        )
        val coordinator = HostLogicalJobCoordinator(registry)
        val caller = caller()
        val request = request()

        val response = coordinator.submit(caller, MismatchingPreparedClient(), request)
        val snapshot = requireNotNull(response.snapshot)

        assertEquals(HostLogicalJobState.FAILED_FINAL.toWireTag(), snapshot.stateTag)
        assertEquals(expectedExecution.capabilityRevision, snapshot.execution.capabilityRevision)
        assertFalse(snapshot.resultAvailable)
    }

    @Test
    fun `runtime pressure fails active logical job and ignores late backend cancellation`() {
        val registry = HostLogicalJobRegistry(
            maxJobs = 4,
            runtimeSessionId = HostRuntimeSessionId("runtime-A"),
            idFactory = { HostLogicalJobId("job-1") },
        )
        val coordinator = HostLogicalJobCoordinator(registry)
        val client = RunningClient()
        val scope = HostLogicalJobScope(caller().applicationId, useCaseId)

        val submitted = coordinator.submit(caller(), client, request())
        assertEquals(HostLogicalJobState.RUNNING.toWireTag(), requireNotNull(submitted.snapshot).stateTag)

        assertEquals(1, coordinator.failActiveJobsForRuntimePressure())
        val failed = coordinator.result("operation-2", scope, HostLogicalJobId("job-1"))
        assertEquals(HostLogicalJobState.FAILED_FINAL.toWireTag(), requireNotNull(failed.snapshot).stateTag)
        assertTrue(client.cancelled)
        assertEquals(1, client.closedSessions)

        requireNotNull(client.listener).onEvent(
            ConsumerGenerationEvent.Failed(
                RequestId("logical:job-1"),
                ConsumerFailure(ConsumerErrorCode.CANCELLED, "Backend cancellation after pressure cleanup"),
            ),
        )
        val afterLateCancellation = coordinator.query("operation-3", scope, HostLogicalJobId("job-1"))
        assertEquals(
            HostLogicalJobState.FAILED_FINAL.toWireTag(),
            requireNotNull(afterLateCancellation.snapshot).stateTag,
        )
    }

    private fun caller() = AuthorizedCaller(
        uid = 42,
        packageName = "io.github.daniele21.redactguard",
        applicationId = ApplicationId("redactguard"),
        allowedUseCases = setOf(useCaseId),
    )

    private fun request() = ConsumerLogicalJobSubmitRequest(
        clientRequestId = ConsumerLogicalJobRequestId("analysis-1:chunk-1"),
        useCaseId = useCaseId,
        preparedId = ConsumerPreparedId("prepared-1"),
        expectedExecution = expectedExecution,
        input = ConsumerGenerationInput.Text("sensitive input"),
        outputConstraint = ConsumerOutputConstraint.Json,
    ).toConsumerLogicalJobWire(ClientTokenParcel("token"), "operation-1")

    private fun execution(capabilityRevision: String, presetVersion: Int): ConsumerExecutionIdentity = ConsumerExecutionIdentity(
        useCaseId = useCaseId,
        capabilityRevision = capabilityRevision,
        preset = InferencePresetRef(InferencePresetId("balanced"), presetVersion),
        reasoningMode = EffectiveConsumerReasoningMode.DISABLED,
        outputConstraint = ConsumerOutputConstraintKind.JSON,
        sessionKind = SessionKind.STATELESS,
    )

    private inner class MismatchingPreparedClient : ConsumerLocalLlmClient {
        override fun capabilities(useCaseId: UseCaseId): ConsumerCapabilityResult = error("not used")

        override fun prepare(request: ConsumerPrepareRequest): ConsumerPrepareResult = error("not used")

        override fun createSession(preparedId: ConsumerPreparedId): ConsumerSessionResult =
            ConsumerSessionResult.Created(SessionId("session-1"))

        override fun generate(request: ConsumerGenerationRequest, listener: ConsumerGenerationListener): ConsumerGenerationStartResult {
            listener.onEvent(
                ConsumerGenerationEvent.Prepared(
                    requestId = request.requestId,
                    execution = execution("capability-b", 2),
                ),
            )
            return ConsumerGenerationStartResult.Accepted(
                object : ConsumerGenerationHandle {
                    override val requestId: RequestId = request.requestId

                    override fun cancel() = Unit
                },
            )
        }

        override fun closeSession(sessionId: SessionId) = Unit
    }

    private inner class RunningClient : ConsumerLocalLlmClient {
        var listener: ConsumerGenerationListener? = null
        var cancelled = false
        var closedSessions = 0

        override fun capabilities(useCaseId: UseCaseId): ConsumerCapabilityResult = error("not used")

        override fun prepare(request: ConsumerPrepareRequest): ConsumerPrepareResult = error("not used")

        override fun createSession(preparedId: ConsumerPreparedId): ConsumerSessionResult =
            ConsumerSessionResult.Created(SessionId("session-1"))

        override fun generate(request: ConsumerGenerationRequest, listener: ConsumerGenerationListener): ConsumerGenerationStartResult {
            this.listener = listener
            listener.onEvent(ConsumerGenerationEvent.Prepared(request.requestId, expectedExecution))
            listener.onEvent(ConsumerGenerationEvent.Started(request.requestId))
            return ConsumerGenerationStartResult.Accepted(
                object : ConsumerGenerationHandle {
                    override val requestId: RequestId = request.requestId

                    override fun cancel() {
                        cancelled = true
                    }
                },
            )
        }

        override fun closeSession(sessionId: SessionId) {
            closedSessions += 1
        }
    }
}
