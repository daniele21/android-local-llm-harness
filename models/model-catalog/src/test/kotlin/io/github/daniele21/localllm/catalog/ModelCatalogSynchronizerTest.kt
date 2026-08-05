package io.github.daniele21.localllm.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ModelCatalogSynchronizerTest {
    private val codec = CatalogJsonCodec()
    private val validator = CatalogValidator()

    @Test
    fun storesValidatedUpdatedCatalog() = withFixture { repository, _ ->
        val source = QueueCatalogSource(
            CatalogFetchResult.Updated(
                bytes = encode(validCatalogDocument(expiresAtEpochMs = 10_000)),
                metadata = CatalogResponseMetadata(etag = "etag-1", lastModified = "date-1"),
            ),
        )

        val result = synchronizer(source, repository, nowEpochMs = 2_000).refresh() as CatalogRefreshResult.Updated

        assertEquals(2_000L, result.snapshot.syncMetadata?.fetchedAtEpochMs)
        assertEquals("etag-1", result.snapshot.syncMetadata?.etag)
        assertEquals(null, source.requests.single().currentRevision)
    }

    @Test
    fun notModifiedUpdatesMetadataWithoutReauthorizingStaleCatalog() {
        withFixture(staleGracePeriodMs = 1_000) { repository, _ ->
            repository.replace(validCatalogDocument(expiresAtEpochMs = 3_000), CatalogSyncMetadata(2_000, "old"), 2_000)
            val source = QueueCatalogSource(CatalogFetchResult.NotModified(CatalogResponseMetadata(etag = "new")))

            val refreshed = synchronizer(source, repository, nowEpochMs = 3_100).refresh()
            val result = refreshed as CatalogRefreshResult.NotModified

            assertEquals(CatalogFreshness.STALE, result.snapshot.freshness)
            assertFalse(result.snapshot.canAuthorizeDownloads)
            assertEquals("new", result.snapshot.syncMetadata?.etag)
            assertEquals(7L, source.requests.single().currentRevision)
        }
    }

    @Test
    fun malformedUpdatePreservesLastGoodCatalog() = withFixture { repository, _ ->
        repository.replace(validCatalogDocument(expiresAtEpochMs = 10_000), CatalogSyncMetadata(2_000), 2_000)
        val source = QueueCatalogSource(CatalogFetchResult.Updated("not-json".encodeToByteArray()))

        val result = synchronizer(source, repository, nowEpochMs = 2_100).refresh() as CatalogRefreshResult.Rejected

        assertEquals(CatalogFailureCode.MALFORMED_DOCUMENT, result.failure.code)
        assertEquals(7L, result.snapshot.document?.revision)
    }

    @Test
    fun transportFailurePreservesLastGoodCatalog() = withFixture { repository, _ ->
        repository.replace(validCatalogDocument(expiresAtEpochMs = 10_000), CatalogSyncMetadata(2_000), 2_000)
        val source = QueueCatalogSource(CatalogFetchResult.Failed(CatalogFailure(CatalogFailureCode.TIMEOUT, 0)))

        val result = synchronizer(source, repository, nowEpochMs = 2_100).refresh() as CatalogRefreshResult.Failed

        assertEquals(CatalogFailureCode.TIMEOUT, result.failure.code)
        assertEquals(2_100L, result.failure.occurredAtEpochMs)
        assertEquals(7L, result.snapshot.document?.revision)
    }

    @Test
    fun notModifiedWithoutCacheFailsClosed() = withFixture { repository, _ ->
        val source = QueueCatalogSource(CatalogFetchResult.NotModified())

        val result = synchronizer(source, repository, nowEpochMs = 2_000).refresh() as CatalogRefreshResult.Rejected

        assertEquals(CatalogFailureCode.NOT_MODIFIED_WITHOUT_CACHE, result.failure.code)
    }

    @Test
    fun unexpectedSourceExceptionIsNormalized() = withFixture { repository, _ ->
        val source = object : ModelCatalogSource {
            override fun fetch(request: CatalogFetchRequest): CatalogFetchResult = error("transport bug")
        }

        val result = synchronizer(source, repository, nowEpochMs = 2_000).refresh() as CatalogRefreshResult.Failed

        assertEquals(CatalogFailureCode.INTERNAL_FAILURE, result.failure.code)
    }

    private fun synchronizer(source: ModelCatalogSource, repository: ModelCatalogRepository, nowEpochMs: Long): ModelCatalogSynchronizer =
        ModelCatalogSynchronizer(source, repository, codec, validator, CatalogClock { nowEpochMs })

    private fun encode(document: CatalogModelDocument): ByteArray = (codec.encode(document) as CatalogEncodeResult.Success).bytes

    private fun withFixture(staleGracePeriodMs: Long = 7L * 24L * 60L * 60L * 1_000L, block: (FileModelCatalogRepository, File) -> Unit) {
        val directory = Files.createTempDirectory("model-catalog-sync-test").toFile()
        try {
            block(FileModelCatalogRepository(directory, codec, validator, staleGracePeriodMs), directory)
        } finally {
            directory.deleteRecursively()
        }
    }

    private class QueueCatalogSource(vararg results: CatalogFetchResult) : ModelCatalogSource {
        val requests = mutableListOf<CatalogFetchRequest>()
        private val queue = ArrayDeque(results.toList())

        override fun fetch(request: CatalogFetchRequest): CatalogFetchResult {
            requests += request
            return queue.removeFirst()
        }
    }
}
