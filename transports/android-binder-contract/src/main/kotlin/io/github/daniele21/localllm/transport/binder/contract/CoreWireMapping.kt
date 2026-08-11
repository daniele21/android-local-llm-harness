package io.github.daniele21.localllm.transport.binder.contract

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ChatTemplateSource
import io.github.daniele21.localllm.contracts.ConfigurationErrorCode
import io.github.daniele21.localllm.contracts.ContextPolicy
import io.github.daniele21.localllm.contracts.ConversationMessage
import io.github.daniele21.localllm.contracts.ConversationRole
import io.github.daniele21.localllm.contracts.EffectiveGenerationMetadata
import io.github.daniele21.localllm.contracts.GenerationContentType
import io.github.daniele21.localllm.contracts.GenerationEvent
import io.github.daniele21.localllm.contracts.GenerationInput
import io.github.daniele21.localllm.contracts.GenerationMetrics
import io.github.daniele21.localllm.contracts.GenerationOverrides
import io.github.daniele21.localllm.contracts.GenerationRequest
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.LocalLlmError
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.ModelLoadKind
import io.github.daniele21.localllm.contracts.OutputConstraint
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.SeedPolicy
import io.github.daniele21.localllm.contracts.SeedPolicyType
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.SessionOptions
import io.github.daniele21.localllm.contracts.StopReason
import io.github.daniele21.localllm.contracts.ThinkingMode
import io.github.daniele21.localllm.contracts.UseCaseId

fun SessionOptions.toWire(): SessionOptionsParcel =
    SessionOptionsParcel(
        contextPolicyTag =
            when (contextPolicy) {
                ContextPolicy.Auto -> WireTags.CONTEXT_AUTO
                is ContextPolicy.Manual -> WireTags.CONTEXT_MANUAL
            },
        manualContextTokens = (contextPolicy as? ContextPolicy.Manual)?.tokens,
        sessionKindTag =
            when (kind) {
                SessionKind.STATELESS -> WireTags.SESSION_STATELESS
                SessionKind.CONVERSATIONAL -> WireTags.SESSION_CONVERSATIONAL
            },
    )

fun SessionOptionsParcel.toCore(): SessionOptions {
    val contextPolicy =
        when (contextPolicyTag) {
            WireTags.CONTEXT_AUTO -> ContextPolicy.Auto
            WireTags.CONTEXT_MANUAL -> ContextPolicy.Manual(requireNotNull(manualContextTokens))
            else -> throw invalidWireTag("context policy", contextPolicyTag)
        }
    val sessionKind =
        when (sessionKindTag) {
            WireTags.SESSION_STATELESS -> SessionKind.STATELESS
            WireTags.SESSION_CONVERSATIONAL -> SessionKind.CONVERSATIONAL
            else -> throw invalidWireTag("session kind", sessionKindTag)
        }
    return SessionOptions(contextPolicy = contextPolicy, kind = sessionKind)
}

fun GenerationRequest.toWire(clientToken: ClientTokenParcel): GenerationRequestParcel =
    GenerationRequestParcel(
        clientToken = clientToken,
        externalRequestId = requestId.value,
        externalSessionId = sessionId.value,
        useCaseId = useCaseId.value,
        input = input.toWire(),
        overrides = overrides.toWire(),
        outputConstraint = outputConstraint.toWire(),
    ).also(::validateGenerationRequest)

fun GenerationRequestParcel.toCore(
    applicationId: ApplicationId,
    internalSessionId: SessionId,
    internalRequestId: RequestId,
): GenerationRequest {
    validateGenerationRequest(this)
    return GenerationRequest(
        requestId = internalRequestId,
        sessionId = internalSessionId,
        applicationId = applicationId,
        useCaseId = UseCaseId(useCaseId),
        input = input.toCore(),
        overrides = overrides.toCore(),
        outputConstraint = outputConstraint.toCore(),
    )
}

fun GenerationEvent.toWire(externalRequestId: String, sequence: Long): GenerationEventParcel {
    val result =
        when (this) {
            is GenerationEvent.Queued ->
                GenerationEventParcel(
                    externalRequestId = externalRequestId,
                    sequence = sequence,
                    eventTag = WireTags.EVENT_QUEUED,
                    queuePosition = position,
                )
            is GenerationEvent.Prepared ->
                GenerationEventParcel(
                    externalRequestId = externalRequestId,
                    sequence = sequence,
                    eventTag = WireTags.EVENT_PREPARED,
                    modelDigestSha256 = modelDigest.sha256,
                    preparedConfiguration = configuration.toWire(),
                )
            is GenerationEvent.Started ->
                GenerationEventParcel(
                    externalRequestId = externalRequestId,
                    sequence = sequence,
                    eventTag = WireTags.EVENT_STARTED,
                    modelDigestSha256 = modelDigest.sha256,
                )
            is GenerationEvent.TextDelta -> {
                require(text.length <= BinderProtocolV1.MAX_DELTA_CHARACTERS) {
                    "Delta must be chunked before Binder mapping"
                }
                GenerationEventParcel(
                    externalRequestId = externalRequestId,
                    sequence = sequence,
                    eventTag = WireTags.EVENT_TEXT_DELTA,
                    deltaText = text,
                    generatedTokens = generatedTokens,
                    contentTypeTag = contentType.toWireTag(),
                )
            }
            is GenerationEvent.Completed ->
                GenerationEventParcel(
                    externalRequestId = externalRequestId,
                    sequence = sequence,
                    eventTag = WireTags.EVENT_COMPLETED,
                    metrics = metrics.toWire(),
                )
            is GenerationEvent.Failed ->
                GenerationEventParcel(
                    externalRequestId = externalRequestId,
                    sequence = sequence,
                    eventTag = WireTags.EVENT_FAILED,
                    error = error.toSafeWire(),
                )
        }
    validateGenerationEvent(result)
    return result
}

class GenerationEventReconstructor(
    private val externalRequestId: String,
    private val internalRequestId: RequestId,
) {
    private var nextSequence = 0L
    private var terminated = false
    private val reasoning = StringBuilder()
    private val answer = StringBuilder()

    fun accept(event: GenerationEventParcel): GenerationEvent {
        check(!terminated) { "Generation stream is already terminated" }
        validateGenerationEvent(event)
        if (event.externalRequestId != externalRequestId) {
            throw protocolFailure("Event request correlation does not match the active request")
        }
        if (event.sequence != nextSequence) {
            throw protocolFailure("Generation event sequence is not contiguous")
        }
        nextSequence += 1

        return when (event.eventTag) {
            WireTags.EVENT_QUEUED -> GenerationEvent.Queued(internalRequestId, requireNotNull(event.queuePosition))
            WireTags.EVENT_PREPARED ->
                GenerationEvent.Prepared(
                    internalRequestId,
                    ModelDigest(requireNotNull(event.modelDigestSha256)),
                    requireNotNull(event.preparedConfiguration).toCore(),
                )
            WireTags.EVENT_STARTED ->
                GenerationEvent.Started(
                    internalRequestId,
                    ModelDigest(requireNotNull(event.modelDigestSha256)),
                )
            WireTags.EVENT_TEXT_DELTA -> {
                val text = requireNotNull(event.deltaText)
                val contentType = event.contentTypeTag.toCoreContentType()
                when (contentType) {
                    GenerationContentType.REASONING -> reasoning.append(text)
                    GenerationContentType.ANSWER -> answer.append(text)
                }
                GenerationEvent.TextDelta(
                    requestId = internalRequestId,
                    text = text,
                    generatedTokens = requireNotNull(event.generatedTokens),
                    contentType = contentType,
                )
            }
            WireTags.EVENT_COMPLETED -> {
                terminated = true
                val answerText = answer.toString()
                GenerationEvent.Completed(
                    requestId = internalRequestId,
                    output = answerText,
                    metrics = requireNotNull(event.metrics).toCore(),
                    reasoningOutput = reasoning.toString(),
                    answerOutput = answerText,
                )
            }
            WireTags.EVENT_FAILED -> {
                terminated = true
                GenerationEvent.Failed(internalRequestId, requireNotNull(event.error).toCore())
            }
            else -> throw invalidWireTag("generation event", event.eventTag)
        }
    }
}

fun chunkDelta(text: String): List<String> {
    if (text.isEmpty()) return emptyList()
    val chunks = mutableListOf<String>()
    var start = 0
    while (start < text.length) {
        var end = minOf(start + BinderProtocolV1.MAX_DELTA_CHARACTERS, text.length)
        if (end < text.length && end > start && Character.isHighSurrogate(text[end - 1]) && Character.isLowSurrogate(text[end])) {
            end -= 1
        }
        if (end == start) {
            end = minOf(start + 2, text.length)
        }
        chunks += text.substring(start, end)
        start = end
    }
    return chunks
}

private fun GenerationInput.toWire(): GenerationInputParcel =
    when (this) {
        is GenerationInput.Text -> GenerationInputParcel(WireTags.INPUT_TEXT, value, emptyList())
        is GenerationInput.RawCompletion -> GenerationInputParcel(WireTags.INPUT_RAW_COMPLETION, value, emptyList())
        is GenerationInput.Messages ->
            GenerationInputParcel(
                typeTag = WireTags.INPUT_MESSAGES,
                text = null,
                messages = values.map { ConversationMessageParcel(it.role.toWireTag(), it.content) },
            )
    }

private fun GenerationInputParcel.toCore(): GenerationInput =
    when (typeTag) {
        WireTags.INPUT_TEXT -> GenerationInput.Text(requireNotNull(text))
        WireTags.INPUT_RAW_COMPLETION -> GenerationInput.RawCompletion(requireNotNull(text))
        WireTags.INPUT_MESSAGES ->
            GenerationInput.Messages(
                messages.map { ConversationMessage(it.roleTag.toCoreRole(), it.content) },
            )
        else -> throw invalidWireTag("generation input", typeTag)
    }

private fun GenerationOverrides.toWire(): GenerationOverridesParcel {
    val requestedSeed = requestedSeedPolicy()
    return GenerationOverridesParcel(
        presetId = preset?.id?.value,
        presetVersion = preset?.version,
        maxOutputTokens = maxOutputTokens,
        temperature = temperature,
        topP = topP,
        topK = topK,
        seedPolicyTag =
            when (requestedSeed) {
                null -> null
                SeedPolicy.Random -> WireTags.SEED_RANDOM
                is SeedPolicy.Fixed -> WireTags.SEED_FIXED
            },
        seedValue = (requestedSeed as? SeedPolicy.Fixed)?.value,
        repeatPenalty = repeatPenalty,
        repeatLastN = repeatLastN,
        thinkingModeTag = thinkingMode?.toWireTag(),
        minP = minP,
        presencePenalty = presencePenalty,
    )
}

private fun GenerationOverridesParcel.toCore(): GenerationOverrides {
    val preset = presetId?.let { InferencePresetRef(InferencePresetId(it), requireNotNull(presetVersion)) }
    val seedPolicy =
        when (seedPolicyTag) {
            null -> null
            WireTags.SEED_RANDOM -> SeedPolicy.Random
            WireTags.SEED_FIXED -> SeedPolicy.Fixed(requireNotNull(seedValue))
            else -> throw invalidWireTag("seed policy", seedPolicyTag)
        }
    val thinkingMode =
        when (thinkingModeTag) {
            null -> null
            WireTags.THINKING_ENABLED -> ThinkingMode.ENABLED
            WireTags.THINKING_DISABLED -> ThinkingMode.DISABLED
            else -> throw invalidWireTag("thinking mode", thinkingModeTag)
        }
    return GenerationOverrides(
        preset = preset,
        maxOutputTokens = maxOutputTokens,
        temperature = temperature,
        topP = topP,
        topK = topK,
        seedPolicy = seedPolicy,
        repeatPenalty = repeatPenalty,
        repeatLastN = repeatLastN,
        thinkingMode = thinkingMode,
        minP = minP,
        presencePenalty = presencePenalty,
    )
}

private fun OutputConstraint.toWire(): OutputConstraintParcel =
    when (this) {
        OutputConstraint.Text -> OutputConstraintParcel(WireTags.CONSTRAINT_TEXT, null)
        OutputConstraint.Json -> OutputConstraintParcel(WireTags.CONSTRAINT_JSON, null)
        is OutputConstraint.JsonSchema -> OutputConstraintParcel(WireTags.CONSTRAINT_JSON_SCHEMA, schema)
    }

private fun OutputConstraintParcel.toCore(): OutputConstraint =
    when (typeTag) {
        WireTags.CONSTRAINT_TEXT -> OutputConstraint.Text
        WireTags.CONSTRAINT_JSON -> OutputConstraint.Json
        WireTags.CONSTRAINT_JSON_SCHEMA -> OutputConstraint.JsonSchema(requireNotNull(jsonSchema))
        else -> throw invalidWireTag("output constraint", typeTag)
    }

private fun EffectiveGenerationMetadata.toWire() =
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

private fun EffectiveGenerationMetadataParcel.toCore() =
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

private fun GenerationMetrics.toWire() =
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

private fun GenerationMetricsParcel.toCore() =
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

private fun ConversationRole.toWireTag() =
    when (this) {
        ConversationRole.USER -> WireTags.ROLE_USER
        ConversationRole.ASSISTANT -> WireTags.ROLE_ASSISTANT
    }

private fun String.toCoreRole() =
    when (this) {
        WireTags.ROLE_USER -> ConversationRole.USER
        WireTags.ROLE_ASSISTANT -> ConversationRole.ASSISTANT
        else -> throw invalidWireTag("conversation role", this)
    }

private fun GenerationContentType.toWireTag() =
    when (this) {
        GenerationContentType.REASONING -> WireTags.CONTENT_REASONING
        GenerationContentType.ANSWER -> WireTags.CONTENT_ANSWER
    }

private fun String?.toCoreContentType() =
    when (this) {
        WireTags.CONTENT_REASONING -> GenerationContentType.REASONING
        WireTags.CONTENT_ANSWER -> GenerationContentType.ANSWER
        else -> throw invalidWireTag("content type", this)
    }

private fun ThinkingMode.toWireTag() =
    when (this) {
        ThinkingMode.ENABLED -> WireTags.THINKING_ENABLED
        ThinkingMode.DISABLED -> WireTags.THINKING_DISABLED
    }

private fun String.toCoreThinkingMode() =
    when (this) {
        WireTags.THINKING_ENABLED -> ThinkingMode.ENABLED
        WireTags.THINKING_DISABLED -> ThinkingMode.DISABLED
        else -> throw invalidWireTag("thinking mode", this)
    }

private fun SeedPolicyType.toWireTag() =
    when (this) {
        SeedPolicyType.RANDOM -> WireTags.SEED_RANDOM
        SeedPolicyType.FIXED -> WireTags.SEED_FIXED
    }

private fun String.toCoreSeedPolicyType() =
    when (this) {
        WireTags.SEED_RANDOM -> SeedPolicyType.RANDOM
        WireTags.SEED_FIXED -> SeedPolicyType.FIXED
        else -> throw invalidWireTag("seed policy type", this)
    }

private fun ChatTemplateSource.toWireTag() =
    when (this) {
        ChatTemplateSource.APPLICATION_OVERRIDE -> "APPLICATION_OVERRIDE"
        ChatTemplateSource.GGUF -> "GGUF"
        ChatTemplateSource.FAMILY_FALLBACK -> "FAMILY_FALLBACK"
        ChatTemplateSource.RAW_COMPLETION -> "RAW_COMPLETION"
    }

private fun String.toCoreChatTemplateSource() =
    when (this) {
        "APPLICATION_OVERRIDE" -> ChatTemplateSource.APPLICATION_OVERRIDE
        "GGUF" -> ChatTemplateSource.GGUF
        "FAMILY_FALLBACK" -> ChatTemplateSource.FAMILY_FALLBACK
        "RAW_COMPLETION" -> ChatTemplateSource.RAW_COMPLETION
        else -> throw invalidWireTag("chat template source", this)
    }

private fun ModelLoadKind.toWireTag() =
    when (this) {
        ModelLoadKind.COLD -> "COLD"
        ModelLoadKind.WARM -> "WARM"
        ModelLoadKind.UNKNOWN -> "UNKNOWN"
    }

private fun String.toCoreModelLoadKind() =
    when (this) {
        "COLD" -> ModelLoadKind.COLD
        "WARM" -> ModelLoadKind.WARM
        "UNKNOWN" -> ModelLoadKind.UNKNOWN
        else -> throw invalidWireTag("model load kind", this)
    }

private fun StopReason.toWireTag() =
    when (this) {
        StopReason.END_OF_GENERATION -> "END_OF_GENERATION"
        StopReason.MAX_OUTPUT_TOKENS -> "MAX_OUTPUT_TOKENS"
        StopReason.STOP_SEQUENCE -> "STOP_SEQUENCE"
        StopReason.GRAMMAR_COMPLETE -> "GRAMMAR_COMPLETE"
        StopReason.GENERATION_GUARD_REPETITION -> "GENERATION_GUARD_REPETITION"
        StopReason.GENERATION_GUARD_THINKING_BUDGET -> "GENERATION_GUARD_THINKING_BUDGET"
        StopReason.UNKNOWN -> "UNKNOWN"
    }

private fun String.toCoreStopReason() =
    when (this) {
        "END_OF_GENERATION" -> StopReason.END_OF_GENERATION
        "MAX_OUTPUT_TOKENS" -> StopReason.MAX_OUTPUT_TOKENS
        "STOP_SEQUENCE" -> StopReason.STOP_SEQUENCE
        "GRAMMAR_COMPLETE" -> StopReason.GRAMMAR_COMPLETE
        "GENERATION_GUARD_REPETITION" -> StopReason.GENERATION_GUARD_REPETITION
        "GENERATION_GUARD_THINKING_BUDGET" -> StopReason.GENERATION_GUARD_THINKING_BUDGET
        "UNKNOWN" -> StopReason.UNKNOWN
        else -> throw invalidWireTag("stop reason", this)
    }

private fun invalidWireTag(label: String, value: Any?): WireProtocolException =
    WireProtocolException(WireErrorCodes.INVALID_WIRE_REQUEST, "Unknown $label tag: $value")

private fun protocolFailure(message: String): WireProtocolException =
    WireProtocolException(WireErrorCodes.TRANSPORT_FAILURE, message)
