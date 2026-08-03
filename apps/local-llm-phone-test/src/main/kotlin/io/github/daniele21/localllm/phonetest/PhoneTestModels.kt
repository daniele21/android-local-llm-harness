package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ApplicationId
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

internal fun resolvedPhoneUseCase(model: ImportedPhoneModel, maxOutputTokens: Int): ResolvedUseCase {
    val applicationId = ApplicationId("play-internal-phone-test")
    val useCaseId = UseCaseId("physical-device-validation")
    val modelProfileId = "play-internal-phone-model"
    val useCaseProfileId = "play-internal-phone-use-case"
    val availableProcessors = Runtime.getRuntime().availableProcessors().coerceAtLeast(1).coerceAtMost(4)
    val modelProfile = GgufModelProfile(
        id = modelProfileId,
        artifact = model.artifact(),
        contextSize = 512,
        batchSize = 128,
        microBatchSize = 64,
        cpuThreads = availableProcessors,
        batchThreads = availableProcessors,
        gpuLayers = 0,
    )
    val useCase = UseCaseProfile(
        id = useCaseProfileId,
        modelProfileId = modelProfileId,
        systemPromptVersion = "play-internal-phone-v1",
        generationDefaults = GenerationDefaults(
            maxOutputTokens = maxOutputTokens,
            temperature = 0f,
            topP = 1f,
            topK = 0,
            seed = 42,
        ),
        outputMode = OutputMode.TEXT,
        cachePolicy = UseCaseCachePolicy(0, false, false, false),
        healthSuiteId = "play-internal-phone-health",
    )
    return ResolvedUseCase(
        binding = AppModelBinding(applicationId, useCaseId, useCase.id),
        useCase = useCase,
        model = modelProfile,
    )
}
