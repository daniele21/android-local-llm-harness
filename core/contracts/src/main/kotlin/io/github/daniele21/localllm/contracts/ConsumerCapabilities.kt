package io.github.daniele21.localllm.contracts

enum class UseCaseReadiness {
    READY,
    AVAILABLE_REQUIRES_PREPARATION,
    UNAVAILABLE_MODEL,
    UNAVAILABLE_HOST_POLICY,
    INCOMPATIBLE,
}

enum class ConsumerReasoningCapability {
    NOT_SUPPORTED,
    SUPPORTED_NOT_SURFACED,
    SURFACED_OPTIONAL,
    SURFACED_REQUIRED_BY_POLICY,
}

enum class ConsumerReasoningPreference {
    DEFAULT,
    DISABLED,
    SURFACED_IF_SUPPORTED,
}

enum class EffectiveConsumerReasoningMode {
    DISABLED,
    SURFACED,
}

enum class ConsumerOutputConstraintKind {
    TEXT,
    JSON,
    JSON_SCHEMA,
}

data class ConsumerLimits(
    val maxInputCharacters: Int,
    val maxConversationMessages: Int,
    val maxJsonSchemaCharacters: Int,
) {
    init {
        require(maxInputCharacters > 0) { "Maximum input characters must be positive" }
        require(maxConversationMessages > 0) { "Maximum conversation messages must be positive" }
        require(maxJsonSchemaCharacters > 0) { "Maximum JSON schema characters must be positive" }
    }
}

data class ConsumerPresetOption(
    val ref: InferencePresetRef,
    val isDefault: Boolean,
)

data class UseCaseCapabilities(
    val useCaseId: UseCaseId,
    val readiness: UseCaseReadiness,
    val presets: List<ConsumerPresetOption>,
    val defaultPreset: InferencePresetRef?,
    val reasoning: ConsumerReasoningCapability,
    val outputConstraints: Set<ConsumerOutputConstraintKind>,
    val sessionKinds: Set<SessionKind>,
    val limits: ConsumerLimits,
    val capabilityRevision: String,
) {
    init {
        require(capabilityRevision.isNotBlank()) { "Capability revision must not be blank" }
        require(sessionKinds.isNotEmpty()) { "At least one session kind must be exposed" }
        require(outputConstraints.isNotEmpty()) { "At least one output constraint must be exposed" }
        require(defaultPreset == null || presets.any { it.ref == defaultPreset && it.isDefault }) {
            "Default preset must be present and marked as default"
        }
        require(presets.count { it.isDefault } <= 1) { "At most one preset may be marked as default" }
    }
}

data class ConsumerSelectionRequest(
    val capabilityRevision: String? = null,
    val preset: InferencePresetRef? = null,
    val reasoning: ConsumerReasoningPreference = ConsumerReasoningPreference.DEFAULT,
    val outputConstraint: ConsumerOutputConstraintKind = ConsumerOutputConstraintKind.TEXT,
    val sessionKind: SessionKind = SessionKind.STATELESS,
) {
    init {
        require(capabilityRevision == null || capabilityRevision.isNotBlank()) {
            "Capability revision must not be blank"
        }
    }
}

enum class ConsumerCapabilityErrorCode {
    USE_CASE_NOT_ALLOWED,
    STALE_CAPABILITY,
    MODEL_UNAVAILABLE,
    CAPABILITY_INCOMPATIBLE,
    PRESET_NOT_ALLOWED,
    REASONING_NOT_ALLOWED,
    REASONING_REQUIRED,
    OUTPUT_NOT_ALLOWED,
    SESSION_KIND_NOT_ALLOWED,
}

sealed interface ConsumerCapabilityResult {
    data class Available(val capabilities: UseCaseCapabilities) : ConsumerCapabilityResult

    data class Rejected(
        val code: ConsumerCapabilityErrorCode,
        val detail: String,
    ) : ConsumerCapabilityResult {
        init {
            require(detail.isNotBlank()) { "Capability rejection detail must not be blank" }
        }
    }
}
