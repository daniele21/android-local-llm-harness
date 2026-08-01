package io.github.daniele21.localllm.models

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ModelDigest
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
)

data class GenerationDefaults(
    val maxOutputTokens: Int,
    val temperature: Float,
    val topP: Float = 0.95f,
    val topK: Int = 40,
    val seed: Long? = null,
)

data class UseCaseProfile(
    val id: String,
    val modelProfileId: String,
    val systemPromptVersion: String,
    val generationDefaults: GenerationDefaults,
    val outputMode: OutputMode,
    val cachePolicy: UseCaseCachePolicy,
    val healthSuiteId: String,
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
