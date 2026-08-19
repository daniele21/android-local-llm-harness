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
    data class Available(
        val useCaseId: UseCaseId,
        val bindingRevision: Int,
        val presets: List<ConsumerPublishedPreset>,
    ) : ConsumerPublishedPresetsResult {
        init {
            require(bindingRevision > 0) { "Binding revision must be positive" }
        }
    }

    data class Rejected(val failure: ConsumerControlPlaneFailure) : ConsumerPublishedPresetsResult
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

    fun activate(request: ConsumerActivationRequest): ConsumerActivationResult

    fun deactivate(activationId: ConsumerActivationId): ConsumerDeactivationResult
}
