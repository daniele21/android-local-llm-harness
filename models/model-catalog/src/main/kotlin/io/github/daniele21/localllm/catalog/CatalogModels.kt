package io.github.daniele21.localllm.catalog

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.UseCaseId
import java.net.URI

data class CatalogId(val value: String)

data class CatalogModelId(val value: String)

data class CatalogModelVersion(val value: String)

data class CatalogReleaseId(val modelId: CatalogModelId, val version: CatalogModelVersion)

data class ModelProfileKey(val value: String)

data class CatalogModelDocument(
    val schemaVersion: Int,
    val catalogId: CatalogId,
    val revision: Long,
    val generatedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val entries: List<CatalogModelRelease>,
)

data class CatalogModelRelease(
    val id: CatalogReleaseId,
    val displayName: String,
    val description: String,
    val artifact: CatalogGgufArtifact,
    val compatibility: CatalogCompatibility,
    val availability: CatalogAvailability,
    val allowedTargets: Set<CatalogTarget>,
    val profileKey: ModelProfileKey,
    val license: CatalogLicense,
    val replacement: CatalogReleaseId? = null,
)

data class CatalogGgufArtifact(
    val digest: ModelDigest,
    val sizeBytes: Long,
    val downloadUri: URI,
    val architecture: String,
    val quantization: String,
    val fileName: String,
)

data class CatalogCompatibility(
    val minSdk: Int,
    val supportedAbis: Set<String>,
    val minRamBytes: Long? = null,
    val recommendedRamBytes: Long? = null,
    val minFreeStorageBytes: Long = 0,
    val minHarnessVersion: String? = null,
    val maxHarnessVersionExclusive: String? = null,
    val supportedBackendIds: Set<String> = emptySet(),
)

data class CatalogTarget(val applicationId: ApplicationId, val useCaseId: UseCaseId)

data class CatalogLicense(val id: String, val displayName: String, val sourceUri: URI? = null, val licenseUri: URI? = null)

enum class CatalogAvailability {
    ACTIVE,
    DEPRECATED,
    REVOKED,
    UNAVAILABLE,
}

data class CatalogDeviceProfile(
    val sdkInt: Int,
    val supportedAbis: Set<String>,
    val totalMemoryBytes: Long?,
    val availableStorageBytes: Long,
    val harnessVersion: String,
    val backendId: String,
)

data class CatalogCompatibilityResult(
    val compatible: Boolean,
    val requiredStorageBytes: Long?,
    val reasons: List<CatalogCompatibilityReason>,
    val warnings: List<CatalogCompatibilityWarning>,
)

enum class CatalogCompatibilityReason {
    TARGET_NOT_ALLOWED,
    RELEASE_REVOKED,
    RELEASE_UNAVAILABLE,
    UNSUPPORTED_ANDROID_API,
    UNSUPPORTED_ABI,
    UNSUPPORTED_BACKEND,
    UNSUPPORTED_HARNESS_VERSION,
    UNSUPPORTED_PROFILE,
    INSUFFICIENT_RAM,
    INSUFFICIENT_STORAGE,
    STORAGE_REQUIREMENT_OVERFLOW,
}

enum class CatalogCompatibilityWarning {
    RELEASE_DEPRECATED,
    RAM_BELOW_RECOMMENDED,
}

interface CatalogVersionMatcher {
    fun isInRange(currentVersion: String, minimumInclusive: String?, maximumExclusive: String?): Boolean
}

interface CatalogProfileResolver {
    fun supports(profileKey: ModelProfileKey, target: CatalogTarget): Boolean
}
