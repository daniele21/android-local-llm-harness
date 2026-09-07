package io.github.daniele21.localllm.models

import io.github.daniele21.localllm.contracts.ThinkingMode

enum class Qwen35ModelTier {
    B0_8,
    B2,
    B4,
}

enum class Qwen35GenerationProfileId {
    QWEN35_TEXT_FAST,
    QWEN35_TEXT_QUALITY,
    QWEN35_THINKING,
    QWEN35_PRECISE,
    QWEN35_JSON,
}

data class Qwen35GenerationProfile(val id: Qwen35GenerationProfileId, val version: Int, val defaults: GenerationDefaults)

object Qwen35GenerationGuardPolicies {
    const val VERSION = 2

    fun forTier(tier: Qwen35ModelTier): GenerationGuardPolicy = when (tier) {
        Qwen35ModelTier.B0_8 -> policy(thinkingBudget = 192, answerReserve = 256, activationTokens = 64)

        Qwen35ModelTier.B2 -> policy(thinkingBudget = 384, answerReserve = 512, activationTokens = 96)

        // Keep the unmeasured 4B tier bounded like 2B until physical-device evidence justifies wider budgets.
        Qwen35ModelTier.B4 -> policy(thinkingBudget = 384, answerReserve = 512, activationTokens = 96)
    }

    private fun policy(thinkingBudget: Int, answerReserve: Int, activationTokens: Int) = GenerationGuardPolicy(
        version = VERSION,
        enabled = true,
        thinkingTokenBudget = thinkingBudget,
        repetitionActivationTokens = activationTokens,
        observationWindowChars = 4_096,
        minPatternChars = 24,
        maxPatternChars = 256,
        repetitionOccurrences = 4,
        answerReserveTokens = answerReserve,
    )
}

object Qwen35GenerationProfiles {
    const val VERSION = 4

    fun forTier(tier: Qwen35ModelTier): List<Qwen35GenerationProfile> = if (tier == Qwen35ModelTier.B4) {
        unslothFourBProfiles()
    } else {
        legacyMobileProfiles(tier)
    }

    fun defaultForTier(tier: Qwen35ModelTier): GenerationDefaults =
        forTier(tier).single { it.id == Qwen35GenerationProfileId.QWEN35_TEXT_QUALITY }.defaults

    /**
     * Unsloth's Qwen3.5 4B guidance expressed through Harnex's existing preset vocabulary.
     *
     * TEXT_FAST / JSON -> non-thinking general
     * TEXT_QUALITY     -> non-thinking reasoning
     * THINKING         -> thinking general
     * PRECISE          -> thinking precise/coding
     */
    private fun unslothFourBProfiles(): List<Qwen35GenerationProfile> = listOf(
        profile(Qwen35ModelTier.B4, Qwen35GenerationProfileId.QWEN35_TEXT_FAST, 256, ThinkingMode.DISABLED, 0.7f, 0.8f, 1.5f),
        profile(Qwen35ModelTier.B4, Qwen35GenerationProfileId.QWEN35_TEXT_QUALITY, 768, ThinkingMode.DISABLED, 1f, 0.95f, 1.5f),
        profile(Qwen35ModelTier.B4, Qwen35GenerationProfileId.QWEN35_THINKING, 1_024, ThinkingMode.ENABLED, 1f, 0.95f, 1.5f),
        profile(Qwen35ModelTier.B4, Qwen35GenerationProfileId.QWEN35_PRECISE, 1_024, ThinkingMode.ENABLED, 0.6f, 0.95f, 0f),
        profile(Qwen35ModelTier.B4, Qwen35GenerationProfileId.QWEN35_JSON, 512, ThinkingMode.DISABLED, 0.7f, 0.8f, 1.5f),
    )

    private fun legacyMobileProfiles(tier: Qwen35ModelTier): List<Qwen35GenerationProfile> {
        val qualityTokens = if (tier == Qwen35ModelTier.B0_8) 512 else 768
        val thinkingTokens = if (tier == Qwen35ModelTier.B0_8) 512 else 1_024
        return listOf(
            profile(tier, Qwen35GenerationProfileId.QWEN35_TEXT_FAST, 256, ThinkingMode.DISABLED, 1f, 1f, 2f),
            profile(tier, Qwen35GenerationProfileId.QWEN35_TEXT_QUALITY, qualityTokens, ThinkingMode.DISABLED, 1f, 1f, 2f),
            profile(tier, Qwen35GenerationProfileId.QWEN35_THINKING, thinkingTokens, ThinkingMode.ENABLED, 1f, 0.95f, 1.5f),
            profile(tier, Qwen35GenerationProfileId.QWEN35_PRECISE, thinkingTokens, ThinkingMode.ENABLED, 0.6f, 0.95f, 0f),
            profile(tier, Qwen35GenerationProfileId.QWEN35_JSON, 512, ThinkingMode.DISABLED, 1f, 1f, 2f),
        )
    }

    private fun profile(
        tier: Qwen35ModelTier,
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
            guardPolicy = Qwen35GenerationGuardPolicies.forTier(tier),
            reasoningStreamProtocol = ReasoningStreamProtocol.QWEN35_THINK_TAGS,
        ),
    )
}
