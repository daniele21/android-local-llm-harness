package io.github.daniele21.localllm.transport.binder.contract

class WireProtocolException(
    val wireCode: String,
    override val message: String,
) : IllegalArgumentException(message)

data class NegotiatedProtocol(
    val minor: Int,
    val enabledFeatures: Set<String>,
)

internal fun validateToken(value: ClientTokenParcel) {
    validateIdentifier(value.value, BinderProtocolV1.MAX_IDENTIFIER_CHARACTERS, "client token")
}

internal fun validateIdentifier(
    value: String,
    maxCharacters: Int,
    label: String,
) {
    requireWire(value.isNotBlank(), "$label must not be blank")
    requireWire('\u0000' !in value, "$label must not contain NUL")
    requireWire(value.length <= maxCharacters, "$label is too long")
}

internal fun validateBoundedContent(
    value: String,
    maxCharacters: Int,
    label: String,
) {
    requireWire(value.isNotBlank(), "$label must not be blank")
    requireWire('\u0000' !in value, "$label must not contain NUL")
    requireWire(value.length <= maxCharacters, "$label exceeds protocol limit")
}

internal fun requireFinite(value: Float, label: String) {
    requireWire(value.isFinite(), "$label must be finite")
}

internal fun requireWire(
    condition: Boolean,
    message: String,
    code: String = WireErrorCodes.INVALID_WIRE_REQUEST,
) {
    if (!condition) {
        throw WireProtocolException(code, message)
    }
}

internal fun invalidWireTag(label: String, value: Any?): WireProtocolException =
    WireProtocolException(WireErrorCodes.INVALID_WIRE_REQUEST, "Unknown $label tag: $value")

internal fun transportFailure(message: String): WireProtocolException =
    WireProtocolException(WireErrorCodes.TRANSPORT_FAILURE, message)
