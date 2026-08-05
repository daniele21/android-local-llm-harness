package io.github.daniele21.localllm.catalog

data class CatalogFetchRequest(val etag: String?, val lastModified: String?, val currentRevision: Long?)

data class CatalogResponseMetadata(val etag: String? = null, val lastModified: String? = null)

sealed interface CatalogFetchResult {
    data class Updated(val bytes: ByteArray, val metadata: CatalogResponseMetadata = CatalogResponseMetadata()) : CatalogFetchResult

    data class NotModified(val metadata: CatalogResponseMetadata = CatalogResponseMetadata()) : CatalogFetchResult

    data class Failed(val failure: CatalogFailure) : CatalogFetchResult
}

interface ModelCatalogSource {
    fun fetch(request: CatalogFetchRequest): CatalogFetchResult
}

sealed interface CatalogRefreshResult {
    data class Updated(val snapshot: CatalogSnapshot) : CatalogRefreshResult
    data class NotModified(val snapshot: CatalogSnapshot) : CatalogRefreshResult
    data class Rejected(val failure: CatalogFailure, val snapshot: CatalogSnapshot) : CatalogRefreshResult
    data class Failed(val failure: CatalogFailure, val snapshot: CatalogSnapshot) : CatalogRefreshResult
}

class ModelCatalogSynchronizer(
    private val source: ModelCatalogSource,
    private val repository: ModelCatalogRepository,
    private val codec: CatalogDocumentCodec,
    private val validator: CatalogValidator,
    private val clock: CatalogClock,
) {
    @Suppress("TooGenericExceptionCaught")
    fun refresh(): CatalogRefreshResult {
        val nowEpochMs = clock.nowEpochMs()
        val current = repository.current(nowEpochMs)
        val request = CatalogFetchRequest(
            etag = current.syncMetadata?.etag,
            lastModified = current.syncMetadata?.lastModified,
            currentRevision = current.document?.revision,
        )
        val fetched = try {
            source.fetch(request)
        } catch (_: RuntimeException) {
            return recordFailure(CatalogFailure(CatalogFailureCode.INTERNAL_FAILURE, nowEpochMs), nowEpochMs)
        }
        return when (fetched) {
            is CatalogFetchResult.Updated -> applyUpdated(fetched, nowEpochMs)
            is CatalogFetchResult.NotModified -> applyNotModified(fetched, current, nowEpochMs)
            is CatalogFetchResult.Failed -> recordFailure(fetched.failure, nowEpochMs)
        }
    }

    private fun applyUpdated(fetched: CatalogFetchResult.Updated, nowEpochMs: Long): CatalogRefreshResult {
        val decoded = when (val result = codec.decode(fetched.bytes)) {
            is CatalogDecodeResult.Success -> result.document

            is CatalogDecodeResult.Failure -> {
                val failure = CatalogFailure(mapCodecFailure(result.error.code), nowEpochMs)
                return reject(failure, nowEpochMs)
            }
        }
        val validation = validator.validate(decoded, nowEpochMs)
        if (!validation.valid) {
            val failure = CatalogFailure(mapValidationFailure(validation), nowEpochMs)
            return reject(failure, nowEpochMs)
        }
        val metadata = fetched.metadata.toSyncMetadata(nowEpochMs)
        return when (val replace = repository.replace(decoded, metadata, nowEpochMs)) {
            is CatalogReplaceResult.Stored -> CatalogRefreshResult.Updated(replace.snapshot)

            is CatalogReplaceResult.Unchanged -> CatalogRefreshResult.NotModified(replace.snapshot)

            is CatalogReplaceResult.Rejected -> reject(
                CatalogFailure(mapReplaceRejection(replace.code), nowEpochMs),
                nowEpochMs,
            )
        }
    }

    private fun applyNotModified(
        fetched: CatalogFetchResult.NotModified,
        current: CatalogSnapshot,
        nowEpochMs: Long,
    ): CatalogRefreshResult {
        if (current.document == null) {
            return reject(CatalogFailure(CatalogFailureCode.NOT_MODIFIED_WITHOUT_CACHE, nowEpochMs), nowEpochMs)
        }
        val metadata = fetched.metadata.toSyncMetadata(nowEpochMs)
        return when (val replace = repository.recordNotModified(metadata, nowEpochMs)) {
            is CatalogReplaceResult.Stored -> CatalogRefreshResult.NotModified(replace.snapshot)

            is CatalogReplaceResult.Unchanged -> CatalogRefreshResult.NotModified(replace.snapshot)

            is CatalogReplaceResult.Rejected -> reject(
                CatalogFailure(mapReplaceRejection(replace.code), nowEpochMs),
                nowEpochMs,
            )
        }
    }

    private fun recordFailure(failure: CatalogFailure, nowEpochMs: Long): CatalogRefreshResult {
        val normalized = failure.copy(occurredAtEpochMs = nowEpochMs)
        repository.markRefreshFailure(normalized)
        return CatalogRefreshResult.Failed(normalized, repository.current(nowEpochMs))
    }

    private fun reject(failure: CatalogFailure, nowEpochMs: Long): CatalogRefreshResult {
        repository.markRefreshFailure(failure)
        return CatalogRefreshResult.Rejected(failure, repository.current(nowEpochMs))
    }

    private fun mapCodecFailure(code: CatalogCodecErrorCode): CatalogFailureCode = when (code) {
        CatalogCodecErrorCode.DOCUMENT_TOO_LARGE,
        CatalogCodecErrorCode.ENCODED_DOCUMENT_TOO_LARGE,
        CatalogCodecErrorCode.JSON_LIMIT_EXCEEDED,
        -> CatalogFailureCode.RESPONSE_TOO_LARGE

        else -> CatalogFailureCode.MALFORMED_DOCUMENT
    }

    private fun mapValidationFailure(result: CatalogValidationResult): CatalogFailureCode = when {
        result.violations.any { it.code == CatalogViolationCode.UNSUPPORTED_SCHEMA } ->
            CatalogFailureCode.UNSUPPORTED_SCHEMA

        result.violations.any { it.code == CatalogViolationCode.DOCUMENT_EXPIRED } ->
            CatalogFailureCode.EXPIRED_DOCUMENT

        else -> CatalogFailureCode.VALIDATION_FAILURE
    }

    private fun mapReplaceRejection(code: CatalogReplaceRejectionCode): CatalogFailureCode = when (code) {
        CatalogReplaceRejectionCode.INVALID_DOCUMENT -> CatalogFailureCode.VALIDATION_FAILURE

        CatalogReplaceRejectionCode.INVALID_METADATA,
        CatalogReplaceRejectionCode.ENCODING_FAILURE,
        CatalogReplaceRejectionCode.PERSISTENCE_FAILURE,
        -> CatalogFailureCode.PERSISTENCE_FAILURE

        CatalogReplaceRejectionCode.ROLLBACK_REJECTED -> CatalogFailureCode.ROLLBACK_REJECTED

        CatalogReplaceRejectionCode.REVISION_CONFLICT -> CatalogFailureCode.REVISION_CONFLICT

        CatalogReplaceRejectionCode.CATALOG_ID_MISMATCH -> CatalogFailureCode.CATALOG_ID_MISMATCH

        CatalogReplaceRejectionCode.NO_CACHED_DOCUMENT -> CatalogFailureCode.NOT_MODIFIED_WITHOUT_CACHE
    }

    private fun CatalogResponseMetadata.toSyncMetadata(nowEpochMs: Long): CatalogSyncMetadata = CatalogSyncMetadata(
        fetchedAtEpochMs = nowEpochMs,
        etag = etag,
        lastModified = lastModified,
    )
}
