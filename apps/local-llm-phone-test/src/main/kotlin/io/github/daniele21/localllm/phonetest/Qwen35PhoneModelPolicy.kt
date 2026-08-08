package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.catalog.CatalogModelRelease
import io.github.daniele21.localllm.catalog.CuratedModelCatalog

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
        require(release.artifact.architecture == QWEN35_ARCHITECTURE) {
            "Only Qwen3.5 artifacts are supported by the phone runtime"
        }
        return release
    }

    private const val QWEN35_ARCHITECTURE = "qwen35"
}
