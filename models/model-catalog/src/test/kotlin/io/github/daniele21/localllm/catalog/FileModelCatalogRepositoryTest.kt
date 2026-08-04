package io.github.daniele21.localllm.catalog

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileModelCatalogRepositoryTest {
    private val codec = CatalogJsonCodec()
    private val validator = CatalogValidator()

    @Test
    fun persistsAndReloadsValidatedSnapshot() = withTemporaryDirectory { directory ->
        val document = validCatalogDocument(expiresAtEpochMs = 10_000)
        val repository = repository(directory)

        assertTrue(repository.replace(document, CatalogSyncMetadata(2_000, "etag-1", "date-1"), 2_000) is CatalogReplaceResult.Stored)
        val reloaded = repository(directory).current(2_100)

        assertEquals(document, reloaded.document)
        assertEquals(CatalogFreshness.FRESH, reloaded.freshness)
        assertTrue(reloaded.canAuthorizeDownloads)
    }

    @Test
    fun rejectsRevisionRollbackWithoutReplacingCache() = withTemporaryDirectory { directory ->
        val repository = repository(directory)
        repository.replace(validCatalogDocument(expiresAtEpochMs = 10_000), CatalogSyncMetadata(2_000), 2_000)
        val rollback = validCatalogDocument(expiresAtEpochMs = 10_000).copy(revision = 6)

        val result = repository.replace(rollback, CatalogSyncMetadata(2_100), 2_100) as CatalogReplaceResult.Rejected

        assertEquals(CatalogReplaceRejectionCode.ROLLBACK_REJECTED, result.code)
        assertEquals(7L, repository.current(2_100).document?.revision)
    }

    @Test
    fun rejectsDifferentPayloadAtSameRevision() = withTemporaryDirectory { directory ->
        val repository = repository(directory)
        val original = validCatalogDocument(expiresAtEpochMs = 10_000)
        repository.replace(original, CatalogSyncMetadata(2_000), 2_000)
        val changedRelease = original.entries.single().copy(description = "Different metadata")

        val result = repository.replace(
            original.copy(entries = listOf(changedRelease)),
            CatalogSyncMetadata(2_100),
            2_100,
        ) as CatalogReplaceResult.Rejected

        assertEquals(CatalogReplaceRejectionCode.REVISION_CONFLICT, result.code)
    }

    @Test
    fun rejectsCatalogIdentityChange() = withTemporaryDirectory { directory ->
        val repository = repository(directory)
        val original = validCatalogDocument(expiresAtEpochMs = 10_000)
        repository.replace(original, CatalogSyncMetadata(2_000), 2_000)

        val result = repository.replace(
            original.copy(catalogId = CatalogId("other-catalog"), revision = 8),
            CatalogSyncMetadata(2_100),
            2_100,
        ) as CatalogReplaceResult.Rejected

        assertEquals(CatalogReplaceRejectionCode.CATALOG_ID_MISMATCH, result.code)
    }

    @Test
    fun exposesFreshStaleAndExpiredStates() = withTemporaryDirectory { directory ->
        val repository = repository(directory, staleGracePeriodMs = 500)
        repository.replace(validCatalogDocument(expiresAtEpochMs = 3_000), CatalogSyncMetadata(2_000), 2_000)

        assertEquals(CatalogFreshness.FRESH, repository.current(2_999).freshness)
        assertEquals(CatalogFreshness.STALE, repository.current(3_000).freshness)
        assertFalse(repository.current(3_000).canAuthorizeDownloads)
        assertEquals(CatalogFreshness.EXPIRED, repository.current(3_500).freshness)
    }

    @Test
    fun recordsRefreshFailureWithoutLosingCache() = withTemporaryDirectory { directory ->
        val document = validCatalogDocument(expiresAtEpochMs = 10_000)
        val repository = repository(directory)
        repository.replace(document, CatalogSyncMetadata(2_000), 2_000)
        repository.markRefreshFailure(CatalogFailure(CatalogFailureCode.NETWORK_UNAVAILABLE, 2_100))

        val reloaded = repository(directory).current(2_100)

        assertEquals(document, reloaded.document)
        assertEquals(CatalogFailureCode.NETWORK_UNAVAILABLE, reloaded.lastFailure?.code)
    }

    @Test
    fun cleansAbandonedTemporaryStateFiles() = withTemporaryDirectory { directory ->
        val abandoned = File(directory, "catalog-state-abandoned.tmp")
        abandoned.writeText("partial")

        repository(directory).current(1_000)

        assertFalse(abandoned.exists())
    }

    @Test
    fun corruptStateFailsClosedWithoutThrowing() = withTemporaryDirectory { directory ->
        File(directory, "catalog-state.json").writeText("not-json")

        val snapshot = repository(directory).current(2_000)

        assertEquals(CatalogFreshness.EMPTY, snapshot.freshness)
        assertEquals(CatalogFailureCode.PERSISTENCE_FAILURE, snapshot.lastFailure?.code)
        assertFalse(snapshot.canAuthorizeDownloads)
    }

    private fun repository(directory: File, staleGracePeriodMs: Long = 7L * 24L * 60L * 60L * 1_000L) =
        FileModelCatalogRepository(directory, codec, validator, staleGracePeriodMs)

    private fun withTemporaryDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("model-catalog-test").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
