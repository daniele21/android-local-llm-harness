package io.github.daniele21.localllm.transport.binder.contract

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

object BinderProtocolV1 {
    const val MAJOR = 1
    const val MINOR = 0
    const val MIN_SUPPORTED_MINOR = 0

    const val MAX_IDENTIFIER_CHARACTERS = 128
    const val MAX_CLIENT_BUILD_ID_CHARACTERS = 128
    const val MAX_USE_CASE_ID_CHARACTERS = 128
    const val MAX_GENERATION_INPUT_CHARACTERS = 32_768
    const val MAX_CONVERSATION_MESSAGES = 128
    const val MAX_JSON_SCHEMA_CHARACTERS = 32_768
    const val MAX_DELTA_CHARACTERS = 4_096
    const val MAX_ESTIMATED_PARCEL_BYTES = 128 * 1_024

    const val FEATURE_MESSAGE_INPUT = "message-input"
    const val FEATURE_RAW_COMPLETION = "raw-completion"
    const val FEATURE_JSON_CONSTRAINT = "json-constraint"
    const val FEATURE_JSON_SCHEMA_CONSTRAINT = "json-schema-constraint"
    const val FEATURE_REASONING_CONTENT = "reasoning-content"
    const val FEATURE_THINKING_MODE = "thinking-mode"
    const val FEATURE_SESSION_OPTIONS = "session-options"

    val KNOWN_FEATURES: Set<String> =
        setOf(
            FEATURE_MESSAGE_INPUT,
            FEATURE_RAW_COMPLETION,
            FEATURE_JSON_CONSTRAINT,
            FEATURE_JSON_SCHEMA_CONSTRAINT,
            FEATURE_REASONING_CONTENT,
            FEATURE_THINKING_MODE,
            FEATURE_SESSION_OPTIONS,
        )
}

object WireTags {
    const val INPUT_TEXT = "TEXT"
    const val INPUT_MESSAGES = "MESSAGES"
    const val INPUT_RAW_COMPLETION = "RAW_COMPLETION"

    const val ROLE_USER = "USER"
    const val ROLE_ASSISTANT = "ASSISTANT"

    const val CONSTRAINT_TEXT = "TEXT"
    const val CONSTRAINT_JSON = "JSON"
    const val CONSTRAINT_JSON_SCHEMA = "JSON_SCHEMA"

    const val CONTEXT_AUTO = "AUTO"
    const val CONTEXT_MANUAL = "MANUAL"

    const val SESSION_STATELESS = "STATELESS"
    const val SESSION_CONVERSATIONAL = "CONVERSATIONAL"

    const val SEED_RANDOM = "RANDOM"
    const val SEED_FIXED = "FIXED"

    const val THINKING_ENABLED = "ENABLED"
    const val THINKING_DISABLED = "DISABLED"

    const val EVENT_QUEUED = "QUEUED"
    const val EVENT_PREPARED = "PREPARED"
    const val EVENT_STARTED = "STARTED"
    const val EVENT_TEXT_DELTA = "TEXT_DELTA"
    const val EVENT_COMPLETED = "COMPLETED"
    const val EVENT_FAILED = "FAILED"

    const val CONTENT_REASONING = "REASONING"
    const val CONTENT_ANSWER = "ANSWER"
}

object WireErrorCodes {
    const val PROTOCOL_INCOMPATIBLE = "PROTOCOL_INCOMPATIBLE"
    const val FEATURE_UNAVAILABLE = "FEATURE_UNAVAILABLE"
    const val INVALID_WIRE_REQUEST = "INVALID_WIRE_REQUEST"
    const val CLIENT_NOT_REGISTERED = "CLIENT_NOT_REGISTERED"
    const val CLIENT_TOKEN_INVALID = "CLIENT_TOKEN_INVALID"
    const val UNAUTHORIZED_USE_CASE = "UNAUTHORIZED_USE_CASE"
    const val MODEL_UNAVAILABLE = "MODEL_UNAVAILABLE"
    const val PREPARATION_FAILED = "PREPARATION_FAILED"
    const val SESSION_UNAVAILABLE = "SESSION_UNAVAILABLE"
    const val CANCELLED = "CANCELLED"
    const val CLIENT_DISCONNECTED = "CLIENT_DISCONNECTED"
    const val SERVICE_DISCONNECTED = "SERVICE_DISCONNECTED"
    const val CLIENT_BACKPRESSURE = "CLIENT_BACKPRESSURE"
    const val PAYLOAD_TOO_LARGE = "PAYLOAD_TOO_LARGE"
    const val TRANSPORT_FAILURE = "TRANSPORT_FAILURE"
    const val RUNTIME_FAILURE = "RUNTIME_FAILURE"
}

@Parcelize
data class ProtocolInfoParcel(
    val protocolMajor: Int,
    val protocolMinor: Int,
    val minSupportedMinor: Int,
    val supportedFeatures: List<String>,
    val hostBuildId: String,
) : Parcelable

@Parcelize
data class ClientHelloParcel(
    val protocolMajor: Int,
    val protocolMinor: Int,
    val minSupportedMinor: Int,
    val requiredFeatures: List<String>,
    val clientBuildId: String,
) : Parcelable

@Parcelize
data class ClientTokenParcel(val value: String) : Parcelable

@Parcelize
data class RegistrationResultParcel(
    val clientToken: ClientTokenParcel?,
    val negotiatedMinor: Int?,
    val enabledFeatures: List<String>,
    val error: WireErrorParcel?,
) : Parcelable

@Parcelize
data class PrepareRequestParcel(
    val clientToken: ClientTokenParcel,
    val operationId: String,
    val useCaseId: String,
) : Parcelable

@Parcelize
data class PrepareResultParcel(
    val operationId: String,
    val ready: Boolean,
    val modelDigestSha256: String?,
    val detail: String,
    val error: WireErrorParcel?,
) : Parcelable

@Parcelize
data class SessionOptionsParcel(
    val contextPolicyTag: String,
    val manualContextTokens: Int?,
    val sessionKindTag: String,
) : Parcelable

@Parcelize
data class OpenSessionRequestParcel(
    val clientToken: ClientTokenParcel,
    val operationId: String,
    val externalSessionId: String,
    val useCaseId: String,
    val options: SessionOptionsParcel,
) : Parcelable

@Parcelize
data class SessionResultParcel(
    val operationId: String,
    val externalSessionId: String?,
    val error: WireErrorParcel?,
) : Parcelable

@Parcelize
data class ConversationMessageParcel(
    val roleTag: String,
    val content: String,
) : Parcelable

@Parcelize
data class GenerationInputParcel(
    val typeTag: String,
    val text: String?,
    val messages: List<ConversationMessageParcel>,
) : Parcelable

@Parcelize
data class GenerationOverridesParcel(
    val presetId: String?,
    val presetVersion: Int?,
    val maxOutputTokens: Int?,
    val temperature: Float?,
    val topP: Float?,
    val topK: Int?,
    val seedPolicyTag: String?,
    val seedValue: Long?,
    val repeatPenalty: Float?,
    val repeatLastN: Int?,
    val thinkingModeTag: String?,
    val minP: Float?,
    val presencePenalty: Float?,
) : Parcelable

@Parcelize
data class OutputConstraintParcel(
    val typeTag: String,
    val jsonSchema: String?,
) : Parcelable

@Parcelize
data class GenerationRequestParcel(
    val clientToken: ClientTokenParcel,
    val externalRequestId: String,
    val externalSessionId: String,
    val useCaseId: String,
    val input: GenerationInputParcel,
    val overrides: GenerationOverridesParcel,
    val outputConstraint: OutputConstraintParcel,
) : Parcelable

@Parcelize
data class CancelRequestParcel(
    val clientToken: ClientTokenParcel,
    val externalRequestId: String,
) : Parcelable

@Parcelize
data class CloseSessionRequestParcel(
    val clientToken: ClientTokenParcel,
    val externalSessionId: String,
) : Parcelable

@Parcelize
data class EffectiveGenerationMetadataParcel(
    val presetId: String?,
    val presetVersion: Int?,
    val temperature: Float,
    val topP: Float,
    val topK: Int,
    val repeatPenalty: Float,
    val repeatLastN: Int,
    val requestedSeedPolicyTag: String,
    val effectiveSeed: Long,
    val maxOutputTokens: Int,
    val contextSize: Int,
    val promptTokenCount: Int,
    val chatTemplateId: String,
    val chatTemplateSource: String,
    val systemPromptVersion: String?,
    val thinkingModeTag: String,
    val minP: Float,
    val presencePenalty: Float,
) : Parcelable

@Parcelize
data class GenerationMetricsParcel(
    val queueMs: Long,
    val modelLoadMs: Long?,
    val timeToFirstTokenMs: Long?,
    val totalMs: Long,
    val inputTokens: Int?,
    val outputTokens: Int?,
    val decodeTokensPerSecond: Double?,
    val prefillMs: Long?,
    val decodeMs: Long?,
    val modelLoadKind: String,
    val stopReason: String,
    val promptPlanningMs: Long?,
    val contextCreationMs: Long?,
    val timeToFirstAnswerMs: Long?,
    val reasoningTokens: Int?,
    val answerTokens: Int?,
) : Parcelable

@Parcelize
data class WireErrorParcel(
    val code: String,
    val safeMessage: String,
    val retryable: Boolean,
) : Parcelable

@Parcelize
data class GenerationEventParcel(
    val externalRequestId: String,
    val sequence: Long,
    val eventTag: String,
    val queuePosition: Int? = null,
    val modelDigestSha256: String? = null,
    val preparedConfiguration: EffectiveGenerationMetadataParcel? = null,
    val deltaText: String? = null,
    val generatedTokens: Int? = null,
    val contentTypeTag: String? = null,
    val metrics: GenerationMetricsParcel? = null,
    val error: WireErrorParcel? = null,
) : Parcelable

class WireProtocolException(
    val wireCode: String,
    override val message: String,
) : IllegalArgumentException(message)

data class NegotiatedProtocol(
    val minor: Int,
    val enabledFeatures: Set<String>,
)

fun negotiateProtocol(host: ProtocolInfoParcel, client: ClientHelloParcel): NegotiatedProtocol {
    validateProtocolInfo(host)
    validateClientHello(client)

    if (host.protocolMajor != client.protocolMajor) {
        throw WireProtocolException(
            WireErrorCodes.PROTOCOL_INCOMPATIBLE,
            "Protocol major versions are incompatible",
        )
    }

    val lowerBound = maxOf(host.minSupportedMinor, client.minSupportedMinor)
    val upperBound = minOf(host.protocolMinor, client.protocolMinor)
    if (lowerBound > upperBound) {
        throw WireProtocolException(
            WireErrorCodes.PROTOCOL_INCOMPATIBLE,
            "Protocol minor ranges do not overlap",
        )
    }

    val supported = host.supportedFeatures.toSet()
    val unavailable = client.requiredFeatures.filterNot(supported::contains)
    if (unavailable.isNotEmpty()) {
        throw WireProtocolException(
            WireErrorCodes.FEATURE_UNAVAILABLE,
            "Required protocol feature is unavailable",
        )
    }

    return NegotiatedProtocol(
        minor = upperBound,
        enabledFeatures = supported.intersect(BinderProtocolV1.KNOWN_FEATURES),
    )
}

fun validateProtocolInfo(value: ProtocolInfoParcel) {
    requireProtocol(value.protocolMajor > 0, "Protocol major must be positive")
    requireProtocol(value.protocolMinor >= 0, "Protocol minor must be non-negative")
    requireProtocol(value.minSupportedMinor in 0..value.protocolMinor, "Invalid host minor range")
    validateFeatureList(value.supportedFeatures)
    validateIdentifier(value.hostBuildId, BinderProtocolV1.MAX_CLIENT_BUILD_ID_CHARACTERS, "host build ID")
}

fun validateClientHello(value: ClientHelloParcel) {
    requireProtocol(value.protocolMajor > 0, "Protocol major must be positive")
    requireProtocol(value.protocolMinor >= 0, "Protocol minor must be non-negative")
    requireProtocol(value.minSupportedMinor in 0..value.protocolMinor, "Invalid client minor range")
    validateFeatureList(value.requiredFeatures)
    validateIdentifier(value.clientBuildId, BinderProtocolV1.MAX_CLIENT_BUILD_ID_CHARACTERS, "client build ID")
}

fun validatePrepareRequest(value: PrepareRequestParcel) {
    validateToken(value.clientToken)
    validateIdentifier(value.operationId, BinderProtocolV1.MAX_IDENTIFIER_CHARACTERS, "operation ID")
    validateIdentifier(value.useCaseId, BinderProtocolV1.MAX_USE_CASE_ID_CHARACTERS, "use-case ID")
}

fun validateOpenSessionRequest(value: OpenSessionRequestParcel) {
    validateToken(value.clientToken)
    validateIdentifier(value.operationId, BinderProtocolV1.MAX_IDENTIFIER_CHARACTERS, "operation ID")
    validateIdentifier(value.externalSessionId, BinderProtocolV1.MAX_IDENTIFIER_CHARACTERS, "session correlation ID")
    validateIdentifier(value.useCaseId, BinderProtocolV1.MAX_USE_CASE_ID_CHARACTERS, "use-case ID")
    validateSessionOptions(value.options)
}

fun validateGenerationRequest(value: GenerationRequestParcel) {
    validateToken(value.clientToken)
    validateIdentifier(value.externalRequestId, BinderProtocolV1.MAX_IDENTIFIER_CHARACTERS, "request correlation ID")
    validateIdentifier(value.externalSessionId, BinderProtocolV1.MAX_IDENTIFIER_CHARACTERS, "session correlation ID")
    validateIdentifier(value.useCaseId, BinderProtocolV1.MAX_USE_CASE_ID_CHARACTERS, "use-case ID")
    validateInput(value.input)
    validateOverrides(value.overrides)
    validateOutputConstraint(value.outputConstraint)

    if (estimateGenerationRequestBytes(value) > BinderProtocolV1.MAX_ESTIMATED_PARCEL_BYTES) {
        throw WireProtocolException(WireErrorCodes.PAYLOAD_TOO_LARGE, "Generation request exceeds protocol payload limit")
    }
}

fun validateCancelRequest(value: CancelRequestParcel) {
    validateToken(value.clientToken)
    validateIdentifier(value.externalRequestId, BinderProtocolV1.MAX_IDENTIFIER_CHARACTERS, "request correlation ID")
}

fun validateCloseSessionRequest(value: CloseSessionRequestParcel) {
    validateToken(value.clientToken)
    validateIdentifier(value.externalSessionId, BinderProtocolV1.MAX_IDENTIFIER_CHARACTERS, "session correlation ID")
}

fun validateGenerationEvent(value: GenerationEventParcel) {
    validateIdentifier(value.externalRequestId, BinderProtocolV1.MAX_IDENTIFIER_CHARACTERS, "request correlation ID")
    requireProtocol(value.sequence >= 0, "Event sequence must be non-negative")

    when (value.eventTag) {
        WireTags.EVENT_QUEUED -> {
            requireProtocol(value.queuePosition != null && value.queuePosition >= 0, "QUEUED requires a valid position")
            requireNoTerminalPayload(value)
        }
        WireTags.EVENT_PREPARED -> {
            requireProtocol(value.preparedConfiguration != null, "PREPARED requires configuration")
            requireProtocol(!value.modelDigestSha256.isNullOrBlank(), "PREPARED requires model digest")
        }
        WireTags.EVENT_STARTED -> requireProtocol(!value.modelDigestSha256.isNullOrBlank(), "STARTED requires model digest")
        WireTags.EVENT_TEXT_DELTA -> {
            val text = value.deltaText ?: throw invalid("TEXT_DELTA requires text")
            requireProtocol(text.isNotEmpty(), "TEXT_DELTA must not be empty")
            requireProtocol(text.length <= BinderProtocolV1.MAX_DELTA_CHARACTERS, "TEXT_DELTA exceeds chunk limit")
            requireProtocol(value.generatedTokens != null && value.generatedTokens >= 0, "TEXT_DELTA requires generated token count")
            requireProtocol(
                value.contentTypeTag == WireTags.CONTENT_REASONING || value.contentTypeTag == WireTags.CONTENT_ANSWER,
                "TEXT_DELTA has an invalid content type",
            )
        }
        WireTags.EVENT_COMPLETED -> {
            requireProtocol(value.metrics != null, "COMPLETED requires metrics")
            requireProtocol(value.error == null, "COMPLETED must not contain an error")
            requireProtocol(value.deltaText == null, "COMPLETED must not duplicate aggregate output")
        }
        WireTags.EVENT_FAILED -> {
            requireProtocol(value.error != null, "FAILED requires an error")
            requireProtocol(value.metrics == null, "FAILED must not contain terminal metrics")
        }
        else -> throw invalid("Unknown generation event tag")
    }
}

fun estimateGenerationRequestBytes(value: GenerationRequestParcel): Int {
    var characters =
        value.clientToken.value.length +
            value.externalRequestId.length +
            value.externalSessionId.length +
            value.useCaseId.length +
            (value.input.text?.length ?: 0) +
            value.input.messages.sumOf { it.roleTag.length + it.content.length } +
            (value.overrides.presetId?.length ?: 0) +
            (value.overrides.seedPolicyTag?.length ?: 0) +
            (value.overrides.thinkingModeTag?.length ?: 0) +
            value.outputConstraint.typeTag.length +
            (value.outputConstraint.jsonSchema?.length ?: 0)
    characters += value.input.typeTag.length
    return 1_024 + (characters * 4)
}

private fun validateToken(value: ClientTokenParcel) {
    validateIdentifier(value.value, BinderProtocolV1.MAX_IDENTIFIER_CHARACTERS, "client token")
}

private fun validateFeatureList(features: List<String>) {
    requireProtocol(features.size <= 64, "Too many protocol features")
    requireProtocol(features.toSet().size == features.size, "Protocol features must be unique")
    features.forEach { validateIdentifier(it, 64, "protocol feature") }
}

private fun validateSessionOptions(value: SessionOptionsParcel) {
    when (value.contextPolicyTag) {
        WireTags.CONTEXT_AUTO -> requireProtocol(value.manualContextTokens == null, "AUTO context must not carry a size")
        WireTags.CONTEXT_MANUAL -> requireProtocol(
            value.manualContextTokens != null && value.manualContextTokens > 0,
            "MANUAL context requires a positive size",
        )
        else -> throw invalid("Unknown context policy tag")
    }
    requireProtocol(
        value.sessionKindTag == WireTags.SESSION_STATELESS || value.sessionKindTag == WireTags.SESSION_CONVERSATIONAL,
        "Unknown session kind tag",
    )
}

private fun validateInput(value: GenerationInputParcel) {
    when (value.typeTag) {
        WireTags.INPUT_TEXT, WireTags.INPUT_RAW_COMPLETION -> {
            val text = value.text ?: throw invalid("Text input requires text")
            validateBoundedContent(text, BinderProtocolV1.MAX_GENERATION_INPUT_CHARACTERS, "generation input")
            requireProtocol(value.messages.isEmpty(), "Text input must not contain messages")
        }
        WireTags.INPUT_MESSAGES -> {
            requireProtocol(value.text == null, "Message input must not contain text")
            requireProtocol(value.messages.isNotEmpty(), "Message input must not be empty")
            requireProtocol(value.messages.size <= BinderProtocolV1.MAX_CONVERSATION_MESSAGES, "Too many conversation messages")
            var total = 0
            value.messages.forEach { message ->
                requireProtocol(
                    message.roleTag == WireTags.ROLE_USER || message.roleTag == WireTags.ROLE_ASSISTANT,
                    "Unknown conversation role tag",
                )
                validateBoundedContent(message.content, BinderProtocolV1.MAX_GENERATION_INPUT_CHARACTERS, "message content")
                total += message.content.length
            }
            requireProtocol(total <= BinderProtocolV1.MAX_GENERATION_INPUT_CHARACTERS, "Conversation input is too large")
        }
        else -> throw invalid("Unknown generation input tag")
    }
}

private fun validateOverrides(value: GenerationOverridesParcel) {
    requireProtocol((value.presetId == null) == (value.presetVersion == null), "Preset ID and version must be supplied together")
    value.presetId?.let { validateIdentifier(it, 64, "preset ID") }
    value.presetVersion?.let { requireProtocol(it > 0, "Preset version must be positive") }
    value.maxOutputTokens?.let { requireProtocol(it > 0, "maxOutputTokens must be positive") }
    value.temperature?.let { requireFinite(it, "temperature") }
    value.topP?.let {
        requireFinite(it, "topP")
        requireProtocol(it in 0f..1f, "topP must be in [0, 1]")
    }
    value.topK?.let { requireProtocol(it >= 0, "topK must be non-negative") }
    value.repeatPenalty?.let { requireFinite(it, "repeatPenalty") }
    value.repeatLastN?.let { requireProtocol(it >= 0, "repeatLastN must be non-negative") }
    value.minP?.let {
        requireFinite(it, "minP")
        requireProtocol(it in 0f..1f, "minP must be in [0, 1]")
    }
    value.presencePenalty?.let {
        requireFinite(it, "presencePenalty")
        requireProtocol(it in 0f..2f, "presencePenalty must be in [0, 2]")
    }

    when (value.seedPolicyTag) {
        null -> requireProtocol(value.seedValue == null, "Seed value requires a seed policy")
        WireTags.SEED_RANDOM -> requireProtocol(value.seedValue == null, "RANDOM seed must not carry a value")
        WireTags.SEED_FIXED -> requireProtocol(value.seedValue != null && value.seedValue >= 0, "FIXED seed requires a non-negative value")
        else -> throw invalid("Unknown seed policy tag")
    }

    if (value.thinkingModeTag != null) {
        requireProtocol(
            value.thinkingModeTag == WireTags.THINKING_ENABLED || value.thinkingModeTag == WireTags.THINKING_DISABLED,
            "Unknown thinking mode tag",
        )
    }
}

private fun validateOutputConstraint(value: OutputConstraintParcel) {
    when (value.typeTag) {
        WireTags.CONSTRAINT_TEXT, WireTags.CONSTRAINT_JSON ->
            requireProtocol(value.jsonSchema == null, "Non-schema constraint must not contain a schema")
        WireTags.CONSTRAINT_JSON_SCHEMA -> {
            val schema = value.jsonSchema ?: throw invalid("JSON_SCHEMA requires schema text")
            validateBoundedContent(schema, BinderProtocolV1.MAX_JSON_SCHEMA_CHARACTERS, "JSON schema")
        }
        else -> throw invalid("Unknown output constraint tag")
    }
}

private fun validateIdentifier(value: String, maxCharacters: Int, label: String) {
    requireProtocol(value.isNotBlank(), "$label must not be blank")
    requireProtocol('\u0000' !in value, "$label must not contain NUL")
    requireProtocol(value.length <= maxCharacters, "$label is too long")
}

private fun validateBoundedContent(value: String, maxCharacters: Int, label: String) {
    requireProtocol(value.isNotBlank(), "$label must not be blank")
    requireProtocol('\u0000' !in value, "$label must not contain NUL")
    requireProtocol(value.length <= maxCharacters, "$label exceeds protocol limit")
}

private fun requireFinite(value: Float, label: String) {
    requireProtocol(value.isFinite(), "$label must be finite")
}

private fun requireNoTerminalPayload(value: GenerationEventParcel) {
    requireProtocol(value.metrics == null && value.error == null && value.deltaText == null, "Event contains invalid payload")
}

private fun requireProtocol(condition: Boolean, message: String) {
    if (!condition) throw invalid(message)
}

private fun invalid(message: String) = WireProtocolException(WireErrorCodes.INVALID_WIRE_REQUEST, message)
