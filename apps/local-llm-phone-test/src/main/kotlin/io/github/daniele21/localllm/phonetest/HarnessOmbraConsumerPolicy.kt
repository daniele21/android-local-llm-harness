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
import io.github.daniele21.localllm.models.HostControlPlaneState
import io.github.daniele21.localllm.models.HostControlPlaneStore
import io.github.daniele21.localllm.models.PresetLifecycleState
import io.github.daniele21.localllm.models.StoredPresetExposure
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
 * Projects canonical Host control-plane preset exposure into Consumer API capability policy.
 * Reads are observational only: no reconciliation, model load or runtime mutation is performed.
 */
internal class HarnessControlPlaneConsumerPolicyRegistry(
    private val store: HostControlPlaneStore,
    private val fallback: ConsumerUseCasePolicyRegistry? = null,
) : ConsumerUseCasePolicyRegistry {
    override fun find(applicationId: ApplicationId, useCaseId: UseCaseId): ConsumerUseCasePolicy? =
        if (isPiiPolicyTarget(applicationId, useCaseId)) {
            findPiiPolicy(applicationId, useCaseId)
        } else {
            fallback?.find(applicationId, useCaseId)
        }

    private fun findPiiPolicy(applicationId: ApplicationId, useCaseId: UseCaseId): ConsumerUseCasePolicy? {
        val state = store.snapshot()
        val application = authorizedApplication(state, applicationId) ?: return null
        val binding = state.latestBinding(application.applicationId, useCaseId)?.takeIf { it.enabled } ?: return null
        val useCaseRevision = activeUseCaseRevision(state, useCaseId) ?: return null
        val exposures = state.exposuresFor(binding.bindingId, binding.revision)
        val exposedPresets = publishedPresetRefs(state, useCaseId, exposures)
        return exposedPresets
            ?.takeIf(Set<InferencePresetRef>::isNotEmpty)
            ?.takeIf { it.size == exposures.size }
            ?.let { refs ->
                HarnessOmbraConsumerPolicy.create(
                    applicationId = applicationId,
                    revision = buildRevision(useCaseRevision, binding.revision, exposures),
                    exposedPresets = refs,
                    defaultPreset = exposures.singleOrNull(StoredPresetExposure::isDefault)?.toPresetRef(),
                )
            }
    }

    private fun isPiiPolicyTarget(applicationId: ApplicationId, useCaseId: UseCaseId): Boolean =
        applicationId in HarnessSharedRuntimeBindings.piiConsumerApplicationIds &&
            useCaseId == HarnessSharedRuntimeBindings.ombraUseCaseId

    private fun authorizedApplication(state: HostControlPlaneState, applicationId: ApplicationId) =
        state.applications.singleOrNull { it.applicationId == applicationId }
            ?.takeIf { it.state == ApplicationRegistrationState.AUTHORIZED }

    private fun activeUseCaseRevision(state: HostControlPlaneState, useCaseId: UseCaseId): Int? = state.latestUseCase(useCaseId)
        ?.takeIf { it.state == UseCaseDefinitionState.ACTIVE }
        ?.revision

    private fun HostControlPlaneState.exposuresFor(bindingId: String, bindingRevision: Int): List<StoredPresetExposure> = exposures
        .filter { it.bindingId == bindingId && it.bindingRevision == bindingRevision }
        .sortedWith(compareBy({ it.presetId }, { it.presetRevision }))

    private fun publishedPresetRefs(
        state: HostControlPlaneState,
        useCaseId: UseCaseId,
        exposures: List<StoredPresetExposure>,
    ): Set<InferencePresetRef>? {
        val refs = exposures.mapNotNull { exposure ->
            state.preset(useCaseId, exposure.presetId, exposure.presetRevision)
                ?.takeIf { it.state == PresetLifecycleState.PUBLISHED }
                ?.let { InferencePresetRef(InferencePresetId(it.metadata.presetId), it.metadata.revision) }
        }.toSet()
        return refs.takeIf { it.size == exposures.size }
    }

    private fun StoredPresetExposure.toPresetRef(): InferencePresetRef = InferencePresetRef(InferencePresetId(presetId), presetRevision)

    private fun buildRevision(useCaseRevision: Int, bindingRevision: Int, exposures: List<StoredPresetExposure>): String = buildString {
        append(HarnessOmbraConsumerPolicy.REVISION)
        append("|uc=").append(useCaseRevision)
        append("|binding=").append(bindingRevision)
        exposures.forEach { exposure ->
            append('|').append(exposure.presetId).append('@').append(exposure.presetRevision)
            if (exposure.isDefault) append(":default")
        }
    }
}
