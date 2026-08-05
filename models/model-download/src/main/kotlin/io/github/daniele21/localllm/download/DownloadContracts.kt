package io.github.daniele21.localllm.download

import io.github.daniele21.localllm.catalog.CatalogModelRelease
import io.github.daniele21.localllm.store.StoredModel
import java.io.Closeable
import java.io.InputStream
import java.net.URI

fun interface DownloadCancellation {
    fun isCancelled(): Boolean
}

fun interface DownloadProgressListener {
    fun onProgress(progress: DownloadProgress)
}

data class DownloadProgress(val downloadedBytes: Long, val expectedBytes: Long)

data class DownloadRequest(
    val release: CatalogModelRelease,
    val cancellation: DownloadCancellation = DownloadCancellation { false },
    val progressListener: DownloadProgressListener = DownloadProgressListener {},
)

sealed interface DownloadResult {
    data class Installed(val model: StoredModel, val downloadedBytes: Long) : DownloadResult

    data class AlreadyInstalled(val model: StoredModel) : DownloadResult

    data class Failed(val error: DownloadError) : DownloadResult
}

data class DownloadError(val code: DownloadErrorCode, val detail: String)

enum class DownloadErrorCode {
    INVALID_URI,
    HOST_NOT_ALLOWED,
    REDIRECT_LIMIT_EXCEEDED,
    INVALID_REDIRECT,
    HTTP_FAILURE,
    CONTENT_LENGTH_MISMATCH,
    SIZE_LIMIT_EXCEEDED,
    DIGEST_MISMATCH,
    CANCELLED,
    IO_FAILURE,
    STORE_FAILURE,
}

data class TransportRequest(val uri: URI, val connectTimeoutMs: Int, val readTimeoutMs: Int)

interface DownloadTransport {
    fun open(request: TransportRequest): TransportResponse
}

interface TransportResponse : Closeable {
    val statusCode: Int
    val contentLength: Long?
    val redirectLocation: String?
    val body: InputStream
}

fun interface DownloadHostPolicy {
    fun isAllowed(host: String): Boolean
}

data class SecureDownloadPolicy(
    val hostPolicy: DownloadHostPolicy,
    val maxRedirects: Int = 5,
    val connectTimeoutMs: Int = 15_000,
    val readTimeoutMs: Int = 30_000,
    val bufferSizeBytes: Int = 64 * 1024,
) {
    init {
        require(maxRedirects in 0..10)
        require(connectTimeoutMs > 0)
        require(readTimeoutMs > 0)
        require(bufferSizeBytes in 4 * 1024..1024 * 1024)
    }
}
