package io.github.daniele21.localllm.catalog

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.UseCaseId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogCompatibilityEvaluatorTest {
    private val evaluator =
        CatalogCompatibilityEvaluator(
            versionMatcher =
            object : CatalogVersionMatcher {
                override fun isInRange(currentVersion: String, minimumInclusive: String?, maximumExclusive: String?): Boolean =
                    currentVersion == "1.5.0"
            },
            profileResolver =
            object : CatalogProfileResolver {
                override fun supports(profileKey: ModelProfileKey, target: CatalogTarget): Boolean =
                    profileKey.value == "qwen-playground" && target == testTarget
            },
        )

    @Test
    fun acceptsCompatibleReleaseAndReportsRecommendedRamWarning() {
        val result =
            evaluator.evaluate(
                release = validCatalogRelease(),
                target = testTarget,
                device = compatibleDevice(totalMemoryBytes = 1536L * 1024L * 1024L),
            )

        assertTrue(result.reasons.toString(), result.compatible)
        assertEquals(listOf(CatalogCompatibilityWarning.RAM_BELOW_RECOMMENDED), result.warnings)
        assertTrue(requireNotNull(result.requiredStorageBytes) > validCatalogRelease().artifact.sizeBytes)
    }

    @Test
    fun blocksUnauthorizedTarget() {
        val otherTarget =
            CatalogTarget(
                applicationId = ApplicationId("other-app"),
                useCaseId = UseCaseId("playground"),
            )

        val result =
            evaluator.evaluate(
                release = validCatalogRelease(),
                target = otherTarget,
                device = compatibleDevice(),
            )

        assertFalse(result.compatible)
        assertTrue(CatalogCompatibilityReason.TARGET_NOT_ALLOWED in result.reasons)
        assertTrue(CatalogCompatibilityReason.UNSUPPORTED_PROFILE in result.reasons)
    }

    @Test
    fun blocksUnsupportedAbi() {
        val result =
            evaluator.evaluate(
                release = validCatalogRelease(),
                target = testTarget,
                device = compatibleDevice(supportedAbis = setOf("x86_64")),
            )

        assertFalse(result.compatible)
        assertTrue(CatalogCompatibilityReason.UNSUPPORTED_ABI in result.reasons)
    }

    @Test
    fun includesDoubleStagingAndSafetyMarginInStorageRequirement() {
        val release = validCatalogRelease { current ->
            current.copy(artifact = current.artifact.copy(sizeBytes = 100L * 1024L * 1024L))
        }
        val result =
            evaluator.evaluate(
                release = release,
                target = testTarget,
                device = compatibleDevice(availableStorageBytes = 300L * 1024L * 1024L),
            )

        assertFalse(result.compatible)
        assertTrue(CatalogCompatibilityReason.INSUFFICIENT_STORAGE in result.reasons)
        assertEquals(360L * 1024L * 1024L, result.requiredStorageBytes)
    }

    @Test
    fun blocksRevokedRelease() {
        val result =
            evaluator.evaluate(
                release = validCatalogRelease { current ->
                    current.copy(availability = CatalogAvailability.REVOKED)
                },
                target = testTarget,
                device = compatibleDevice(),
            )

        assertFalse(result.compatible)
        assertTrue(CatalogCompatibilityReason.RELEASE_REVOKED in result.reasons)
    }

    @Test
    fun exposesDeprecatedReleaseAsWarning() {
        val result =
            evaluator.evaluate(
                release = validCatalogRelease { current ->
                    current.copy(availability = CatalogAvailability.DEPRECATED)
                },
                target = testTarget,
                device = compatibleDevice(),
            )

        assertTrue(result.compatible)
        assertTrue(CatalogCompatibilityWarning.RELEASE_DEPRECATED in result.warnings)
    }

    @Test
    fun filtersCatalogByExactApplicationAndUseCase() {
        val otherTarget =
            CatalogTarget(
                applicationId = ApplicationId("other-app"),
                useCaseId = UseCaseId("other-use-case"),
            )
        val document =
            validCatalogDocument(
                entries =
                listOf(
                    validCatalogRelease { current ->
                        current.copy(id = current.id.copy(modelId = CatalogModelId("allowed")))
                    },
                    validCatalogRelease { current ->
                        current.copy(
                            id = CatalogReleaseId(
                                modelId = CatalogModelId("other"),
                                version = CatalogModelVersion("2.0.0"),
                            ),
                            artifact = current.artifact.copy(digest = ModelDigest("b".repeat(64))),
                            allowedTargets = setOf(otherTarget),
                        )
                    },
                ),
            )

        val result = CatalogQueries.releasesForTarget(document, testTarget)

        assertEquals(listOf("allowed"), result.map { it.id.modelId.value })
    }

    private fun compatibleDevice(
        supportedAbis: Set<String> = setOf("arm64-v8a"),
        totalMemoryBytes: Long? = 4L * 1024L * 1024L * 1024L,
        availableStorageBytes: Long = 2L * 1024L * 1024L * 1024L,
    ): CatalogDeviceProfile = CatalogDeviceProfile(
        sdkInt = 36,
        supportedAbis = supportedAbis,
        totalMemoryBytes = totalMemoryBytes,
        availableStorageBytes = availableStorageBytes,
        harnessVersion = "1.5.0",
        backendId = "llama.cpp",
    )
}
