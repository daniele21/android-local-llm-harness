package io.github.daniele21.localllm.transport.binder.contract

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

object BinderProtocolV1 {
    const val MAJOR = 1
    const val MINOR = 1
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
    const val FEATURE_CONSUMER_API_V1 = "consumer-api-v1"

    val KNOWN_FEATURES: Set<String> =
        setOf(
            FEATURE_MESSAGE_INPUT,
            FEATURE_RAW_COMPLETION,
            FEATURE_JSON_CONSTRAINT,
            FEATURE_JSON_SCHEMA_CONSTRAINT,
            FEATURE_REASONING_CONTENT,
            FEATURE_THINKING_MODE,
            FEATURE_SESSION_OPTIONS,
            FEATURE_CONSUMER_API_V1,
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
data class PrepareRequestParcel(val clientToken: ClientTokenParcel, val operationId: String, val useCaseId: String) : Parcelable

@Parcelize
data class PrepareResultParcel(
    val operationId: String,
    val ready: Boolean,
    val modelDigestSha256: String?,
    val detail: String,
    val error: WireErrorParcel?,
) : Parcelable

@Parcelize
data class SessionOptionsParcel(val contextPolicyTag: String, val manualContextTokens: Int?, val sessionKindTag: String) : Parcelable

@Parcelize
data class OpenSessionRequestParcel(
    val clientToken: ClientTokenParcel,
    val operationId: String,
    val externalSessionId: String,
    val useCaseId: String,
    val options: SessionOptionsParcel,
) : Parcelable

@Parcelize
data class SessionResultParcel(val operationId: String, val externalSessionId: String?, val error: WireErrorParcel?) : Parcelable

@Parcelize
data class ConversationMessageParcel(val roleTag: String, val content: String) : Parcelable

@Parcelize
data class GenerationInputParcel(val typeTag: String, val text: String?, val messages: List<ConversationMessageParcel>) : Parcelable

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
data class OutputConstraintParcel(val typeTag: String, val jsonSchema: String?) : Parcelable

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
data class CancelRequestParcel(val clientToken: ClientTokenParcel, val externalRequestId: String) : Parcelable

@Parcelize
data class CloseSessionRequestParcel(val clientToken: ClientTokenParcel, val externalSessionId: String) : Parcelable

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
data class WireErrorParcel(val code: String, val safeMessage: String, val retryable: Boolean) : Parcelable

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
