package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.ConfigurationErrorCode

internal enum class RuntimeFailureFamily {
    STORAGE_INTEGRITY,
    COMPATIBILITY,
    LOAD_INITIALIZATION,
    CONTEXT,
    GENERATION,
    CANCELLATION,
    RESOURCE_PRESSURE,
    TRANSPORT,
    INVARIANT,
}

internal enum class RuntimeRecoveryConsequence {
    REQUIRE_USER_ACTION,
    TERMINAL_OPERATION,
    TERMINAL_REQUEST,
    RELEASE_RESOURCES,
    RECONNECT_TRANSPORT,
    DEGRADE_RUNTIME,
}

internal data class RuntimeFailureDecision(
    val family: RuntimeFailureFamily,
    val recovery: RuntimeRecoveryConsequence,
    val configurationError: ConfigurationErrorCode? = null,
    val automaticRetryLimit: Int = 0,
) {
    init {
        require(automaticRetryLimit >= 0) { "Automatic retry limit cannot be negative" }
    }
}

/**
 * Stable internal recovery policy for runtime/backend failures.
 *
 * This policy deliberately does not expose new public error types and does not enable automatic
 * retry. A future bounded retry must be justified per operation by idempotency, a deterministic
 * budget and explicit regression coverage.
 */
internal object RuntimeFailurePolicy {
    fun forFamily(family: RuntimeFailureFamily): RuntimeFailureDecision = RuntimeFailureDecision(
        family = family,
        recovery = defaultRecovery(family),
    )

    fun classifyBackendCode(
        code: String,
        fallbackFamily: RuntimeFailureFamily = RuntimeFailureFamily.GENERATION,
    ): RuntimeFailureDecision {
        val normalized = code.trim().uppercase()
        val classified = BACKEND_CODE_DECISIONS[normalized]
        return classified ?: forFamily(fallbackFamily)
    }

    private fun defaultRecovery(family: RuntimeFailureFamily): RuntimeRecoveryConsequence = when (family) {
        RuntimeFailureFamily.STORAGE_INTEGRITY,
        RuntimeFailureFamily.COMPATIBILITY,
        -> RuntimeRecoveryConsequence.REQUIRE_USER_ACTION

        RuntimeFailureFamily.LOAD_INITIALIZATION -> RuntimeRecoveryConsequence.TERMINAL_OPERATION

        RuntimeFailureFamily.CONTEXT,
        RuntimeFailureFamily.GENERATION,
        RuntimeFailureFamily.CANCELLATION,
        -> RuntimeRecoveryConsequence.TERMINAL_REQUEST

        RuntimeFailureFamily.RESOURCE_PRESSURE -> RuntimeRecoveryConsequence.RELEASE_RESOURCES
        RuntimeFailureFamily.TRANSPORT -> RuntimeRecoveryConsequence.RECONNECT_TRANSPORT
        RuntimeFailureFamily.INVARIANT -> RuntimeRecoveryConsequence.DEGRADE_RUNTIME
    }

    private fun backendDecision(
        family: RuntimeFailureFamily,
        configurationError: ConfigurationErrorCode? = null,
    ): RuntimeFailureDecision = RuntimeFailureDecision(
        family = family,
        recovery = defaultRecovery(family),
        configurationError = configurationError,
    )

    private val BACKEND_CODE_DECISIONS = mapOf(
        "MODEL_UNAVAILABLE" to backendDecision(RuntimeFailureFamily.STORAGE_INTEGRITY),
        "MODEL_INTEGRITY" to backendDecision(RuntimeFailureFamily.STORAGE_INTEGRITY),
        "CHAT_TEMPLATE_UNAVAILABLE" to backendDecision(
            RuntimeFailureFamily.COMPATIBILITY,
            ConfigurationErrorCode.CHAT_TEMPLATE_UNAVAILABLE,
        ),
        "CHAT_TEMPLATE_UNSUPPORTED" to backendDecision(
            RuntimeFailureFamily.COMPATIBILITY,
            ConfigurationErrorCode.CHAT_TEMPLATE_UNSUPPORTED,
        ),
        "OUTPUT_CONSTRAINT_UNSUPPORTED" to backendDecision(
            RuntimeFailureFamily.COMPATIBILITY,
            ConfigurationErrorCode.OUTPUT_CONSTRAINT_UNSUPPORTED,
        ),
        "INVALID_OUTPUT_CONSTRAINT" to backendDecision(
            RuntimeFailureFamily.COMPATIBILITY,
            ConfigurationErrorCode.INVALID_OUTPUT_CONSTRAINT,
        ),
        "TOKENIZATION_FAILED" to backendDecision(
            RuntimeFailureFamily.CONTEXT,
            ConfigurationErrorCode.PROMPT_TOKENIZATION_FAILED,
        ),
        "CONTEXT_OVERFLOW" to backendDecision(
            RuntimeFailureFamily.CONTEXT,
            ConfigurationErrorCode.CONTEXT_CAPACITY_EXCEEDED,
        ),
        "CONTEXT_CREATE_FAILED" to backendDecision(RuntimeFailureFamily.CONTEXT),
        "CONTEXT_RELEASE_FAILED" to backendDecision(RuntimeFailureFamily.CONTEXT),
        "LOAD_FAILED" to backendDecision(RuntimeFailureFamily.LOAD_INITIALIZATION),
        "MODEL_LOAD_FAILED" to backendDecision(RuntimeFailureFamily.LOAD_INITIALIZATION),
        "INITIALIZATION_FAILED" to backendDecision(RuntimeFailureFamily.LOAD_INITIALIZATION),
        "GENERATION_FAILED" to backendDecision(RuntimeFailureFamily.GENERATION),
        "DECODE_FAILED" to backendDecision(RuntimeFailureFamily.GENERATION),
        "CANCEL_FAILED" to backendDecision(RuntimeFailureFamily.CANCELLATION),
        "MEMORY_BUDGET_EXCEEDED" to backendDecision(RuntimeFailureFamily.RESOURCE_PRESSURE),
        "RESOURCE_EXHAUSTED" to backendDecision(RuntimeFailureFamily.RESOURCE_PRESSURE),
        "TRANSPORT_UNAVAILABLE" to backendDecision(RuntimeFailureFamily.TRANSPORT),
        "CONNECTION_LOST" to backendDecision(RuntimeFailureFamily.TRANSPORT),
        "INVARIANT_VIOLATION" to backendDecision(RuntimeFailureFamily.INVARIANT),
    )
}
