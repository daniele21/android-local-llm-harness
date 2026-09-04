package io.github.daniele21.localllm.transport.binder.contract

fun validatePrepareRequest(value: PrepareRequestParcel) {
    validateToken(value.clientToken)
    validateIdentifier(value.operationId, BinderProtocolV1.MAX_IDENTIFIER_CHARACTERS, "operation ID")
    validateIdentifier(value.useCaseId, BinderProtocolV1.MAX_USE_CASE_ID_CHARACTERS, "use-case ID")
}

fun validateOpenSessionRequest(value: OpenSessionRequestParcel) {
    validateToken(value.clientToken)
    validateIdentifier(value.operationId, BinderProtocolV1.MAX_IDENTIFIER_CHARACTERS, "operation ID")
    validateIdentifier(
        value.externalSessionId,
        BinderProtocolV1.MAX_IDENTIFIER_CHARACTERS,
        "session correlation ID",
    )
    validateIdentifier(value.useCaseId, BinderProtocolV1.MAX_USE_CASE_ID_CHARACTERS, "use-case ID")
    validateSessionOptions(value.options)
}

fun validateGenerationRequest(value: GenerationRequestParcel) {
    validateToken(value.clientToken)
    validateIdentifier(
        value.externalRequestId,
        BinderProtocolV1.MAX_IDENTIFIER_CHARACTERS,
        "request correlation ID",
    )
    validateIdentifier(
        value.externalSessionId,
        BinderProtocolV1.MAX_IDENTIFIER_CHARACTERS,
        "session correlation ID",
    )
    validateIdentifier(value.useCaseId, BinderProtocolV1.MAX_USE_CASE_ID_CHARACTERS, "use-case ID")
    validateGenerationInput(value.input)
    validateGenerationOverrides(value.overrides)
    validateOutputConstraint(value.outputConstraint)
    requireWire(
        estimateGenerationRequestBytes(value) <= BinderProtocolV1.MAX_ESTIMATED_PARCEL_BYTES,
        "Generation request exceeds protocol payload limit",
        WireErrorCodes.PAYLOAD_TOO_LARGE,
    )
}

fun validateCancelRequest(value: CancelRequestParcel) {
    validateToken(value.clientToken)
    validateIdentifier(
        value.externalRequestId,
        BinderProtocolV1.MAX_IDENTIFIER_CHARACTERS,
        "request correlation ID",
    )
}

fun validateCloseSessionRequest(value: CloseSessionRequestParcel) {
    validateToken(value.clientToken)
    validateIdentifier(
        value.externalSessionId,
        BinderProtocolV1.MAX_IDENTIFIER_CHARACTERS,
        "session correlation ID",
    )
}

fun estimateGenerationRequestBytes(value: GenerationRequestParcel): Int {
    val characters =
        value.clientToken.value.length +
            value.externalRequestId.length +
            value.externalSessionId.length +
            value.useCaseId.length +
            value.input.typeTag.length +
            (value.input.text?.length ?: 0) +
            value.input.messages.sumOf { it.roleTag.length + it.content.length } +
            (value.overrides.presetId?.length ?: 0) +
            (value.overrides.seedPolicyTag?.length ?: 0) +
            (value.overrides.thinkingModeTag?.length ?: 0) +
            value.outputConstraint.typeTag.length +
            (value.outputConstraint.jsonSchema?.length ?: 0)
    return 1_024 + (characters * 4)
}

private fun validateSessionOptions(value: SessionOptionsParcel) {
    when (value.contextPolicyTag) {
        WireTags.CONTEXT_AUTO ->
            requireWire(value.manualContextTokens == null, "AUTO context must not carry a size")

        WireTags.CONTEXT_MANUAL ->
            requireWire(
                value.manualContextTokens != null && value.manualContextTokens > 0,
                "MANUAL context requires a positive size",
            )

        else -> throw invalidWireTag("context policy", value.contextPolicyTag)
    }
    requireWire(
        value.sessionKindTag == WireTags.SESSION_STATELESS ||
            value.sessionKindTag == WireTags.SESSION_CONVERSATIONAL,
        "Unknown session kind tag",
    )
}
