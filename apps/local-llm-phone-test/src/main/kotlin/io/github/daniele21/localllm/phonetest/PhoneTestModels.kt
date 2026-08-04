package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.GenerationMetrics
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.models.AppModelBinding
import io.github.daniele21.localllm.models.ArtifactSource
import io.github.daniele21.localllm.models.GenerationDefaults
import io.github.daniele21.localllm.models.GgufArtifact
import io.github.daniele21.localllm.models.GgufModelProfile
import io.github.daniele21.localllm.models.ModelProfileRegistry
import io.github.daniele21.localllm.models.OutputMode
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
    fun artifact(): GgufArtifact = GgufArtifact(
        digest = digest,
        fileName = fileName,
        sizeBytes = sizeBytes,
        architecture = architecture,
        quantization = quantization,
        source = ArtifactSource.Imported("storage-access-framework"),
    )
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
) {
    val active: Boolean
        get() = phase == PlaygroundPhase.PREPARING ||
            phase == PlaygroundPhase.QUEUED ||
            phase == PlaygroundPhase.GENERATING
}

internal data class PlaygroundRequestOptions(val maxOutputTokens: Int, val temperature: Float, val seed: Long) {
    companion object {
        fun parse(maxOutputTokens: String, temperature: String, seed: String): PlaygroundRequestOptions {
            val parsedMaxOutputTokens = requireNotNull(maxOutputTokens.trim().toIntOrNull()) {
                "Maximum output tokens must be an integer"
            }
            require(parsedMaxOutputTokens in MIN_OUTPUT_TOKENS..MAX_OUTPUT_TOKENS) {
                "Maximum output tokens must be between $MIN_OUTPUT_TOKENS and $MAX_OUTPUT_TOKENS"
            }
            val parsedTemperature = requireNotNull(temperature.trim().toFloatOrNull()) {
                "Temperature must be a number"
            }
            require(parsedTemperature in MIN_TEMPERATURE..MAX_TEMPERATURE) {
                "Temperature must be between $MIN_TEMPERATURE and $MAX_TEMPERATURE"
            }
            val parsedSeed = requireNotNull(seed.trim().toLongOrNull()) {
                "Seed must be an integer"
            }
            return PlaygroundRequestOptions(
                maxOutputTokens = parsedMaxOutputTokens,
                temperature = parsedTemperature,
                seed = parsedSeed,
            )
        }

        private const val MIN_OUTPUT_TOKENS = 1
        private const val MAX_OUTPUT_TOKENS = 512
        private const val MIN_TEMPERATURE = 0f
        private const val MAX_TEMPERATURE = 2f
    }
}

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
    val applicationId = ApplicationId("play-internal-phone-test")
    val useCaseId = UseCaseId(useCaseValue)
    val modelProfileId = "play-internal-phone-model-$profileSuffix"
    val useCaseProfileId = "play-internal-phone-use-case-$profileSuffix"
    val availableProcessors = Runtime.getRuntime().availableProcessors().coerceAtLeast(1).coerceAtMost(4)
    val modelProfile = GgufModelProfile(
        id = modelProfileId,
        artifact = model.artifact(),
        contextSize = contextSize,
        batchSize = 128,
        microBatchSize = 64,
        cpuThreads = availableProcessors,
        batchThreads = availableProcessors,
        gpuLayers = 0,
    )
    val useCase = UseCaseProfile(
        id = useCaseProfileId,
        modelProfileId = modelProfileId,
        systemPromptVersion = "play-internal-phone-$profileSuffix-v1",
        generationDefaults = GenerationDefaults(
            maxOutputTokens = maxOutputTokens,
            temperature = 0f,
            topP = 1f,
            topK = 0,
            seed = 42,
        ),
        outputMode = OutputMode.TEXT,
        cachePolicy = UseCaseCachePolicy(0, false, false, false),
        healthSuiteId = "play-internal-phone-$profileSuffix-health",
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
