package io.github.daniele21.localllm.catalog

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.UseCaseId
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CuratedModelCatalogTest {
    @Test
    fun curatedDocumentValidatesAndRoundTripsCanonicalPayload() {
        val document = CuratedModelCatalog.document(GENERATED_AT, EXPIRES_AT)

        val validation = CatalogValidator().validate(document, GENERATED_AT + 1)
        assertTrue(validation.violations.toString(), validation.valid)
        assertEquals(4L, document.revision)

        val codec = CatalogJsonCodec()
        val encoded = codec.encode(document) as CatalogEncodeResult.Success
        val reencoded = codec.encode(document) as CatalogEncodeResult.Success
        assertArrayEquals(encoded.bytes, reencoded.bytes)

        val decoded = codec.decode(encoded.bytes) as CatalogDecodeResult.Success
        assertEquals(document, decoded.document)
    }

    @Test
    fun exposesSevenQwen35CandidateReleasesForThePhonePlayground() {
        val target =
            CatalogTarget(
                applicationId = ApplicationId("play-internal-phone-test"),
                useCaseId = UseCaseId("manual-inference-playground"),
            )
        val releases = CatalogQueries.releasesForTarget(CuratedModelCatalog.document(GENERATED_AT, EXPIRES_AT), target)

        assertEquals(EXPECTED_MODEL_IDS, releases.mapTo(linkedSetOf()) { it.id.modelId.value })
        assertTrue(releases.all { it.artifact.architecture == "qwen35" })
        assertTrue(releases.all { it.availability == CatalogAvailability.CANDIDATE })
        assertTrue(releases.all { it.artifact.quantization.isNotBlank() })
        assertTrue(releases.all { it.compatibility.minSdk == 26 })
        assertTrue(releases.all { it.compatibility.supportedAbis == setOf("arm64-v8a") })
        assertTrue(releases.all { it.compatibility.supportedBackendIds == setOf("llama.cpp") })
    }

    @Test
    fun candidateReleaseRemainsSelectableWithExplicitWarning() {
        val target =
            CatalogTarget(
                applicationId = ApplicationId("play-internal-phone-test"),
                useCaseId = UseCaseId("manual-inference-playground"),
            )
        val evaluator =
            CatalogCompatibilityEvaluator(
                versionMatcher = AcceptingVersionMatcher,
                profileResolver = AcceptingProfileResolver,
            )

        val result =
            evaluator.evaluate(
                release = CuratedModelCatalog.releases.first(),
                target = target,
                device =
                CatalogDeviceProfile(
                    sdkInt = 36,
                    supportedAbis = setOf("arm64-v8a"),
                    totalMemoryBytes = 8_000_000_000,
                    availableStorageBytes = 4_000_000_000,
                    harnessVersion = "0.3.0",
                    backendId = "llama.cpp",
                ),
            )

        assertTrue(result.reasons.toString(), result.compatible)
        assertEquals(listOf(CatalogCompatibilityWarning.RELEASE_CANDIDATE), result.warnings)
        assertFalse(result.reasons.contains(CatalogCompatibilityReason.RELEASE_UNAVAILABLE))
    }

    private object AcceptingVersionMatcher : CatalogVersionMatcher {
        override fun isInRange(currentVersion: String, minimumInclusive: String?, maximumExclusive: String?): Boolean = true
    }

    private object AcceptingProfileResolver : CatalogProfileResolver {
        override fun supports(profileKey: ModelProfileKey, target: CatalogTarget): Boolean = true
    }

    private companion object {
        const val GENERATED_AT = 1_800_000_000_000
        const val EXPIRES_AT = 1_800_086_400_000
        val EXPECTED_MODEL_IDS =
            setOf(
                "qwen35-08b-q4-k-m",
                "qwen35-08b-q5-k-m",
                "qwen35-08b-q8-0",
                "qwen35-08b-ud-iq2-xxs",
                "qwen35-2b-q4-k-m",
                "qwen35-2b-q5-k-m",
                "qwen35-2b-ud-iq2-xxs",
            )
    }
}
