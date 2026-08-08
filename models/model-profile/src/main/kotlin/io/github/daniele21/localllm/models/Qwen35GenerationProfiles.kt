package io.github.daniele21.localllm.models

import io.github.daniele21.localllm.contracts.ThinkingMode

enum class Qwen35ModelTier {
    B0_8,
    B2,
}

enum class Qwen35GenerationProfileId {
    QWEN35_TEXT_FAST,
    QWEN35_TEXT_QUALITY,
    QWEN35_THINKING,
    QWEN35_PRECISE,
    QWEN35_JSON,
}

data class Qwen35GenerationProfile(val id: Qwen35GenerationProfileId, val version: Int, val defaults: GenerationDefaults)

object Qwen35GenerationProfiles {
    const val VERSION = 1

    fun forTier(tier: Qwen35ModelTier): List<Qwen35GenerationProfile> {
        val qualityTokens = if (tier == Qwen35ModelTier.B0_8) 512 else 768
        val thinkingTokens = if (tier == Qwen35ModelTier.B0_8) 512 else 1_024
        return listOf(
            profile(Qwen35GenerationProfileId.QWEN35_TEXT_FAST, 256, ThinkingMode.DISABLED, 1f, 1f, 2f),
            profile(Qwen35GenerationProfileId.QWEN35_TEXT_QUALITY, qualityTokens, ThinkingMode.DISABLED, 1f, 1f, 2f),
            profile(Qwen35GenerationProfileId.QWEN35_THINKING, thinkingTokens, ThinkingMode.ENABLED, 1f, 0.95f, 1.5f),
            profile(Qwen35GenerationProfileId.QWEN35_PRECISE, thinkingTokens, ThinkingMode.ENABLED, 0.6f, 0.95f, 0f),
            profile(Qwen35GenerationProfileId.QWEN35_JSON, 512, ThinkingMode.DISABLED, 1f, 1f, 2f),
        )
    }

    fun defaultForTier(tier: Qwen35ModelTier): GenerationDefaults =
        forTier(tier).single { it.id == Qwen35GenerationProfileId.QWEN35_TEXT_QUALITY }.defaults

    private fun profile(
        id: Qwen35GenerationProfileId,
        maxOutputTokens: Int,
        thinkingMode: ThinkingMode,
        temperature: Float,
        topP: Float,
        presencePenalty: Float,
    ) = Qwen35GenerationProfile(
        id = id,
        version = VERSION,
        defaults = GenerationDefaults(
            maxOutputTokens = maxOutputTokens,
            temperature = temperature,
            topP = topP,
            topK = 20,
            minP = 0f,
            presencePenalty = presencePenalty,
            thinkingMode = thinkingMode,
            repeatPenalty = 1f,
            repeatLastN = 64,
        ),
    )
}
