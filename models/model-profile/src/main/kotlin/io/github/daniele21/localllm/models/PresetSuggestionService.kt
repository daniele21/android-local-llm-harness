package io.github.daniele21.localllm.models

import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.UseCaseId

enum class PresetSuggestionOptimization {
    FAST,
    BALANCED,
    QUALITY,
}

enum class PresetSuggestionGlobalBlocker {
    USE_CASE_NOT_ACTIVE,
    OUTPUT_MODE_UNSUPPORTED,
    SESSION_KIND_UNSUPPORTED,
    REASONING_UNSUPPORTED,
    CONTEXT_LIMIT_UNSUPPORTED,
}

enum class PresetSuggestionProfileRejection {
    INSUFFICIENT_CONTEXT,
}

data class PresetSuggestionRuntimeCapabilities(
    val supportedOutputModes: Set<OutputMode>,
    val supportedSessionKinds: Set<SessionKind>,
    val reasoningSupported: Boolean,
    val maximumContextTokens: Int? = null,
) {
    init {
        require(supportedOutputModes.isNotEmpty()) { "Supported output modes must not be empty" }
        require(supportedSessionKinds.isNotEmpty()) { "Supported session kinds must not be empty" }
        require(maximumContextTokens == null || maximumContextTokens > 0) {
            "Maximum context tokens must be positive"
        }
    }
}

data class SuggestedPresetTemplate(
    val templateId: String,
    val useCaseId: UseCaseId,
    val displayName: String,
    val description: String,
    val optimization: PresetSuggestionOptimization,
    val modelProfileId: String,
    val contextTokens: Int,
    val rationale: List<String>,
    val creationSource: PresetCreationSource = PresetCreationSource.SUGGESTED,
    val lifecycleState: PresetLifecycleState = PresetLifecycleState.DRAFT,
) {
    init {
        require(templateId.isNotBlank()) { "Suggestion template ID must not be blank" }
        require(displayName.isNotBlank()) { "Suggestion display name must not be blank" }
        require(description.isNotBlank()) { "Suggestion description must not be blank" }
        require(modelProfileId.isNotBlank()) { "Suggestion model profile ID must not be blank" }
        require(contextTokens > 0) { "Suggestion context tokens must be positive" }
        require(rationale.isNotEmpty()) { "Suggestion rationale must not be empty" }
        require(creationSource == PresetCreationSource.SUGGESTED) { "Suggested templates must use SUGGESTED creation source" }
        require(lifecycleState == PresetLifecycleState.DRAFT) { "Suggested templates must remain draft until explicitly published" }
    }
}

data class RejectedPresetSuggestionProfile(
    val modelProfileId: String,
    val reasons: Set<PresetSuggestionProfileRejection>,
) {
    init {
        require(modelProfileId.isNotBlank()) { "Rejected model profile ID must not be blank" }
        require(reasons.isNotEmpty()) { "Rejected profile must contain at least one reason" }
    }
}

data class PresetSuggestionResult(
    val suggestions: List<SuggestedPresetTemplate>,
    val rejectedProfiles: List<RejectedPresetSuggestionProfile>,
    val globalBlockers: Set<PresetSuggestionGlobalBlocker>,
) {
    init {
        require(suggestions.size <= MAX_SUGGESTIONS) { "Preset suggestions must remain bounded" }
        require(globalBlockers.isEmpty() || suggestions.isEmpty()) {
            "Globally blocked use cases must not receive preset suggestions"
        }
    }

    companion object {
        const val MAX_SUGGESTIONS = 3
    }
}

class PresetSuggestionService {
    fun suggest(
        useCase: UseCaseDefinition,
        installedProfiles: Collection<GgufModelProfile>,
        runtimeCapabilities: PresetSuggestionRuntimeCapabilities,
    ): PresetSuggestionResult {
        val blockers = globalBlockers(useCase, runtimeCapabilities)
        if (blockers.isNotEmpty()) {
            return PresetSuggestionResult(
                suggestions = emptyList(),
                rejectedProfiles = emptyList(),
                globalBlockers = blockers,
            )
        }

        val sortedProfiles = installedProfiles
            .distinctBy(GgufModelProfile::id)
            .sortedWith(PROFILE_ORDER)
        val compatible = mutableListOf<GgufModelProfile>()
        val rejected = mutableListOf<RejectedPresetSuggestionProfile>()
        sortedProfiles.forEach { profile ->
            val reasons = buildSet {
                if (profile.contextSize < useCase.requirements.minimumContextTokens) {
                    add(PresetSuggestionProfileRejection.INSUFFICIENT_CONTEXT)
                }
            }
            if (reasons.isEmpty()) {
                compatible += profile
            } else {
                rejected += RejectedPresetSuggestionProfile(profile.id, reasons)
            }
        }

        return PresetSuggestionResult(
            suggestions = buildSuggestions(useCase, compatible),
            rejectedProfiles = rejected,
            globalBlockers = emptySet(),
        )
    }

    private fun globalBlockers(
        useCase: UseCaseDefinition,
        runtimeCapabilities: PresetSuggestionRuntimeCapabilities,
    ): Set<PresetSuggestionGlobalBlocker> = buildSet {
        if (useCase.state != UseCaseDefinitionState.ACTIVE) {
            add(PresetSuggestionGlobalBlocker.USE_CASE_NOT_ACTIVE)
        }
        if (useCase.requirements.outputMode !in runtimeCapabilities.supportedOutputModes) {
            add(PresetSuggestionGlobalBlocker.OUTPUT_MODE_UNSUPPORTED)
        }
        if (useCase.requirements.sessionKind !in runtimeCapabilities.supportedSessionKinds) {
            add(PresetSuggestionGlobalBlocker.SESSION_KIND_UNSUPPORTED)
        }
        if (useCase.requirements.reasoningSupported && !runtimeCapabilities.reasoningSupported) {
            add(PresetSuggestionGlobalBlocker.REASONING_UNSUPPORTED)
        }
        if (
            runtimeCapabilities.maximumContextTokens != null &&
            runtimeCapabilities.maximumContextTokens < useCase.requirements.minimumContextTokens
        ) {
            add(PresetSuggestionGlobalBlocker.CONTEXT_LIMIT_UNSUPPORTED)
        }
    }

    private fun buildSuggestions(
        useCase: UseCaseDefinition,
        compatibleProfiles: List<GgufModelProfile>,
    ): List<SuggestedPresetTemplate> {
        if (compatibleProfiles.isEmpty()) return emptyList()
        if (compatibleProfiles.size == 1) {
            return listOf(template(useCase, compatibleProfiles.single(), PresetSuggestionOptimization.BALANCED))
        }

        val selections = linkedMapOf<PresetSuggestionOptimization, GgufModelProfile>()
        selections[PresetSuggestionOptimization.FAST] = compatibleProfiles.first()
        if (compatibleProfiles.size >= 3) {
            selections[PresetSuggestionOptimization.BALANCED] = compatibleProfiles[compatibleProfiles.size / 2]
        }
        selections[PresetSuggestionOptimization.QUALITY] = compatibleProfiles.last()

        return selections.map { (optimization, profile) -> template(useCase, profile, optimization) }
    }

    private fun template(
        useCase: UseCaseDefinition,
        profile: GgufModelProfile,
        optimization: PresetSuggestionOptimization,
    ): SuggestedPresetTemplate {
        val metadata = when (optimization) {
            PresetSuggestionOptimization.FAST -> Triple(
                "Fast",
                "Prioritizes lower local model footprint for latency and memory pressure.",
                "Selected the smallest compatible installed model artifact; this is a latency-oriented heuristic, not a quality guarantee.",
            )

            PresetSuggestionOptimization.BALANCED -> Triple(
                "Balanced",
                "Balances local model footprint with available model capacity.",
                "Selected the median compatible installed model by deterministic local footprint ordering.",
            )

            PresetSuggestionOptimization.QUALITY -> Triple(
                "Quality",
                "Prioritizes available local model capacity over footprint.",
                "Selected the largest compatible installed model artifact; this is a capacity-oriented heuristic, not an accuracy guarantee.",
            )
        }
        return SuggestedPresetTemplate(
            templateId = "${useCase.useCaseId.value}-${optimization.name.lowercase()}",
            useCaseId = useCase.useCaseId,
            displayName = metadata.first,
            description = metadata.second,
            optimization = optimization,
            modelProfileId = profile.id,
            contextTokens = useCase.requirements.minimumContextTokens,
            rationale = listOf(
                metadata.third,
                "Model context ${profile.contextSize} satisfies required minimum ${useCase.requirements.minimumContextTokens} tokens.",
                "Output ${useCase.requirements.outputMode} and session ${useCase.requirements.sessionKind} are supported by the supplied runtime capabilities.",
            ),
        )
    }

    private companion object {
        val PROFILE_ORDER = compareBy<GgufModelProfile>(
            { it.artifact.sizeBytes },
            { it.contextSize },
            GgufModelProfile::id,
        )
    }
}
