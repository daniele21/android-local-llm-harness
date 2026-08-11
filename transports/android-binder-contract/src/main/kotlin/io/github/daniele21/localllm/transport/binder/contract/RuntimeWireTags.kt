package io.github.daniele21.localllm.transport.binder.contract

import io.github.daniele21.localllm.contracts.ChatTemplateSource
import io.github.daniele21.localllm.contracts.ModelLoadKind
import io.github.daniele21.localllm.contracts.SeedPolicyType
import io.github.daniele21.localllm.contracts.StopReason

internal fun SeedPolicyType.toWireTag() = when (this) {
    SeedPolicyType.RANDOM -> WireTags.SEED_RANDOM
    SeedPolicyType.FIXED -> WireTags.SEED_FIXED
}

internal fun String.toCoreSeedPolicyType() = when (this) {
    WireTags.SEED_RANDOM -> SeedPolicyType.RANDOM
    WireTags.SEED_FIXED -> SeedPolicyType.FIXED
    else -> throw invalidWireTag("seed policy type", this)
}

internal fun ChatTemplateSource.toWireTag() = when (this) {
    ChatTemplateSource.APPLICATION_OVERRIDE -> "APPLICATION_OVERRIDE"
    ChatTemplateSource.GGUF -> "GGUF"
    ChatTemplateSource.FAMILY_FALLBACK -> "FAMILY_FALLBACK"
    ChatTemplateSource.RAW_COMPLETION -> "RAW_COMPLETION"
}

internal fun String.toCoreChatTemplateSource() = when (this) {
    "APPLICATION_OVERRIDE" -> ChatTemplateSource.APPLICATION_OVERRIDE
    "GGUF" -> ChatTemplateSource.GGUF
    "FAMILY_FALLBACK" -> ChatTemplateSource.FAMILY_FALLBACK
    "RAW_COMPLETION" -> ChatTemplateSource.RAW_COMPLETION
    else -> throw invalidWireTag("chat template source", this)
}

internal fun ModelLoadKind.toWireTag() = when (this) {
    ModelLoadKind.COLD -> "COLD"
    ModelLoadKind.WARM -> "WARM"
    ModelLoadKind.UNKNOWN -> "UNKNOWN"
}

internal fun String.toCoreModelLoadKind() = when (this) {
    "COLD" -> ModelLoadKind.COLD
    "WARM" -> ModelLoadKind.WARM
    else -> ModelLoadKind.UNKNOWN
}

internal fun StopReason.toWireTag() = when (this) {
    StopReason.END_OF_GENERATION -> "END_OF_GENERATION"
    StopReason.MAX_OUTPUT_TOKENS -> "MAX_OUTPUT_TOKENS"
    StopReason.STOP_SEQUENCE -> "STOP_SEQUENCE"
    StopReason.GRAMMAR_COMPLETE -> "GRAMMAR_COMPLETE"
    StopReason.GENERATION_GUARD_REPETITION -> "GENERATION_GUARD_REPETITION"
    StopReason.GENERATION_GUARD_THINKING_BUDGET -> "GENERATION_GUARD_THINKING_BUDGET"
    StopReason.UNKNOWN -> "UNKNOWN"
}

internal fun String.toCoreStopReason() = when (this) {
    "END_OF_GENERATION" -> StopReason.END_OF_GENERATION
    "MAX_OUTPUT_TOKENS" -> StopReason.MAX_OUTPUT_TOKENS
    "STOP_SEQUENCE" -> StopReason.STOP_SEQUENCE
    "GRAMMAR_COMPLETE" -> StopReason.GRAMMAR_COMPLETE
    "GENERATION_GUARD_REPETITION" -> StopReason.GENERATION_GUARD_REPETITION
    "GENERATION_GUARD_THINKING_BUDGET" -> StopReason.GENERATION_GUARD_THINKING_BUDGET
    else -> StopReason.UNKNOWN
}
