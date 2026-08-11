package io.github.daniele21.localllm.transport.binder.contract

import io.github.daniele21.localllm.contracts.MAX_NATIVE_SEED

internal fun validateGenerationInput(value: GenerationInputParcel) {
    when (value.typeTag) {
        WireTags.INPUT_TEXT, WireTags.INPUT_RAW_COMPLETION -> validateTextInput(value)
        WireTags.INPUT_MESSAGES -> validateMessageInput(value)
        else -> throw invalidWireTag("generation input", value.typeTag)
    }
}

internal fun validateGenerationOverrides(value: GenerationOverridesParcel) {
    validatePresetOverrides(value)
    validateSamplingOverrides(value)
    validateSeedOverride(value)
    validateThinkingOverride(value)
}

internal fun validateOutputConstraint(value: OutputConstraintParcel) {
    when (value.typeTag) {
        WireTags.CONSTRAINT_TEXT, WireTags.CONSTRAINT_JSON ->
            requireWire(value.jsonSchema == null, "Non-schema constraint must not contain a schema")

        WireTags.CONSTRAINT_JSON_SCHEMA -> {
            val schema = value.jsonSchema ?: throw invalidWireTag("output constraint payload", value.typeTag)
            validateBoundedContent(schema, BinderProtocolV1.MAX_JSON_SCHEMA_CHARACTERS, "JSON schema")
        }

        else -> throw invalidWireTag("output constraint", value.typeTag)
    }
}

private fun validateTextInput(value: GenerationInputParcel) {
    val text = value.text ?: throw invalidWireTag("generation input payload", value.typeTag)
    validateBoundedContent(text, BinderProtocolV1.MAX_GENERATION_INPUT_CHARACTERS, "generation input")
    requireWire(value.messages.isEmpty(), "Text input must not contain messages")
}

private fun validateMessageInput(value: GenerationInputParcel) {
    requireWire(value.text == null, "Message input must not contain text")
    requireWire(value.messages.isNotEmpty(), "Message input must not be empty")
    requireWire(
        value.messages.size <= BinderProtocolV1.MAX_CONVERSATION_MESSAGES,
        "Too many conversation messages",
    )
    value.messages.forEach(::validateMessage)
    requireWire(
        value.messages.sumOf { it.content.length } <= BinderProtocolV1.MAX_GENERATION_INPUT_CHARACTERS,
        "Conversation input is too large",
    )
}

private fun validateMessage(message: ConversationMessageParcel) {
    requireWire(
        message.roleTag == WireTags.ROLE_USER || message.roleTag == WireTags.ROLE_ASSISTANT,
        "Unknown conversation role tag",
    )
    validateBoundedContent(
        message.content,
        BinderProtocolV1.MAX_GENERATION_INPUT_CHARACTERS,
        "message content",
    )
}

private fun validatePresetOverrides(value: GenerationOverridesParcel) {
    requireWire(
        (value.presetId == null) == (value.presetVersion == null),
        "Preset ID and version must be supplied together",
    )
    value.presetId?.let { validateIdentifier(it, 64, "preset ID") }
    value.presetVersion?.let { requireWire(it > 0, "Preset version must be positive") }
    value.maxOutputTokens?.let { requireWire(it > 0, "maxOutputTokens must be positive") }
}

private fun validateSamplingOverrides(value: GenerationOverridesParcel) {
    value.temperature?.let { requireFinite(it, "temperature") }
    value.topP?.let {
        requireFinite(it, "topP")
        requireWire(it in 0f..1f, "topP must be in [0, 1]")
    }
    value.topK?.let { requireWire(it >= 0, "topK must be non-negative") }
    value.repeatPenalty?.let { requireFinite(it, "repeatPenalty") }
    value.repeatLastN?.let { requireWire(it >= 0, "repeatLastN must be non-negative") }
    value.minP?.let {
        requireFinite(it, "minP")
        requireWire(it in 0f..1f, "minP must be in [0, 1]")
    }
    value.presencePenalty?.let {
        requireFinite(it, "presencePenalty")
        requireWire(it in 0f..2f, "presencePenalty must be in [0, 2]")
    }
}

private fun validateSeedOverride(value: GenerationOverridesParcel) {
    when (value.seedPolicyTag) {
        null -> requireWire(value.seedValue == null, "Seed value requires a seed policy")
        WireTags.SEED_RANDOM -> requireWire(value.seedValue == null, "RANDOM seed must not carry a value")
        WireTags.SEED_FIXED ->
            requireWire(
                value.seedValue != null && value.seedValue in 0..MAX_NATIVE_SEED,
                "FIXED seed must fit the native seed range",
            )

        else -> throw invalidWireTag("seed policy", value.seedPolicyTag)
    }
}

private fun validateThinkingOverride(value: GenerationOverridesParcel) {
    if (value.thinkingModeTag == null) return
    requireWire(
        value.thinkingModeTag == WireTags.THINKING_ENABLED ||
            value.thinkingModeTag == WireTags.THINKING_DISABLED,
        "Unknown thinking mode tag",
    )
}
