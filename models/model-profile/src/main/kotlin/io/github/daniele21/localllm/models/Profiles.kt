package io.github.daniele21.localllm.models

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.SeedPolicy
import io.github.daniele21.localllm.contracts.ThinkingMode
import io.github.daniele21.localllm.contracts.UseCaseId

data class GgufArtifact(
    val digest: ModelDigest,
    val fileName: String,
    val sizeBytes: Long,
    val architecture: String,
    val quantization: String,
    val source: ArtifactSource,
)

sealed interface ArtifactSource {
    data class Bundled(val assetPath: String) : ArtifactSource
    data class Download(val url: String) : ArtifactSource
    data class Imported(val displayName: String) : ArtifactSource
}

data class RuntimeCapabilityProfile(
    val requiredBackendId: String? = null,
    val requiredBackendRevision: String? = null,
    val approvedContextTiers: List<Int> = emptyList(),
    val contextSafetyReserveTokens: Int = 256,
    val supportsStatelessContextReuse: Boolean = true,
    val supportsPrefixSnapshot: Boolean = false,
    val supportsSessionRestore: Boolean = false,
    val supportsPrefixReuse: Boolean = false,
) {
    init {
        require(requiredBackendId == null || requiredBackendId.isNotBlank()) { "Required backend ID must not be blank" }
        require(requiredBackendRevision == null || requiredBackendRevision.isNotBlank()) {
            "Required backend revision must not be blank"
        }
        require(contextSafetyReserveTokens >= 0) { "Context safety reserve must not be negative" }
        require(approvedContextTiers.all { it > 0 }) { "Approved context tiers must be positive" }
        require(approvedContextTiers == approvedContextTiers.distinct().sorted()) {
            "Approved context tiers must be unique and sorted"
        }
    }
}

data class GenerationGuardPolicy(
    val version: Int = 1,
    val enabled: Boolean = false,
    val thinkingTokenBudget: Int = 0,
    val repetitionActivationTokens: Int = 0,
    val observationWindowChars: Int = 0,
    val minPatternChars: Int = 0,
    val maxPatternChars: Int = 0,
    val repetitionOccurrences: Int = 0,
) {
    init {
        require(version > 0) { "Generation guard version must be positive" }
        if (enabled) {
            require(thinkingTokenBudget > 0) { "Thinking token budget must be positive" }
            require(repetitionActivationTokens > 0) { "Repetition activation threshold must be positive" }
            require(observationWindowChars > 0) { "Observation window must be positive" }
            require(minPatternChars > 0) { "Minimum repetition pattern must be positive" }
            require(maxPatternChars >= minPatternChars) { "Maximum repetition pattern must not be smaller than minimum" }
            require(repetitionOccurrences >= 2) { "Repetition occurrences must be at least two" }
            require(observationWindowChars >= maxPatternChars * repetitionOccurrences) {
                "Observation window must contain the configured repetition evidence"
            }
        }
    }

    companion object {
        fun disabled(): GenerationGuardPolicy = GenerationGuardPolicy()
    }
}

data class GgufModelProfile(
    val id: String,
    val artifact: GgufArtifact,
    val contextSize: Int,
    val batchSize: Int,
    val microBatchSize: Int,
    val cpuThreads: Int,
    val batchThreads: Int,
    val gpuLayers: Int,
    val useMmap: Boolean = true,
    val useMlock: Boolean = false,
    val flashAttention: Boolean = false,
    val kvCacheTypeK: String? = null,
    val kvCacheTypeV: String? = null,
    val chatTemplatePolicy: ChatTemplatePolicy = ChatTemplatePolicy(),
    val runtimeCapabilities: RuntimeCapabilityProfile = RuntimeCapabilityProfile(),
)

data class GenerationDefaults(
    val maxOutputTokens: Int,
    val temperature: Float,
    val topP: Float = 0.95f,
    val topK: Int = 40,
    val minP: Float = 0f,
    val presencePenalty: Float = 0f,
    val thinkingMode: ThinkingMode = ThinkingMode.DISABLED,
    val seed: Long? = null,
    val seedPolicy: SeedPolicy = seed?.let(SeedPolicy::Fixed) ?: SeedPolicy.Random,
    val repeatPenalty: Float = DEFAULT_REPEAT_PENALTY,
    val repeatLastN: Int = DEFAULT_REPEAT_LAST_N,
    val guardPolicy: GenerationGuardPolicy = GenerationGuardPolicy.disabled(),
) {
    init {
        require(maxOutputTokens > 0) { "Maximum output tokens must be positive" }
        require(temperature.isFinite() && temperature in 0f..2f) { "Temperature must be in [0, 2]" }
        require(topP.isFinite() && topP > 0f && topP <= 1f) { "Top-p must be in (0, 1]" }
        require(topK in 0..MAX_TOP_K) { "Top-k must be in [0, $MAX_TOP_K]" }
        require(minP.isFinite() && minP in 0f..1f) { "Min-p must be in [0, 1]" }
        require(presencePenalty.isFinite() && presencePenalty in 0f..2f) {
            "Presence penalty must be in [0, 2]"
        }
        require(repeatPenalty.isFinite() && repeatPenalty in MIN_REPEAT_PENALTY..MAX_REPEAT_PENALTY) {
            "Repeat penalty must be in [$MIN_REPEAT_PENALTY, $MAX_REPEAT_PENALTY]"
        }
        require(repeatLastN in 0..MAX_REPEAT_LAST_N) { "Repeat window must be in [0, $MAX_REPEAT_LAST_N]" }
        require(repeatPenalty == DEFAULT_REPEAT_PENALTY || repeatLastN > 0) {
            "Repeat window must be positive when repeat penalty is enabled"
        }
    }
}

data class ChatTemplatePolicy(
    val applicationOverrideId: String? = null,
    val applicationOverride: String? = null,
    val familyFallbackId: String? = null,
    val familyFallback: String? = null,
    val allowRawCompletion: Boolean = false,
    val stopSequences: List<String> = emptyList(),
) {
    init {
        require((applicationOverrideId == null) == (applicationOverride == null)) {
            "Application chat template ID and template must be provided together"
        }
        require((familyFallbackId == null) == (familyFallback == null)) {
            "Family chat template ID and template must be provided together"
        }
        require(stopSequences.size <= MAX_STOP_SEQUENCES) { "Too many stop sequences" }
        require(stopSequences.none(String::isBlank)) { "Stop sequences must not be blank" }
        require(stopSequences.none { '\u0000' in it }) { "Stop sequences must not contain NUL" }
        require(stopSequences.all { it.toByteArray(Charsets.UTF_8).size <= MAX_STOP_SEQUENCE_BYTES }) {
            "Stop sequence exceeds $MAX_STOP_SEQUENCE_BYTES UTF-8 bytes"
        }
        require(stopSequences.sumOf { it.toByteArray(Charsets.UTF_8).size } <= MAX_TOTAL_STOP_SEQUENCE_BYTES) {
            "Stop sequences exceed $MAX_TOTAL_STOP_SEQUENCE_BYTES total UTF-8 bytes"
        }
    }
}

data class ContextPreference(val preferredTokens: Int? = null, val recommendedMaximumTokens: Int? = null, val maximumTokens: Int? = null) {
    init {
        require(preferredTokens == null || preferredTokens > 0) { "Preferred context tokens must be positive" }
        require(recommendedMaximumTokens == null || recommendedMaximumTokens > 0) {
            "Recommended maximum context tokens must be positive"
        }
        require(maximumTokens == null || maximumTokens > 0) { "Maximum context tokens must be positive" }
        require(preferredTokens == null || recommendedMaximumTokens == null || preferredTokens <= recommendedMaximumTokens) {
            "Preferred context tokens must not exceed the recommended maximum"
        }
        require(preferredTokens == null || maximumTokens == null || preferredTokens <= maximumTokens) {
            "Preferred context tokens must not exceed the maximum"
        }
        require(recommendedMaximumTokens == null || maximumTokens == null || recommendedMaximumTokens <= maximumTokens) {
            "Recommended maximum context tokens must not exceed the hard maximum"
        }
    }
}

data class InferencePreset(
    val ref: InferencePresetRef,
    val generation: GenerationDefaults,
    val systemPromptVersion: String,
    val systemPrompt: String,
    val contextPreference: ContextPreference = ContextPreference(),
    val allowedOutputModes: Set<OutputMode> = setOf(OutputMode.TEXT),
)

data class UseCaseProfile(
    val id: String,
    val modelProfileId: String,
    val systemPromptVersion: String,
    val generationDefaults: GenerationDefaults,
    val outputMode: OutputMode,
    val cachePolicy: UseCaseCachePolicy,
    val healthSuiteId: String,
    val systemPrompt: String? = null,
    val presets: List<InferencePreset> = emptyList(),
    val defaultPreset: InferencePresetRef? = null,
)

enum class OutputMode {
    TEXT,
    JSON,
    JSON_SCHEMA,
}

data class UseCaseCachePolicy(
    val retainModelWarmMs: Long,
    val reuseStatelessContext: Boolean,
    val enablePrefixSnapshot: Boolean,
    val enableDeterministicResultCache: Boolean,
)

data class AppModelBinding(
    val applicationId: ApplicationId,
    val useCaseId: UseCaseId,
    val useCaseProfileId: String,
    val enabled: Boolean = true,
)

interface ModelProfileRegistry {
    fun resolve(applicationId: ApplicationId, useCaseId: UseCaseId): ResolvedUseCase
}

data class ResolvedUseCase(val binding: AppModelBinding, val useCase: UseCaseProfile, val model: GgufModelProfile)

private const val MAX_TOP_K = 1_000
private const val DEFAULT_REPEAT_PENALTY = 1f
private const val DEFAULT_REPEAT_LAST_N = 64
private const val MIN_REPEAT_PENALTY = 1f
private const val MAX_REPEAT_PENALTY = 2f
private const val MAX_REPEAT_LAST_N = 4_096
private const val MAX_STOP_SEQUENCES = 8
private const val MAX_STOP_SEQUENCE_BYTES = 128
private const val MAX_TOTAL_STOP_SEQUENCE_BYTES = 512
