package io.github.daniele21.localllm.download

import io.github.daniele21.localllm.catalog.CatalogGgufArtifact
import io.github.daniele21.localllm.models.ArtifactSource
import io.github.daniele21.localllm.models.GgufArtifact
import io.github.daniele21.localllm.store.ModelImportException
import io.github.daniele21.localllm.store.ModelStore
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import java.security.MessageDigest

class SecureModelDownloader(
    private val workingDirectory: File,
    private val transport: DownloadTransport,
    private val modelStore: ModelStore,
    private val policy: SecureDownloadPolicy,
) {
    fun download(request: DownloadRequest): DownloadResult {
        val catalogArtifact = request.release.artifact
        modelStore.find(catalogArtifact.digest)?.let { return DownloadResult.AlreadyInstalled(it) }
        val validatedUri = validateUri(catalogArtifact.downloadUri) ?: return invalidUri()
        if (!policy.hostPolicy.isAllowed(validatedUri.host)) {
            return failure(DownloadErrorCode.HOST_NOT_ALLOWED, "Download host is not allowlisted")
        }
        if (!workingDirectory.exists() && !workingDirectory.mkdirs()) {
            return failure(DownloadErrorCode.IO_FAILURE, "Unable to create download directory")
        }
        val partFile = File(workingDirectory, "${catalogArtifact.digest.sha256.lowercase()}.part")
        return try {
            partFile.delete()
            val transfer = transfer(validatedUri, catalogArtifact, partFile, request)
            when (transfer) {
                is TransferResult.Failure -> DownloadResult.Failed(transfer.error)
                is TransferResult.Success -> importVerified(partFile, catalogArtifact, transfer.bytes)
            }
        } finally {
            partFile.delete()
        }
    }

    private fun transfer(
        initialUri: URI,
        artifact: CatalogGgufArtifact,
        partFile: File,
        request: DownloadRequest,
    ): TransferResult {
        var current = initialUri
        var redirects = 0
        while (true) {
            if (request.cancellation.isCancelled()) return cancelled()
            val response =
                try {
                    transport.open(
                        TransportRequest(
                            uri = current,
                            connectTimeoutMs = policy.connectTimeoutMs,
                            readTimeoutMs = policy.readTimeoutMs,
                        ),
                    )
                } catch (error: IOException) {
                    return transferFailure(DownloadErrorCode.IO_FAILURE, error.message ?: "Transport failure")
                }
            response.use {
                if (response.statusCode in REDIRECT_STATUS_CODES) {
                    if (redirects >= policy.maxRedirects) {
                        return transferFailure(
                            DownloadErrorCode.REDIRECT_LIMIT_EXCEEDED,
                            "Redirect limit exceeded",
                        )
                    }
                    current = resolveRedirect(current, response.redirectLocation) ?: return invalidRedirect()
                    redirects += 1
                    continue
                }
                if (response.statusCode !in 200..299) {
                    return transferFailure(
                        DownloadErrorCode.HTTP_FAILURE,
                        "Unexpected HTTP status ${response.statusCode}",
                    )
                }
                val declaredLength = response.contentLength
                if (declaredLength != null && declaredLength != artifact.sizeBytes) {
                    return transferFailure(
                        DownloadErrorCode.CONTENT_LENGTH_MISMATCH,
                        "Declared content length does not match catalog size",
                    )
                }
                return copyAndVerify(response, artifact, partFile, request)
            }
        }
    }

    private fun copyAndVerify(
        response: TransportResponse,
        artifact: CatalogGgufArtifact,
        partFile: File,
        request: DownloadRequest,
    ): TransferResult {
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        val buffer = ByteArray(policy.bufferSizeBytes)
        return try {
            FileOutputStream(partFile).use { output ->
                while (true) {
                    if (request.cancellation.isCancelled()) return cancelled()
                    val read = response.body.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > artifact.sizeBytes) {
                        return transferFailure(
                            DownloadErrorCode.SIZE_LIMIT_EXCEEDED,
                            "Downloaded bytes exceed catalog size",
                        )
                    }
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                    request.progressListener.onProgress(DownloadProgress(total, artifact.sizeBytes))
                }
                output.fd.sync()
            }
            if (total != artifact.sizeBytes) {
                return transferFailure(
                    DownloadErrorCode.CONTENT_LENGTH_MISMATCH,
                    "Downloaded byte count does not match catalog size",
                )
            }
            val actualDigest = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
            if (!actualDigest.equals(artifact.digest.sha256, ignoreCase = true)) {
                return transferFailure(DownloadErrorCode.DIGEST_MISMATCH, "SHA-256 mismatch")
            }
            TransferResult.Success(total)
        } catch (error: IOException) {
            transferFailure(DownloadErrorCode.IO_FAILURE, error.message ?: "Download I/O failure")
        }
    }

    private fun importVerified(
        partFile: File,
        artifact: CatalogGgufArtifact,
        downloadedBytes: Long,
    ): DownloadResult =
        try {
            val stored = modelStore.import(partFile, artifact.toStoreArtifact())
            DownloadResult.Installed(stored, downloadedBytes)
        } catch (error: ModelImportException) {
            failure(DownloadErrorCode.STORE_FAILURE, error.message ?: error.code.name)
        } catch (error: IllegalStateException) {
            failure(DownloadErrorCode.STORE_FAILURE, error.message ?: "Model store failure")
        }

    private fun CatalogGgufArtifact.toStoreArtifact() =
        GgufArtifact(
            digest = digest,
            fileName = fileName,
            sizeBytes = sizeBytes,
            architecture = architecture,
            quantization = quantization,
            source = ArtifactSource.Download(downloadUri.toString()),
        )

    private fun resolveRedirect(base: URI, location: String?): URI? {
        if (location.isNullOrBlank()) return null
        val resolved = runCatching { base.resolve(location) }.getOrNull() ?: return null
        val valid = validateUri(resolved) ?: return null
        return valid.takeIf { policy.hostPolicy.isAllowed(it.host) }
    }

    private fun validateUri(uri: URI): URI? {
        val host = uri.host ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.userInfo != null || uri.fragment != null || host.isBlank()) return null
        if (uri.port !in setOf(-1, 443)) return null
        return uri.normalize()
    }

    private fun invalidUri() = failure(DownloadErrorCode.INVALID_URI, "Only canonical HTTPS URIs are allowed")

    private fun invalidRedirect() =
        transferFailure(DownloadErrorCode.INVALID_REDIRECT, "Redirect target is missing or not allowed")

    private fun cancelled() = transferFailure(DownloadErrorCode.CANCELLED, "Download cancelled")

    private fun failure(code: DownloadErrorCode, detail: String) = DownloadResult.Failed(DownloadError(code, detail))

    private fun transferFailure(code: DownloadErrorCode, detail: String) =
        TransferResult.Failure(DownloadError(code, detail))

    private sealed interface TransferResult {
        data class Success(val bytes: Long) : TransferResult

        data class Failure(val error: DownloadError) : TransferResult
    }

    companion object {
        private val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)
    }
}
