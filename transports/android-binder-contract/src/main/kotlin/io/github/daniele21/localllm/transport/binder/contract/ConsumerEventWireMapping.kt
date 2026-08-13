package io.github.daniele21.localllm.transport.binder.contract

import io.github.daniele21.localllm.contracts.ConsumerGenerationEvent

fun ConsumerGenerationEvent.toConsumerWire(
    externalRequestId: String,
    sequence: Long,
): List<ConsumerGenerationEventParcel> =
    when (this) {
        is ConsumerGenerationEvent.ContentDelta ->
            chunkDelta(text).mapIndexed { index, chunk ->
                ConsumerGenerationEventParcel(
                    externalRequestId = externalRequestId,
                    sequence = sequence + index,
                    eventTag = ConsumerWireTags.EVENT_CONTENT_DELTA,
                    deltaText = chunk,
                    contentTypeTag = contentType.name,
                )
            }

        is ConsumerGenerationEvent.Queued ->
            listOf(
                ConsumerGenerationEventParcel(
                    externalRequestId = externalRequestId,
                    sequence = sequence,
                    eventTag = ConsumerWireTags.EVENT_QUEUED,
                    queuePosition = position,
                ),
            )

        is ConsumerGenerationEvent.Prepared ->
            listOf(
                ConsumerGenerationEventParcel(
                    externalRequestId = externalRequestId,
                    sequence = sequence,
                    eventTag = ConsumerWireTags.EVENT_PREPARED,
                    execution = execution.toConsumerWire(),
                ),
            )

        is ConsumerGenerationEvent.Started ->
            listOf(
                ConsumerGenerationEventParcel(
                    externalRequestId = externalRequestId,
                    sequence = sequence,
                    eventTag = ConsumerWireTags.EVENT_STARTED,
                ),
            )

        is ConsumerGenerationEvent.Completed ->
            listOf(
                ConsumerGenerationEventParcel(
                    externalRequestId = externalRequestId,
                    sequence = sequence,
                    eventTag = ConsumerWireTags.EVENT_COMPLETED,
                    execution = result.execution.toConsumerWire(),
                    metrics = result.metrics.toConsumerWire(),
                ),
            )

        is ConsumerGenerationEvent.Failed ->
            listOf(
                ConsumerGenerationEventParcel(
                    externalRequestId = externalRequestId,
                    sequence = sequence,
                    eventTag = ConsumerWireTags.EVENT_FAILED,
                    error = failure.toWireError(),
                ),
            )
    }
