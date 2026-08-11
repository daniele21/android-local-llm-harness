package io.github.daniele21.localllm.transport.binder.contract

import io.github.daniele21.localllm.contracts.ConversationRole
import io.github.daniele21.localllm.contracts.GenerationContentType
import io.github.daniele21.localllm.contracts.ThinkingMode

internal fun ConversationRole.toWireTag() =
    when (this) {
        ConversationRole.USER -> WireTags.ROLE_USER
        ConversationRole.ASSISTANT -> WireTags.ROLE_ASSISTANT
    }

internal fun String.toCoreRole() =
    when (this) {
        WireTags.ROLE_USER -> ConversationRole.USER
        WireTags.ROLE_ASSISTANT -> ConversationRole.ASSISTANT
        else -> throw invalidWireTag("conversation role", this)
    }

internal fun GenerationContentType.toWireTag() =
    when (this) {
        GenerationContentType.REASONING -> WireTags.CONTENT_REASONING
        GenerationContentType.ANSWER -> WireTags.CONTENT_ANSWER
    }

internal fun String?.toCoreContentType() =
    when (this) {
        WireTags.CONTENT_REASONING -> GenerationContentType.REASONING
        WireTags.CONTENT_ANSWER -> GenerationContentType.ANSWER
        else -> throw invalidWireTag("content type", this)
    }

internal fun ThinkingMode.toWireTag() =
    when (this) {
        ThinkingMode.ENABLED -> WireTags.THINKING_ENABLED
        ThinkingMode.DISABLED -> WireTags.THINKING_DISABLED
    }

internal fun String.toCoreThinkingMode() =
    when (this) {
        WireTags.THINKING_ENABLED -> ThinkingMode.ENABLED
        WireTags.THINKING_DISABLED -> ThinkingMode.DISABLED
        else -> throw invalidWireTag("thinking mode", this)
    }
