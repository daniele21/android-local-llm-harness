package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ConsumerLimits
import io.github.daniele21.localllm.contracts.ConsumerOutputConstraintKind
import io.github.daniele21.localllm.contracts.ConsumerReasoningCapability
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.runtime.ConsumerUseCasePolicy

/** Consumer capability policy for Aura's two reviewed local transaction-import use cases. */
internal object HarnessAuraImportConsumerPolicy {
    const val REVISION = "aura-transaction-import-v1"
    const val MAX_INPUT_CHARACTERS = 12_000
    const val MAX_JSON_SCHEMA_CHARACTERS = 4_096

    fun create(
        applicationId: ApplicationId,
        useCaseId: UseCaseId,
        preset: InferencePresetRef = HarnessSharedRuntimeBindings.auraDefaultPreset,
    ): ConsumerUseCasePolicy {
        require(applicationId == HarnessSharedRuntimeBindings.auraApplicationId) {
            "Aura import policy requires the Aura application identity"
        }
        require(useCaseId in HarnessSharedRuntimeBindings.auraUseCases) {
            "Unsupported Aura import useCaseId ${useCaseId.value}"
        }
        return ConsumerUseCasePolicy(
            applicationId = applicationId,
            useCaseId = useCaseId,
            revision = REVISION,
            exposedPresets = setOf(preset),
            defaultPreset = preset,
            reasoning = ConsumerReasoningCapability.NOT_SUPPORTED,
            outputConstraints = setOf(ConsumerOutputConstraintKind.JSON_SCHEMA),
            defaultOutputConstraint = ConsumerOutputConstraintKind.JSON_SCHEMA,
            sessionKinds = setOf(SessionKind.STATELESS),
            defaultSessionKind = SessionKind.STATELESS,
            limits = ConsumerLimits(
                maxInputCharacters = MAX_INPUT_CHARACTERS,
                maxConversationMessages = 1,
                maxJsonSchemaCharacters = MAX_JSON_SCHEMA_CHARACTERS,
            ),
        )
    }
}
