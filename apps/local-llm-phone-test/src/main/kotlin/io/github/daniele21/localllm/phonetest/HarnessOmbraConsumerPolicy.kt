package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ConsumerLimits
import io.github.daniele21.localllm.contracts.ConsumerOutputConstraintKind
import io.github.daniele21.localllm.contracts.ConsumerReasoningCapability
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.runtime.ConsumerUseCasePolicy

/** Fixed host policy for the document PII reference use case. */
internal object HarnessOmbraConsumerPolicy {
    const val REVISION = "ombra-document-pii-v1"
    const val MAX_INPUT_CHARACTERS = 12_000
    const val MAX_JSON_SCHEMA_CHARACTERS = 4_096

    fun create(applicationId: ApplicationId): ConsumerUseCasePolicy {
        require(applicationId in HarnessSharedRuntimeBindings.piiConsumerApplicationIds) {
            "document-pii-detection policy is not configured for applicationId ${applicationId.value}"
        }
        return ConsumerUseCasePolicy(
            applicationId = applicationId,
            useCaseId = HarnessSharedRuntimeBindings.ombraUseCaseId,
            revision = REVISION,
            exposedPresets = setOf(HarnessSharedRuntimeBindings.ombraDefaultPreset),
            defaultPreset = HarnessSharedRuntimeBindings.ombraDefaultPreset,
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
}
