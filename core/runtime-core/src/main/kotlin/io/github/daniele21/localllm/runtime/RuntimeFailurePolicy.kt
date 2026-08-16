package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.ConfigurationErrorCode
import io.github.daniele21.localllm.contracts.LocalLlmError

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

internal data class RuntimeFailureResolution(
    val decision: RuntimeFailureDecision,
    val publicError: LocalLlmError,
    val backendCode: String,
)

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

    fun classifyBackendCode(code: String, fallbackFamily: RuntimeFailureFamily = RuntimeFailureFamily.GENERATION): RuntimeFailureDecision {
        val normalized = normalizeBackendCode(code)
        val classified = BACKEND_CODE_DECISIONS[normalized]
        return classified ?: forFamily(fallbackFamily)
    }

    fun resolveBackendFailure(
        error: BackendException,
        fallbackFamily: RuntimeFailureFamily = RuntimeFailureFamily.GENERATION,
    ): RuntimeFailureResolution {
        val backendCode = normalizeBackendCode(error.code)
        val decision = classifyBackendCode(backendCode, fallbackFamily)
        return RuntimeFailureResolution(
            decision = decision,
            publicError = publicError(decision, error.message),
            backendCode = backendCode,
        )
    }

    private fun publicError(decision: RuntimeFailureDecision, backendMessage: String?): LocalLlmError {
        decision.configurationError?.let { reason ->
            return LocalLlmError.Configuration(
                message = backendMessage?.takeIf(String::isNotBlank) ?: "Generation configuration is unsupported",
                reason = reason,
            )
        }
        return when (decision.family) {
            RuntimeFailureFamily.STORAGE_INTEGRITY -> LocalLlmError.ModelUnavailable(
                "Requested model is unavailable or failed integrity verification",
            )

            RuntimeFailureFamily.COMPATIBILITY -> LocalLlmError.NativeRuntime("Local model/runtime compatibility check failed")
            RuntimeFailureFamily.LOAD_INITIALIZATION -> LocalLlmError.NativeRuntime("Local model initialization failed")
            RuntimeFailureFamily.CONTEXT -> LocalLlmError.NativeRuntime("Local inference context failed")
            RuntimeFailureFamily.GENERATION -> LocalLlmError.NativeRuntime("Local generation failed")
            RuntimeFailureFamily.CANCELLATION -> LocalLlmError.NativeRuntime("Local cancellation failed")
            RuntimeFailureFamily.RESOURCE_PRESSURE -> LocalLlmError.NativeRuntime("Local inference resources are unavailable")
            RuntimeFailureFamily.TRANSPORT -> LocalLlmError.NativeRuntime("Local runtime transport is unavailable")
            RuntimeFailureFamily.INVARIANT -> LocalLlmError.NativeRuntime("Local runtime entered an invalid state")
        }
    }

    private fun normalizeBackendCode(code: String): String = code.trim().uppercase()

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

    private fun backendDecision(family: RuntimeFailureFamily, configurationError: ConfigurationErrorCode? = null): RuntimeFailureDecision =
        RuntimeFailureDecision(
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
