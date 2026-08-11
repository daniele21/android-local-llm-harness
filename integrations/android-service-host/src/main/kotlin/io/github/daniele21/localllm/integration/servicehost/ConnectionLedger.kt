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

private class ClientConnectionState(val caller: AuthorizedCaller) {
    private val sessions = LinkedHashMap<String, SessionId>()
    private val requests = LinkedHashMap<String, RequestId>()
    private var closing = false

    fun matches(candidate: AuthorizedCaller): Boolean = caller.uid == candidate.uid && caller.packageName == candidate.packageName

    fun registerSession(externalSessionId: String, internalSessionId: SessionId, maxSessions: Int): LedgerResult<SessionId> = when {
        closing -> LedgerResult.Failure(LedgerFailure.CLIENT_CLOSING)

        sessions.size >= maxSessions -> LedgerResult.Failure(LedgerFailure.SESSION_LIMIT)

        externalSessionId in sessions -> LedgerResult.Failure(LedgerFailure.DUPLICATE_EXTERNAL_SESSION_ID)

        else -> {
            sessions[externalSessionId] = internalSessionId
            LedgerResult.Success(internalSessionId)
        }
    }

    fun sessionId(externalSessionId: String): LedgerResult<SessionId> {
        val sessionId = sessions[externalSessionId]
            ?: return LedgerResult.Failure(LedgerFailure.SESSION_NOT_OWNED)
        return LedgerResult.Success(sessionId)
    }

    fun removeSession(externalSessionId: String): LedgerResult<SessionId> {
        val removed = sessions.remove(externalSessionId)
            ?: return LedgerResult.Failure(LedgerFailure.SESSION_NOT_OWNED)
        return LedgerResult.Success(removed)
    }

    fun allocateRequest(externalRequestId: String, requestId: RequestId, maxRequests: Int): LedgerResult<RequestId> = when {
        closing -> LedgerResult.Failure(LedgerFailure.CLIENT_CLOSING)

        requests.size >= maxRequests -> LedgerResult.Failure(LedgerFailure.REQUEST_LIMIT)

        externalRequestId in requests -> LedgerResult.Failure(LedgerFailure.DUPLICATE_EXTERNAL_REQUEST_ID)

        else -> {
            requests[externalRequestId] = requestId
            LedgerResult.Success(requestId)
        }
    }

    fun requestId(externalRequestId: String): LedgerResult<RequestId> {
        val requestId = requests[externalRequestId]
            ?: return LedgerResult.Failure(LedgerFailure.REQUEST_NOT_OWNED)
        return LedgerResult.Success(requestId)
    }

    fun removeRequest(externalRequestId: String): LedgerResult<RequestId> {
        val removed = requests.remove(externalRequestId)
            ?: return LedgerResult.Failure(LedgerFailure.REQUEST_NOT_OWNED)
        return LedgerResult.Success(removed)
    }

    fun beginClose(): ClosingResources {
        closing = true
        return ClosingResources(
            requestIds = requests.values.toList(),
            sessionIds = sessions.values.toList(),
        )
    }
}

class ClientConnectionLedger(
    private val quotas: HostQuotas = HostQuotas(),
    private val identifiers: HostIdentifierFactory = SecureHostIdentifierFactory(),
) {
    private val connections = LinkedHashMap<HostClientToken, ClientConnectionState>()

    @Synchronized
    fun register(caller: AuthorizedCaller): LedgerResult<HostClientToken> {
        if (connections.size >= quotas.maxConnections) {
            return LedgerResult.Failure(LedgerFailure.CONNECTION_LIMIT)
        }

        repeat(MAX_TOKEN_GENERATION_ATTEMPTS) {
            val token = identifiers.newClientToken()
            if (token !in connections) {
                connections[token] = ClientConnectionState(caller = caller)
                return LedgerResult.Success(token)
            }
        }
        return LedgerResult.Failure(LedgerFailure.TOKEN_GENERATION_FAILED)
    }

    @Synchronized
    fun validateConnection(token: HostClientToken, caller: AuthorizedCaller): LedgerResult<Unit> =
        if (connections.activeConnection(token, caller) != null) LedgerResult.Success(Unit) else invalidConnection()

    @Synchronized
    fun registerSession(
        token: HostClientToken,
        caller: AuthorizedCaller,
        externalSessionId: String,
        internalSessionId: SessionId,
    ): LedgerResult<SessionId> {
        val connection = connections.activeConnection(token, caller) ?: return invalidConnection()
        return connection.registerSession(externalSessionId, internalSessionId, quotas.maxSessionsPerConnection)
    }

    @Synchronized
    fun sessionId(token: HostClientToken, caller: AuthorizedCaller, externalSessionId: String): LedgerResult<SessionId> {
        val connection = connections.activeConnection(token, caller) ?: return invalidConnection()
        return connection.sessionId(externalSessionId)
    }

    @Synchronized
    fun removeSession(token: HostClientToken, caller: AuthorizedCaller, externalSessionId: String): LedgerResult<SessionId> {
        val connection = connections.activeConnection(token, caller) ?: return invalidConnection()
        return connection.removeSession(externalSessionId)
    }

    @Synchronized
    fun allocateRequest(token: HostClientToken, caller: AuthorizedCaller, externalRequestId: String): LedgerResult<RequestId> {
        val connection = connections.activeConnection(token, caller) ?: return invalidConnection()
        return connection.allocateRequest(externalRequestId, identifiers.newRequestId(), quotas.maxRequestsPerConnection)
    }

    @Synchronized
    fun requestId(token: HostClientToken, caller: AuthorizedCaller, externalRequestId: String): LedgerResult<RequestId> {
        val connection = connections.activeConnection(token, caller) ?: return invalidConnection()
        return connection.requestId(externalRequestId)
    }

    @Synchronized
    fun removeRequest(token: HostClientToken, caller: AuthorizedCaller, externalRequestId: String): LedgerResult<RequestId> {
        val connection = connections.activeConnection(token, caller) ?: return invalidConnection()
        return connection.removeRequest(externalRequestId)
    }

    @Synchronized
    fun beginClose(token: HostClientToken, caller: AuthorizedCaller): LedgerResult<ClosingResources> {
        val connection = connections.activeConnection(token, caller) ?: return invalidConnection()
        return LedgerResult.Success(connection.beginClose())
    }

    @Synchronized
    fun finishClose(token: HostClientToken, caller: AuthorizedCaller): LedgerResult<Unit> {
        connections.activeConnection(token, caller) ?: return invalidConnection()
        connections.remove(token)
        return LedgerResult.Success(Unit)
    }

    private companion object {
        const val MAX_TOKEN_GENERATION_ATTEMPTS = 8
    }
}

private fun Map<HostClientToken, ClientConnectionState>.activeConnection(
    token: HostClientToken,
    caller: AuthorizedCaller,
): ClientConnectionState? = get(token)?.takeIf { it.matches(caller) }

private fun invalidConnection(): LedgerResult.Failure = LedgerResult.Failure(LedgerFailure.CLIENT_TOKEN_INVALID)
