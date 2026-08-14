package io.github.daniele21.localllm.transport.binder.contract

import io.github.daniele21.localllm.contracts.ConsumerExecutionIdentity
import io.github.daniele21.localllm.contracts.ConsumerGenerationInput
import io.github.daniele21.localllm.contracts.ConsumerInferenceMetrics
import io.github.daniele21.localllm.contracts.ConsumerOutputConstraint
import io.github.daniele21.localllm.contracts.ConversationMessage
import io.github.daniele21.localllm.contracts.ConversationRole
import io.github.daniele21.localllm.contracts.UseCaseId

fun ConsumerGenerationInput.toConsumerWire(): ConsumerGenerationInputParcel = when (this) {
    is ConsumerGenerationInput.Text ->
        ConsumerGenerationInputParcel(WireTags.INPUT_TEXT, value, emptyList())

    is ConsumerGenerationInput.Messages ->
        ConsumerGenerationInputParcel(
            typeTag = WireTags.INPUT_MESSAGES,
            text = null,
            messages = values.map { ConversationMessageParcel(it.role.name, it.content) },
        )
}

fun ConsumerGenerationInputParcel.toCoreConsumerInput(): ConsumerGenerationInput = when (typeTag) {
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

fun ConsumerOutputConstraint.toConsumerWire(): ConsumerOutputConstraintParcel = when (this) {
    ConsumerOutputConstraint.Text -> ConsumerOutputConstraintParcel(WireTags.CONSTRAINT_TEXT, null)

    ConsumerOutputConstraint.Json -> ConsumerOutputConstraintParcel(WireTags.CONSTRAINT_JSON, null)

    is ConsumerOutputConstraint.JsonSchema ->
        ConsumerOutputConstraintParcel(WireTags.CONSTRAINT_JSON_SCHEMA, schema)
}

fun ConsumerOutputConstraintParcel.toCoreConsumerOutput(): ConsumerOutputConstraint = when (typeTag) {
    WireTags.CONSTRAINT_TEXT -> ConsumerOutputConstraint.Text
    WireTags.CONSTRAINT_JSON -> ConsumerOutputConstraint.Json
    WireTags.CONSTRAINT_JSON_SCHEMA -> ConsumerOutputConstraint.JsonSchema(requireNotNull(jsonSchema))
    else -> throw invalidWireTag("consumer output constraint", typeTag)
}

fun ConsumerExecutionIdentity.toConsumerWire(): ConsumerExecutionIdentityParcel = ConsumerExecutionIdentityParcel(
    useCaseId = useCaseId.value,
    capabilityRevision = capabilityRevision,
    preset = preset?.toConsumerWire(),
    reasoningModeTag = reasoningMode.name,
    outputConstraintTag = outputConstraint.name,
    sessionKindTag = sessionKind.name,
)

fun ConsumerExecutionIdentityParcel.toCoreExecutionIdentity(): ConsumerExecutionIdentity = ConsumerExecutionIdentity(
    useCaseId = UseCaseId(useCaseId),
    capabilityRevision = capabilityRevision,
    preset = preset?.toCorePreset(),
    reasoningMode = enumTag(reasoningModeTag, "consumer reasoning mode"),
    outputConstraint = enumTag(outputConstraintTag, "consumer output constraint"),
    sessionKind = enumTag(sessionKindTag, "consumer session kind"),
)

fun ConsumerInferenceMetrics.toConsumerWire(): ConsumerInferenceMetricsParcel = ConsumerInferenceMetricsParcel(
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

fun ConsumerInferenceMetricsParcel.toCoreConsumerMetrics(): ConsumerInferenceMetrics = ConsumerInferenceMetrics(
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
