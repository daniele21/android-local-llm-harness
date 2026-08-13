package io.github.daniele21.localllm.transport.binder.contract

import io.github.daniele21.localllm.contracts.ConsumerCapabilityErrorCode
import io.github.daniele21.localllm.contracts.ConsumerCapabilityResult
import io.github.daniele21.localllm.contracts.ConsumerErrorCode
import io.github.daniele21.localllm.contracts.ConsumerFailure
import io.github.daniele21.localllm.contracts.ConsumerPrepareResult
import io.github.daniele21.localllm.contracts.ConsumerSessionResult
import io.github.daniele21.localllm.contracts.SessionId

fun ConsumerCapabilityResult.toConsumerWire(operationId: String): ConsumerResultParcel =
    when (this) {
        is ConsumerCapabilityResult.Available ->
            ConsumerResultParcel(operationId, capabilities = capabilities.toConsumerWire())

        is ConsumerCapabilityResult.Rejected ->
            ConsumerResultParcel(
                operationId,
                error = WireErrorParcel(code.name, "Consumer capability is unavailable", false),
            )
    }

fun ConsumerResultParcel.toCoreCapabilityResult(): ConsumerCapabilityResult =
    capabilities?.let { ConsumerCapabilityResult.Available(it.toCoreCapabilities()) }
        ?: ConsumerCapabilityResult.Rejected(
            requireNotNull(error).toCapabilityErrorCode(),
            "Consumer capability is unavailable",
        )

fun ConsumerPrepareResult.toConsumerWire(operationId: String): ConsumerResultParcel =
    when (this) {
        is ConsumerPrepareResult.Prepared ->
            ConsumerResultParcel(operationId, preparedSelection = selection.toConsumerWire())

        is ConsumerPrepareResult.Rejected ->
            ConsumerResultParcel(operationId, error = failure.toWireError())
    }

fun ConsumerResultParcel.toCorePrepareResult(): ConsumerPrepareResult =
    preparedSelection?.let { ConsumerPrepareResult.Prepared(it.toCorePreparedSelection()) }
        ?: ConsumerPrepareResult.Rejected(requireNotNull(error).toConsumerFailure())

fun ConsumerSessionResult.toConsumerWire(
    operationId: String,
    externalSessionId: String,
): ConsumerResultParcel =
    when (this) {
        is ConsumerSessionResult.Created -> ConsumerResultParcel(operationId, externalSessionId = externalSessionId)
        is ConsumerSessionResult.Rejected -> ConsumerResultParcel(operationId, error = failure.toWireError())
    }

fun ConsumerResultParcel.toCoreSessionResult(): ConsumerSessionResult =
    externalSessionId?.let { ConsumerSessionResult.Created(SessionId(it)) }
        ?: ConsumerSessionResult.Rejected(requireNotNull(error).toConsumerFailure())

fun ConsumerFailure.toWireError(): WireErrorParcel =
    WireErrorParcel(code.name, "Consumer request failed", code == ConsumerErrorCode.MODEL_UNAVAILABLE)

fun WireErrorParcel.toConsumerFailure(): ConsumerFailure {
    val mapped =
        enumTagOrNull<ConsumerErrorCode>(code)
            ?: when (code) {
                WireErrorCodes.UNAUTHORIZED_USE_CASE -> ConsumerErrorCode.USE_CASE_NOT_ALLOWED
                WireErrorCodes.MODEL_UNAVAILABLE -> ConsumerErrorCode.MODEL_UNAVAILABLE
                WireErrorCodes.CANCELLED -> ConsumerErrorCode.CANCELLED
                else -> ConsumerErrorCode.RUNTIME_FAILURE
            }
    return ConsumerFailure(mapped, mapped.safeMessage())
}

private fun WireErrorParcel.toCapabilityErrorCode(): ConsumerCapabilityErrorCode =
    enumTagOrNull<ConsumerCapabilityErrorCode>(code)
        ?: when (code) {
            WireErrorCodes.UNAUTHORIZED_USE_CASE -> ConsumerCapabilityErrorCode.USE_CASE_NOT_ALLOWED
            WireErrorCodes.MODEL_UNAVAILABLE -> ConsumerCapabilityErrorCode.MODEL_UNAVAILABLE
            else -> ConsumerCapabilityErrorCode.CAPABILITY_INCOMPATIBLE
        }

private fun ConsumerErrorCode.safeMessage(): String =
    when (this) {
        ConsumerErrorCode.CANCELLED -> "Generation was cancelled"
        ConsumerErrorCode.MODEL_UNAVAILABLE -> "Required local model is unavailable"
        ConsumerErrorCode.USE_CASE_NOT_ALLOWED -> "Use case is not authorized"
        else -> "Consumer request failed"
    }

