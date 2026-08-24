package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ModelDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceModelOptionTest {
    @Test
    fun `only installed compatible catalog models become evaluation options`() {
        val installed = installedMetadata("a".repeat(64), "qwen35-0.8b", "Qwen 3.5 0.8B")
        val distribution = PhoneModelDistributionState(
            catalogStatus = PhoneCatalogLoadStatus.READY,
            models = listOf(
                modelUi("installed", installed, compatible = true),
                modelUi(
                    "incompatible",
                    installedMetadata("b".repeat(64), "qwen35-2b", "Qwen 3.5 2B"),
                    compatible = false,
                ),
                modelUi("not-installed", null, compatible = true),
            ),
        )

        val options = performanceModelOptions(distribution)

        assertEquals(1, options.size)
        assertEquals(installed.digest, options.single().identity.artifactDigest)
        assertEquals(installed.profileKey, options.single().identity.modelProfileId)
        assertEquals(installed.quantization, options.single().identity.quantization)
    }

    @Test
    fun `empty catalog never invents an evaluation model`() {
        assertTrue(performanceModelOptions(PhoneModelDistributionState()).isEmpty())
    }

    private fun modelUi(stableId: String, installed: InstalledCatalogModelMetadata?, compatible: Boolean): PhoneCatalogModelUi =
        PhoneCatalogModelUi(
            stableId = stableId,
            displayName = installed?.displayName ?: "Not installed",
            description = "test",
            fileName = installed?.fileName ?: "$stableId.gguf",
            sizeBytes = installed?.sizeBytes ?: 512L,
            architecture = "qwen3_5",
            quantization = "Q4_K_M",
            profileKey = installed?.profileKey ?: "qwen35-test",
            licenseName = "Apache-2.0",
            status = if (installed == null) PhoneCatalogModelStatus.READY_TO_DOWNLOAD else PhoneCatalogModelStatus.INSTALLED,
            compatible = compatible,
            compatibilityReasons = emptyList(),
            compatibilityWarnings = emptyList(),
            installedModel = installed,
        )

    private fun installedMetadata(digest: String, profileKey: String, displayName: String): InstalledCatalogModelMetadata =
        InstalledCatalogModelMetadata(
            digest = ModelDigest(digest),
            modelId = profileKey,
            version = "1.0.0",
            displayName = displayName,
            profileKey = profileKey,
            applicationId = "harness",
            useCaseId = "evaluation",
            fileName = "$profileKey.gguf",
            sizeBytes = 512L * 1024L * 1024L,
            architecture = "qwen3_5",
            quantization = "Q4_K_M",
            installedAtEpochMs = 1L,
        )
}
