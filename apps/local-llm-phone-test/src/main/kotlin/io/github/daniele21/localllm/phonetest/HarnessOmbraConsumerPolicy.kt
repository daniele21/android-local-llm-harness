package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ConsumerLimits
import io.github.daniele21.localllm.contracts.ConsumerOutputConstraintKind
import io.github.daniele21.localllm.contracts.ConsumerReasoningCapability
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.models.ApplicationRegistrationState
import io.github.daniele21.localllm.models.HostControlPlaneStore
import io.github.daniele21.localllm.models.PresetLifecycleState
import io.github.daniele21.localllm.models.UseCaseDefinitionState
import io.github.daniele21.localllm.runtime.ConsumerUseCasePolicy
import io.github.daniele21.localllm.runtime.ConsumerUseCasePolicyRegistry

/** Safety constraints for the document PII reference use case. Preset exposure is control-plane owned. */
internal object HarnessOmbraConsumerPolicy {
    const val REVISION = "ombra-document-pii-v1"
    const val MAX_INPUT_CHARACTERS = 12_000
    const val MAX_JSON_SCHEMA_CHARACTERS = 4_096

    fun create(
        applicationId: ApplicationId,
        revision: String = REVISION,
        exposedPresets: Set<InferencePresetRef> = setOf(HarnessSharedRuntimeBindings.ombraDefaultPreset),
        defaultPreset: InferencePresetRef? = HarnessSharedRuntimeBindings.ombraDefaultPreset,
    ): ConsumerUseCasePolicy {
        require(applicationId in HarnessSharedRuntimeBindings.piiConsumerApplicationIds) {
            "document-pii-detection policy is not configured for applicationId ${applicationId.value}"
        }
        return ConsumerUseCasePolicy(
            applicationId = applicationId,
            useCaseId = HarnessSharedRuntimeBindings.ombraUseCaseId,
            revision = revision,
            exposedPresets = exposedPresets,
            defaultPreset = defaultPreset,
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

/**
 * Projects the canonical Host control-plane exposure into Consumer API capability policy.
 *
 * This keeps discovery/activation and data-plane preparation on one preset/default source. Reads are
 * observational only: no seeding, reconciliation, model load or runtime mutation is performed here.
 */
internal class HarnessControlPlaneConsumerPolicyRegistry(
    private val store: HostControlPlaneStore,
) : ConsumerUseCasePolicyRegistry {
    override fun find(applicationId: ApplicationId, useCaseId: UseCaseId): ConsumerUseCasePolicy? {
        if (applicationId !in HarnessSharedRuntimeBindings.piiConsumerApplicationIds ||
            useCaseId != HarnessSharedRuntimeBindings.ombraUseCaseId
        ) {
            return null
        }
        val state = store.snapshot()
        val application = state.applications.singleOrNull { it.applicationId == applicationId }
            ?.takeIf { it.state == ApplicationRegistrationState.AUTHORIZED }
            ?: return null
        val binding = state.latestBinding(application.applicationId, useCaseId)
            ?.takeIf { it.enabled }
            ?: return null
        val useCase = state.latestUseCase(useCaseId)
            ?.takeIf { it.state == UseCaseDefinitionState.ACTIVE }
            ?: return null
        val exposures = state.exposures
            .filter { it.bindingId == binding.bindingId && it.bindingRevision == binding.revision }
            .sortedWith(compareBy({ it.presetId }, { it.presetRevision }))
        val exposedPresets = exposures.mapNotNull { exposure ->
            state.preset(useCaseId, exposure.presetId, exposure.presetRevision)
                ?.takeIf { it.state == PresetLifecycleState.PUBLISHED }
                ?.let {
                    InferencePresetRef(
                        InferencePresetId(it.metadata.presetId),
                        it.metadata.revision,
                    )
                }
        }.toSet()
        if (exposedPresets.size != exposures.size || exposedPresets.isEmpty()) return null
        val defaultExposure = exposures.singleOrNull { it.isDefault }
        val defaultPreset = defaultExposure?.let {
            InferencePresetRef(InferencePresetId(it.presetId), it.presetRevision)
        }
        return HarnessOmbraConsumerPolicy.create(
            applicationId = applicationId,
            revision = buildRevision(useCase.revision, binding.revision, exposures),
            exposedPresets = exposedPresets,
            defaultPreset = defaultPreset,
        )
    }

    private fun buildRevision(
        useCaseRevision: Int,
        bindingRevision: Int,
        exposures: List<io.github.daniele21.localllm.models.StoredPresetExposure>,
    ): String = buildString {
        append(HarnessOmbraConsumerPolicy.REVISION)
        append("|uc=").append(useCaseRevision)
        append("|binding=").append(bindingRevision)
        exposures.forEach { exposure ->
            append('|').append(exposure.presetId).append('@').append(exposure.presetRevision)
            if (exposure.isDefault) append(":default")
        }
    }
}
