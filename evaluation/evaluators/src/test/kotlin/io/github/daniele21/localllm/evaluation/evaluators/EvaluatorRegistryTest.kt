package io.github.daniele21.localllm.evaluation.evaluators

import io.github.daniele21.localllm.evaluation.EvaluationFailureCode
import io.github.daniele21.localllm.evaluation.EvaluatorSpec
import io.github.daniele21.localllm.evaluation.EvaluatorType
import io.github.daniele21.localllm.evaluation.EvaluatorVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluatorRegistryTest {
    @Test
    fun `known type version and parameters resolve`() {
        val registry = registry()
        val result = registry.resolve(
            EvaluatorSpec(
                type = EvaluatorType.EXACT_MATCH,
                version = EvaluatorVersion(1),
                parameters = mapOf("trim" to "true"),
            ),
        )

        assertTrue(result is EvaluatorLookupResult.Supported)
    }

    @Test
    fun `unknown version fails closed`() {
        val result = registry().resolve(
            EvaluatorSpec(
                type = EvaluatorType.EXACT_MATCH,
                version = EvaluatorVersion(2),
            ),
        )

        val rejected = result as EvaluatorLookupResult.Rejected
        assertEquals(EvaluationFailureCode.UNKNOWN_EVALUATOR, rejected.failure.code)
    }

    @Test
    fun `unknown parameter fails closed`() {
        val result = registry().resolve(
            EvaluatorSpec(
                type = EvaluatorType.EXACT_MATCH,
                version = EvaluatorVersion(1),
                parameters = mapOf("trim" to "true", "script" to "run-me"),
            ),
        )

        val rejected = result as EvaluatorLookupResult.Rejected
        assertEquals(EvaluationFailureCode.INVALID_EVALUATOR_PARAMETERS, rejected.failure.code)
    }

    @Test
    fun `required parameter must be present`() {
        val registry = EvaluatorRegistry(
            listOf(
                EvaluatorRegistration(
                    key = EvaluatorKey(EvaluatorType.MULTIPLE_CHOICE, EvaluatorVersion(1)),
                    parameters = EvaluatorParameterPolicy(requiredKeys = setOf("labels")),
                ),
            ),
        )

        val rejected = registry.resolve(
            EvaluatorSpec(
                type = EvaluatorType.MULTIPLE_CHOICE,
                version = EvaluatorVersion(1),
            ),
        ) as EvaluatorLookupResult.Rejected

        assertEquals(EvaluationFailureCode.INVALID_EVALUATOR_PARAMETERS, rejected.failure.code)
    }

    @Test
    fun `allowed values are validated deterministically`() {
        val registry = registry()
        val rejected = registry.resolve(
            EvaluatorSpec(
                type = EvaluatorType.EXACT_MATCH,
                version = EvaluatorVersion(1),
                parameters = mapOf("trim" to "sometimes"),
            ),
        ) as EvaluatorLookupResult.Rejected

        assertEquals(EvaluationFailureCode.INVALID_EVALUATOR_PARAMETERS, rejected.failure.code)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `duplicate registrations are rejected`() {
        val registration = EvaluatorRegistration(
            key = EvaluatorKey(EvaluatorType.EXACT_MATCH, EvaluatorVersion(1)),
        )
        EvaluatorRegistry(listOf(registration, registration))
    }

    private fun registry() = EvaluatorRegistry(
        listOf(
            EvaluatorRegistration(
                key = EvaluatorKey(EvaluatorType.EXACT_MATCH, EvaluatorVersion(1)),
                parameters = EvaluatorParameterPolicy(
                    optionalKeys = setOf("trim", "ignoreCase"),
                    allowedValues = mapOf(
                        "trim" to setOf("true", "false"),
                        "ignoreCase" to setOf("true", "false"),
                    ),
                ),
            ),
        ),
    )
}
