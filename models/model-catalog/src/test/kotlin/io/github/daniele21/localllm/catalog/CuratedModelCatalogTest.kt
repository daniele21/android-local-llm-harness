package io.github.daniele21.localllm.catalog

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.UseCaseId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class CuratedModelCatalogTest {
    @Test
    fun curatedDocumentValidatesAndHasStableCanonicalPayload() {
        val document = CuratedModelCatalog.document(GENERATED_AT, EXPIRES_AT)

        val validation = CatalogValidator().validate(document, GENERATED_AT + 1)
        assertTrue(validation.violations.toString(), validation.valid)

        val encoded = CatalogJsonCodec().encode(document) as CatalogEncodeResult.Success
        assertEquals(EXPECTED_CANONICAL_SHA256, encoded.bytes.sha256())

        val decoded = CatalogJsonCodec().decode(encoded.bytes) as CatalogDecodeResult.Success
        assertEquals(document, decoded.document)
    }

    @Test
    fun exposesFifteenCandidateReleasesForThePhonePlayground() {
        val target =
            CatalogTarget(
                applicationId = ApplicationId("play-internal-phone-test"),
                useCaseId = UseCaseId("manual-inference-playground"),
            )
        val releases = CatalogQueries.releasesForTarget(CuratedModelCatalog.document(GENERATED_AT, EXPIRES_AT), target)

        assertEquals(EXPECTED_MODEL_IDS, releases.mapTo(linkedSetOf()) { it.id.modelId.value })
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

    private fun ByteArray.sha256(): String =
        MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { byte -> "%02x".format(byte) }

    private object AcceptingVersionMatcher : CatalogVersionMatcher {
        override fun isInRange(currentVersion: String, minimumInclusive: String?, maximumExclusive: String?): Boolean = true
    }

    private object AcceptingProfileResolver : CatalogProfileResolver {
        override fun supports(profileKey: ModelProfileKey, target: CatalogTarget): Boolean = true
    }

    private companion object {
        const val GENERATED_AT = 1_800_000_000_000
        const val EXPIRES_AT = 1_800_086_400_000
        const val EXPECTED_CANONICAL_SHA256 = "e67acbd5c5a25bfd253fde791e28d46096c5699b8152070fa04565eb8c27800a"
        val EXPECTED_MODEL_IDS =
            setOf(
                "lfm2.5-1.2b-instruct-q4-k-m",
                "smollm2-360m-instruct-q4-k-m",
                "qwen3-8b-ud-iq1-s",
                "qwen3-8b-ud-iq1-m",
                "qwen3-8b-q2-k",
                "qwen3-8b-q3-k-m",
                "qwen3-8b-q4-k-m",
                "qwen3-8b-q5-k-m",
                "qwen35-08b-q4-k-m",
                "qwen35-08b-q5-k-m",
                "qwen35-08b-q8-0",
                "qwen35-2b-q4-k-m",
                "qwen35-2b-q5-k-m",
                "qwen35-4b-q4-k-m",
                "qwen35-4b-q5-k-m",
            )
    }
}
