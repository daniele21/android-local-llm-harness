package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.contracts.PrepareResult
import io.github.daniele21.localllm.transport.binder.contract.ClientTokenParcel
import io.github.daniele21.localllm.transport.binder.contract.GenerationEventParcel
import io.github.daniele21.localllm.transport.binder.contract.PrepareResultParcel
import io.github.daniele21.localllm.transport.binder.contract.RegistrationResultParcel
import io.github.daniele21.localllm.transport.binder.contract.SessionResultParcel
import io.github.daniele21.localllm.transport.binder.contract.WireErrorCodes
import io.github.daniele21.localllm.transport.binder.contract.WireErrorParcel
import io.github.daniele21.localllm.transport.binder.contract.WireProtocolException
import io.github.daniele21.localllm.transport.binder.contract.WireTags

internal fun registrationSuccess(token: HostClientToken, negotiatedMinor: Int, features: List<String>) = RegistrationResultParcel(
    clientToken = ClientTokenParcel(token.value),
    negotiatedMinor = negotiatedMinor,
    enabledFeatures = features,
    error = null,
)

internal fun registrationFailure(error: WireErrorParcel) = RegistrationResultParcel(null, null, emptyList(), error)

internal fun prepareResult(operationId: String, result: PrepareResult) = PrepareResultParcel(
    operationId = operationId,
    ready = result.ready,
    modelDigestSha256 = result.modelDigest?.sha256,
    detail = if (result.ready) "Model ready" else "Preparation failed",
    error = if (result.ready) null else wireError(WireErrorCodes.PREPARATION_FAILED),
)

internal fun prepareFailure(operationId: String, error: WireErrorParcel) =
    PrepareResultParcel(operationId, false, null, "Preparation failed", error)

internal fun sessionSuccess(operationId: String, externalSessionId: String) = SessionResultParcel(operationId, externalSessionId, null)

internal fun sessionFailure(operationId: String, error: WireErrorParcel) = SessionResultParcel(operationId, null, error)

internal fun generationFailure(externalRequestId: String, error: WireErrorParcel) = GenerationEventParcel(
    externalRequestId = externalRequestId,
    sequence = 0,
    eventTag = WireTags.EVENT_FAILED,
    error = error,
)

internal fun LedgerFailure.toHostWireError(): WireErrorParcel = when (this) {
    LedgerFailure.CLIENT_TOKEN_INVALID -> wireError(WireErrorCodes.CLIENT_TOKEN_INVALID)

    LedgerFailure.CLIENT_CLOSING -> wireError(WireErrorCodes.CLIENT_DISCONNECTED)

    LedgerFailure.SESSION_LIMIT, LedgerFailure.REQUEST_LIMIT, LedgerFailure.CONNECTION_LIMIT -> wireError(
        WireErrorCodes.CLIENT_BACKPRESSURE,
    )

    LedgerFailure.DUPLICATE_EXTERNAL_SESSION_ID, LedgerFailure.DUPLICATE_EXTERNAL_REQUEST_ID -> wireError(
        WireErrorCodes.INVALID_WIRE_REQUEST,
    )

    LedgerFailure.SESSION_NOT_OWNED -> wireError(WireErrorCodes.SESSION_UNAVAILABLE)

    LedgerFailure.REQUEST_NOT_OWNED -> wireError(WireErrorCodes.INVALID_WIRE_REQUEST)

    LedgerFailure.TOKEN_GENERATION_FAILED -> wireError(WireErrorCodes.TRANSPORT_FAILURE)
}

internal fun WireProtocolException.toHostWireError(): WireErrorParcel = wireError(wireCode)

internal fun wireError(code: String): WireErrorParcel = when (code) {
    WireErrorCodes.PROTOCOL_INCOMPATIBLE -> WireErrorParcel(code, "Protocol incompatible", false)
    WireErrorCodes.FEATURE_UNAVAILABLE -> WireErrorParcel(code, "Required feature unavailable", false)
    WireErrorCodes.PAYLOAD_TOO_LARGE -> WireErrorParcel(code, "Request payload too large", false)
    WireErrorCodes.CLIENT_NOT_REGISTERED -> WireErrorParcel(code, "Client is not registered", false)
    WireErrorCodes.UNAUTHORIZED_USE_CASE -> WireErrorParcel(code, "Use case is not authorized", false)
    WireErrorCodes.CLIENT_TOKEN_INVALID -> WireErrorParcel(code, "Client token is invalid", false)
    WireErrorCodes.CLIENT_DISCONNECTED -> WireErrorParcel(code, "Client is disconnected", true)
    WireErrorCodes.CLIENT_BACKPRESSURE -> WireErrorParcel(code, "Host capacity is exhausted", true)
    WireErrorCodes.SESSION_UNAVAILABLE -> WireErrorParcel(code, "Session is unavailable", false)
    WireErrorCodes.PREPARATION_FAILED -> WireErrorParcel(code, "Preparation failed", true)
    WireErrorCodes.RUNTIME_FAILURE -> WireErrorParcel(code, "Runtime operation failed", true)
    WireErrorCodes.TRANSPORT_FAILURE -> WireErrorParcel(code, "Host transport unavailable", true)
    else -> WireErrorParcel(WireErrorCodes.INVALID_WIRE_REQUEST, "Invalid request", false)
}
