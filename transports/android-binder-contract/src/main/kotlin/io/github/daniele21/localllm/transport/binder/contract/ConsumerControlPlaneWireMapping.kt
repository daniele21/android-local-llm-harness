package io.github.daniele21.localllm.transport.binder.contract

import io.github.daniele21.localllm.contracts.ConsumerActivation
import io.github.daniele21.localllm.contracts.ConsumerActivationId
import io.github.daniele21.localllm.contracts.ConsumerActivationRequest
import io.github.daniele21.localllm.contracts.ConsumerActivationResult
import io.github.daniele21.localllm.contracts.ConsumerAssignedUseCase
import io.github.daniele21.localllm.contracts.ConsumerAssignedUseCasesResult
import io.github.daniele21.localllm.contracts.ConsumerControlPlaneErrorCode
import io.github.daniele21.localllm.contracts.ConsumerControlPlaneFailure
import io.github.daniele21.localllm.contracts.ConsumerDeactivationResult
import io.github.daniele21.localllm.contracts.ConsumerPublishedPreset
import io.github.daniele21.localllm.contracts.ConsumerPublishedPresetsResult
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.UseCaseId

fun ConsumerAssignedUseCasesResult.toConsumerControlPlaneWire(operationId: String): ConsumerControlPlaneResultParcel = when (this) {
    is ConsumerAssignedUseCasesResult.Available -> ConsumerControlPlaneResultParcel(
        operationId = operationId,
        assignments = assignments.map(ConsumerAssignedUseCase::toWire),
    )

    is ConsumerAssignedUseCasesResult.Rejected -> ConsumerControlPlaneResultParcel(
        operationId = operationId,
        error = failure.toWireError(),
    )
}

fun ConsumerControlPlaneResultParcel.toCoreAssignedUseCasesResult(): ConsumerAssignedUseCasesResult =
    error?.let {
        ConsumerAssignedUseCasesResult.Rejected(it.toConsumerControlPlaneFailure())
    } ?: ConsumerAssignedUseCasesResult.Available(assignments.map(ConsumerAssignedUseCaseParcel::toCore))

fun ConsumerPublishedPresetsResult.toConsumerControlPlaneWire(operationId: String): ConsumerControlPlaneResultParcel = when (this) {
    is ConsumerPublishedPresetsResult.Available -> ConsumerControlPlaneResultParcel(
        operationId = operationId,
        useCaseId = useCaseId.value,
        bindingRevision = bindingRevision,
        presets = presets.map(ConsumerPublishedPreset::toWire),
    )

    is ConsumerPublishedPresetsResult.Rejected -> ConsumerControlPlaneResultParcel(
        operationId = operationId,
        error = failure.toWireError(),
    )
}

fun ConsumerControlPlaneResultParcel.toCorePublishedPresetsResult(): ConsumerPublishedPresetsResult =
    error?.let {
        ConsumerPublishedPresetsResult.Rejected(it.toConsumerControlPlaneFailure())
    } ?: ConsumerPublishedPresetsResult.Available(
        useCaseId = UseCaseId(requireNotNull(useCaseId)),
        bindingRevision = requireNotNull(bindingRevision),
        presets = presets.map(ConsumerPublishedPresetMetadataParcel::toCore),
    )

fun ConsumerActivationResult.toConsumerControlPlaneWire(operationId: String): ConsumerControlPlaneResultParcel = when (this) {
    is ConsumerActivationResult.Activated -> ConsumerControlPlaneResultParcel(
        operationId = operationId,
        activation = activation.toWire(),
    )

    is ConsumerActivationResult.Rejected -> ConsumerControlPlaneResultParcel(
        operationId = operationId,
        error = failure.toWireError(),
    )
}

fun ConsumerControlPlaneResultParcel.toCoreActivationResult(): ConsumerActivationResult =
    error?.let {
        ConsumerActivationResult.Rejected(it.toConsumerControlPlaneFailure())
    } ?: ConsumerActivationResult.Activated(requireNotNull(activation).toCore())

fun ConsumerDeactivationResult.toConsumerControlPlaneWire(
    operationId: String,
    activationId: ConsumerActivationId,
): ConsumerControlPlaneResultParcel = when (this) {
    ConsumerDeactivationResult.Released -> ConsumerControlPlaneResultParcel(
        operationId = operationId,
        releasedActivationId = activationId.value,
    )

    is ConsumerDeactivationResult.Rejected -> ConsumerControlPlaneResultParcel(
        operationId = operationId,
        error = failure.toWireError(),
    )
}

fun ConsumerControlPlaneResultParcel.toCoreDeactivationResult(
    expectedActivationId: ConsumerActivationId,
): ConsumerDeactivationResult =
    error?.let {
        ConsumerDeactivationResult.Rejected(it.toConsumerControlPlaneFailure())
    } ?: if (releasedActivationId == expectedActivationId.value) {
        ConsumerDeactivationResult.Released
    } else {
        ConsumerDeactivationResult.Rejected(
            ConsumerControlPlaneFailure(
                ConsumerControlPlaneErrorCode.TRANSPORT_FAILURE,
                "Consumer control-plane response is invalid",
            ),
        )
    }

fun ConsumerActivationRequest.toConsumerControlPlaneWire(
    clientToken: ClientTokenParcel,
    operationId: String,
): ConsumerControlPlaneRequestParcel = ConsumerControlPlaneRequestParcel(
    clientToken = clientToken,
    operationId = operationId,
    useCaseId = useCaseId.value,
    useCaseRevision = useCaseRevision,
    bindingRevision = bindingRevision,
    preset = ConsumerPresetParcel(preset.id.value, preset.version),
)

private fun ConsumerAssignedUseCase.toWire() = ConsumerAssignedUseCaseParcel(
    useCaseId = useCaseId.value,
    useCaseRevision = useCaseRevision,
    bindingRevision = bindingRevision,
    displayName = displayName,
    description = description,
    isDefault = isDefault,
)

private fun ConsumerAssignedUseCaseParcel.toCore() = ConsumerAssignedUseCase(
    useCaseId = UseCaseId(useCaseId),
    useCaseRevision = useCaseRevision,
    bindingRevision = bindingRevision,
    displayName = displayName,
    description = description,
    isDefault = isDefault,
)

private fun ConsumerPublishedPreset.toWire() = ConsumerPublishedPresetMetadataParcel(
    preset = ConsumerPresetParcel(preset.id.value, preset.version),
    displayName = displayName,
    description = description,
    isDefault = isDefault,
)

private fun ConsumerPublishedPresetMetadataParcel.toCore() = ConsumerPublishedPreset(
    preset = InferencePresetRef(InferencePresetId(preset.id), preset.version),
    displayName = displayName,
    description = description,
    isDefault = isDefault,
)

private fun ConsumerActivation.toWire() = ConsumerActivationParcel(
    activationId = activationId.value,
    useCaseId = useCaseId.value,
    useCaseRevision = useCaseRevision,
    bindingRevision = bindingRevision,
    preset = ConsumerPresetParcel(preset.id.value, preset.version),
)

private fun ConsumerActivationParcel.toCore() = ConsumerActivation(
    activationId = ConsumerActivationId(activationId),
    useCaseId = UseCaseId(useCaseId),
    useCaseRevision = useCaseRevision,
    bindingRevision = bindingRevision,
    preset = InferencePresetRef(InferencePresetId(preset.id), preset.version),
)

private fun ConsumerControlPlaneFailure.toWireError(): WireErrorParcel = WireErrorParcel(
    code = code.name,
    safeMessage = "Consumer control-plane request failed",
    retryable = code == ConsumerControlPlaneErrorCode.MODEL_UNAVAILABLE,
)

fun WireErrorParcel.toConsumerControlPlaneFailure(): ConsumerControlPlaneFailure {
    val mapped = enumTagOrNull<ConsumerControlPlaneErrorCode>(code) ?: when (code) {
        WireErrorCodes.FEATURE_UNAVAILABLE -> ConsumerControlPlaneErrorCode.FEATURE_UNAVAILABLE
        WireErrorCodes.MODEL_UNAVAILABLE -> ConsumerControlPlaneErrorCode.MODEL_UNAVAILABLE
        else -> ConsumerControlPlaneErrorCode.RUNTIME_FAILURE
    }
    return ConsumerControlPlaneFailure(mapped, mapped.safeMessage())
}

private fun ConsumerControlPlaneErrorCode.safeMessage(): String = when (this) {
    ConsumerControlPlaneErrorCode.FEATURE_UNAVAILABLE -> "Consumer control plane is unavailable"
    ConsumerControlPlaneErrorCode.UNKNOWN_APPLICATION -> "Consumer application is unknown"
    ConsumerControlPlaneErrorCode.APPLICATION_NOT_AUTHORIZED -> "Consumer application is not authorized"
    ConsumerControlPlaneErrorCode.USE_CASE_NOT_ASSIGNED -> "Use case is not assigned"
    ConsumerControlPlaneErrorCode.PRESET_NOT_EXPOSED -> "Preset is not available to this consumer"
    ConsumerControlPlaneErrorCode.STALE_REVISION -> "Consumer configuration changed; refresh assignments"
    ConsumerControlPlaneErrorCode.MODEL_UNAVAILABLE -> "Required local model is unavailable"
    ConsumerControlPlaneErrorCode.MODEL_CONFLICT -> "Another active use case protects a different local model"
    ConsumerControlPlaneErrorCode.CONFIGURATION_REQUIRED -> "Harness configuration is required"
    ConsumerControlPlaneErrorCode.INVALID_REQUEST -> "Consumer control-plane request is invalid"
    ConsumerControlPlaneErrorCode.TRANSPORT_FAILURE -> "Shared runtime transport is unavailable"
    ConsumerControlPlaneErrorCode.RUNTIME_FAILURE -> "Consumer control-plane request failed"
}
