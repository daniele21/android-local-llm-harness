package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.ConfigurationErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeFailurePolicyTest {
    @Test
    fun `every failure family has an explicit default recovery consequence`() {
        val expected = mapOf(
            RuntimeFailureFamily.STORAGE_INTEGRITY to RuntimeRecoveryConsequence.REQUIRE_USER_ACTION,
            RuntimeFailureFamily.COMPATIBILITY to RuntimeRecoveryConsequence.REQUIRE_USER_ACTION,
            RuntimeFailureFamily.LOAD_INITIALIZATION to RuntimeRecoveryConsequence.TERMINAL_OPERATION,
            RuntimeFailureFamily.CONTEXT to RuntimeRecoveryConsequence.TERMINAL_REQUEST,
            RuntimeFailureFamily.GENERATION to RuntimeRecoveryConsequence.TERMINAL_REQUEST,
            RuntimeFailureFamily.CANCELLATION to RuntimeRecoveryConsequence.TERMINAL_REQUEST,
            RuntimeFailureFamily.RESOURCE_PRESSURE to RuntimeRecoveryConsequence.RELEASE_RESOURCES,
            RuntimeFailureFamily.TRANSPORT to RuntimeRecoveryConsequence.RECONNECT_TRANSPORT,
            RuntimeFailureFamily.INVARIANT to RuntimeRecoveryConsequence.DEGRADE_RUNTIME,
        )

        assertEquals(RuntimeFailureFamily.entries.toSet(), expected.keys)
        expected.forEach { (family, recovery) ->
            val decision = RuntimeFailurePolicy.forFamily(family)
            assertEquals(recovery, decision.recovery)
            assertEquals(0, decision.automaticRetryLimit)
        }
    }

    @Test
    fun `known backend codes map to stable families`() {
        val expected = mapOf(
            "MODEL_UNAVAILABLE" to RuntimeFailureFamily.STORAGE_INTEGRITY,
            "MODEL_INTEGRITY" to RuntimeFailureFamily.STORAGE_INTEGRITY,
            "CHAT_TEMPLATE_UNAVAILABLE" to RuntimeFailureFamily.COMPATIBILITY,
            "CHAT_TEMPLATE_UNSUPPORTED" to RuntimeFailureFamily.COMPATIBILITY,
            "OUTPUT_CONSTRAINT_UNSUPPORTED" to RuntimeFailureFamily.COMPATIBILITY,
            "INVALID_OUTPUT_CONSTRAINT" to RuntimeFailureFamily.COMPATIBILITY,
            "TOKENIZATION_FAILED" to RuntimeFailureFamily.CONTEXT,
            "CONTEXT_OVERFLOW" to RuntimeFailureFamily.CONTEXT,
            "CONTEXT_CREATE_FAILED" to RuntimeFailureFamily.CONTEXT,
            "CONTEXT_RELEASE_FAILED" to RuntimeFailureFamily.CONTEXT,
            "LOAD_FAILED" to RuntimeFailureFamily.LOAD_INITIALIZATION,
            "MODEL_LOAD_FAILED" to RuntimeFailureFamily.LOAD_INITIALIZATION,
            "INITIALIZATION_FAILED" to RuntimeFailureFamily.LOAD_INITIALIZATION,
            "GENERATION_FAILED" to RuntimeFailureFamily.GENERATION,
            "DECODE_FAILED" to RuntimeFailureFamily.GENERATION,
            "CANCEL_FAILED" to RuntimeFailureFamily.CANCELLATION,
            "MEMORY_BUDGET_EXCEEDED" to RuntimeFailureFamily.RESOURCE_PRESSURE,
            "RESOURCE_EXHAUSTED" to RuntimeFailureFamily.RESOURCE_PRESSURE,
            "TRANSPORT_UNAVAILABLE" to RuntimeFailureFamily.TRANSPORT,
            "CONNECTION_LOST" to RuntimeFailureFamily.TRANSPORT,
            "INVARIANT_VIOLATION" to RuntimeFailureFamily.INVARIANT,
        )

        expected.forEach { (code, family) ->
            assertEquals(family, RuntimeFailurePolicy.classifyBackendCode(code).family)
        }
    }

    @Test
    fun `existing public configuration mappings are preserved by the internal policy`() {
        val expected = mapOf(
            "CHAT_TEMPLATE_UNAVAILABLE" to ConfigurationErrorCode.CHAT_TEMPLATE_UNAVAILABLE,
            "CHAT_TEMPLATE_UNSUPPORTED" to ConfigurationErrorCode.CHAT_TEMPLATE_UNSUPPORTED,
            "TOKENIZATION_FAILED" to ConfigurationErrorCode.PROMPT_TOKENIZATION_FAILED,
            "CONTEXT_OVERFLOW" to ConfigurationErrorCode.CONTEXT_CAPACITY_EXCEEDED,
            "OUTPUT_CONSTRAINT_UNSUPPORTED" to ConfigurationErrorCode.OUTPUT_CONSTRAINT_UNSUPPORTED,
            "INVALID_OUTPUT_CONSTRAINT" to ConfigurationErrorCode.INVALID_OUTPUT_CONSTRAINT,
        )

        expected.forEach { (code, reason) ->
            assertEquals(reason, RuntimeFailurePolicy.classifyBackendCode(code).configurationError)
        }
    }

    @Test
    fun `unknown backend code fails closed to caller supplied family without automatic retry`() {
        val decision = RuntimeFailurePolicy.classifyBackendCode(
            code = "future-backend-error",
            fallbackFamily = RuntimeFailureFamily.INVARIANT,
        )

        assertEquals(RuntimeFailureFamily.INVARIANT, decision.family)
        assertEquals(RuntimeRecoveryConsequence.DEGRADE_RUNTIME, decision.recovery)
        assertEquals(0, decision.automaticRetryLimit)
    }

    @Test
    fun `backend code normalization is deterministic`() {
        val decision = RuntimeFailurePolicy.classifyBackendCode("  context_overflow  ")

        assertEquals(RuntimeFailureFamily.CONTEXT, decision.family)
        assertEquals(ConfigurationErrorCode.CONTEXT_CAPACITY_EXCEEDED, decision.configurationError)
    }

    @Test
    fun `no default recovery path enables automatic retry`() {
        val decisions = RuntimeFailureFamily.entries.map(RuntimeFailurePolicy::forFamily)

        assertTrue(decisions.all { it.automaticRetryLimit == 0 })
    }
}
