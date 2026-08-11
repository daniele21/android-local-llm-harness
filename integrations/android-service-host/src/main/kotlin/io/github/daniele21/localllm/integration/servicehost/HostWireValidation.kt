package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.transport.binder.contract.CancelRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.CloseSessionRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.GenerationRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.OpenSessionRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.PrepareRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.WireErrorParcel
import io.github.daniele21.localllm.transport.binder.contract.WireProtocolException
import io.github.daniele21.localllm.transport.binder.contract.validateCancelRequest
import io.github.daniele21.localllm.transport.binder.contract.validateCloseSessionRequest
import io.github.daniele21.localllm.transport.binder.contract.validateGenerationRequest
import io.github.daniele21.localllm.transport.binder.contract.validateOpenSessionRequest
import io.github.daniele21.localllm.transport.binder.contract.validatePrepareRequest

internal fun validatePrepare(caller: AuthorizedCaller, request: PrepareRequestParcel): WireErrorParcel? =
    validateWireAndUseCase(caller, request.useCaseId) { validatePrepareRequest(request) }

internal fun validateOpenSession(caller: AuthorizedCaller, request: OpenSessionRequestParcel): WireErrorParcel? =
    validateWireAndUseCase(caller, request.useCaseId) { validateOpenSessionRequest(request) }

internal fun validateGeneration(caller: AuthorizedCaller, request: GenerationRequestParcel): WireErrorParcel? =
    validateWireAndUseCase(caller, request.useCaseId) { validateGenerationRequest(request) }

internal fun validateCancel(request: CancelRequestParcel): WireErrorParcel? = validateWire { validateCancelRequest(request) }

internal fun validateClose(request: CloseSessionRequestParcel): WireErrorParcel? = validateWire { validateCloseSessionRequest(request) }

private fun validateWireAndUseCase(caller: AuthorizedCaller, useCase: String, validation: () -> Unit): WireErrorParcel? {
    val wireError = validateWire(validation)
    if (wireError != null) return wireError
    return if (caller.allows(UseCaseId(useCase))) null else wireError(io.github.daniele21.localllm.transport.binder.contract.WireErrorCodes.UNAUTHORIZED_USE_CASE)
}

private fun validateWire(validation: () -> Unit): WireErrorParcel? =
    try {
        validation()
        null
    } catch (error: WireProtocolException) {
        error.toHostWireError()
    }
