package io.github.daniele21.localllm.download

import java.net.IDN
import java.net.URI
import java.util.Locale

data class AllowedSourceHost(val host: String, val includeSubdomains: Boolean = false)

sealed interface SourcePolicyResult {
    data class Allowed(val uri: URI, val normalizedHost: String, val port: Int) : SourcePolicyResult
    data class Rejected(val reason: SourcePolicyRejection, val detail: String) : SourcePolicyResult
}

enum class SourcePolicyRejection {
    URI_NOT_ABSOLUTE,
    HTTPS_REQUIRED,
    HOST_MISSING,
    HOST_INVALID,
    HOST_NOT_ALLOWED,
    IP_LITERAL_REJECTED,
    LOCAL_HOST_REJECTED,
    USER_INFO_REJECTED,
    FRAGMENT_REJECTED,
    PORT_REJECTED,
}

fun interface ArtifactSourcePolicy {
    fun validate(uri: URI): SourcePolicyResult
}

class AllowlistedHttpsSourcePolicy(allowedHosts: Set<AllowedSourceHost>, private val allowedPorts: Set<Int> = setOf(443)) :
    ArtifactSourcePolicy {
    private val normalizedAllowedHosts = allowedHosts.map(::normalizeAllowedHost)

    init {
        require(normalizedAllowedHosts.isNotEmpty())
        require(allowedPorts.isNotEmpty())
        require(allowedPorts.all { it in 1..65_535 })
    }

    @Suppress("ReturnCount")
    override fun validate(uri: URI): SourcePolicyResult {
        if (!uri.isAbsolute) return rejected(SourcePolicyRejection.URI_NOT_ABSOLUTE)
        if (!uri.scheme.equals(HTTPS, ignoreCase = true)) return rejected(SourcePolicyRejection.HTTPS_REQUIRED)
        if (uri.rawUserInfo != null) return rejected(SourcePolicyRejection.USER_INFO_REJECTED)
        if (uri.rawFragment != null) return rejected(SourcePolicyRejection.FRAGMENT_REJECTED)

        val rawHost = uri.host ?: return rejected(SourcePolicyRejection.HOST_MISSING)
        val host = normalizeHost(rawHost) ?: return rejected(SourcePolicyRejection.HOST_INVALID)
        if (isIpLiteral(host)) return rejected(SourcePolicyRejection.IP_LITERAL_REJECTED)
        if (isLocalHost(host)) return rejected(SourcePolicyRejection.LOCAL_HOST_REJECTED)

        val port = if (uri.port == -1) DEFAULT_HTTPS_PORT else uri.port
        if (port !in allowedPorts) return rejected(SourcePolicyRejection.PORT_REJECTED)
        if (normalizedAllowedHosts.none { it.matches(host) }) {
            return rejected(SourcePolicyRejection.HOST_NOT_ALLOWED, host)
        }
        return SourcePolicyResult.Allowed(uri.normalize(), host, port)
    }

    private fun normalizeAllowedHost(value: AllowedSourceHost): NormalizedAllowedHost {
        val normalized = normalizeHost(value.host)
        requireNotNull(normalized) { "Allowed host is invalid" }
        require(!isIpLiteral(normalized)) { "IP literals cannot be allowlisted" }
        require(!isLocalHost(normalized)) { "Local hosts cannot be allowlisted" }
        return NormalizedAllowedHost(normalized, value.includeSubdomains)
    }

    private data class NormalizedAllowedHost(val host: String, val includeSubdomains: Boolean) {
        fun matches(candidate: String): Boolean = candidate == host ||
            (includeSubdomains && candidate.endsWith(".$host"))
    }

    private companion object {
        const val HTTPS = "https"
        const val DEFAULT_HTTPS_PORT = 443

        fun rejected(reason: SourcePolicyRejection, detail: String = reason.name): SourcePolicyResult.Rejected =
            SourcePolicyResult.Rejected(reason, detail)

        fun normalizeHost(raw: String): String? = try {
            IDN.toASCII(raw.trim().trimEnd('.'), IDN.USE_STD3_ASCII_RULES)
                .lowercase(Locale.ROOT)
                .takeIf { it.isNotBlank() && it.length <= 253 }
        } catch (_: IllegalArgumentException) {
            null
        }

        fun isLocalHost(host: String): Boolean = host == "localhost" ||
            host.endsWith(".localhost") ||
            host.endsWith(".local")

        fun isIpLiteral(host: String): Boolean = host.contains(':') || IPV4.matches(host)

        val IPV4 = Regex("^(?:\\d{1,3}\\.){3}\\d{1,3}$")
    }
}
