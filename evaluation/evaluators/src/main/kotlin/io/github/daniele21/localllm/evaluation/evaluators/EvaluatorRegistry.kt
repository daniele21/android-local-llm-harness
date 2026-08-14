package io.github.daniele21.localllm.evaluation.evaluators

import io.github.daniele21.localllm.evaluation.EvaluationFailure
import io.github.daniele21.localllm.evaluation.EvaluationFailureCode
import io.github.daniele21.localllm.evaluation.EvaluationFailureStage
import io.github.daniele21.localllm.evaluation.EvaluatorSpec
import io.github.daniele21.localllm.evaluation.EvaluatorType
import io.github.daniele21.localllm.evaluation.EvaluatorVersion

data class EvaluatorKey(
    val type: EvaluatorType,
    val version: EvaluatorVersion,
)

data class EvaluatorParameterPolicy(
    val requiredKeys: Set<String> = emptySet(),
    val optionalKeys: Set<String> = emptySet(),
    val allowedValues: Map<String, Set<String>> = emptyMap(),
) {
    init {
        require(requiredKeys.intersect(optionalKeys).isEmpty()) {
            "Evaluator parameter key cannot be both required and optional"
        }
        val declared = requiredKeys + optionalKeys
        require(allowedValues.keys.all { it in declared }) {
            "Allowed-value rules must target declared evaluator parameters"
        }
        declared.forEach(::validateParameterName)
        allowedValues.values.flatten().forEach { value ->
            require(value.isNotEmpty()) { "Allowed evaluator parameter value must not be empty" }
            require('\u0000' !in value) { "Allowed evaluator parameter value must not contain NUL" }
        }
    }

    fun validate(spec: EvaluatorSpec): Boolean {
        val actualKeys = spec.parameters.keys
        val declaredKeys = requiredKeys + optionalKeys
        if (!actualKeys.containsAll(requiredKeys)) return false
        if (!declaredKeys.containsAll(actualKeys)) return false
        return allowedValues.all { (key, values) ->
            val actual = spec.parameters[key]
            actual == null || actual in values
        }
    }
}

data class EvaluatorRegistration(
    val key: EvaluatorKey,
    val parameters: EvaluatorParameterPolicy = EvaluatorParameterPolicy(),
)

sealed interface EvaluatorLookupResult {
    data class Supported(val registration: EvaluatorRegistration) : EvaluatorLookupResult

    data class Rejected(val failure: EvaluationFailure) : EvaluatorLookupResult
}

class EvaluatorRegistry(registrations: List<EvaluatorRegistration>) {
    private val byKey: Map<EvaluatorKey, EvaluatorRegistration>

    init {
        require(registrations.isNotEmpty()) { "Evaluator registry must contain at least one registration" }
        val duplicates = registrations.groupingBy { it.key }.eachCount().filterValues { it > 1 }.keys
        require(duplicates.isEmpty()) { "Evaluator registry contains duplicate type/version registrations" }
        byKey = registrations.associateBy { it.key }
    }

    fun resolve(spec: EvaluatorSpec): EvaluatorLookupResult {
        val registration = byKey[EvaluatorKey(spec.type, spec.version)]
            ?: return EvaluatorLookupResult.Rejected(
                EvaluationFailure(
                    stage = EvaluationFailureStage.PREFLIGHT,
                    code = EvaluationFailureCode.UNKNOWN_EVALUATOR,
                ),
            )

        if (!registration.parameters.validate(spec)) {
            return EvaluatorLookupResult.Rejected(
                EvaluationFailure(
                    stage = EvaluationFailureStage.PREFLIGHT,
                    code = EvaluationFailureCode.INVALID_EVALUATOR_PARAMETERS,
                ),
            )
        }

        return EvaluatorLookupResult.Supported(registration)
    }

    fun supportedKeys(): Set<EvaluatorKey> = byKey.keys.toSet()
}

private fun validateParameterName(value: String) {
    require(value.isNotBlank()) { "Evaluator parameter name must not be blank" }
    require(value.length <= MAX_PARAMETER_NAME_LENGTH) {
        "Evaluator parameter name must not exceed $MAX_PARAMETER_NAME_LENGTH characters"
    }
    require('\u0000' !in value) { "Evaluator parameter name must not contain NUL" }
}

private const val MAX_PARAMETER_NAME_LENGTH = 64
