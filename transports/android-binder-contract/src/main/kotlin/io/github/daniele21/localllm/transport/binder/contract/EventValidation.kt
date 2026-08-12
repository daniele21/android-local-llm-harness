package io.github.daniele21.localllm.transport.binder.contract

fun validateGenerationEvent(value: GenerationEventParcel) {
    validateIdentifier(
        value.externalRequestId,
        BinderProtocolV1.MAX_IDENTIFIER_CHARACTERS,
        "request correlation ID",
    )
    requireWire(value.sequence >= 0, "Event sequence must be non-negative")

    when (value.eventTag) {
        WireTags.EVENT_QUEUED -> validateQueuedEvent(value)
        WireTags.EVENT_PREPARED -> validatePreparedEvent(value)
        WireTags.EVENT_STARTED -> validateStartedEvent(value)
        WireTags.EVENT_TEXT_DELTA -> validateDeltaEvent(value)
        WireTags.EVENT_COMPLETED -> validateCompletedEvent(value)
        WireTags.EVENT_FAILED -> validateFailedEvent(value)
        else -> throw invalidWireTag("generation event", value.eventTag)
    }
}

private fun validateQueuedEvent(value: GenerationEventParcel) {
    requireWire(value.queuePosition != null && value.queuePosition >= 0, "QUEUED requires a valid position")
    requireNoTerminalPayload(value)
}

private fun validatePreparedEvent(value: GenerationEventParcel) {
    requireWire(value.preparedConfiguration != null, "PREPARED requires configuration")
    requireWire(!value.modelDigestSha256.isNullOrBlank(), "PREPARED requires model digest")
}

private fun validateStartedEvent(value: GenerationEventParcel) {
    requireWire(!value.modelDigestSha256.isNullOrBlank(), "STARTED requires model digest")
}

private fun validateDeltaEvent(value: GenerationEventParcel) {
    val text = value.deltaText ?: throw invalidWireTag("generation delta payload", value.eventTag)
    requireWire(text.isNotEmpty(), "TEXT_DELTA must not be empty")
    requireWire(text.length <= BinderProtocolV1.MAX_DELTA_CHARACTERS, "TEXT_DELTA exceeds chunk limit")
    requireWire(
        value.generatedTokens != null && value.generatedTokens >= 0,
        "TEXT_DELTA requires generated token count",
    )
    requireWire(
        value.contentTypeTag == WireTags.CONTENT_REASONING || value.contentTypeTag == WireTags.CONTENT_ANSWER,
        "TEXT_DELTA has an invalid content type",
    )
}

private fun validateCompletedEvent(value: GenerationEventParcel) {
    requireWire(value.metrics != null, "COMPLETED requires metrics")
    requireWire(value.error == null, "COMPLETED must not contain an error")
    requireWire(value.deltaText == null, "COMPLETED must not duplicate aggregate output")
}

private fun validateFailedEvent(value: GenerationEventParcel) {
    requireWire(value.error != null, "FAILED requires an error")
    requireWire(value.metrics == null, "FAILED must not contain terminal metrics")
}

private fun requireNoTerminalPayload(value: GenerationEventParcel) {
    requireWire(
        value.metrics == null && value.error == null && value.deltaText == null,
        "Event contains invalid payload",
    )
}
