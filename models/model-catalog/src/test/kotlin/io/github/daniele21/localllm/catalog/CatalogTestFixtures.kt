package io.github.daniele21.localllm.catalog

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.UseCaseId
import java.net.URI

internal val testTarget = CatalogTarget(
    applicationId = ApplicationId("harness-phone"),
    useCaseId = UseCaseId("playground"),
)

internal fun validCatalogDocument(
    entries: List<CatalogModelRelease> = listOf(validCatalogRelease()),
    expiresAtEpochMs: Long = 2_000,
): CatalogModelDocument = CatalogModelDocument(
    schemaVersion = 1,
    catalogId = CatalogId("harness-models"),
    revision = 7,
    generatedAtEpochMs = 1_000,
    expiresAtEpochMs = expiresAtEpochMs,
    entries = entries,
)

internal fun validCatalogRelease(transform: (CatalogModelRelease) -> CatalogModelRelease = { it }): CatalogModelRelease = transform(
    CatalogModelRelease(
        id = CatalogReleaseId(
            modelId = CatalogModelId("qwen-small"),
            version = CatalogModelVersion("1.0.0"),
        ),
        displayName = "Qwen Small",
        description = "A small GGUF model for the local playground.",
        artifact = CatalogGgufArtifact(
            digest = ModelDigest("a".repeat(64)),
            sizeBytes = 8L * 1024L * 1024L,
            downloadUri = URI("https://models.example.test/qwen-small.gguf"),
            architecture = "qwen3",
            quantization = "Q4_K_M",
            fileName = "qwen-small.gguf",
        ),
        compatibility = CatalogCompatibility(
            minSdk = 26,
            supportedAbis = setOf("arm64-v8a"),
            minRamBytes = 1024L * 1024L * 1024L,
            recommendedRamBytes = 2L * 1024L * 1024L * 1024L,
            minFreeStorageBytes = 32L * 1024L * 1024L,
            minHarnessVersion = "1.0.0",
            maxHarnessVersionExclusive = "2.0.0",
            supportedBackendIds = setOf("llama.cpp"),
        ),
        availability = CatalogAvailability.ACTIVE,
        allowedTargets = setOf(testTarget),
        profileKey = ModelProfileKey("qwen-playground"),
        license = CatalogLicense(
            id = "apache-2.0",
            displayName = "Apache License 2.0",
            sourceUri = URI("https://models.example.test/source"),
            licenseUri = URI("https://models.example.test/license"),
        ),
    ),
)
