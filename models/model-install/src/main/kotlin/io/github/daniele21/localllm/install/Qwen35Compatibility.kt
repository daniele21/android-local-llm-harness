package io.github.daniele21.localllm.install

import io.github.daniele21.localllm.catalog.CatalogModelId
import io.github.daniele21.localllm.catalog.CatalogModelRelease
import io.github.daniele21.localllm.catalog.CatalogModelVersion
import io.github.daniele21.localllm.catalog.CatalogReleaseId
import io.github.daniele21.localllm.contracts.ModelDigest

internal enum class Qwen35Tier {
    B0_8,
    B2,
}

internal data class Qwen35ArtifactDescriptor(
    val releaseId: CatalogReleaseId,
    val tier: Qwen35Tier,
    val digest: ModelDigest,
    val sizeBytes: Long,
    val quantization: String,
    val ggufVersion: UInt,
    val architecture: String,
    val name: String,
    val fileType: Long,
    val keyValueCount: Long,
    val tensorCount: Long,
    val contextLength: Long,
    val blockCount: Long,
    val embeddingLength: Long,
) {
    fun mismatch(release: CatalogModelRelease, metadata: GgufArtifactMetadata): String? =
        releaseMismatch(release) ?: metadataMismatch(metadata)

    private fun releaseMismatch(release: CatalogModelRelease): String? = when {
        release.id != releaseId -> "release identity"
        release.artifact.digest != digest -> "artifact digest"
        release.artifact.sizeBytes != sizeBytes -> "artifact size"
        normalize(release.artifact.quantization) != normalize(quantization) -> "quantization"
        else -> null
    }

    private fun metadataMismatch(metadata: GgufArtifactMetadata): String? = when {
        metadata.version != ggufVersion -> "GGUF version"
        normalize(metadata.architecture) != normalize(architecture) -> "GGUF architecture"
        metadata.name != name -> "GGUF model name"
        metadata.fileType != fileType -> "GGUF file type"
        metadata.keyValueCount != keyValueCount -> "GGUF key/value count"
        metadata.tensorCount != tensorCount -> "GGUF tensor count"
        metadata.contextLength != contextLength -> "GGUF context length"
        metadata.blockCount != blockCount -> "GGUF block count"
        metadata.embeddingLength != embeddingLength -> "GGUF embedding length"
        else -> null
    }

    private fun normalize(value: String?): String? = value?.trim()?.lowercase()?.replace('-', '_')
}

internal data class Qwen35CompatibilityEvidence(val modelDigest: ModelDigest, val backendId: String, val backendRevision: String)

internal object Qwen35CompatibilityManifest {
    const val LLAMA_CPP_REVISION = "aedb2a5e9ca3d4064148bbb919e0ddc0c1b70ab3"

    val references: List<Qwen35ArtifactDescriptor> = listOf(
        Qwen35ArtifactDescriptor(
            releaseId = releaseId("qwen35-08b-q4-k-m"),
            tier = Qwen35Tier.B0_8,
            digest = ModelDigest("bd258782e35f7f458f8aced1adc053e6e92e89bc735ba3be89d38a06121dc517"),
            sizeBytes = 532_517_120,
            quantization = "Q4_K_M",
            ggufVersion = 3u,
            architecture = "qwen35",
            name = "Qwen3.5-0.8B",
            fileType = 15,
            keyValueCount = 46,
            tensorCount = 320,
            contextLength = 262_144,
            blockCount = 24,
            embeddingLength = 1_024,
        ),
        Qwen35ArtifactDescriptor(
            releaseId = releaseId("qwen35-2b-q4-k-m"),
            tier = Qwen35Tier.B2,
            digest = ModelDigest("aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223"),
            sizeBytes = 1_280_835_840,
            quantization = "Q4_K_M",
            ggufVersion = 3u,
            architecture = "qwen35",
            name = "Qwen3.5-2B",
            fileType = 15,
            keyValueCount = 46,
            tensorCount = 320,
            contextLength = 262_144,
            blockCount = 24,
            embeddingLength = 2_048,
        ),
    )

    fun forRelease(releaseId: CatalogReleaseId): Qwen35ArtifactDescriptor? = references.singleOrNull { it.releaseId == releaseId }

    fun evidenceFor(descriptor: Qwen35ArtifactDescriptor): Qwen35CompatibilityEvidence = Qwen35CompatibilityEvidence(
        modelDigest = descriptor.digest,
        backendId = "llama.cpp",
        backendRevision = LLAMA_CPP_REVISION,
    )

    private fun releaseId(modelId: String) = CatalogReleaseId(CatalogModelId(modelId), CatalogModelVersion("1.0.0"))
}
