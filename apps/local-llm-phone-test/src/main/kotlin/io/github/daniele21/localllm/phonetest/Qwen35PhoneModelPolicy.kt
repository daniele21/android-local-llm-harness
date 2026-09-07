package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.catalog.CatalogModelRelease
import io.github.daniele21.localllm.catalog.CuratedModelCatalog
import io.github.daniele21.localllm.models.Qwen35ModelTier

internal object Qwen35PhoneModelPolicy {
    fun requireCurated(model: ImportedPhoneModel): CatalogModelRelease {
        val release = CuratedModelCatalog.releases.singleOrNull { candidate ->
            candidate.artifact.digest == model.digest &&
                candidate.artifact.fileName == model.fileName &&
                candidate.artifact.sizeBytes == model.sizeBytes &&
                candidate.artifact.architecture == model.architecture &&
                candidate.artifact.quantization == model.quantization
        }
        requireNotNull(release) {
            "Selected model is not an exact artifact from the curated Qwen3.5 catalog"
        }
        requireSupported(release)
        return release
    }

    fun tierFor(release: CatalogModelRelease): Qwen35ModelTier {
        requireSupported(release)
        val modelId = release.id.modelId.value
        return when {
            modelId.startsWith(QWEN35_08B_MODEL_PREFIX) -> Qwen35ModelTier.B0_8
            modelId.startsWith(QWEN35_2B_MODEL_PREFIX) -> Qwen35ModelTier.B2
            modelId.startsWith(QWEN35_4B_MODEL_PREFIX) -> Qwen35ModelTier.B4
            else -> error("Unsupported curated Qwen3.5 model tier: $modelId")
        }
    }

    private fun requireSupported(release: CatalogModelRelease) {
        require(release.artifact.architecture == QWEN35_ARCHITECTURE) {
            "Only Qwen3.5 artifacts are supported by the phone runtime"
        }
    }

    private const val QWEN35_ARCHITECTURE = "qwen35"
    private const val QWEN35_08B_MODEL_PREFIX = "qwen35-08b-"
    private const val QWEN35_2B_MODEL_PREFIX = "qwen35-2b-"
    private const val QWEN35_4B_MODEL_PREFIX = "qwen35-4b-"
}
