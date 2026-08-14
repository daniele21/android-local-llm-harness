package io.github.daniele21.localllm.transport.binder.contract

import io.github.daniele21.localllm.contracts.ConsumerExecutionIdentity
import io.github.daniele21.localllm.contracts.ConsumerGenerationEvent
import io.github.daniele21.localllm.contracts.ConsumerInferenceMetrics
import io.github.daniele21.localllm.contracts.ConsumerInferenceResult
import io.github.daniele21.localllm.contracts.ConsumerOutputConstraintKind
import io.github.daniele21.localllm.contracts.ConsumerStopReason
import io.github.daniele21.localllm.contracts.EffectiveConsumerReasoningMode
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.UseCaseId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ConsumerBinderContractTest {
    @Test
    fun `consumer wire DTOs do not expose host model artifact or raw tuning authority`() {
        val fieldNames =
            listOf(
                ConsumerRequestParcel::class.java,
                ConsumerResultParcel::class.java,
                ConsumerGenerationEventParcel::class.java,
                ConsumerExecutionIdentityParcel::class.java,
                ConsumerInferenceMetricsParcel::class.java,
            ).flatMap { type -> type.declaredFields.map { it.name.lowercase() } }
        val forbiddenFragments =
            listOf(
                "applicationid",
                "modelid",
                "modeldigest",
                "sha256",
                "path",
                "url",
                "temperature",
                "topp",
                "topk",
                "minp",
                "presencepenalty",
                "repeatpenalty",
                "contextsize",
                "thread",
                "batch",
                "thermal",
                "memory",
                "pss",
            )

        assertFalse(fieldNames.any { field -> forbiddenFragments.any(field::contains) })
    }

    @Test
    fun `completed event reconstructs output from deltas without terminal duplication`() {
        val requestId = RequestId("public-request")
        val externalRequestId = "wire-request"
        val execution =
            ConsumerExecutionIdentity(
                useCaseId = UseCaseId("summarize"),
                capabilityRevision = "cap-rev-1",
                preset = null,
                reasoningMode = EffectiveConsumerReasoningMode.DISABLED,
                outputConstraint = ConsumerOutputConstraintKind.TEXT,
                sessionKind = SessionKind.STATELESS,
            )
        val metrics =
            ConsumerInferenceMetrics(
                outputTokens = 1,
                timeToFirstTokenMs = 2,
                totalMs = 4,
                decodeTokensPerSecond = 10.0,
                inputTokens = 1,
                reasoningTokens = null,
                answerTokens = 1,
                queueMs = 0,
                stopReason = ConsumerStopReason.END_OF_GENERATION,
            )
        val reconstructor = ConsumerGenerationEventReconstructor(externalRequestId, requestId)
        val delta =
            ConsumerGenerationEvent.ContentDelta(
                requestId,
                "answer",
                io.github.daniele21.localllm.contracts.ConsumerContentType.ANSWER,
            ).toConsumerWire(externalRequestId, 0).single()
        val completed =
            ConsumerGenerationEvent.Completed(
                requestId,
                ConsumerInferenceResult("answer", null, metrics, execution),
            ).toConsumerWire(externalRequestId, 1).single()

        reconstructor.accept(delta)
        val result = reconstructor.accept(completed) as ConsumerGenerationEvent.Completed

        assertEquals("answer", result.answer)
        assertEquals(metrics, result.metrics)
        assertEquals(execution, result.execution)
        assertNull(completed.deltaText)
    }

    @Test
    fun `consumer reconstructor rejects duplicate callbacks`() {
        val externalRequestId = "wire-request"
        val requestId = RequestId("public-request")
        val reconstructor = ConsumerGenerationEventReconstructor(externalRequestId, requestId)
        val started =
            ConsumerGenerationEventParcel(
                externalRequestId = externalRequestId,
                sequence = 0,
                eventTag = ConsumerWireTags.EVENT_STARTED,
            )

        reconstructor.accept(started)

        assertThrows(WireProtocolException::class.java) {
            reconstructor.accept(started)
        }
    }
}
