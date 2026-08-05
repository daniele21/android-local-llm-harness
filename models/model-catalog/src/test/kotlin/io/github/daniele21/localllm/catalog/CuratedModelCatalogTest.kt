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
    fun exposesFourCandidateReleasesForThePhonePlayground() {
        val target =
            CatalogTarget(
                applicationId = ApplicationId("play-internal-phone-test"),
                useCaseId = UseCaseId("manual-inference-playground"),
            )
        val releases = CatalogQueries.releasesForTarget(CuratedModelCatalog.document(GENERATED_AT, EXPIRES_AT), target)

        assertEquals(EXPECTED_MODEL_IDS, releases.mapTo(linkedSetOf()) { it.id.modelId.value })
        assertTrue(releases.all { it.availability == CatalogAvailability.CANDIDATE })
        assertTrue(releases.all { it.artifact.quantization == "Q4_K_M" })
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
        const val EXPECTED_CANONICAL_SHA256 = "c18fe4508bc20b920f7f68d52ab8a3b8377c4a4511c13a8d476d9117619717e0"
        val EXPECTED_MODEL_IDS =
            setOf(
                "qwen3.5-0.8b-instruct-q4-k-m",
                "lfm2.5-1.2b-instruct-q4-k-m",
                "smollm2-360m-instruct-q4-k-m",
                "qwen3.5-2b-instruct-q4-k-m",
            )
    }
}
