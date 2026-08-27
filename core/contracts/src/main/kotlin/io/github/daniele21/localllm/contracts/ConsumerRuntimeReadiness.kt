package io.github.daniele21.localllm.contracts

enum class ConsumerRuntimePhase {
    IDLE,
    PREPARING,
    READY,
    GENERATING,
    FAILED,
}

enum class ConsumerPreparationAction {
    NONE,
    LOADING,
    REUSING,
    SWITCHING,
}

enum class ConsumerRuntimeIssue {
    MODEL_UNAVAILABLE,
    MODEL_CONFLICT,
    CONFIGURATION_STALE,
    PREPARATION_FAILED,
    RUNTIME_FAILED,
    CANCELLED,
}

data class ConsumerRuntimeReadiness(
    val activationId: ConsumerActivationId,
    val phase: ConsumerRuntimePhase,
    val preparationAction: ConsumerPreparationAction = ConsumerPreparationAction.NONE,
    val issue: ConsumerRuntimeIssue? = null,
    val retryable: Boolean = false,
) {
    init {
        require((phase == ConsumerRuntimePhase.PREPARING) == (preparationAction != ConsumerPreparationAction.NONE)) {
            "Preparation action is present only while preparing"
        }
        require((phase == ConsumerRuntimePhase.FAILED) == (issue != null)) {
            "Runtime issue is present only for failed readiness"
        }
        require(!retryable || phase == ConsumerRuntimePhase.FAILED) {
            "Retryable applies only to failed readiness"
        }
    }
}

sealed interface ConsumerRuntimeReadinessResult {
    data class Available(val readiness: ConsumerRuntimeReadiness) : ConsumerRuntimeReadinessResult

    data class Rejected(val failure: ConsumerControlPlaneFailure) : ConsumerRuntimeReadinessResult
}

/** Optional consumer-safe runtime lifecycle view negotiated separately from Consumer API v1 inference. */
interface ConsumerRuntimeReadinessClient {
    fun runtimeReadiness(activationId: ConsumerActivationId): ConsumerRuntimeReadinessResult
}
