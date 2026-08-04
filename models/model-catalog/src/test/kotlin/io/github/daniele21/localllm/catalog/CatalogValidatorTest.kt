package io.github.daniele21.localllm.catalog

import java.net.URI
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogValidatorTest {
    private val validator = CatalogValidator()

    @Test
    fun acceptsValidCatalog() {
        val result = validator.validate(validCatalogDocument(), nowEpochMs = 1_500)

        assertTrue(result.violations.toString(), result.valid)
    }

    @Test
    fun rejectsExpiredCatalog() {
        val result =
            validator.validate(
                validCatalogDocument(expiresAtEpochMs = 1_400),
                nowEpochMs = 1_500,
            )

        assertFalse(result.valid)
        assertTrue(result.has(CatalogViolationCode.DOCUMENT_EXPIRED))
    }

    @Test
    fun rejectsNonHttpsDownloadUri() {
        val release = validCatalogRelease(downloadUri = URI("http://models.example.test/model.gguf"))

        val result = validator.validate(validCatalogDocument(listOf(release)), nowEpochMs = 1_500)

        assertFalse(result.valid)
        assertTrue(result.has(CatalogViolationCode.INVALID_DOWNLOAD_URI))
    }

    @Test
    fun rejectsInvalidDigest() {
        val release = validCatalogRelease(digest = "not-a-sha256")

        val result = validator.validate(validCatalogDocument(listOf(release)), nowEpochMs = 1_500)

        assertFalse(result.valid)
        assertTrue(result.has(CatalogViolationCode.INVALID_DIGEST))
    }

    @Test
    fun rejectsDuplicateReleaseIdentity() {
        val release = validCatalogRelease()

        val result =
            validator.validate(
                validCatalogDocument(entries = listOf(release, release)),
                nowEpochMs = 1_500,
            )

        assertFalse(result.valid)
        assertTrue(result.has(CatalogViolationCode.DUPLICATE_RELEASE))
    }

    @Test
    fun rejectsConflictingMetadataForSameDigest() {
        val first = validCatalogRelease(version = "1.0.0", sizeBytes = 10)
        val second = validCatalogRelease(version = "1.1.0", sizeBytes = 11)

        val result =
            validator.validate(
                validCatalogDocument(entries = listOf(first, second)),
                nowEpochMs = 1_500,
            )

        assertFalse(result.valid)
        assertTrue(result.has(CatalogViolationCode.CONFLICTING_DIGEST_METADATA))
    }

    @Test
    fun rejectsReleaseReplacingItself() {
        val original = validCatalogRelease()
        val release = original.copy(replacement = original.id)

        val result = validator.validate(validCatalogDocument(listOf(release)), nowEpochMs = 1_500)

        assertFalse(result.valid)
        assertTrue(result.has(CatalogViolationCode.SELF_REPLACEMENT))
    }

    private fun CatalogValidationResult.has(code: CatalogViolationCode): Boolean =
        violations.any { it.code == code }
}
