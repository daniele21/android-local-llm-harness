package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.catalog.CatalogCompatibilityReason
import io.github.daniele21.localllm.catalog.CatalogCompatibilityResult
import io.github.daniele21.localllm.catalog.CatalogDeviceProfile
import io.github.daniele21.localllm.catalog.CatalogModelRelease
import java.util.Locale

internal object PhoneCatalogCompatibilityPresentation {
    fun detail(
        release: CatalogModelRelease,
        result: CatalogCompatibilityResult,
        device: CatalogDeviceProfile,
    ): String? = result.reasons
        .takeIf { it.isNotEmpty() }
        ?.joinToString(separator = "\n") { reason -> reason.message(release, result, device) }

    private fun CatalogCompatibilityReason.message(
        release: CatalogModelRelease,
        result: CatalogCompatibilityResult,
        device: CatalogDeviceProfile,
    ): String = when (this) {
        CatalogCompatibilityReason.TARGET_NOT_ALLOWED ->
            "This model is not enabled for the current app and use case."

        CatalogCompatibilityReason.RELEASE_REVOKED ->
            "This model release has been revoked."

        CatalogCompatibilityReason.RELEASE_UNAVAILABLE ->
            "This model release is currently unavailable."

        CatalogCompatibilityReason.UNSUPPORTED_ANDROID_API ->
            "Requires Android API ${release.compatibility.minSdk}+; this device is API ${device.sdkInt}."

        CatalogCompatibilityReason.UNSUPPORTED_ABI -> {
            val required = release.compatibility.supportedAbis.sorted().joinToString()
            val available = device.supportedAbis.sorted().joinToString().ifBlank { "none reported" }
            "Requires $required ABI; device reports $available."
        }

        CatalogCompatibilityReason.UNSUPPORTED_BACKEND -> {
            val required = release.compatibility.supportedBackendIds.sorted().joinToString()
            "Requires backend $required; current backend is ${device.backendId}."
        }

        CatalogCompatibilityReason.UNSUPPORTED_HARNESS_VERSION ->
            "Harnex ${device.harnessVersion} is outside the supported version range for this model."

        CatalogCompatibilityReason.UNSUPPORTED_PROFILE ->
            "The required Harnex model profile is unavailable for this app and use case."

        CatalogCompatibilityReason.INSUFFICIENT_RAM -> {
            val minimum = release.compatibility.minRamBytes
            val reported = device.totalMemoryBytes
            when {
                minimum != null && reported != null ->
                    "Needs at least ${formatDecimalGigabytes(minimum)} GB RAM; Android reports " +
                        "${formatDecimalGigabytes(reported)} GB."

                minimum != null ->
                    "Needs at least ${formatDecimalGigabytes(minimum)} GB RAM."

                else -> "Not enough device RAM."
            }
        }

        CatalogCompatibilityReason.INSUFFICIENT_STORAGE -> {
            val required = result.requiredStorageBytes
            if (required != null) {
                "Needs at least ${formatGibibytes(required)} GiB free storage; " +
                    "${formatGibibytes(device.availableStorageBytes)} GiB is available."
            } else {
                "Not enough free storage for verified download and installation."
            }
        }

        CatalogCompatibilityReason.STORAGE_REQUIREMENT_OVERFLOW ->
            "The required free-storage amount could not be evaluated safely."
    }

    private fun formatDecimalGigabytes(bytes: Long): String =
        String.format(Locale.US, "%.1f", bytes / 1_000_000_000.0)

    private fun formatGibibytes(bytes: Long): String =
        String.format(Locale.US, "%.1f", bytes / (1024.0 * 1024.0 * 1024.0))
}
