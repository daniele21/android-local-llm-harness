package io.github.daniele21.localllm.transport.binder.contract

import io.github.daniele21.localllm.contracts.ConsumerContentType
import io.github.daniele21.localllm.contracts.ConsumerGenerationEvent
import io.github.daniele21.localllm.contracts.ConsumerInferenceResult
import io.github.daniele21.localllm.contracts.RequestId

class ConsumerGenerationEventReconstructor(
    private val externalRequestId: String,
    private val requestId: RequestId,
) {
    private var nextSequence = 0L
    private var terminated = false
    private val reasoning = StringBuilder()
    private val answer = StringBuilder()

    fun accept(event: ConsumerGenerationEventParcel): ConsumerGenerationEvent {
        requireWire(
            !terminated,
            "Consumer generation stream is already terminated",
            WireErrorCodes.TRANSPORT_FAILURE,
        )
        requireWire(
            event.externalRequestId == externalRequestId,
            "Consumer event request correlation mismatch",
            WireErrorCodes.TRANSPORT_FAILURE,
        )
        requireWire(
            event.sequence == nextSequence,
            "Consumer event sequence is not contiguous",
            WireErrorCodes.TRANSPORT_FAILURE,
        )
        val mapped = mapEvent(event)
        nextSequence += 1
        return mapped
    }

    private fun mapEvent(event: ConsumerGenerationEventParcel): ConsumerGenerationEvent =
        when (event.eventTag) {
            ConsumerWireTags.EVENT_QUEUED ->
                ConsumerGenerationEvent.Queued(requestId, requireNotNull(event.queuePosition))

            ConsumerWireTags.EVENT_PREPARED ->
                ConsumerGenerationEvent.Prepared(
                    requestId,
                    requireNotNull(event.execution).toCoreExecutionIdentity(),
                )

            ConsumerWireTags.EVENT_STARTED -> ConsumerGenerationEvent.Started(requestId)
            ConsumerWireTags.EVENT_CONTENT_DELTA -> mapDelta(event)
            ConsumerWireTags.EVENT_COMPLETED -> mapCompleted(event)
            ConsumerWireTags.EVENT_FAILED -> mapFailed(event)
            else -> throw invalidWireTag("consumer generation event", event.eventTag)
        }

    private fun mapDelta(event: ConsumerGenerationEventParcel): ConsumerGenerationEvent.ContentDelta {
        val text = requireNotNull(event.deltaText)
        val contentType =
            enumTag<ConsumerContentType>(
                requireNotNull(event.contentTypeTag),
                "consumer content type",
            )
        when (contentType) {
            ConsumerContentType.REASONING -> reasoning.append(text)
            ConsumerContentType.ANSWER -> answer.append(text)
        }
        return ConsumerGenerationEvent.ContentDelta(requestId, text, contentType)
    }

    private fun mapCompleted(event: ConsumerGenerationEventParcel): ConsumerGenerationEvent.Completed {
        terminated = true
        return ConsumerGenerationEvent.Completed(
            requestId = requestId,
            result =
                ConsumerInferenceResult(
                    answer = answer.toString(),
                    surfacedReasoning = reasoning.toString().takeIf { it.isNotEmpty() },
                    metrics = requireNotNull(event.metrics).toCoreConsumerMetrics(),
                    execution = requireNotNull(event.execution).toCoreExecutionIdentity(),
                ),
        )
    }

    private fun mapFailed(event: ConsumerGenerationEventParcel): ConsumerGenerationEvent.Failed {
        terminated = true
        return ConsumerGenerationEvent.Failed(
            requestId,
            requireNotNull(event.error).toConsumerFailure(),
        )
    }
}
