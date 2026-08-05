package io.github.daniele21.localllm.download

import io.github.daniele21.localllm.catalog.CatalogGgufArtifact
import java.io.File
import java.io.IOException
import java.net.URI

internal class SecureArtifactTransfer(
    private val transport: DownloadTransport,
    policy: SecureDownloadPolicy,
) {
    private val uriPolicy = DownloadUriPolicy(policy)
    private val fileWriter = ArtifactFileWriter(policy)
    private val connectTimeoutMs = policy.connectTimeoutMs
    private val readTimeoutMs = policy.readTimeoutMs
    private val maxRedirects = policy.maxRedirects

    fun transfer(
        artifact: CatalogGgufArtifact,
        partFile: File,
        request: DownloadRequest,
    ): ArtifactTransferResult =
        when (val initial = uriPolicy.validateInitial(artifact.downloadUri)) {
            is UriValidation.Failure -> ArtifactTransferResult.Failure(initial.error)
            is UriValidation.Success -> runTransfer(initial.uri, artifact, partFile, request)
        }

    private fun runTransfer(
        initialUri: URI,
        artifact: CatalogGgufArtifact,
        partFile: File,
        request: DownloadRequest,
    ): ArtifactTransferResult {
        var state: TransferState = TransferState.Continue(initialUri, redirectCount = 0)
        while (state is TransferState.Continue) {
            state = executeStep(state, artifact, partFile, request)
        }
        return (state as TransferState.Complete).result
    }

    private fun executeStep(
        state: TransferState.Continue,
        artifact: CatalogGgufArtifact,
        partFile: File,
        request: DownloadRequest,
    ): TransferState {
        if (request.cancellation.isCancelled()) {
            return TransferState.Complete(cancelled())
        }
        return when (val response = open(state.uri)) {
            is OpenResponse.Failure ->
                TransferState.Complete(ArtifactTransferResult.Failure(response.error))
            is OpenResponse.Success ->
                response.response.use { opened ->
                    inspectResponse(state, opened, artifact, partFile, request)
                }
        }
    }

    private fun inspectResponse(
        state: TransferState.Continue,
        response: TransportResponse,
        artifact: CatalogGgufArtifact,
        partFile: File,
        request: DownloadRequest,
    ): TransferState =
        when {
            response.statusCode in REDIRECT_STATUS_CODES ->
                redirectState(state, response.redirectLocation)
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
            else ->
                TransferState.Complete(
                    fileWriter.copyAndVerify(response, artifact, partFile, request),
                )
        }

    private fun redirectState(
        state: TransferState.Continue,
        location: String?,
    ): TransferState {
        if (state.redirectCount >= maxRedirects) {
            return completeFailure(
                DownloadErrorCode.REDIRECT_LIMIT_EXCEEDED,
                "Redirect limit exceeded",
            )
        }
        return when (val target = uriPolicy.resolveRedirect(state.uri, location)) {
            null ->
                completeFailure(
                    DownloadErrorCode.INVALID_REDIRECT,
                    "Redirect target is missing or not allowed",
                )
            else -> TransferState.Continue(target, state.redirectCount + 1)
        }
    }

    private fun open(uri: URI): OpenResponse =
        try {
            OpenResponse.Success(
                transport.open(
                    TransportRequest(
                        uri = uri,
                        connectTimeoutMs = connectTimeoutMs,
                        readTimeoutMs = readTimeoutMs,
                    ),
                ),
            )
        } catch (error: IOException) {
            OpenResponse.Failure(
                DownloadError(
                    DownloadErrorCode.IO_FAILURE,
                    error.message ?: "Transport failure",
                ),
            )
        }

    private fun completeFailure(
        code: DownloadErrorCode,
        detail: String,
    ): TransferState.Complete =
        TransferState.Complete(
            ArtifactTransferResult.Failure(DownloadError(code, detail)),
        )

    private fun cancelled(): ArtifactTransferResult.Failure =
        ArtifactTransferResult.Failure(
            DownloadError(DownloadErrorCode.CANCELLED, "Download cancelled"),
        )

    private sealed interface TransferState {
        data class Continue(
            val uri: URI,
            val redirectCount: Int,
        ) : TransferState

        data class Complete(val result: ArtifactTransferResult) : TransferState
    }

    private sealed interface OpenResponse {
        data class Success(val response: TransportResponse) : OpenResponse

        data class Failure(val error: DownloadError) : OpenResponse
    }

    companion object {
        private val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)
    }
}

internal sealed interface ArtifactTransferResult {
    data class Success(val bytes: Long) : ArtifactTransferResult

    data class Failure(val error: DownloadError) : ArtifactTransferResult
}
