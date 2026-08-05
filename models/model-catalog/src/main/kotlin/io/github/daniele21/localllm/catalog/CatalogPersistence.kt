@file:Suppress("TooManyFunctions")

package io.github.daniele21.localllm.catalog

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

fun interface CatalogClock {
    fun nowEpochMs(): Long
}

data class CatalogSyncMetadata(val fetchedAtEpochMs: Long, val etag: String? = null, val lastModified: String? = null)

enum class CatalogFreshness {
    EMPTY,
    FRESH,
    STALE,
    EXPIRED,
}

enum class CatalogFailureCode {
    NETWORK_UNAVAILABLE,
    TIMEOUT,
    HTTP_REJECTED,
    RESPONSE_TOO_LARGE,
    MALFORMED_DOCUMENT,
    UNSUPPORTED_SCHEMA,
    EXPIRED_DOCUMENT,
    VALIDATION_FAILURE,
    ROLLBACK_REJECTED,
    REVISION_CONFLICT,
    CATALOG_ID_MISMATCH,
    NOT_MODIFIED_WITHOUT_CACHE,
    PERSISTENCE_FAILURE,
    INTERNAL_FAILURE,
}

data class CatalogFailure(val code: CatalogFailureCode, val occurredAtEpochMs: Long)

data class CatalogSnapshot(
    val document: CatalogModelDocument?,
    val syncMetadata: CatalogSyncMetadata?,
    val freshness: CatalogFreshness,
    val canAuthorizeDownloads: Boolean,
    val lastFailure: CatalogFailure?,
)

enum class CatalogReplaceRejectionCode {
    INVALID_DOCUMENT,
    INVALID_METADATA,
    ENCODING_FAILURE,
    ROLLBACK_REJECTED,
    REVISION_CONFLICT,
    CATALOG_ID_MISMATCH,
    NO_CACHED_DOCUMENT,
    PERSISTENCE_FAILURE,
}

sealed interface CatalogReplaceResult {
    data class Stored(val snapshot: CatalogSnapshot) : CatalogReplaceResult
    data class Unchanged(val snapshot: CatalogSnapshot) : CatalogReplaceResult
    data class Rejected(val code: CatalogReplaceRejectionCode) : CatalogReplaceResult
}

interface ModelCatalogRepository {
    fun current(nowEpochMs: Long): CatalogSnapshot

    fun replace(document: CatalogModelDocument, metadata: CatalogSyncMetadata, nowEpochMs: Long): CatalogReplaceResult

    fun recordNotModified(metadata: CatalogSyncMetadata, nowEpochMs: Long): CatalogReplaceResult

    fun markRefreshFailure(failure: CatalogFailure)
}

@Suppress("TooManyFunctions")
class FileModelCatalogRepository(
    private val rootDirectory: File,
    private val codec: CatalogDocumentCodec,
    private val validator: CatalogValidator,
    private val staleGracePeriodMs: Long = DEFAULT_STALE_GRACE_PERIOD_MS,
    private val maxStateBytes: Int = DEFAULT_MAX_STATE_BYTES,
    private val stateFileName: String = DEFAULT_STATE_FILE_NAME,
) : ModelCatalogRepository {
    private val lock = Any()
    private var loaded = false
    private var state = RepositoryState.EMPTY

    init {
        require(staleGracePeriodMs >= 0)
        require(maxStateBytes > 0)
        require(stateFileName.isNotBlank())
        require(!stateFileName.contains('/') && !stateFileName.contains('\\'))
        require(stateFileName != "." && stateFileName != "..")
    }

    override fun current(nowEpochMs: Long): CatalogSnapshot = synchronized(lock) {
        ensureLoaded(nowEpochMs)
        state.toSnapshot(nowEpochMs)
    }

    @Suppress("ReturnCount")
    override fun replace(document: CatalogModelDocument, metadata: CatalogSyncMetadata, nowEpochMs: Long): CatalogReplaceResult =
        synchronized(lock) {
            ensureLoaded(nowEpochMs)
            val validation = validator.validate(document, nowEpochMs)
            if (!validation.valid) {
                return@synchronized CatalogReplaceResult.Rejected(CatalogReplaceRejectionCode.INVALID_DOCUMENT)
            }
            if (!validMetadata(metadata)) {
                return@synchronized CatalogReplaceResult.Rejected(CatalogReplaceRejectionCode.INVALID_METADATA)
            }

            val incomingBytes = when (val encoded = codec.encode(document)) {
                is CatalogEncodeResult.Success -> encoded.bytes

                is CatalogEncodeResult.Failure -> return@synchronized CatalogReplaceResult.Rejected(
                    CatalogReplaceRejectionCode.ENCODING_FAILURE,
                )
            }
            val currentDocument = state.document
            if (currentDocument != null) {
                compareRevision(currentDocument, document)?.let { rejection ->
                    return@synchronized CatalogReplaceResult.Rejected(rejection)
                }
            }

            val unchanged = currentDocument != null && currentDocument.revision == document.revision
            val candidate = RepositoryState(
                document = document,
                encodedDocument = incomingBytes,
                syncMetadata = metadata,
                lastFailure = null,
                persistedAtEpochMs = nowEpochMs,
            )
            if (!persist(candidate)) {
                return@synchronized CatalogReplaceResult.Rejected(CatalogReplaceRejectionCode.PERSISTENCE_FAILURE)
            }
            state = candidate
            val snapshot = state.toSnapshot(nowEpochMs)
            if (unchanged) CatalogReplaceResult.Unchanged(snapshot) else CatalogReplaceResult.Stored(snapshot)
        }

    override fun recordNotModified(metadata: CatalogSyncMetadata, nowEpochMs: Long): CatalogReplaceResult = synchronized(lock) {
        ensureLoaded(nowEpochMs)
        if (state.document == null) {
            return@synchronized CatalogReplaceResult.Rejected(CatalogReplaceRejectionCode.NO_CACHED_DOCUMENT)
        }
        if (!validMetadata(metadata)) {
            return@synchronized CatalogReplaceResult.Rejected(CatalogReplaceRejectionCode.INVALID_METADATA)
        }
        val candidate = state.copy(
            syncMetadata = metadata,
            lastFailure = null,
            persistedAtEpochMs = nowEpochMs,
        )
        if (!persist(candidate)) {
            return@synchronized CatalogReplaceResult.Rejected(CatalogReplaceRejectionCode.PERSISTENCE_FAILURE)
        }
        state = candidate
        CatalogReplaceResult.Unchanged(state.toSnapshot(nowEpochMs))
    }

    override fun markRefreshFailure(failure: CatalogFailure) {
        synchronized(lock) {
            ensureLoaded(failure.occurredAtEpochMs)
            val candidate = state.copy(
                lastFailure = failure,
                persistedAtEpochMs = failure.occurredAtEpochMs,
            )
            if (persist(candidate)) {
                state = candidate
            } else {
                state = candidate.copy(
                    lastFailure = CatalogFailure(CatalogFailureCode.PERSISTENCE_FAILURE, failure.occurredAtEpochMs),
                )
            }
        }
    }

    private fun compareRevision(current: CatalogModelDocument, incoming: CatalogModelDocument): CatalogReplaceRejectionCode? {
        if (current.catalogId != incoming.catalogId) return CatalogReplaceRejectionCode.CATALOG_ID_MISMATCH
        if (incoming.revision < current.revision) return CatalogReplaceRejectionCode.ROLLBACK_REJECTED
        if (incoming.revision > current.revision) return null
        return if (current == incoming) null else CatalogReplaceRejectionCode.REVISION_CONFLICT
    }

    private fun ensureLoaded(nowEpochMs: Long) {
        if (loaded) return
        loaded = true
        cleanupTemporaryFiles()
        state = try {
            readState() ?: RepositoryState.EMPTY
        } catch (_: IOException) {
            RepositoryState.EMPTY.copy(lastFailure = CatalogFailure(CatalogFailureCode.PERSISTENCE_FAILURE, nowEpochMs))
        } catch (_: IllegalArgumentException) {
            RepositoryState.EMPTY.copy(lastFailure = CatalogFailure(CatalogFailureCode.PERSISTENCE_FAILURE, nowEpochMs))
        }
    }

    private fun readState(): RepositoryState? {
        val file = stateFile()
        if (!file.exists()) return null
        ensureValidState(file.isFile && file.length() <= maxStateBytes)
        val bytes = readBoundedState(file)
        val root = STATE_PARSER.parse(bytes) as? CatalogJsonObject ?: invalidState()
        requireFields(root, STATE_FIELDS)
        val schemaVersion = requiredLong(root, "storageSchemaVersion")
        ensureValidState(schemaVersion == STORAGE_SCHEMA_VERSION.toLong())
        val persistedAt = requiredLong(root, "persistedAtEpochMs")
        ensureValidState(persistedAt >= 0)
        val catalogString = optionalString(root, "catalogJson")
        val document =
            catalogString?.let { encoded ->
                when (val decoded = codec.decode(encoded.toByteArray(Charsets.UTF_8))) {
                    is CatalogDecodeResult.Success -> decoded.document
                    is CatalogDecodeResult.Failure -> invalidState()
                }
            }
        val metadata = optionalObject(root, "syncMetadata")?.let(::decodeMetadata)
        val failure = optionalObject(root, "lastFailure")?.let(::decodeFailure)
        ensureValidState((document == null) == (metadata == null))
        return RepositoryState(
            document = document,
            encodedDocument = catalogString?.toByteArray(Charsets.UTF_8),
            syncMetadata = metadata,
            lastFailure = failure,
            persistedAtEpochMs = persistedAt,
        )
    }

    private fun ensureValidState(valid: Boolean) {
        if (!valid) invalidState()
    }

    private fun invalidState(): Nothing = throw CatalogStateException()

    private fun decodeMetadata(value: CatalogJsonObject): CatalogSyncMetadata {
        requireFields(value, METADATA_FIELDS)
        val metadata = CatalogSyncMetadata(
            fetchedAtEpochMs = requiredLong(value, "fetchedAtEpochMs"),
            etag = optionalString(value, "etag"),
            lastModified = optionalString(value, "lastModified"),
        )
        if (!validMetadata(metadata)) throw CatalogStateException()
        return metadata
    }

    private fun decodeFailure(value: CatalogJsonObject): CatalogFailure {
        requireFields(value, FAILURE_FIELDS)
        val rawCode = requiredString(value, "code")
        val code = CatalogFailureCode.entries.firstOrNull { it.name == rawCode } ?: throw CatalogStateException()
        val occurredAt = requiredLong(value, "occurredAtEpochMs")
        if (occurredAt < 0) throw CatalogStateException()
        return CatalogFailure(code, occurredAt)
    }

    private fun persist(candidate: RepositoryState): Boolean {
        return try {
            val bytes = encodeState(candidate)
            if (bytes.size > maxStateBytes) return false
            ensureRootDirectory()
            val temporary = File.createTempFile(TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX, rootDirectory)
            try {
                FileOutputStream(temporary).use { output ->
                    output.write(bytes)
                    output.flush()
                    output.fd.sync()
                }
                moveIntoPlace(temporary, stateFile())
            } finally {
                if (temporary.exists()) temporary.delete()
            }
            true
        } catch (_: IOException) {
            false
        } catch (_: CatalogJsonWriteException) {
            false
        }
    }

    private fun encodeState(value: RepositoryState): ByteArray {
        val catalogJson = value.encodedDocument?.toString(Charsets.UTF_8)
        val root = jsonObject(
            "storageSchemaVersion" to jsonNumber(STORAGE_SCHEMA_VERSION),
            "persistedAtEpochMs" to jsonNumber(value.persistedAtEpochMs),
            "syncMetadata" to value.syncMetadata?.let(::encodeMetadata).orNull(),
            "lastFailure" to value.lastFailure?.let(::encodeFailure).orNull(),
            "catalogJson" to catalogJson?.let(::CatalogJsonString).orNull(),
        )
        return CatalogJsonWriter.encode(root)
    }

    private fun encodeMetadata(value: CatalogSyncMetadata): CatalogJsonObject = jsonObject(
        "fetchedAtEpochMs" to jsonNumber(value.fetchedAtEpochMs),
        "etag" to value.etag?.let(::CatalogJsonString).orNull(),
        "lastModified" to value.lastModified?.let(::CatalogJsonString).orNull(),
    )

    private fun encodeFailure(value: CatalogFailure): CatalogJsonObject = jsonObject(
        "code" to CatalogJsonString(value.code.name),
        "occurredAtEpochMs" to jsonNumber(value.occurredAtEpochMs),
    )

    private fun validMetadata(metadata: CatalogSyncMetadata): Boolean {
        val validEtag = validOptionalHeader(metadata.etag)
        val validLastModified = validOptionalHeader(metadata.lastModified)
        return metadata.fetchedAtEpochMs >= 0 && validEtag && validLastModified
    }

    private fun validOptionalHeader(value: String?): Boolean = value == null ||
        (value.length <= MAX_METADATA_VALUE_LENGTH && value.none(Char::isISOControl) && value.hasValidSurrogates())

    private fun String.hasValidSurrogates(): Boolean {
        var index = 0
        while (index < length) {
            val char = this[index]
            when {
                char.isHighSurrogate() -> {
                    if (index + 1 >= length || !this[index + 1].isLowSurrogate()) return false
                    index += 2
                }

                char.isLowSurrogate() -> return false

                else -> index += 1
            }
        }
        return true
    }

    private fun readBoundedState(file: File): ByteArray {
        val output = ByteArrayOutputStream(minOf(file.length(), maxStateBytes.toLong()).toInt())
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(STATE_READ_BUFFER_BYTES)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxStateBytes) throw CatalogStateException()
                output.write(buffer, 0, read)
            }
        }
        return output.toByteArray()
    }

    private fun ensureRootDirectory() {
        if (rootDirectory.isDirectory) return
        if (rootDirectory.exists() || !rootDirectory.mkdirs()) throw IOException("Unable to create catalog directory")
    }

    private fun moveIntoPlace(source: File, destination: File) {
        try {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun cleanupTemporaryFiles() {
        val temporaryFiles = rootDirectory.listFiles { file ->
            file.name.startsWith(TEMP_FILE_PREFIX) && file.name.endsWith(TEMP_FILE_SUFFIX)
        }
        temporaryFiles?.forEach(File::delete)
    }

    private fun stateFile(): File = File(rootDirectory, stateFileName)

    private fun RepositoryState.toSnapshot(nowEpochMs: Long): CatalogSnapshot {
        val freshness = freshness(nowEpochMs)
        return CatalogSnapshot(
            document = document,
            syncMetadata = syncMetadata,
            freshness = freshness,
            canAuthorizeDownloads = freshness == CatalogFreshness.FRESH,
            lastFailure = lastFailure,
        )
    }

    private fun RepositoryState.freshness(nowEpochMs: Long): CatalogFreshness {
        val current = document ?: return CatalogFreshness.EMPTY
        if (nowEpochMs < current.expiresAtEpochMs) return CatalogFreshness.FRESH
        val staleUntil = try {
            Math.addExact(current.expiresAtEpochMs, staleGracePeriodMs)
        } catch (_: ArithmeticException) {
            Long.MAX_VALUE
        }
        return if (nowEpochMs < staleUntil) CatalogFreshness.STALE else CatalogFreshness.EXPIRED
    }

    private data class RepositoryState(
        val document: CatalogModelDocument?,
        val encodedDocument: ByteArray?,
        val syncMetadata: CatalogSyncMetadata?,
        val lastFailure: CatalogFailure?,
        val persistedAtEpochMs: Long,
    ) {
        companion object {
            val EMPTY = RepositoryState(null, null, null, null, 0)
        }
    }

    private class CatalogStateException : IllegalArgumentException()

    private companion object {
        const val STORAGE_SCHEMA_VERSION = 1
        const val DEFAULT_STATE_FILE_NAME = "catalog-state.json"
        const val DEFAULT_MAX_STATE_BYTES = 8 * 1024 * 1024
        const val DEFAULT_STALE_GRACE_PERIOD_MS = 7L * 24L * 60L * 60L * 1000L
        const val MAX_METADATA_VALUE_LENGTH = 1024
        const val STATE_READ_BUFFER_BYTES = 16 * 1024
        const val TEMP_FILE_PREFIX = "catalog-state-"
        const val TEMP_FILE_SUFFIX = ".tmp"
        val STATE_FIELDS = setOf(
            "storageSchemaVersion",
            "persistedAtEpochMs",
            "syncMetadata",
            "lastFailure",
            "catalogJson",
        )
        val METADATA_FIELDS = setOf("fetchedAtEpochMs", "etag", "lastModified")
        val FAILURE_FIELDS = setOf("code", "occurredAtEpochMs")
        val STATE_PARSER = BoundedCatalogJsonParser(
            maxDepth = 8,
            maxNodes = 256,
            maxStringChars = DEFAULT_MAX_STATE_BYTES,
        )
    }
}

private fun jsonObject(vararg fields: Pair<String, CatalogJsonValue>): CatalogJsonObject {
    val values = linkedMapOf<String, CatalogJsonValue>()
    fields.forEach { (name, value) -> values[name] = value }
    return CatalogJsonObject(values)
}

private fun jsonNumber(value: Number): CatalogJsonNumber = CatalogJsonNumber(value.toString())

private fun CatalogJsonValue?.orNull(): CatalogJsonValue = this ?: CatalogJsonNull

private fun requireFields(value: CatalogJsonObject, expected: Set<String>) {
    require(value.fields.keys == expected) { "Unexpected catalog state fields" }
}

private fun requiredValue(value: CatalogJsonObject, name: String): CatalogJsonValue =
    value.fields[name] ?: throw IllegalArgumentException("Missing catalog state field")

private fun requiredString(value: CatalogJsonObject, name: String): String = (requiredValue(value, name) as? CatalogJsonString)?.value
    ?: throw IllegalArgumentException("Invalid catalog state string")

private fun optionalString(value: CatalogJsonObject, name: String): String? = when (val field = requiredValue(value, name)) {
    CatalogJsonNull -> null
    is CatalogJsonString -> field.value
    else -> throw IllegalArgumentException("Invalid optional catalog state string")
}

private fun requiredLong(value: CatalogJsonObject, name: String): Long {
    val number = requiredValue(value, name) as? CatalogJsonNumber
    requireNotNull(number) { "Invalid catalog state number" }
    val raw = number.raw
    require(raw.none { it == '.' || it == 'e' || it == 'E' }) {
        "Invalid catalog state integer"
    }
    return requireNotNull(raw.toLongOrNull()) { "Catalog state integer out of range" }
}

private fun optionalObject(value: CatalogJsonObject, name: String): CatalogJsonObject? = when (val field = requiredValue(value, name)) {
    CatalogJsonNull -> null
    is CatalogJsonObject -> field
    else -> throw IllegalArgumentException("Invalid optional catalog state object")
}
