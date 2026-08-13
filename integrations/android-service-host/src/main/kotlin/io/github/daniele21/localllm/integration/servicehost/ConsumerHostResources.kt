package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.contracts.ConsumerGenerationHandle
import io.github.daniele21.localllm.contracts.ConsumerLocalLlmClient
import io.github.daniele21.localllm.contracts.GenerationHandle
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.SessionId
import java.util.concurrent.ConcurrentHashMap

internal class ConsumerHostResources {
    private val clients = ConcurrentHashMap<HostClientToken, ConsumerLocalLlmClient>()
    private val sessions = ConcurrentHashMap<SessionId, HostClientToken>()

    fun attachClient(token: HostClientToken, client: ConsumerLocalLlmClient) {
        clients[token] = client
    }

    fun client(token: HostClientToken): ConsumerLocalLlmClient? = clients[token]

    fun removeClient(token: HostClientToken): ConsumerLocalLlmClient? = clients.remove(token)

    fun markSession(token: HostClientToken, sessionId: SessionId) {
        sessions[sessionId] = token
    }

    fun ownsSession(token: HostClientToken, sessionId: SessionId): Boolean = sessions[sessionId] == token

    fun removeSession(token: HostClientToken, sessionId: SessionId): Boolean = sessions.remove(sessionId, token)

    fun clear() {
        sessions.clear()
        clients.clear()
    }
}

internal class ConsumerGenerationHandleBridge(
    private val delegate: ConsumerGenerationHandle,
) : GenerationHandle {
    override val requestId: RequestId = delegate.requestId

    override fun cancel() = delegate.cancel()
}
