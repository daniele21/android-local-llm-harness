package io.github.daniele21.localllm.download

import java.io.InputStream
import java.net.HttpURLConnection

class HttpUrlConnectionDownloadTransport : DownloadTransport {
    override fun open(request: TransportRequest): TransportResponse {
        val connection = request.uri.toURL().openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = request.connectTimeoutMs
        connection.readTimeout = request.readTimeoutMs
        connection.requestMethod = "GET"
        connection.useCaches = false
        connection.setRequestProperty("Accept-Encoding", "identity")
        connection.connect()
        return HttpResponse(connection)
    }

    private class HttpResponse(private val connection: HttpURLConnection) : TransportResponse {
        override val statusCode: Int = connection.responseCode
        override val contentLength: Long? = connection.contentLengthLong.takeIf { it >= 0 }
        override val redirectLocation: String? = connection.getHeaderField("Location")
        override val body: InputStream by lazy {
            if (statusCode in 200..299) connection.inputStream else connection.errorStream ?: InputStream.nullInputStream()
        }

        override fun close() {
            runCatching { body.close() }
            connection.disconnect()
        }
    }
}

class AllowlistedDownloadHosts(hosts: Set<String>) : DownloadHostPolicy {
    private val canonicalHosts = hosts.mapTo(linkedSetOf()) { it.lowercase() }

    init {
        require(canonicalHosts.isNotEmpty())
        require(canonicalHosts.all { host -> host.isNotBlank() && '/' !in host && ':' !in host })
    }

    override fun isAllowed(host: String): Boolean = host.lowercase() in canonicalHosts
}
