package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.catalog.CatalogCompatibilityReason
import io.github.daniele21.localllm.catalog.CatalogCompatibilityResult
import io.github.daniele21.localllm.catalog.CatalogDeviceProfile
import io.github.daniele21.localllm.catalog.CuratedModelCatalog
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneCatalogCompatibilityPresentationTest {
    @Test
    fun incompatibleModelExplainsMeasuredRamAndStorageRequirements() {
        val release = CuratedModelCatalog.releases.first { it.id.modelId.value == "qwen35-4b-q4-k-m" }
        val device =
            CatalogDeviceProfile(
                sdkInt = 36,
                supportedAbis = setOf("arm64-v8a"),
                totalMemoryBytes = 6_500_000_000,
                availableStorageBytes = 4_294_967_296,
                harnessVersion = "0.3.0",
                backendId = "llama.cpp",
            )
        val result =
            CatalogCompatibilityResult(
                compatible = false,
                requiredStorageBytes = 5_500_000_000,
                reasons =
                    listOf(
                        CatalogCompatibilityReason.INSUFFICIENT_RAM,
                        CatalogCompatibilityReason.INSUFFICIENT_STORAGE,
                    ),
                warnings = emptyList(),
            )

        val detail = PhoneCatalogCompatibilityPresentation.detail(release, result, device).orEmpty()

        assertTrue(detail, detail.contains("Needs at least 7.0 GB RAM; Android reports 6.5 GB."))
        assertTrue(detail, detail.contains("Needs at least 5.1 GiB free storage; 4.0 GiB is available."))
    }

    @Test
    fun compatibleModelDoesNotInventFailureDetail() {
        val release = CuratedModelCatalog.releases.first()
        val device =
            CatalogDeviceProfile(
                sdkInt = 36,
                supportedAbis = setOf("arm64-v8a"),
                totalMemoryBytes = 12_000_000_000,
                availableStorageBytes = 20_000_000_000,
                harnessVersion = "0.3.0",
                backendId = "llama.cpp",
            )
        val result =
            CatalogCompatibilityResult(
                compatible = true,
                requiredStorageBytes = 1_000_000_000,
                reasons = emptyList(),
                warnings = emptyList(),
            )

        assertNull(PhoneCatalogCompatibilityPresentation.detail(release, result, device))
    }
}
