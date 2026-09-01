package io.github.daniele21.localllm.transport.binder.client

import io.github.daniele21.localllm.contracts.ConsumerErrorCode
import io.github.daniele21.localllm.contracts.ConsumerGenerationInput
import io.github.daniele21.localllm.contracts.ConsumerInferenceJobResponse
import io.github.daniele21.localllm.contracts.ConsumerInferenceJobState
import io.github.daniele21.localllm.contracts.ConsumerLogicalJobRequestId
import io.github.daniele21.localllm.contracts.ConsumerLogicalJobSubmitRequest
import io.github.daniele21.localllm.contracts.ConsumerOutputConstraint
import io.github.daniele21.localllm.contracts.ConsumerPreparedId
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.transport.binder.contract.BinderProtocolV1
import io.github.daniele21.localllm.transport.binder.contract.ConsumerLogicalJobResultParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerLogicalJobSnapshotParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerLogicalJobWireTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BinderConsumerLogicalJobAdapterTest {
    private val token = successfulRegistration().clientToken!!
    private val useCaseId = UseCaseId("document-pii-detection")

    @Test
    fun `minor five feature gating rejects logical job before remote call`() {
        val service = FakeSharedRuntimeRemoteService()
        val adapter =
            BinderConsumerLogicalJobAdapter(
                endpointProvider = { RegisteredSharedRuntimeEndpoint(service, token) },
                enabledFeaturesProvider = { setOf(BinderProtocolV1.FEATURE_CONSUMER_SETUP_RESOLUTION_V1) },
                blockingCallGuard = BlockingCallGuard {},
                operationTimeoutMillis = 100,
                correlationIds = CorrelationIdSource { "logical-job-op" },
            )

        val result = adapter.submitLogicalGeneration(submitRequest())

        assertTrue(result is ConsumerInferenceJobResponse.Rejected)
        assertEquals(
            ConsumerErrorCode.CAPABILITY_INCOMPATIBLE,
            (result as ConsumerInferenceJobResponse.Rejected).failure.code,
        )
        assertEquals(0, service.consumerLogicalJobSubmitCalls)
    }

    @Test
    fun `minor six submit returns durable job identity`() {
        val service = FakeSharedRuntimeRemoteService().apply {
            consumerLogicalJobSubmitHandler = { request, callback ->
                callback(
                    ConsumerLogicalJobResultParcel(
                        operationId = request.operationId,
                        snapshot =
                        ConsumerLogicalJobSnapshotParcel(
                            jobId = "job-42",
                            clientRequestId = request.clientRequestId,
                            useCaseId = request.useCaseId,
                            stateTag = ConsumerLogicalJobWireTags.STATE_QUEUED,
                            revision = 0,
                            attempt = 1,
                            runtimeSessionId = "runtime-42",
                            resultAvailable = false,
                        ),
                    ),
                )
            }
        }
        val adapter = adapter(service)

        val result = adapter.submitLogicalGeneration(submitRequest())

        assertTrue(result is ConsumerInferenceJobResponse.Available)
        val snapshot = (result as ConsumerInferenceJobResponse.Available).snapshot
        assertEquals("job-42", snapshot.jobId.value)
        assertEquals(ConsumerInferenceJobState.QUEUED, snapshot.state)
        assertEquals("analysis-42", snapshot.clientRequestId.value)
        assertEquals(1, service.consumerLogicalJobSubmitCalls)
    }

    @Test
    fun `endpoint invalidation is transport loss and never implicit logical job cancellation`() {
        val invalidations = FakeEndpointInvalidations()
        val service = FakeSharedRuntimeRemoteService()
        val endpoint = RegisteredSharedRuntimeEndpoint(service, token, connectionEpoch = 11L)
        service.consumerLogicalJobSubmitHandler = { _, _ -> invalidations.invalidate(11L, "host transport detached") }
        val adapter =
            BinderConsumerLogicalJobAdapter(
                endpointProvider = { endpoint },
                enabledFeaturesProvider = { setOf(BinderProtocolV1.FEATURE_CONSUMER_LOGICAL_JOBS_V1) },
                endpointInvalidations = invalidations,
                blockingCallGuard = BlockingCallGuard {},
                operationTimeoutMillis = 5_000,
                correlationIds = CorrelationIdSource { "logical-job-op" },
            )

        val result = adapter.submitLogicalGeneration(submitRequest())

        assertTrue(result is ConsumerInferenceJobResponse.Rejected)
        assertEquals(
            ConsumerErrorCode.RUNTIME_FAILURE,
            (result as ConsumerInferenceJobResponse.Rejected).failure.code,
        )
        assertEquals(0, service.consumerLogicalJobCancelCalls)
    }

    private fun adapter(service: FakeSharedRuntimeRemoteService): BinderConsumerLogicalJobAdapter =
        BinderConsumerLogicalJobAdapter(
            endpointProvider = { RegisteredSharedRuntimeEndpoint(service, token) },
            enabledFeaturesProvider = { setOf(BinderProtocolV1.FEATURE_CONSUMER_LOGICAL_JOBS_V1) },
            blockingCallGuard = BlockingCallGuard {},
            operationTimeoutMillis = 100,
            correlationIds = CorrelationIdSource { "logical-job-op" },
        )

    private fun submitRequest() =
        ConsumerLogicalJobSubmitRequest(
            clientRequestId = ConsumerLogicalJobRequestId("analysis-42"),
            useCaseId = useCaseId,
            preparedId = ConsumerPreparedId("prepared-42"),
            input = ConsumerGenerationInput.Text("sensitive document text"),
            outputConstraint = ConsumerOutputConstraint.Json,
        )
}
