package io.github.daniele21.localllm.contracts

@JvmInline
value class ConsumerActivationId(val value: String) {
    init {
        require(value.isNotBlank()) { "Consumer activation ID must not be blank" }
    }
}

data class ConsumerAssignedUseCase(
    val useCaseId: UseCaseId,
    val useCaseRevision: Int,
    val bindingRevision: Int,
    val displayName: String,
    val description: String,
    val isDefault: Boolean,
) {
    init {
        require(useCaseRevision > 0) { "Use-case revision must be positive" }
        require(bindingRevision > 0) { "Binding revision must be positive" }
        require(displayName.isNotBlank()) { "Use-case display name must not be blank" }
        require(description.isNotBlank()) { "Use-case description must not be blank" }
    }
}

data class ConsumerPublishedPreset(
    val preset: InferencePresetRef,
    val displayName: String,
    val description: String,
    val isDefault: Boolean,
) {
    init {
        require(displayName.isNotBlank()) { "Preset display name must not be blank" }
        require(description.isNotBlank()) { "Preset description must not be blank" }
    }
}

data class ConsumerActivationRequest(
    val useCaseId: UseCaseId,
    val useCaseRevision: Int,
    val bindingRevision: Int,
    val preset: InferencePresetRef,
) {
    init {
        require(useCaseRevision > 0) { "Use-case revision must be positive" }
        require(bindingRevision > 0) { "Binding revision must be positive" }
    }
}

/**
 * Exact published setup identity a consumer wants to inspect before activation.
 * Resolution is observational: it must not activate, prepare, load, open a session or acquire residency.
 */
data class ConsumerSetupResolutionRequest(
    val useCaseId: UseCaseId,
    val useCaseRevision: Int,
    val bindingRevision: Int,
    val preset: InferencePresetRef,
) {
    init {
        require(useCaseRevision > 0) { "Use-case revision must be positive" }
        require(bindingRevision > 0) { "Binding revision must be positive" }
    }
}

data class ConsumerGenerationConfiguration(
    val maxOutputTokens: Int,
    val temperature: Float,
    val topP: Float,
    val topK: Int,
    val minP: Float,
    val presencePenalty: Float,
    val repeatPenalty: Float,
    val repeatLastN: Int,
    val thinkingMode: ThinkingMode,
    val seedPolicy: SeedPolicyType,
) {
    init {
        require(maxOutputTokens > 0) { "Maximum output tokens must be positive" }
        require(temperature.isFinite() && temperature in 0f..2f) { "Temperature must be in [0, 2]" }
        require(topP.isFinite() && topP > 0f && topP <= 1f) { "Top-p must be in (0, 1]" }
        require(topK in 0..1_000) { "Top-k must be in [0, 1000]" }
        require(minP.isFinite() && minP in 0f..1f) { "Min-p must be in [0, 1]" }
        require(presencePenalty.isFinite() && presencePenalty in 0f..2f) { "Presence penalty must be in [0, 2]" }
        require(repeatPenalty.isFinite() && repeatPenalty in 1f..2f) { "Repeat penalty must be in [1, 2]" }
        require(repeatLastN in 0..4_096) { "Repeat window must be in [0, 4096]" }
    }
}

data class ConsumerResolvedSetup(
    val useCaseId: UseCaseId,
    val useCaseRevision: Int,
    val bindingRevision: Int,
    val preset: InferencePresetRef,
    val modelProfileId: String,
    val contextTokens: Int,
    val generation: ConsumerGenerationConfiguration,
) {
    init {
        require(useCaseRevision > 0) { "Use-case revision must be positive" }
        require(bindingRevision > 0) { "Binding revision must be positive" }
        require(modelProfileId.isNotBlank()) { "Model profile ID must not be blank" }
        require(contextTokens > 0) { "Context tokens must be positive" }
    }
}

data class ConsumerActivation(
    val activationId: ConsumerActivationId,
    val useCaseId: UseCaseId,
    val useCaseRevision: Int,
    val bindingRevision: Int,
    val preset: InferencePresetRef,
) {
    init {
        require(useCaseRevision > 0) { "Use-case revision must be positive" }
        require(bindingRevision > 0) { "Binding revision must be positive" }
    }
}

enum class ConsumerControlPlaneErrorCode {
    FEATURE_UNAVAILABLE,
    UNKNOWN_APPLICATION,
    APPLICATION_NOT_AUTHORIZED,
    USE_CASE_NOT_ASSIGNED,
    PRESET_NOT_EXPOSED,
    STALE_REVISION,
    MODEL_UNAVAILABLE,
    MODEL_CONFLICT,
    ACTIVATION_ALREADY_ACTIVE,
    CONFIGURATION_REQUIRED,
    INVALID_REQUEST,
    TRANSPORT_FAILURE,
    RUNTIME_FAILURE,
}

data class ConsumerControlPlaneFailure(val code: ConsumerControlPlaneErrorCode, val message: String) {
    init {
        require(message.isNotBlank()) { "Consumer control-plane failure message must not be blank" }
    }
}

sealed interface ConsumerAssignedUseCasesResult {
    data class Available(val assignments: List<ConsumerAssignedUseCase>) : ConsumerAssignedUseCasesResult

    data class Rejected(val failure: ConsumerControlPlaneFailure) : ConsumerAssignedUseCasesResult
}

sealed interface ConsumerPublishedPresetsResult {
    data class Available(val useCaseId: UseCaseId, val bindingRevision: Int, val presets: List<ConsumerPublishedPreset>) :
        ConsumerPublishedPresetsResult {
        init {
            require(bindingRevision > 0) { "Binding revision must be positive" }
        }
    }

    data class Rejected(val failure: ConsumerControlPlaneFailure) : ConsumerPublishedPresetsResult
}

sealed interface ConsumerSetupResolutionResult {
    data class Resolved(val setup: ConsumerResolvedSetup) : ConsumerSetupResolutionResult

    data class Rejected(val failure: ConsumerControlPlaneFailure) : ConsumerSetupResolutionResult
}

sealed interface ConsumerActivationResult {
    data class Activated(val activation: ConsumerActivation) : ConsumerActivationResult

    data class Rejected(val failure: ConsumerControlPlaneFailure) : ConsumerActivationResult
}

sealed interface ConsumerDeactivationResult {
    data object Released : ConsumerDeactivationResult

    data class Rejected(val failure: ConsumerControlPlaneFailure) : ConsumerDeactivationResult
}

/** Optional product-level lifecycle API negotiated separately from Consumer API v1 inference. */
interface ConsumerControlPlaneClient {
    fun assignedUseCases(): ConsumerAssignedUseCasesResult

    fun publishedPresets(useCaseId: UseCaseId): ConsumerPublishedPresetsResult

    /** Read-only setup projection; never creates activation/runtime residency. */
    fun resolveSetup(request: ConsumerSetupResolutionRequest): ConsumerSetupResolutionResult =
        ConsumerSetupResolutionResult.Rejected(
            ConsumerControlPlaneFailure(
                ConsumerControlPlaneErrorCode.FEATURE_UNAVAILABLE,
                "Consumer setup resolution is unavailable",
            ),
        )

    fun activate(request: ConsumerActivationRequest): ConsumerActivationResult

    fun deactivate(activationId: ConsumerActivationId): ConsumerDeactivationResult
}