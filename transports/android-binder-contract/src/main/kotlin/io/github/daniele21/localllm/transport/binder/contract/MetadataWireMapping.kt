package io.github.daniele21.localllm.transport.binder.contract

import io.github.daniele21.localllm.contracts.ConfigurationErrorCode
import io.github.daniele21.localllm.contracts.EffectiveGenerationMetadata
import io.github.daniele21.localllm.contracts.GenerationMetrics
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.LocalLlmError

internal fun EffectiveGenerationMetadata.toWire() =
    EffectiveGenerationMetadataParcel(
        presetId = preset?.id?.value,
        presetVersion = preset?.version,
        temperature = temperature,
        topP = topP,
        topK = topK,
        repeatPenalty = repeatPenalty,
        repeatLastN = repeatLastN,
        requestedSeedPolicyTag = requestedSeedPolicy.toWireTag(),
        effectiveSeed = effectiveSeed,
        maxOutputTokens = maxOutputTokens,
        contextSize = contextSize,
        promptTokenCount = promptTokenCount,
        chatTemplateId = chatTemplateId,
        chatTemplateSource = chatTemplateSource.toWireTag(),
        systemPromptVersion = systemPromptVersion,
        thinkingModeTag = thinkingMode.toWireTag(),
        minP = minP,
        presencePenalty = presencePenalty,
    )

internal fun EffectiveGenerationMetadataParcel.toCore() =
    EffectiveGenerationMetadata(
        preset = presetId?.let { InferencePresetRef(InferencePresetId(it), requireNotNull(presetVersion)) },
        temperature = temperature,
        topP = topP,
        topK = topK,
        repeatPenalty = repeatPenalty,
        repeatLastN = repeatLastN,
        requestedSeedPolicy = requestedSeedPolicyTag.toCoreSeedPolicyType(),
        effectiveSeed = effectiveSeed,
        maxOutputTokens = maxOutputTokens,
        contextSize = contextSize,
        promptTokenCount = promptTokenCount,
        chatTemplateId = chatTemplateId,
        chatTemplateSource = chatTemplateSource.toCoreChatTemplateSource(),
        systemPromptVersion = systemPromptVersion,
        thinkingMode = thinkingModeTag.toCoreThinkingMode(),
        minP = minP,
        presencePenalty = presencePenalty,
    )

internal fun GenerationMetrics.toWire() =
    GenerationMetricsParcel(
        queueMs = queueMs,
        modelLoadMs = modelLoadMs,
        timeToFirstTokenMs = timeToFirstTokenMs,
        totalMs = totalMs,
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        decodeTokensPerSecond = decodeTokensPerSecond,
        prefillMs = prefillMs,
        decodeMs = decodeMs,
        modelLoadKind = modelLoadKind.toWireTag(),
        stopReason = stopReason.toWireTag(),
        promptPlanningMs = promptPlanningMs,
        contextCreationMs = contextCreationMs,
        timeToFirstAnswerMs = timeToFirstAnswerMs,
        reasoningTokens = reasoningTokens,
        answerTokens = answerTokens,
    )

internal fun GenerationMetricsParcel.toCore() =
    GenerationMetrics(
        queueMs = queueMs,
        modelLoadMs = modelLoadMs,
        timeToFirstTokenMs = timeToFirstTokenMs,
        totalMs = totalMs,
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        decodeTokensPerSecond = decodeTokensPerSecond,
        prefillMs = prefillMs,
        decodeMs = decodeMs,
        modelLoadKind = modelLoadKind.toCoreModelLoadKind(),
        stopReason = stopReason.toCoreStopReason(),
        promptPlanningMs = promptPlanningMs,
        contextCreationMs = contextCreationMs,
        timeToFirstAnswerMs = timeToFirstAnswerMs,
        reasoningTokens = reasoningTokens,
        answerTokens = answerTokens,
    )

fun LocalLlmError.toSafeWire(): WireErrorParcel =
    when (this) {
        is LocalLlmError.Configuration ->
            WireErrorParcel(
                code = reason.name,
                safeMessage = "Generation configuration was rejected",
                retryable = false,
            )

        is LocalLlmError.ModelUnavailable ->
            WireErrorParcel(
                code = WireErrorCodes.MODEL_UNAVAILABLE,
                safeMessage = "The required local model is unavailable",
                retryable = true,
            )

        is LocalLlmError.NativeRuntime ->
            WireErrorParcel(
                code = WireErrorCodes.RUNTIME_FAILURE,
                safeMessage = "The local inference runtime failed",
                retryable = false,
            )

        is LocalLlmError.Cancelled ->
            WireErrorParcel(
                code = WireErrorCodes.CANCELLED,
                safeMessage = "Generation was cancelled",
                retryable = false,
            )
    }

fun WireErrorParcel.toCore(): LocalLlmError {
    val configurationReason = ConfigurationErrorCode.entries.firstOrNull { it.name == code }
    return when {
        configurationReason != null -> LocalLlmError.Configuration(safeMessage, configurationReason)
        code == WireErrorCodes.MODEL_UNAVAILABLE -> LocalLlmError.ModelUnavailable(safeMessage)
        code == WireErrorCodes.CANCELLED -> LocalLlmError.Cancelled(safeMessage)
        else -> LocalLlmError.NativeRuntime(safeMessage)
    }
}
