package io.github.daniele21.localllm.download

import java.net.URI

internal class DownloadUriPolicy(private val policy: SecureDownloadPolicy) {
    fun validateInitial(uri: URI): UriValidation = validateUri(uri)?.let { validated ->
        if (policy.hostPolicy.isAllowed(validated.host)) {
            UriValidation.Success(validated)
        } else {
            UriValidation.Failure(
                DownloadError(
                    DownloadErrorCode.HOST_NOT_ALLOWED,
                    "Download host is not allowlisted",
                ),
            )
        }
    } ?: UriValidation.Failure(
        DownloadError(
            DownloadErrorCode.INVALID_URI,
            "Only canonical HTTPS URIs are allowed",
        ),
    )

    fun resolveRedirect(base: URI, location: String?): URI? = location
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
}

internal sealed interface UriValidation {
    data class Success(val uri: URI) : UriValidation

    data class Failure(val error: DownloadError) : UriValidation
}
