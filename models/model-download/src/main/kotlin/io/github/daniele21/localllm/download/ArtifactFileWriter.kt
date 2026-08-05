package io.github.daniele21.localllm.download

import io.github.daniele21.localllm.catalog.CatalogGgufArtifact
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest

internal class ArtifactFileWriter(private val policy: SecureDownloadPolicy) {
    fun copyAndVerify(
        response: TransportResponse,
        artifact: CatalogGgufArtifact,
        partFile: File,
        request: DownloadRequest,
    ): ArtifactTransferResult = when (val copied = copy(response, artifact, partFile, request)) {
        is CopyResult.Failure -> ArtifactTransferResult.Failure(copied.error)
        is CopyResult.Success -> verify(copied, artifact)
    }

    private fun copy(response: TransportResponse, artifact: CatalogGgufArtifact, partFile: File, request: DownloadRequest): CopyResult =
        try {
            FileOutputStream(partFile).use { output ->
                val result = stream(response, output, artifact, request)
                output.fd.sync()
                result
            }
        } catch (error: IOException) {
            CopyResult.Failure(
                DownloadError(
                    DownloadErrorCode.IO_FAILURE,
                    error.message ?: "Download I/O failure",
                ),
            )
        }

    private fun stream(
        response: TransportResponse,
        output: OutputStream,
        artifact: CatalogGgufArtifact,
        request: DownloadRequest,
    ): CopyResult {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(policy.bufferSizeBytes)
        var total = 0L
        while (true) {
            if (request.cancellation.isCancelled()) {
                return failure(DownloadErrorCode.CANCELLED, "Download cancelled")
            }
            val read = response.body.read(buffer)
            if (read < 0) {
                return CopyResult.Success(total, digest.digest().toHex())
            }
            total += read
            if (total > artifact.sizeBytes) {
                return failure(
                    DownloadErrorCode.SIZE_LIMIT_EXCEEDED,
                    "Downloaded bytes exceed catalog size",
                )
            }
            digest.update(buffer, 0, read)
            output.write(buffer, 0, read)
            request.progressListener.onProgress(
                DownloadProgress(total, artifact.sizeBytes),
            )
        }
    }

    private fun verify(copied: CopyResult.Success, artifact: CatalogGgufArtifact): ArtifactTransferResult = when {
        copied.bytes != artifact.sizeBytes ->
            ArtifactTransferResult.Failure(
                DownloadError(
                    DownloadErrorCode.CONTENT_LENGTH_MISMATCH,
                    "Downloaded byte count does not match catalog size",
                ),
            )

        !copied.sha256.equals(artifact.digest.sha256, ignoreCase = true) ->
            ArtifactTransferResult.Failure(
                DownloadError(
                    DownloadErrorCode.DIGEST_MISMATCH,
                    "SHA-256 mismatch",
                ),
            )

        else -> ArtifactTransferResult.Success(copied.bytes)
    }

    private fun failure(code: DownloadErrorCode, detail: String): CopyResult.Failure = CopyResult.Failure(DownloadError(code, detail))

    private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }

    private sealed interface CopyResult {
        data class Success(val bytes: Long, val sha256: String) : CopyResult

        data class Failure(val error: DownloadError) : CopyResult
    }
}
