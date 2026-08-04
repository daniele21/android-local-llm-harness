package io.github.daniele21.localllm.catalog

object CatalogQueries {
    fun releasesForTarget(document: CatalogModelDocument, target: CatalogTarget): List<CatalogModelRelease> =
        document.entries.filter { target in it.allowedTargets }
}

class CatalogCompatibilityEvaluator(
    private val versionMatcher: CatalogVersionMatcher,
    private val profileResolver: CatalogProfileResolver,
    private val storageSafetyMarginBytes: Long = DEFAULT_STORAGE_SAFETY_MARGIN_BYTES,
    private val importCopyCount: Int = DEFAULT_IMPORT_COPY_COUNT,
) {
    init {
        require(storageSafetyMarginBytes >= 0)
        require(importCopyCount >= 1)
    }

    fun evaluate(release: CatalogModelRelease, target: CatalogTarget, device: CatalogDeviceProfile): CatalogCompatibilityResult {
        val reasons = linkedSetOf<CatalogCompatibilityReason>()
        val warnings = linkedSetOf<CatalogCompatibilityWarning>()

        evaluateAvailability(release.availability, reasons, warnings)
        if (target !in release.allowedTargets) reasons += CatalogCompatibilityReason.TARGET_NOT_ALLOWED
        if (device.sdkInt < release.compatibility.minSdk) {
            reasons += CatalogCompatibilityReason.UNSUPPORTED_ANDROID_API
        }
        if (!hasCompatibleAbi(release.compatibility.supportedAbis, device.supportedAbis)) {
            reasons += CatalogCompatibilityReason.UNSUPPORTED_ABI
        }
        if (release.compatibility.supportedBackendIds.isNotEmpty() &&
            device.backendId !in release.compatibility.supportedBackendIds
        ) {
            reasons += CatalogCompatibilityReason.UNSUPPORTED_BACKEND
        }
        if (!supportsHarnessVersion(release.compatibility, device.harnessVersion)) {
            reasons += CatalogCompatibilityReason.UNSUPPORTED_HARNESS_VERSION
        }
        if (!profileResolver.supports(release.profileKey, target)) {
            reasons += CatalogCompatibilityReason.UNSUPPORTED_PROFILE
        }
        evaluateMemory(release.compatibility, device, reasons, warnings)
        val requiredStorageBytes = requiredStorageBytes(release)
        if (requiredStorageBytes == null) {
            reasons += CatalogCompatibilityReason.STORAGE_REQUIREMENT_OVERFLOW
        } else if (device.availableStorageBytes < requiredStorageBytes) {
            reasons += CatalogCompatibilityReason.INSUFFICIENT_STORAGE
        }

        return CatalogCompatibilityResult(
            compatible = reasons.isEmpty(),
            requiredStorageBytes = requiredStorageBytes,
            reasons = reasons.sortedBy { it.ordinal },
            warnings = warnings.sortedBy { it.ordinal },
        )
    }

    private fun evaluateAvailability(
        availability: CatalogAvailability,
        reasons: MutableSet<CatalogCompatibilityReason>,
        warnings: MutableSet<CatalogCompatibilityWarning>,
    ) {
        when (availability) {
            CatalogAvailability.ACTIVE -> Unit
            CatalogAvailability.DEPRECATED -> warnings += CatalogCompatibilityWarning.RELEASE_DEPRECATED
            CatalogAvailability.REVOKED -> reasons += CatalogCompatibilityReason.RELEASE_REVOKED
            CatalogAvailability.UNAVAILABLE -> reasons += CatalogCompatibilityReason.RELEASE_UNAVAILABLE
        }
    }

    private fun evaluateMemory(
        compatibility: CatalogCompatibility,
        device: CatalogDeviceProfile,
        reasons: MutableSet<CatalogCompatibilityReason>,
        warnings: MutableSet<CatalogCompatibilityWarning>,
    ) {
        val totalMemoryBytes = device.totalMemoryBytes ?: return
        compatibility.minRamBytes?.let { minimum ->
            if (totalMemoryBytes < minimum) reasons += CatalogCompatibilityReason.INSUFFICIENT_RAM
        }
        compatibility.recommendedRamBytes?.let { recommended ->
            if (totalMemoryBytes < recommended) {
                warnings += CatalogCompatibilityWarning.RAM_BELOW_RECOMMENDED
            }
        }
    }

    private fun supportsHarnessVersion(compatibility: CatalogCompatibility, harnessVersion: String): Boolean {
        val constrained =
            compatibility.minHarnessVersion != null || compatibility.maxHarnessVersionExclusive != null
        return !constrained ||
            versionMatcher.isInRange(
                currentVersion = harnessVersion,
                minimumInclusive = compatibility.minHarnessVersion,
                maximumExclusive = compatibility.maxHarnessVersionExclusive,
            )
    }

    private fun hasCompatibleAbi(required: Set<String>, available: Set<String>): Boolean {
        val normalizedAvailable = available.mapTo(mutableSetOf()) { it.lowercase() }
        return required.any { it.lowercase() in normalizedAvailable }
    }

    private fun requiredStorageBytes(release: CatalogModelRelease): Long? = try {
        val artifactCopies = Math.multiplyExact(release.artifact.sizeBytes, importCopyCount.toLong())
        val withCatalogMinimum = Math.addExact(artifactCopies, release.compatibility.minFreeStorageBytes)
        Math.addExact(withCatalogMinimum, storageSafetyMarginBytes)
    } catch (_: ArithmeticException) {
        null
    }

    private companion object {
        const val DEFAULT_IMPORT_COPY_COUNT = 2
        const val DEFAULT_STORAGE_SAFETY_MARGIN_BYTES = 128L * 1024L * 1024L
    }
}
