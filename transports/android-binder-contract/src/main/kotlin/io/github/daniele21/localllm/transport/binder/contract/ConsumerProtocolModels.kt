package io.github.daniele21.localllm.transport.binder.contract

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

object ConsumerWireTags {
    const val READINESS_READY = "READY"
    const val READINESS_AVAILABLE_REQUIRES_PREPARATION = "AVAILABLE_REQUIRES_PREPARATION"
    const val READINESS_UNAVAILABLE_MODEL = "UNAVAILABLE_MODEL"
    const val READINESS_UNAVAILABLE_HOST_POLICY = "UNAVAILABLE_HOST_POLICY"
    const val READINESS_INCOMPATIBLE = "INCOMPATIBLE"

    const val REASONING_NOT_SUPPORTED = "NOT_SUPPORTED"
    const val REASONING_SUPPORTED_NOT_SURFACED = "SUPPORTED_NOT_SURFACED"
    const val REASONING_SURFACED_OPTIONAL = "SURFACED_OPTIONAL"
    const val REASONING_SURFACED_REQUIRED_BY_POLICY = "SURFACED_REQUIRED_BY_POLICY"

    const val REASONING_PREFERENCE_DEFAULT = "DEFAULT"
    const val REASONING_PREFERENCE_DISABLED = "DISABLED"
    const val REASONING_PREFERENCE_SURFACED_IF_SUPPORTED = "SURFACED_IF_SUPPORTED"

    const val REASONING_MODE_DISABLED = "DISABLED"
    const val REASONING_MODE_SURFACED = "SURFACED"

    const val EVENT_QUEUED = "QUEUED"
    const val EVENT_PREPARED = "PREPARED"
    const val EVENT_STARTED = "STARTED"
    const val EVENT_CONTENT_DELTA = "CONTENT_DELTA"
    const val EVENT_COMPLETED = "COMPLETED"
    const val EVENT_FAILED = "FAILED"

    const val STOP_END_OF_GENERATION = "END_OF_GENERATION"
    const val STOP_MAX_OUTPUT_TOKENS = "MAX_OUTPUT_TOKENS"
    const val STOP_STOP_SEQUENCE = "STOP_SEQUENCE"
    const val STOP_GRAMMAR_COMPLETE = "GRAMMAR_COMPLETE"
    const val STOP_GENERATION_GUARD_REPETITION = "GENERATION_GUARD_REPETITION"
    const val STOP_GENERATION_GUARD_THINKING_BUDGET = "GENERATION_GUARD_THINKING_BUDGET"
    const val STOP_UNKNOWN = "UNKNOWN"
}

@Parcelize
data class ConsumerPresetParcel(val id: String, val version: Int) : Parcelable

@Parcelize
data class ConsumerPresetOptionParcel(val preset: ConsumerPresetParcel, val isDefault: Boolean) : Parcelable

@Parcelize
data class ConsumerLimitsParcel(
    val maxInputCharacters: Int,
    val maxConversationMessages: Int,
    val maxJsonSchemaCharacters: Int,
) : Parcelable

@Parcelize
data class ConsumerCapabilitiesParcel(
    val useCaseId: String,
    val readinessTag: String,
    val presets: List<ConsumerPresetOptionParcel>,
    val defaultPreset: ConsumerPresetParcel?,
    val reasoningTag: String,
    val outputConstraintTags: List<String>,
    val defaultOutputConstraintTag: String,
    val sessionKindTags: List<String>,
    val defaultSessionKindTag: String,
    val limits: ConsumerLimitsParcel,
    val capabilityRevision: String,
) : Parcelable

@Parcelize
data class ConsumerCapabilitiesRequestParcel(
    val clientToken: ClientTokenParcel,
    val operationId: String,
    val useCaseId: String,
) : Parcelable

@Parcelize
data class ConsumerCapabilitiesResultParcel(
    val operationId: String,
    val capabilities: ConsumerCapabilitiesParcel?,
    val error: WireErrorParcel?,
) : Parcelable

@Parcelize
data class ConsumerSelectionParcel(
    val capabilityRevision: String?,
    val preset: ConsumerPresetParcel?,
    val reasoningPreferenceTag: String,
    val outputConstraintTag: String?,
    val sessionKindTag: String?,
) : Parcelable

@Parcelize
data class ConsumerPrepareRequestParcel(
    val clientToken: ClientTokenParcel,
    val operationId: String,
    val useCaseId: String,
    val selection: ConsumerSelectionParcel,
) : Parcelable

@Parcelize
data class ConsumerPreparedSelectionParcel(
    val preparedId: String,
    val useCaseId: String,
    val capabilityRevision: String,
    val preset: ConsumerPresetParcel?,
    val reasoningModeTag: String,
    val outputConstraintTag: String,
    val sessionKindTag: String,
) : Parcelable

@Parcelize
data class ConsumerPrepareResultParcel(
    val operationId: String,
    val selection: ConsumerPreparedSelectionParcel?,
    val error: WireErrorParcel?,
) : Parcelable

@Parcelize
data class ConsumerCreateSessionRequestParcel(
    val clientToken: ClientTokenParcel,
    val operationId: String,
    val preparedId: String,
    val externalSessionId: String,
) : Parcelable

@Parcelize
data class ConsumerSessionResultParcel(
    val operationId: String,
    val externalSessionId: String?,
    val error: WireErrorParcel?,
) : Parcelable

@Parcelize
data class ConsumerGenerationInputParcel(
    val typeTag: String,
    val text: String?,
    val messages: List<ConversationMessageParcel>,
) : Parcelable

@Parcelize
data class ConsumerOutputConstraintParcel(val typeTag: String, val jsonSchema: String?) : Parcelable

@Parcelize
data class ConsumerGenerationRequestParcel(
    val clientToken: ClientTokenParcel,
    val externalRequestId: String,
    val externalSessionId: String,
    val input: ConsumerGenerationInputParcel,
    val outputConstraint: ConsumerOutputConstraintParcel,
) : Parcelable

@Parcelize
data class ConsumerExecutionIdentityParcel(
    val useCaseId: String,
    val capabilityRevision: String,
    val preset: ConsumerPresetParcel?,
    val reasoningModeTag: String,
    val outputConstraintTag: String,
    val sessionKindTag: String,
) : Parcelable

@Parcelize
data class ConsumerInferenceMetricsParcel(
    val outputTokens: Int?,
    val timeToFirstTokenMs: Long?,
    val totalMs: Long,
    val decodeTokensPerSecond: Double?,
    val inputTokens: Int?,
    val reasoningTokens: Int?,
    val answerTokens: Int?,
    val queueMs: Long,
    val stopReasonTag: String,
) : Parcelable

@Parcelize
data class ConsumerGenerationEventParcel(
    val externalRequestId: String,
    val sequence: Long,
    val eventTag: String,
    val queuePosition: Int? = null,
    val execution: ConsumerExecutionIdentityParcel? = null,
    val deltaText: String? = null,
    val contentTypeTag: String? = null,
    val metrics: ConsumerInferenceMetricsParcel? = null,
    val error: WireErrorParcel? = null,
) : Parcelable
