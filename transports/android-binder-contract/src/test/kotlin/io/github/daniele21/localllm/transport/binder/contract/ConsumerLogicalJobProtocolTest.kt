package io.github.daniele21.localllm.transport.binder.contract

import io.github.daniele21.localllm.contracts.ConsumerGenerationInput
import io.github.daniele21.localllm.contracts.ConsumerInferenceJobResponse
import io.github.daniele21.localllm.contracts.ConsumerInferenceJobState
import io.github.daniele21.localllm.contracts.ConsumerLogicalJobRequestId
import io.github.daniele21.localllm.contracts.ConsumerLogicalJobSubmitRequest
import io.github.daniele21.localllm.contracts.ConsumerOutputConstraint
import io.github.daniele21.localllm.contracts.ConsumerPreparedId
import io.github.daniele21.localllm.contracts.UseCaseId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsumerLogicalJobProtocolTest {
    @Test
    fun `protocol minor six owns logical job feature after setup resolution`() {
        assertEquals(5, BinderProtocolV1.minimumMinorForFeature(BinderProtocolV1.FEATURE_CONSUMER_SETUP_RESOLUTION_V1))
        assertEquals(6, BinderProtocolV1.minimumMinorForFeature(BinderProtocolV1.FEATURE_CONSUMER_LOGICAL_JOBS_V1))
        assertTrue(BinderProtocolV1.FEATURE_CONSUMER_LOGICAL_JOBS_V1 in BinderProtocolV1.KNOWN_FEATURES)
    }

    @Test
    fun `logical submit maps public identity and redaction-safe request metadata`() {
        val request =
            ConsumerLogicalJobSubmitRequest(
                clientRequestId = ConsumerLogicalJobRequestId("analysis-42"),
                useCaseId = UseCaseId("document-pii-detection"),
                preparedId = ConsumerPreparedId("prepared-7"),
                input = ConsumerGenerationInput.Text("sensitive input"),
                outputConstraint = ConsumerOutputConstraint.Json,
            )

        val wire = request.toConsumerLogicalJobWire(ClientTokenParcel("token"), "operation-1")

        assertEquals("analysis-42", wire.clientRequestId)
        assertEquals("document-pii-detection", wire.useCaseId)
        assertEquals("prepared-7", wire.preparedId)
        assertFalse(request.toString().contains("sensitive input"))
    }

    @Test
    fun `logical job snapshot round trip keeps reattachment identity without content`() {
        val wire =
            ConsumerLogicalJobResultParcel(
                operationId = "operation-2",
                snapshot =
                    ConsumerLogicalJobSnapshotParcel(
                        jobId = "job-9",
                        clientRequestId = "analysis-42",
                        useCaseId = "document-pii-detection",
                        stateTag = ConsumerLogicalJobWireTags.STATE_RUNNING,
                        revision = 3,
                        attempt = 1,
                        runtimeSessionId = "runtime-1",
                        resultAvailable = false,
                        errorCode = null,
                    ),
            )

        val response = wire.toCoreLogicalJobResponse() as ConsumerInferenceJobResponse.Available

        assertEquals("job-9", response.snapshot.jobId.value)
        assertEquals("analysis-42", response.snapshot.clientRequestId.value)
        assertEquals(ConsumerInferenceJobState.RUNNING, response.snapshot.state)
        assertEquals(3L, response.snapshot.revision)
        assertEquals("runtime-1", response.snapshot.runtimeSessionId.value)
        assertFalse(response.snapshot.resultAvailable)
        assertNull(response.output)
    }
}
