package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.catalog.CatalogRelease
import io.github.daniele21.localllm.catalog.CuratedModelCatalog
import io.github.daniele21.localllm.contracts.ModelDigest

internal fun CuratedModelCatalog.requireByDigestAndSize(digest: ModelDigest, sizeBytes: Long): CatalogRelease =
    requireNotNull(
        entries.firstOrNull { release ->
            release.artifact.digest == digest && release.artifact.sizeBytes == sizeBytes
        },
    ) { "Installed artifact is not present in the curated phone catalog" }

internal val CatalogRelease.architecture: String
    get() = artifact.architecture

internal val CatalogRelease.quantization: String
    get() = artifact.quantization
