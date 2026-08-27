package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.models.ContextPreference
import io.github.daniele21.localllm.models.HostControlPlaneState
import io.github.daniele21.localllm.models.PresetLifecycleState
import io.github.daniele21.localllm.models.ResolvedHostExecution
import io.github.daniele21.localllm.models.ResolvedUseCase
import io.github.daniele21.localllm.models.StoredPresetExposure

/**
 * Converts one already-resolved Host control-plane execution into the exact runtime profile exposed
 * to the activated consumer. It reuses canonical generation/prompt definitions by reference rather
 * than duplicating tuning values in control-plane persistence.
 */
internal object HarnessActivatedUseCaseMaterializer {
    fun materialize(
        model: ImportedPhoneModel,
        applicationId: ApplicationId,
        execution: ResolvedHostExecution,
        state: HostControlPlaneState,
    ): ResolvedUseCase {
        val base = HarnessSharedRuntimeBindings.resolveOmbra(model, applicationId)
        require(base.model.artifact.digest == execution.modelDigest) { "Resolved model digest changed before activation" }
        require(base.model.id == execution.modelProfileId) { "Resolved model profile changed before activation" }

        val exposures = state.exposuresFor(execution.bindingId, execution.bindingRevision)
        require(exposures.isNotEmpty()) { "Activated binding exposes no runtime presets" }
        val materializedPresets = exposures.map { exposure ->
            val definition = requireNotNull(
                state.preset(execution.useCaseId, exposure.presetId, exposure.presetRevision),
            ) { "Exposed preset disappeared before activation" }
            require(definition.state == PresetLifecycleState.PUBLISHED) { "Exposed preset is not published" }
            val canonical = requireNotNull(
                base.useCase.presets.singleOrNull { it.ref == definition.execution.inferencePreset },
            ) { "Preset references an unavailable canonical inference profile" }
            val contextTokens = definition.execution.contextTokens
            canonical.copy(
                ref = consumerRef(exposure),
                contextPreference =
                if (contextTokens == null) {
                    canonical.contextPreference
                } else {
                    ContextPreference(
                        preferredTokens = contextTokens,
                        recommendedMaximumTokens = contextTokens,
                        maximumTokens = contextTokens,
                    )
                },
            )
        }
        val selectedRef = InferencePresetRef(InferencePresetId(execution.presetId), execution.presetRevision)
        val selected = requireNotNull(materializedPresets.singleOrNull { it.ref == selectedRef }) {
            "Resolved preset is no longer exposed by the activated binding"
        }
        val defaultPreset = exposures.singleOrNull(StoredPresetExposure::isDefault)?.let(::consumerRef)
        val useCase = base.useCase.copy(
            generationDefaults = selected.generation,
            systemPromptVersion = selected.systemPromptVersion,
            systemPrompt = selected.systemPrompt,
            cachePolicy = execution.cachePolicy,
            presets = materializedPresets,
            defaultPreset = defaultPreset,
        )
        return base.copy(
            binding = base.binding.copy(useCaseProfileId = useCase.id),
            useCase = useCase,
        )
    }

    private fun HostControlPlaneState.exposuresFor(bindingId: String, bindingRevision: Int): List<StoredPresetExposure> = exposures
        .filter { it.bindingId == bindingId && it.bindingRevision == bindingRevision }
        .sortedWith(compareBy({ it.presetId }, { it.presetRevision }))

    private fun consumerRef(exposure: StoredPresetExposure): InferencePresetRef =
        InferencePresetRef(InferencePresetId(exposure.presetId), exposure.presetRevision)
}
