package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.EffectiveGenerationMetadata
import io.github.daniele21.localllm.contracts.GenerationMetrics
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef
import io.github.daniele21.localllm.contracts.LocalLlmClient
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.SeedPolicy
import io.github.daniele21.localllm.contracts.ThinkingMode
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.models.AppModelBinding
import io.github.daniele21.localllm.models.ArtifactSource
import io.github.daniele21.localllm.models.ChatTemplatePolicy
import io.github.daniele21.localllm.models.ContextPreference
import io.github.daniele21.localllm.models.GenerationDefaults
import io.github.daniele21.localllm.models.GgufArtifact
import io.github.daniele21.localllm.models.GgufModelProfile
import io.github.daniele21.localllm.models.InferencePreset
import io.github.daniele21.localllm.models.ModelProfileRegistry
import io.github.daniele21.localllm.models.OutputMode
import io.github.daniele21.localllm.models.Qwen35GenerationProfiles
import io.github.daniele21.localllm.models.Qwen35ModelTier
import io.github.daniele21.localllm.models.Qwen35RuntimeTuningProfiles
import io.github.daniele21.localllm.models.ResolvedUseCase
import io.github.daniele21.localllm.models.UseCaseCachePolicy
import io.github.daniele21.localllm.models.UseCaseProfile
import io.github.daniele21.localllm.runtime.RuntimeOrchestrator

data class ImportedPhoneModel(
    val digest: ModelDigest,
    val fileName: String,
    val sizeBytes: Long,
    val architecture: String,
    val quantization: String,
) {
    fun artifact(): GgufArtifact {
        Qwen35PhoneModelPolicy.requireCurated(this)
        return GgufArtifact(
            digest = digest,
            fileName = fileName,
            sizeBytes = sizeBytes,
            architecture = architecture,
            quantization = quantization,
            source = ArtifactSource.Download("administrator-curated-catalog"),
        )
    }
}

internal data class PhoneHarness(
    val runtime: RuntimeOrchestrator,
    val client: LocalLlmClient,
    val applicationId: ApplicationId,
    val useCaseId: UseCaseId,
)

internal enum class PlaygroundPhase {
    IDLE,
    PREPARING,
    QUEUED,
    GENERATING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

internal data class PlaygroundMetrics(
    val queueMs: Long?,
    val modelLoadMs: Long?,
    val timeToFirstTokenMs: Long?,
    val prefillMs: Long?,
    val decodeMs: Long?,
    val totalMs: Long?,
    val inputTokens: Int?,
    val outputTokens: Int?,
    val decodeTokensPerSecond: Double?,
    val modelLoadKind: String,
    val stopReason: String = "UNKNOWN",
    val timeToFirstAnswerMs: Long? = null,
    val reasoningTokens: Int? = null,
    val answerTokens: Int? = null,
) {
    companion object {
        fun from(metrics: GenerationMetrics): PlaygroundMetrics = PlaygroundMetrics(
            queueMs = metrics.queueMs,
            modelLoadMs = metrics.modelLoadMs,
            timeToFirstTokenMs = metrics.timeToFirstTokenMs,
            prefillMs = metrics.prefillMs,
            decodeMs = metrics.decodeMs,
            totalMs = metrics.totalMs,
            inputTokens = metrics.inputTokens,
            outputTokens = metrics.outputTokens,
            decodeTokensPerSecond = metrics.decodeTokensPerSecond,
            modelLoadKind = metrics.modelLoadKind.name,
            stopReason = metrics.stopReason.name,
            timeToFirstAnswerMs = metrics.timeToFirstAnswerMs,
            reasoningTokens = metrics.reasoningTokens,
            answerTokens = metrics.answerTokens,
        )
    }
}

internal data class PlaygroundState(
    val phase: PlaygroundPhase = PlaygroundPhase.IDLE,
    val output: String = "",
    val reasoningOutput: String = "",
    val answerOutput: String = "",
    val outputTruncated: Boolean = false,
    val generatedTokens: Int? = null,
    val cancellationAvailable: Boolean = false,
    val cancellationRequested: Boolean = false,
    val metrics: PlaygroundMetrics? = null,
    val effectiveConfiguration: EffectiveGenerationMetadata? = null,
    val errorCode: String? = null,
    val detail: String = "Ready",
) {
    val active: Boolean
        get() = phase in setOf(PlaygroundPhase.PREPARING, PlaygroundPhase.QUEUED, PlaygroundPhase.GENERATING)
}

internal data class PlaygroundRequestOptions(
    val maxOutputTokens: Int = PHONE_DEFAULT_MAX_OUTPUT_TOKENS,
    val temperature: Float = PHONE_DEFAULT_TEMPERATURE,
    val topP: Float = PHONE_DEFAULT_TOP_P,
    val topK: Int? = null,
    val minP: Float? = null,
    val presencePenalty: Float? = null,
    val thinkingMode: ThinkingMode = PHONE_DEFAULT_THINKING_MODE,
    val repeatPenalty: Float? = null,
    val repeatLastN: Int? = null,
    val seedPolicy: SeedPolicy = PHONE_DEFAULT_SEED_POLICY,
    val contextTokens: Int? = null,
    val presetId: String? = PHONE_DEFAULT_PRESET_REF.id.value,
)

internal data class PhoneModelProfile(
    val profile: GgufModelProfile,
    val binding: AppModelBinding,
    val useCase: UseCaseProfile,
)

internal const val PHONE_INFERENCE_PRESET_VERSION = 1
internal const val PHONE_FAST_PRESET_ID = "qwen35-fast"
internal const val PHONE_BALANCED_PRESET_ID = "qwen35-balanced"
internal const val PHONE_QUALITY_PRESET_ID = "qwen35-quality"
internal const val PHONE_THINKING_PRESET_ID = "qwen35-thinking"

internal val PHONE_DEFAULT_PRESET_REF = InferencePresetRef(InferencePresetId(PHONE_BALANCED_PRESET_ID), PHONE_INFERENCE_PRESET_VERSION)
internal val PHONE_DEFAULT_THINKING_MODE: ThinkingMode = ThinkingMode.DISABLED
internal val PHONE_DEFAULT_SEED_POLICY: SeedPolicy = SeedPolicy.Random

internal val PHONE_DEFAULT_MAX_OUTPUT_TOKENS: Int = Qwen35GenerationProfiles.STANDARD.maxOutputTokens
internal val PHONE_DEFAULT_TEMPERATURE: Float = Qwen35GenerationProfiles.STANDARD.temperature
internal val PHONE_DEFAULT_TOP_P: Float = Qwen35GenerationProfiles.STANDARD.topP

internal fun phoneInferencePresets(): List<InferencePreset> = listOf(
    phoneInferencePreset(
        id = PHONE_FAST_PRESET_ID,
        displayName = "Fast",
        description = "Lower context and output budget for quick local responses.",
        context = ContextPreference.Manual(2_048),
        generation = Qwen35GenerationProfiles.FAST,
    ),
    phoneInferencePreset(
        id = PHONE_BALANCED_PRESET_ID,
        displayName = "Balanced",
        description = "Recommended general-purpose local inference profile.",
        context = ContextPreference.Auto,
        generation = Qwen35GenerationProfiles.STANDARD,
    ),
    phoneInferencePreset(
        id = PHONE_QUALITY_PRESET_ID,
        displayName = "Quality",
        description = "Larger output budget with deterministic local generation.",
        context = ContextPreference.Auto,
        generation = Qwen35GenerationProfiles.QUALITY,
    ),
    phoneInferencePreset(
        id = PHONE_THINKING_PRESET_ID,
        displayName = "Thinking",
        description = "Enables model reasoning output with the recommended Qwen3.5 sampling profile.",
        context = ContextPreference.Auto,
        generation = Qwen35GenerationProfiles.THINKING,
    ),
)

internal fun phoneInferencePreset(id: String): InferencePreset? = phoneInferencePresets().firstOrNull { it.id.value == id }

private fun phoneInferencePreset(
    id: String,
    displayName: String,
    description: String,
    context: ContextPreference,
    generation: io.github.daniele21.localllm.models.Qwen35GenerationProfile,
): InferencePreset = InferencePreset(
    id = InferencePresetId(id),
    version = PHONE_INFERENCE_PRESET_VERSION,
    displayName = displayName,
    description = description,
    context = context,
    generation = GenerationDefaults(
        maxOutputTokens = generation.maxOutputTokens,
        temperature = generation.temperature,
        topP = generation.topP,
        topK = generation.topK,
        minP = generation.minP,
        presencePenalty = generation.presencePenalty,
        thinkingMode = if (id == PHONE_THINKING_PRESET_ID) ThinkingMode.ENABLED else generation.thinkingMode,
        repeatPenalty = generation.repeatPenalty,
        repeatLastN = generation.repeatLastN,
        seedPolicy = generation.seedPolicy,
    ),
)

internal fun resolvedPhonePlaygroundUseCase(model: ImportedPhoneModel): ResolvedUseCase {
    val profile = phoneProfile(model)
    return ResolvedUseCase(
        model = profile.profile,
        binding = profile.binding,
        useCase = profile.useCase,
        preset = phoneInferencePreset(PHONE_BALANCED_PRESET_ID),
    )
}

internal fun resolvedPhoneUseCase(model: ImportedPhoneModel, maxOutputTokens: Int): ResolvedUseCase {
    val profile = phoneProfile(model, maxOutputTokens)
    return ResolvedUseCase(profile.profile, profile.binding, profile.useCase)
}

private fun phoneProfile(model: ImportedPhoneModel, maxOutputTokens: Int = PHONE_DEFAULT_MAX_OUTPUT_TOKENS): PhoneModelProfile {
    Qwen35PhoneModelPolicy.requireCurated(model)
    val artifact = model.artifact()
    val runtimeTuning = Qwen35RuntimeTuningProfiles.recommended(
        tier = Qwen35PhoneModelPolicy.requireTier(model),
        profile = Qwen35RuntimeTuningProfiles.CPU_BALANCED,
    )
    val profile = GgufModelProfile(
        id = "phone-${artifact.digest.sha256.take(12)}",
        revision = 1,
        artifact = artifact,
        requiredBackendId = "llama.cpp",
        context = ContextPreference.Auto,
        chatTemplate = ChatTemplatePolicy.UseGgufMetadata,
        generation = GenerationDefaults(
            maxOutputTokens = maxOutputTokens,
            temperature = PHONE_DEFAULT_TEMPERATURE,
            topP = PHONE_DEFAULT_TOP_P,
            topK = Qwen35GenerationProfiles.STANDARD.topK,
            minP = Qwen35GenerationProfiles.STANDARD.minP,
            presencePenalty = Qwen35GenerationProfiles.STANDARD.presencePenalty,
            thinkingMode = PHONE_DEFAULT_THINKING_MODE,
            seedPolicy = PHONE_DEFAULT_SEED_POLICY,
            repeatPenalty = Qwen35GenerationProfiles.STANDARD.repeatPenalty,
            repeatLastN = Qwen35GenerationProfiles.STANDARD.repeatLastN,
        ),
        runtime = runtimeTuning,
        qwen35Tier = Qwen35PhoneModelPolicy.requireTier(model),
        inferencePresets = phoneInferencePresets(),
    )
    val binding = AppModelBinding(
        applicationId = HarnessRuntimeGraph.APPLICATION_ID,
        useCaseId = UseCaseId("manual-inference-playground"),
        modelDigest = artifact.digest,
        modelProfileId = profile.id,
        requiredModelProfileRevision = profile.revision,
    )
    val useCase = UseCaseProfile(
        useCaseId = binding.useCaseId,
        outputMode = OutputMode.TEXT,
        allowReasoning = true,
        maxPromptChars = 32_768,
        cachePolicy = UseCaseCachePolicy.Disabled,
    )
    return PhoneModelProfile(profile, binding, useCase)
}
