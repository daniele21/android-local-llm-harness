package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.catalog.CuratedModelCatalog
import io.github.daniele21.localllm.models.GenerationDefaults
import io.github.daniele21.localllm.models.Qwen35GenerationProfiles
import io.github.daniele21.localllm.models.Qwen35ModelTier

internal data class HarnessPresetModelOption(
    val modelId: String,
    val modelProfileId: String,
    val displayName: String,
    val description: String,
    val tier: Qwen35ModelTier,
)

internal data class HarnessPresetConfigurationRow(val label: String, val value: String)

internal data class HarnessPresetConfigurationSummary(
    val rows: List<HarnessPresetConfigurationRow>,
    val unavailableReason: String? = null,
) {
    val available: Boolean
        get() = unavailableReason == null
}

internal fun harnessPresetModelOptions(useCaseId: String): List<HarnessPresetModelOption> =
    CuratedModelCatalog.releases.mapNotNull { release ->
        val modelProfileId = HarnessSharedRuntimeBindings.modelProfileId(useCaseId, release.profileKey.value)
            ?: return@mapNotNull null
        val tier = runCatching { Qwen35PhoneModelPolicy.tierFor(release) }.getOrNull() ?: return@mapNotNull null
        HarnessPresetModelOption(
            modelId = release.id.modelId.value,
            modelProfileId = modelProfileId,
            displayName = release.displayName,
            description = release.description,
            tier = tier,
        )
    }

internal fun isHarnessPresetModelSelectionValid(
    useCaseId: String,
    modelProfileId: String?,
): Boolean = modelProfileId == null || harnessPresetModelOptions(useCaseId).any { it.modelProfileId == modelProfileId }

internal fun harnessPresetConfigurationSummary(
    useCaseId: String,
    preset: HarnessPresetSummary,
    selectedModelProfileId: String?,
): HarnessPresetConfigurationSummary {
    val inferencePresetId = preset.inferencePresetId
        ?: return unavailableSummary("Inference profile is unavailable from the canonical preset definition.")
    val inferenceRevision = preset.inferencePresetRevision
        ?: return unavailableSummary("Inference profile revision is unavailable from the canonical preset definition.")
    val option = playgroundPresetOptions.singleOrNull { it.id == inferencePresetId }
        ?: return unavailableSummary("Inference profile '$inferencePresetId' is not supported by this phone runtime.")
    if (inferenceRevision != Qwen35GenerationProfiles.VERSION) {
        return unavailableSummary(
            "Inference profile revision $inferenceRevision is not supported by runtime revision ${Qwen35GenerationProfiles.VERSION}.",
        )
    }
    val modelOptions = harnessPresetModelOptions(useCaseId)
    val tiers = if (selectedModelProfileId == null) {
        listOf(Qwen35ModelTier.B0_8, Qwen35ModelTier.B2)
    } else {
        val selected = modelOptions.singleOrNull { it.modelProfileId == selectedModelProfileId }
            ?: return unavailableSummary("Selected model target is not available in the curated runtime catalog.")
        listOf(selected.tier)
    }
    val defaults = tiers.map { tier ->
        tier to Qwen35GenerationProfiles.forTier(tier).single { it.id == option.profileId }.defaults
    }
    val modelTarget = selectedModelProfileId?.let { selected ->
        modelOptions.singleOrNull { it.modelProfileId == selected }?.displayName ?: selected
    } ?: "Automatic compatible local model"

    return HarnessPresetConfigurationSummary(
        rows = listOf(
            HarnessPresetConfigurationRow("Model target", modelTarget),
            HarnessPresetConfigurationRow("Inference profile", "$inferencePresetId · v$inferenceRevision"),
            profileRow("Max output tokens", defaults) { it.maxOutputTokens.toString() },
            profileRow("Thinking", defaults) { it.thinkingMode.name.lowercase() },
            profileRow("Temperature", defaults) { it.temperature.toString() },
            profileRow("Top-p", defaults) { it.topP.toString() },
            profileRow("Top-k", defaults) { it.topK.toString() },
            profileRow("Min-p", defaults) { it.minP.toString() },
            profileRow("Presence penalty", defaults) { it.presencePenalty.toString() },
            profileRow("Repeat penalty", defaults) { it.repeatPenalty.toString() },
            profileRow("Repeat window", defaults) { it.repeatLastN.toString() },
            profileRow("Seed policy", defaults) { if (it.seed == null) "random" else "fixed ${it.seed}" },
            HarnessPresetConfigurationRow("Context tokens", preset.contextTokens?.toString() ?: "Use-case default"),
            HarnessPresetConfigurationRow("Warm retention", preset.retainModelWarmMs.toDurationLabel()),
            HarnessPresetConfigurationRow("Stateless context reuse", preset.reuseStatelessContext.toEnabledLabel()),
            HarnessPresetConfigurationRow("Prefix snapshot", preset.enablePrefixSnapshot.toEnabledLabel()),
            HarnessPresetConfigurationRow("Deterministic result cache", preset.enableDeterministicResultCache.toEnabledLabel()),
        ),
    )
}

private fun profileRow(
    label: String,
    defaults: List<Pair<Qwen35ModelTier, GenerationDefaults>>,
    value: (GenerationDefaults) -> String,
): HarnessPresetConfigurationRow {
    val values = defaults.map { (tier, generation) -> tier to value(generation) }
    val distinct = values.map { it.second }.distinct()
    val rendered = if (distinct.size == 1) {
        distinct.single()
    } else {
        values.joinToString(" / ") { (tier, profileValue) -> "$profileValue (${tier.label()})" }
    }
    return HarnessPresetConfigurationRow(label, rendered)
}

private fun Qwen35ModelTier.label(): String = when (this) {
    Qwen35ModelTier.B0_8 -> "0.8B"
    Qwen35ModelTier.B2 -> "2B"
}

private fun Long?.toDurationLabel(): String = when {
    this == null -> "Unavailable"
    this == 0L -> "No warm retention"
    this % 60_000L == 0L -> "${this / 60_000L} min"
    else -> "$this ms"
}

private fun Boolean?.toEnabledLabel(): String = when (this) {
    true -> "Enabled"
    false -> "Disabled"
    null -> "Unavailable"
}

private fun unavailableSummary(reason: String) = HarnessPresetConfigurationSummary(emptyList(), reason)
