package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ConsumerLimits
import io.github.daniele21.localllm.contracts.ConsumerOutputConstraintKind
import io.github.daniele21.localllm.contracts.ConsumerReasoningCapability
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.runtime.ConsumerUseCasePolicy

/** Host policy for the document PII reference use case. */
internal object HarnessOmbraConsumerPolicy {
    const val REVISION = "ombra-document-pii-v1"
    const val MAX_INPUT_CHARACTERS = 12_000
    const val MAX_JSON_SCHEMA_CHARACTERS = 4_096

    /**
     * Builds the capability policy for an application already authorized and assigned by the control plane.
     * Application identity is intentionally not restricted to built-in consumers here: Binder authorization and
     * the persisted application/use-case binding are the canonical access boundary for user-created connections.
     */
    fun create(
        applicationId: ApplicationId,
        preset: InferencePresetRef = HarnessSharedRuntimeBindings.ombraDefaultPreset,
    ): ConsumerUseCasePolicy = ConsumerUseCasePolicy(
        applicationId = applicationId,
        useCaseId = HarnessSharedRuntimeBindings.ombraUseCaseId,
        revision = REVISION,
        exposedPresets = setOf(preset),
        defaultPreset = preset,
        reasoning = ConsumerReasoningCapability.NOT_SUPPORTED,
        outputConstraints = setOf(ConsumerOutputConstraintKind.JSON_SCHEMA),
        defaultOutputConstraint = ConsumerOutputConstraintKind.JSON_SCHEMA,
        sessionKinds = setOf(SessionKind.STATELESS),
        defaultSessionKind = SessionKind.STATELESS,
        limits =
        ConsumerLimits(
            maxInputCharacters = MAX_INPUT_CHARACTERS,
            maxConversationMessages = 1,
            maxJsonSchemaCharacters = MAX_JSON_SCHEMA_CHARACTERS,
        ),
    )
}
