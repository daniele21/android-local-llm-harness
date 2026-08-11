package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.SessionId
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

@JvmInline
value class HostClientToken(val value: String) {
    init {
        require(value.isNotBlank()) { "Client token must not be blank" }
    }
}

data class HostQuotas(val maxConnections: Int = 8, val maxSessionsPerConnection: Int = 8, val maxRequestsPerConnection: Int = 16) {
    init {
        require(maxConnections > 0) { "maxConnections must be positive" }
        require(maxSessionsPerConnection > 0) { "maxSessionsPerConnection must be positive" }
        require(maxRequestsPerConnection > 0) { "maxRequestsPerConnection must be positive" }
    }
}

interface HostIdentifierFactory {
    fun newClientToken(): HostClientToken

    fun newRequestId(): RequestId
}

class SecureHostIdentifierFactory : HostIdentifierFactory {
    private val random = SecureRandom()

    override fun newClientToken(): HostClientToken {
        val bytes = ByteArray(CLIENT_TOKEN_BYTES)
        random.nextBytes(bytes)
        return HostClientToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes))
    }

    override fun newRequestId(): RequestId = RequestId(UUID.randomUUID().toString())

    private companion object {
        const val CLIENT_TOKEN_BYTES = 32
    }
}

enum class LedgerFailure {
    CONNECTION_LIMIT,
    CLIENT_TOKEN_INVALID,
    CLIENT_CLOSING,
    SESSION_LIMIT,
    REQUEST_LIMIT,
    DUPLICATE_EXTERNAL_SESSION_ID,
    DUPLICATE_EXTERNAL_REQUEST_ID,
    SESSION_NOT_OWNED,
    REQUEST_NOT_OWNED,
    TOKEN_GENERATION_FAILED,
}

sealed interface LedgerResult<out T> {
    data class Success<T>(val value: T) : LedgerResult<T>

    data class Failure(val reason: LedgerFailure) : LedgerResult<Nothing>
}

data class ClosingResources(val requestIds: List<RequestId>, val sessionIds: List<SessionId>)

class ClientConnectionLedger(
    private val quotas: HostQuotas = HostQuotas(),
    private val identifiers: HostIdentifierFactory = SecureHostIdentifierFactory(),
) {
    private val connections = LinkedHashMap<HostClientToken, Connection>()

    @Synchronized
    fun register(caller: AuthorizedCaller): LedgerResult<HostClientToken> {
        if (connections.size >= quotas.maxConnections) {
            return LedgerResult.Failure(LedgerFailure.CONNECTION_LIMIT)
        }

        repeat(MAX_TOKEN_GENERATION_ATTEMPTS) {
            val token = identifiers.newClientToken()
            if (token !in connections) {
                connections[token] = Connection(caller = caller)
                return LedgerResult.Success(token)
            }
        }
        return LedgerResult.Failure(LedgerFailure.TOKEN_GENERATION_FAILED)
    }

    @Synchronized
    fun registerSession(
        token: HostClientToken,
        caller: AuthorizedCaller,
        externalSessionId: String,
        internalSessionId: SessionId,
    ): LedgerResult<SessionId> {
        val connection = activeConnection(token, caller) ?: return invalidConnection()
        if (connection.closing) {
            return LedgerResult.Failure(LedgerFailure.CLIENT_CLOSING)
        }
        if (connection.sessions.size >= quotas.maxSessionsPerConnection) {
            return LedgerResult.Failure(LedgerFailure.SESSION_LIMIT)
        }
        if (externalSessionId in connection.sessions) {
            return LedgerResult.Failure(LedgerFailure.DUPLICATE_EXTERNAL_SESSION_ID)
        }
        connection.sessions[externalSessionId] = internalSessionId
        return LedgerResult.Success(internalSessionId)
    }

    @Synchronized
    fun sessionId(token: HostClientToken, caller: AuthorizedCaller, externalSessionId: String): LedgerResult<SessionId> {
        val connection = activeConnection(token, caller) ?: return invalidConnection()
        val sessionId = connection.sessions[externalSessionId]
            ?: return LedgerResult.Failure(LedgerFailure.SESSION_NOT_OWNED)
        return LedgerResult.Success(sessionId)
    }

    @Synchronized
    fun removeSession(token: HostClientToken, caller: AuthorizedCaller, externalSessionId: String): LedgerResult<SessionId> {
        val connection = activeConnection(token, caller) ?: return invalidConnection()
        val removed = connection.sessions.remove(externalSessionId)
            ?: return LedgerResult.Failure(LedgerFailure.SESSION_NOT_OWNED)
        return LedgerResult.Success(removed)
    }

    @Synchronized
    fun allocateRequest(token: HostClientToken, caller: AuthorizedCaller, externalRequestId: String): LedgerResult<RequestId> {
        val connection = activeConnection(token, caller) ?: return invalidConnection()
        if (connection.closing) {
            return LedgerResult.Failure(LedgerFailure.CLIENT_CLOSING)
        }
        if (connection.requests.size >= quotas.maxRequestsPerConnection) {
            return LedgerResult.Failure(LedgerFailure.REQUEST_LIMIT)
        }
        if (externalRequestId in connection.requests) {
            return LedgerResult.Failure(LedgerFailure.DUPLICATE_EXTERNAL_REQUEST_ID)
        }
        val requestId = identifiers.newRequestId()
        connection.requests[externalRequestId] = requestId
        return LedgerResult.Success(requestId)
    }

    @Synchronized
    fun requestId(token: HostClientToken, caller: AuthorizedCaller, externalRequestId: String): LedgerResult<RequestId> {
        val connection = activeConnection(token, caller) ?: return invalidConnection()
        val requestId = connection.requests[externalRequestId]
            ?: return LedgerResult.Failure(LedgerFailure.REQUEST_NOT_OWNED)
        return LedgerResult.Success(requestId)
    }

    @Synchronized
    fun removeRequest(token: HostClientToken, caller: AuthorizedCaller, externalRequestId: String): LedgerResult<RequestId> {
        val connection = activeConnection(token, caller) ?: return invalidConnection()
        val removed = connection.requests.remove(externalRequestId)
            ?: return LedgerResult.Failure(LedgerFailure.REQUEST_NOT_OWNED)
        return LedgerResult.Success(removed)
    }

    @Synchronized
    fun beginClose(token: HostClientToken, caller: AuthorizedCaller): LedgerResult<ClosingResources> {
        val connection = activeConnection(token, caller) ?: return invalidConnection()
        connection.closing = true
        return LedgerResult.Success(
            ClosingResources(
                requestIds = connection.requests.values.toList(),
                sessionIds = connection.sessions.values.toList(),
            ),
        )
    }

    @Synchronized
    fun finishClose(token: HostClientToken, caller: AuthorizedCaller): LedgerResult<Unit> {
        activeConnection(token, caller) ?: return invalidConnection()
        connections.remove(token)
        return LedgerResult.Success(Unit)
    }

    private fun activeConnection(token: HostClientToken, caller: AuthorizedCaller): Connection? =
        connections[token]?.takeIf { it.caller.uid == caller.uid && it.caller.packageName == caller.packageName }

    private fun invalidConnection(): LedgerResult.Failure = LedgerResult.Failure(LedgerFailure.CLIENT_TOKEN_INVALID)

    private data class Connection(
        val caller: AuthorizedCaller,
        val sessions: LinkedHashMap<String, SessionId> = LinkedHashMap(),
        val requests: LinkedHashMap<String, RequestId> = LinkedHashMap(),
        var closing: Boolean = false,
    )

    private companion object {
        const val MAX_TOKEN_GENERATION_ATTEMPTS = 8
    }
}
