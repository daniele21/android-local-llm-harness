package io.github.daniele21.localllm.transport.binder.contract

import io.github.daniele21.localllm.contracts.ConsumerCapabilityErrorCode
import io.github.daniele21.localllm.contracts.ConsumerCapabilityResult
import io.github.daniele21.localllm.contracts.ConsumerErrorCode
import io.github.daniele21.localllm.contracts.ConsumerExecutionIdentity
import io.github.daniele21.localllm.contracts.ConsumerFailure
import io.github.daniele21.localllm.contracts.ConsumerGenerationInput
import io.github.daniele21.localllm.contracts.ConsumerInferenceMetrics
import io.github.daniele21.localllm.contracts.ConsumerLimits
import io.github.daniele21.localllm.contracts.ConsumerOutputConstraint
import io.github.daniele21.localllm.contracts.ConsumerOutputConstraintKind
import io.github.daniele21.localllm.contracts.ConsumerPrepareResult
import io.github.daniele21.localllm.contracts.ConsumerPreparedId
import io.github.daniele21.localllm.contracts.ConsumerPreparedSelection
import io.github.daniele21.localllm.contracts.ConsumerPresetOption
import io.github.daniele21.localllm.contracts.ConsumerSelectionRequest
import io.github.daniele21.localllm.contracts.ConsumerSessionResult
import io.github.daniele21.localllm.contracts.ConversationMessage
import io.github.daniele21.localllm.contracts.ConversationRole
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.UseCaseCapabilities
import io.github.daniele21.localllm.contracts.UseCaseId

fun UseCaseCapabilities.toConsumerWire(): ConsumerCapabilitiesParcel =
    ConsumerCapabilitiesParcel(
        useCaseId = useCaseId.value,
        readinessTag = readiness.name,
        presets = presets.map { ConsumerPresetOptionParcel(it.ref.toConsumerWire(), it.isDefault) },
        defaultPreset = defaultPreset?.toConsumerWire(),
        reasoningTag = reasoning.name,
        outputConstraintTags = outputConstraints.map { it.name }.sorted(),
        defaultOutputConstraintTag = defaultOutputConstraint.name,
        sessionKindTags = sessionKinds.map { it.name }.sorted(),
        defaultSessionKindTag = defaultSessionKind.name,
        limits = limits.toConsumerWire(),
        capabilityRevision = capabilityRevision,
    )

fun ConsumerCapabilitiesParcel.toCoreCapabilities(): UseCaseCapabilities =
    UseCaseCapabilities(
        useCaseId = UseCaseId(useCaseId),
        readiness = enumTag(readinessTag, "consumer readiness"),
        presets = presets.map { ConsumerPresetOption(it.preset.toCorePreset(), it.isDefault) },
        defaultPreset = defaultPreset?.toCorePreset(),
        reasoning = enumTag(reasoningTag, "consumer reasoning capability"),
        outputConstraints =
            outputConstraintTags
                .map { enumTag<ConsumerOutputConstraintKind>(it, "consumer output constraint") }
                .toSet(),
        defaultOutputConstraint = enumTag(defaultOutputConstraintTag, "consumer default output constraint"),
        sessionKinds = sessionKindTags.map { enumTag<SessionKind>(it, "consumer session kind") }.toSet(),
        defaultSessionKind = enumTag(defaultSessionKindTag, "consumer default session kind"),
        limits = limits.toCoreLimits(),
        capabilityRevision = capabilityRevision,
    )

fun ConsumerSelectionRequest.toConsumerWire(): ConsumerSelectionParcel =
    ConsumerSelectionParcel(
        capabilityRevision = capabilityRevision,
        preset = preset?.toConsumerWire(),
        reasoningPreferenceTag = reasoning.name,
        outputConstraintTag = outputConstraint?.name,
        sessionKindTag = sessionKind?.name,
    )

fun ConsumerSelectionParcel.toCoreSelection(): ConsumerSelectionRequest =
    ConsumerSelectionRequest(
        capabilityRevision = capabilityRevision,
        preset = preset?.toCorePreset(),
        reasoning = enumTag(reasoningPreferenceTag, "consumer reasoning preference"),
        outputConstraint = outputConstraintTag?.let { enumTag(it, "consumer output constraint") },
        sessionKind = sessionKindTag?.let { enumTag(it, "consumer session kind") },
    )

fun ConsumerPreparedSelection.toConsumerWire(): ConsumerPreparedSelectionParcel =
    ConsumerPreparedSelectionParcel(
        preparedId = preparedId.value,
        useCaseId = useCaseId.value,
        capabilityRevision = capabilityRevision,
        preset = preset?.toConsumerWire(),
        reasoningModeTag = reasoningMode.name,
        outputConstraintTag = outputConstraint.name,
        sessionKindTag = sessionKind.name,
    )

fun ConsumerPreparedSelectionParcel.toCorePreparedSelection(): ConsumerPreparedSelection =
    ConsumerPreparedSelection(
        preparedId = ConsumerPreparedId(preparedId),
        useCaseId = UseCaseId(useCaseId),
        capabilityRevision = capabilityRevision,
        preset = preset?.toCorePreset(),
        reasoningMode = enumTag(reasoningModeTag, "consumer reasoning mode"),
        outputConstraint = enumTag(outputConstraintTag, "consumer output constraint"),
        sessionKind = enumTag(sessionKindTag, "consumer session kind"),
    )

fun ConsumerGenerationInput.toConsumerWire(): ConsumerGenerationInputParcel =
    when (this) {
        is ConsumerGenerationInput.Text ->
            ConsumerGenerationInputParcel(WireTags.INPUT_TEXT, value, emptyList())

        is ConsumerGenerationInput.Messages ->
            ConsumerGenerationInputParcel(
                typeTag = WireTags.INPUT_MESSAGES,
                text = null,
                messages = values.map { ConversationMessageParcel(it.role.name, it.content) },
            )
    }

fun ConsumerGenerationInputParcel.toCoreConsumerInput(): ConsumerGenerationInput =
    when (typeTag) {
        WireTags.INPUT_TEXT -> ConsumerGenerationInput.Text(requireNotNull(text))
        WireTags.INPUT_MESSAGES ->
            ConsumerGenerationInput.Messages(
                messages.map {
                    ConversationMessage(
                        role = enumTag<ConversationRole>(it.roleTag, "conversation role"),
                        content = it.content,
                    )
                },
            )

        else -> throw invalidWireTag("consumer input", typeTag)
    }

fun ConsumerOutputConstraint.toConsumerWire(): ConsumerOutputConstraintParcel =
    when (this) {
        ConsumerOutputConstraint.Text ->
            ConsumerOutputConstraintParcel(WireTags.CONSTRAINT_TEXT, null)

        ConsumerOutputConstraint.Json ->
            ConsumerOutputConstraintParcel(WireTags.CONSTRAINT_JSON, null)

        is ConsumerOutputConstraint.JsonSchema ->
            ConsumerOutputConstraintParcel(WireTags.CONSTRAINT_JSON_SCHEMA, schema)
    }

fun ConsumerOutputConstraintParcel.toCoreConsumerOutput(): ConsumerOutputConstraint =
    when (typeTag) {
        WireTags.CONSTRAINT_TEXT -> ConsumerOutputConstraint.Text
        WireTags.CONSTRAINT_JSON -> ConsumerOutputConstraint.Json
        WireTags.CONSTRAINT_JSON_SCHEMA -> ConsumerOutputConstraint.JsonSchema(requireNotNull(jsonSchema))
        else -> throw invalidWireTag("consumer output constraint", typeTag)
    }

fun ConsumerExecutionIdentity.toConsumerWire(): ConsumerExecutionIdentityParcel =
    ConsumerExecutionIdentityParcel(
        useCaseId = useCaseId.value,
        capabilityRevision = capabilityRevision,
        preset = preset?.toConsumerWire(),
        reasoningModeTag = reasoningMode.name,
        outputConstraintTag = outputConstraint.name,
        sessionKindTag = sessionKind.name,
    )

fun ConsumerExecutionIdentityParcel.toCoreExecutionIdentity(): ConsumerExecutionIdentity =
    ConsumerExecutionIdentity(
        useCaseId = UseCaseId(useCaseId),
        capabilityRevision = capabilityRevision,
        preset = preset?.toCorePreset(),
        reasoningMode = enumTag(reasoningModeTag, "consumer reasoning mode"),
        outputConstraint = enumTag(outputConstraintTag, "consumer output constraint"),
        sessionKind = enumTag(sessionKindTag, "consumer session kind"),
    )

fun ConsumerInferenceMetrics.toConsumerWire(): ConsumerInferenceMetricsParcel =
    ConsumerInferenceMetricsParcel(
        outputTokens = outputTokens,
        timeToFirstTokenMs = timeToFirstTokenMs,
        totalMs = totalMs,
        decodeTokensPerSecond = decodeTokensPerSecond,
        inputTokens = inputTokens,
        reasoningTokens = reasoningTokens,
        answerTokens = answerTokens,
        queueMs = queueMs,
        stopReasonTag = stopReason.name,
    )

fun ConsumerInferenceMetricsParcel.toCoreConsumerMetrics(): ConsumerInferenceMetrics =
    ConsumerInferenceMetrics(
        outputTokens = outputTokens,
        timeToFirstTokenMs = timeToFirstTokenMs,
        totalMs = totalMs,
        decodeTokensPerSecond = decodeTokensPerSecond,
        inputTokens = inputTokens,
        reasoningTokens = reasoningTokens,
        answerTokens = answerTokens,
        queueMs = queueMs,
        stopReason = enumTag(stopReasonTag, "consumer stop reason"),
    )

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

private fun InferencePresetRef.toConsumerWire() = ConsumerPresetParcel(id.value, version)

private fun ConsumerPresetParcel.toCorePreset() = InferencePresetRef(InferencePresetId(id), version)

private fun ConsumerLimits.toConsumerWire() =
    ConsumerLimitsParcel(maxInputCharacters, maxConversationMessages, maxJsonSchemaCharacters)

private fun ConsumerLimitsParcel.toCoreLimits() =
    ConsumerLimits(maxInputCharacters, maxConversationMessages, maxJsonSchemaCharacters)

internal inline fun <reified T : Enum<T>> enumTag(value: String, label: String): T =
    enumTagOrNull<T>(value) ?: throw invalidWireTag(label, value)

internal inline fun <reified T : Enum<T>> enumTagOrNull(value: String): T? =
    enumValues<T>().firstOrNull { it.name == value }
