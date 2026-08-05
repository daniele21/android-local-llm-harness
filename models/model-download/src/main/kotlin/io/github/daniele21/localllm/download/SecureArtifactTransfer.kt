package io.github.daniele21.localllm.download

import io.github.daniele21.localllm.catalog.CatalogGgufArtifact
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import java.security.MessageDigest

internal class SecureArtifactTransfer(private val transport: DownloadTransport, private val policy: SecureDownloadPolicy) {
    fun transfer(artifact: CatalogGgufArtifact, partFile: File, request: DownloadRequest): ArtifactTransferResult =
        when (val initial = validateInitialUri(artifact.downloadUri)) {
            is UriValidation.Failure -> ArtifactTransferResult.Failure(initial.error)
            is UriValidation.Success -> followResponses(initial.uri, artifact, partFile, request)
        }

    private fun followResponses(
        initialUri: URI,
        artifact: CatalogGgufArtifact,
        partFile: File,
        request: DownloadRequest,
    ): ArtifactTransferResult {
        var currentUri = initialUri
        var redirectCount = 0
        var result: ArtifactTransferResult? = null
        while (result == null) {
            if (request.cancellation.isCancelled()) {
                result = cancelled()
            } else {
                when (val response = open(currentUri)) {
                    is OpenResponse.Failure -> result = ArtifactTransferResult.Failure(response.error)

                    is OpenResponse.Success ->
                        response.response.use { opened ->
                            when (val decision = inspect(currentUri, redirectCount, opened, artifact, partFile, request)) {
                                is ResponseDecision.Complete -> result = decision.result

                                is ResponseDecision.Redirect -> {
                                    currentUri = decision.uri
                                    redirectCount += 1
                                }
                            }
                        }
                }
            }
        }
        return result
    }

    private fun open(uri: URI): OpenResponse = try {
        OpenResponse.Success(
            transport.open(
                TransportRequest(
                    uri = uri,
                    connectTimeoutMs = policy.connectTimeoutMs,
                    readTimeoutMs = policy.readTimeoutMs,
                ),
            ),
        )
    } catch (error: IOException) {
        OpenResponse.Failure(
            DownloadError(DownloadErrorCode.IO_FAILURE, error.message ?: "Transport failure"),
        )
    }

    private fun inspect(
        currentUri: URI,
        redirectCount: Int,
        response: TransportResponse,
        artifact: CatalogGgufArtifact,
        partFile: File,
        request: DownloadRequest,
    ): ResponseDecision = when {
        response.statusCode in REDIRECT_STATUS_CODES ->
            redirectDecision(currentUri, redirectCount, response.redirectLocation)

        response.statusCode !in 200..299 ->
            completeFailure(
                DownloadErrorCode.HTTP_FAILURE,
                "Unexpected HTTP status ${response.statusCode}",
            )

        response.contentLength?.let { it != artifact.sizeBytes } == true ->
            completeFailure(
                DownloadErrorCode.CONTENT_LENGTH_MISMATCH,
                "Declared content length does not match catalog size",
            )

        else -> ResponseDecision.Complete(copyAndVerify(response, artifact, partFile, request))
    }

    private fun redirectDecision(currentUri: URI, redirectCount: Int, location: String?): ResponseDecision {
        val limitExceeded = redirectCount >= policy.maxRedirects
        val target = if (limitExceeded) null else resolveRedirect(currentUri, location)
        return when {
            limitExceeded ->
                completeFailure(
                    DownloadErrorCode.REDIRECT_LIMIT_EXCEEDED,
                    "Redirect limit exceeded",
                )

            target == null ->
                completeFailure(
                    DownloadErrorCode.INVALID_REDIRECT,
                    "Redirect target is missing or not allowed",
                )

            else -> ResponseDecision.Redirect(target)
        }
    }

    private fun copyAndVerify(
        response: TransportResponse,
        artifact: CatalogGgufArtifact,
        partFile: File,
        request: DownloadRequest,
    ): ArtifactTransferResult = when (val copied = copy(response, artifact, partFile, request)) {
        is CopyResult.Failure -> ArtifactTransferResult.Failure(copied.error)
        is CopyResult.Success -> verifyCopy(copied, artifact)
    }

    private fun copy(response: TransportResponse, artifact: CatalogGgufArtifact, partFile: File, request: DownloadRequest): CopyResult {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(policy.bufferSizeBytes)
        var total = 0L
        var failure: DownloadError? = null
        try {
            FileOutputStream(partFile).use { output ->
                while (failure == null) {
                    failure = cancellationError(request)
                    if (failure == null) {
                        val read = response.body.read(buffer)
                        if (read < 0) break
                        total += read
                        failure = sizeError(total, artifact.sizeBytes)
                        if (failure == null) {
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                            request.progressListener.onProgress(
                                DownloadProgress(total, artifact.sizeBytes),
                            )
                        }
                    }
                }
                output.fd.sync()
            }
        } catch (error: IOException) {
            failure = DownloadError(DownloadErrorCode.IO_FAILURE, error.message ?: "Download I/O failure")
        }
        return failure?.let(CopyResult::Failure)
            ?: CopyResult.Success(total, digest.digest().toHex())
    }

    private fun verifyCopy(copied: CopyResult.Success, artifact: CatalogGgufArtifact): ArtifactTransferResult = when {
        copied.bytes != artifact.sizeBytes ->
            transferFailure(
                DownloadErrorCode.CONTENT_LENGTH_MISMATCH,
                "Downloaded byte count does not match catalog size",
            )

        !copied.sha256.equals(artifact.digest.sha256, ignoreCase = true) ->
            transferFailure(DownloadErrorCode.DIGEST_MISMATCH, "SHA-256 mismatch")

        else -> ArtifactTransferResult.Success(copied.bytes)
    }

    private fun validateInitialUri(uri: URI): UriValidation = validateUri(uri)?.let { validated ->
        if (policy.hostPolicy.isAllowed(validated.host)) {
            UriValidation.Success(validated)
        } else {
            UriValidation.Failure(
                DownloadError(DownloadErrorCode.HOST_NOT_ALLOWED, "Download host is not allowlisted"),
            )
        }
    } ?: UriValidation.Failure(
        DownloadError(DownloadErrorCode.INVALID_URI, "Only canonical HTTPS URIs are allowed"),
    )

    private fun resolveRedirect(base: URI, location: String?): URI? = location
        ?.takeIf(String::isNotBlank)
        ?.let { runCatching { base.resolve(it) }.getOrNull() }
        ?.let(::validateUri)
        ?.takeIf { policy.hostPolicy.isAllowed(it.host) }

    private fun validateUri(uri: URI): URI? = uri.normalize().takeIf { normalized ->
        normalized.scheme.equals("https", ignoreCase = true) &&
            !normalized.host.isNullOrBlank() &&
            normalized.userInfo == null &&
            normalized.fragment == null &&
            normalized.port in setOf(-1, 443)
    }

    private fun cancellationError(request: DownloadRequest): DownloadError? = if (request.cancellation.isCancelled()) {
        DownloadError(DownloadErrorCode.CANCELLED, "Download cancelled")
    } else {
        null
    }

    private fun sizeError(downloadedBytes: Long, expectedBytes: Long): DownloadError? = if (downloadedBytes > expectedBytes) {
        DownloadError(DownloadErrorCode.SIZE_LIMIT_EXCEEDED, "Downloaded bytes exceed catalog size")
    } else {
        null
    }

    private fun completeFailure(code: DownloadErrorCode, detail: String) = ResponseDecision.Complete(transferFailure(code, detail))

    private fun transferFailure(code: DownloadErrorCode, detail: String) = ArtifactTransferResult.Failure(DownloadError(code, detail))

    private fun cancelled() = transferFailure(DownloadErrorCode.CANCELLED, "Download cancelled")

    private fun ByteArray.toHex() = joinToString("") { byte -> "%02x".format(byte) }

    private sealed interface UriValidation {
        data class Success(val uri: URI) : UriValidation

        data class Failure(val error: DownloadError) : UriValidation
    }

    private sealed interface OpenResponse {
        data class Success(val response: TransportResponse) : OpenResponse

        data class Failure(val error: DownloadError) : OpenResponse
    }

    private sealed interface ResponseDecision {
        data class Redirect(val uri: URI) : ResponseDecision

        data class Complete(val result: ArtifactTransferResult) : ResponseDecision
    }

    private sealed interface CopyResult {
        data class Success(val bytes: Long, val sha256: String) : CopyResult

        data class Failure(val error: DownloadError) : CopyResult
    }

    companion object {
        private val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)
    }
}

internal sealed interface ArtifactTransferResult {
    data class Success(val bytes: Long) : ArtifactTransferResult

    data class Failure(val error: DownloadError) : ArtifactTransferResult
}
