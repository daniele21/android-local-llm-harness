package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.EffectiveGenerationMetadata
import io.github.daniele21.localllm.contracts.GenerationMetrics
import io.github.daniele21.localllm.contracts.InferencePresetId
import io.github.daniele21.localllm.contracts.InferencePresetRef
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

internal data class PhoneHarness(val runtime: RuntimeOrchestrator, val applicationId: ApplicationId, val useCaseId: UseCaseId)

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
        )
    }
}

internal data class PlaygroundState(
    val phase: PlaygroundPhase = PlaygroundPhase.IDLE,
    val output: String = "",
    val outputTruncated: Boolean = false,
    val generatedTokens: Int? = null,
    val cancellationAvailable: Boolean = false,
    val cancellationRequested: Boolean = false,
    val metrics: PlaygroundMetrics? = null,
    val errorCode: String? = null,
    val detail: String = "Ready",
    val effectiveConfiguration: EffectiveGenerationMetadata? = null,
) {
    val active: Boolean
        get() = phase == PlaygroundPhase.PREPARING ||
            phase == PlaygroundPhase.QUEUED ||
            phase == PlaygroundPhase.GENERATING
}

internal data class PlaygroundRequestOptions(
    val presetId: String?,
    val maxOutputTokens: Int,
    val temperature: Float,
    val topP: Float,
    val topK: Int,
    val minP: Float,
    val presencePenalty: Float,
    val thinkingMode: ThinkingMode,
    val repeatPenalty: Float,
    val repeatLastN: Int,
    val seedPolicy: SeedPolicy,
    val contextTokens: Int?,
) {
    companion object {
        fun parse(fields: PlaygroundRequestFields): PlaygroundRequestOptions {
            val parsedMaxOutputTokens = requireNotNull(fields.maxOutputTokens.trim().toIntOrNull()) {
                "Maximum output tokens must be an integer"
            }
            require(parsedMaxOutputTokens in MIN_OUTPUT_TOKENS..MAX_OUTPUT_TOKENS) {
                "Maximum output tokens must be between $MIN_OUTPUT_TOKENS and $MAX_OUTPUT_TOKENS"
            }
            val parsedTemperature = requireNotNull(fields.temperature.trim().toFloatOrNull()) {
                "Temperature must be a number"
            }
            require(parsedTemperature in MIN_TEMPERATURE..MAX_TEMPERATURE) {
                "Temperature must be between $MIN_TEMPERATURE and $MAX_TEMPERATURE"
            }
            val parsedTopP = requireNotNull(fields.topP.trim().toFloatOrNull()) { "Top-p must be a number" }
            require(parsedTopP > 0f && parsedTopP <= 1f) { "Top-p must be in (0, 1]" }
            val parsedTopK = requireNotNull(fields.topK.trim().toIntOrNull()) { "Top-k must be an integer" }
            require(parsedTopK in 0..1000) { "Top-k must be between 0 and 1000" }
            val parsedMinP = requireNotNull(fields.minP.trim().toFloatOrNull()) { "Min-p must be a number" }
            require(parsedMinP.isFinite() && parsedMinP in 0f..1f) { "Min-p must be between 0 and 1" }
            val parsedPresencePenalty = requireNotNull(fields.presencePenalty.trim().toFloatOrNull()) {
                "Presence penalty must be a number"
            }
            require(parsedPresencePenalty.isFinite() && parsedPresencePenalty in 0f..2f) {
                "Presence penalty must be between 0 and 2"
            }
            val parsedRepeatPenalty = requireNotNull(fields.repeatPenalty.trim().toFloatOrNull()) {
                "Repeat penalty must be a number"
            }
            require(parsedRepeatPenalty.isFinite() && parsedRepeatPenalty in 1f..2f) {
                "Repeat penalty must be between 1 and 2"
            }
            val parsedRepeatLastN = requireNotNull(fields.repeatLastN.trim().toIntOrNull()) {
                "Repeat window must be an integer"
            }
            require(parsedRepeatLastN in 0..4_096) { "Repeat window must be between 0 and 4096" }
            require(parsedRepeatPenalty == 1f || parsedRepeatLastN > 0) {
                "Repeat window must be positive when repeat penalty is enabled"
            }
            val parsedSeed = fields.seed.trim().takeIf(String::isNotEmpty)?.let {
                SeedPolicy.Fixed(requireNotNull(it.toLongOrNull()) { "Seed must be an integer" })
            } ?: SeedPolicy.Random
            val parsedContext = fields.context.trim().takeIf(String::isNotEmpty)?.let {
                requireNotNull(it.toIntOrNull()) { "Context must be an integer or blank for Auto" }
            }
            return PlaygroundRequestOptions(
                maxOutputTokens = parsedMaxOutputTokens,
                temperature = parsedTemperature,
                topP = parsedTopP,
                topK = parsedTopK,
                minP = parsedMinP,
                presencePenalty = parsedPresencePenalty,
                thinkingMode = fields.thinkingMode,
                repeatPenalty = parsedRepeatPenalty,
                repeatLastN = parsedRepeatLastN,
                seedPolicy = parsedSeed,
                contextTokens = parsedContext,
                presetId = fields.presetId.trim().takeIf(String::isNotEmpty),
            )
        }

        private const val MIN_OUTPUT_TOKENS = 1
        private const val MAX_OUTPUT_TOKENS = 32_768
        private const val MIN_TEMPERATURE = 0f
        private const val MAX_TEMPERATURE = 2f
    }
}

internal data class PlaygroundRequestFields(
    val presetId: String,
    val maxOutputTokens: String,
    val temperature: String,
    val topP: String,
    val topK: String,
    val minP: String = "0",
    val presencePenalty: String = "0",
    val thinkingMode: ThinkingMode = ThinkingMode.DISABLED,
    val repeatPenalty: String,
    val repeatLastN: String,
    val seed: String,
    val context: String,
)

internal class SinglePhoneBindingRegistry(private val resolved: ResolvedUseCase) : ModelProfileRegistry {
    override fun resolve(applicationId: ApplicationId, useCaseId: UseCaseId): ResolvedUseCase {
        require(applicationId == resolved.binding.applicationId) {
            "Unknown applicationId ${applicationId.value}"
        }
        require(useCaseId == resolved.binding.useCaseId) {
            "Unknown useCaseId ${useCaseId.value}"
        }
        return resolved
    }
}

internal fun resolvedPhoneUseCase(
    model: ImportedPhoneModel,
    maxOutputTokens: Int,
    useCaseValue: String = "physical-device-validation",
    profileSuffix: String = "validation",
    contextSize: Int = 512,
): ResolvedUseCase {
    val release = Qwen35PhoneModelPolicy.requireCurated(model)
    val tier = if (release.id.modelId.value.startsWith("qwen35-08b-")) Qwen35ModelTier.B0_8 else Qwen35ModelTier.B2
    val applicationId = ApplicationId("play-internal-phone-test")
    val useCaseId = UseCaseId(useCaseValue)
    val modelProfileId = "${release.profileKey.value}-$profileSuffix"
    val useCaseProfileId = "play-internal-phone-use-case-$profileSuffix"
    val availableProcessors = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    val runtimeProfile = Qwen35RuntimeTuningProfiles.candidateForTier(tier)
    val runtimeTuning = runtimeProfile.resolve(availableProcessors)
    val modelProfile = GgufModelProfile(
        id = modelProfileId,
        artifact = model.artifact(),
        contextSize = contextSize,
        batchSize = runtimeTuning.batchSize,
        microBatchSize = runtimeTuning.microBatchSize,
        cpuThreads = runtimeTuning.cpuThreads,
        batchThreads = runtimeTuning.batchThreads,
        gpuLayers = 0,
        useMmap = runtimeTuning.useMmap,
        useMlock = runtimeTuning.useMlock,
        flashAttention = runtimeTuning.flashAttention,
        chatTemplatePolicy = ChatTemplatePolicy(),
        runtimeCapabilities = runtimeProfile.runtimeCapabilities(),

    )
    val useCase = UseCaseProfile(
        id = useCaseProfileId,
        modelProfileId = modelProfileId,
        systemPromptVersion = "play-internal-phone-$profileSuffix-v1",
        generationDefaults = Qwen35GenerationProfiles.defaultForTier(tier).copy(
            maxOutputTokens = maxOutputTokens,
            seed = 42,
            seedPolicy = SeedPolicy.Fixed(42),
        ),
        outputMode = OutputMode.TEXT,
        cachePolicy = UseCaseCachePolicy(0, false, false, false),
        healthSuiteId = "play-internal-phone-$profileSuffix-health",
        systemPrompt = "You are a concise, accurate assistant running entirely on the user's device.",
        presets = phoneInferencePresets(tier),
        defaultPreset = InferencePresetRef(InferencePresetId("qwen35-text-quality"), PHONE_INFERENCE_PRESET_VERSION),
    )
    return ResolvedUseCase(
        binding = AppModelBinding(applicationId, useCaseId, useCase.id),
        useCase = useCase,
        model = modelProfile,
    )
}

internal fun resolvedPhonePlaygroundUseCase(model: ImportedPhoneModel): ResolvedUseCase = resolvedPhoneUseCase(
    model = model,
    maxOutputTokens = 128,
    useCaseValue = "manual-inference-playground",
    profileSuffix = "playground",
    contextSize = 2048,
)

private fun phoneInferencePresets(tier: Qwen35ModelTier): List<InferencePreset> = playgroundPresetOptions.map { phonePreset(tier, it) }

private fun phonePreset(tier: Qwen35ModelTier, preset: PlaygroundPresetOption): InferencePreset {
    val profile = Qwen35GenerationProfiles.forTier(tier).single { it.id == preset.profileId }
    return InferencePreset(
        ref = InferencePresetRef(InferencePresetId(preset.id), profile.version),
        generation = profile.defaults,
        systemPromptVersion = "play-internal-phone-${preset.id}-v1",
        systemPrompt = preset.systemPrompt,
        contextPreference = ContextPreference(
            preferredTokens = preset.preferredContextTokens,
            recommendedMaximumTokens = preset.recommendedMaximumContextTokens,
        ),
        allowedOutputModes = if (preset.id == "qwen35-json") {
            setOf(OutputMode.TEXT, OutputMode.JSON, OutputMode.JSON_SCHEMA)
        } else {
            setOf(OutputMode.TEXT)
        },
    )
}

internal const val PHONE_INFERENCE_PRESET_VERSION = Qwen35GenerationProfiles.VERSION
