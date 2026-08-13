package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.contracts.ConsumerGenerationHandle
import io.github.daniele21.localllm.contracts.ConsumerLocalLlmClient
import io.github.daniele21.localllm.contracts.GenerationHandle
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.SessionId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

internal class HostRuntimeResources {
    private val closed = AtomicBoolean(false)
    private val handles = ConcurrentHashMap<RequestId, GenerationHandle>()
    private val consumerClients = ConcurrentHashMap<HostClientToken, ConsumerLocalLlmClient>()
    private val consumerSessions = ConcurrentHashMap<SessionId, HostClientToken>()
    private val deathLinks = ConcurrentHashMap<HostClientToken, ClientDeathLink>()
    private val callbackDispatchers = ConcurrentHashMap<HostClientToken, HostCallbackDispatcher>()

    fun attachHandle(requestId: RequestId, handle: GenerationHandle) {
        if (closed.get()) {
            handle.cancelSafely()
            return
        }
        handles[requestId] = handle
        if (closed.get() && handles.remove(requestId, handle)) {
            handle.cancelSafely()
        }
    }

    fun attachConsumerHandle(handle: ConsumerGenerationHandle) {
        attachHandle(handle.requestId, ConsumerGenerationHandleBridge(handle))
    }

    fun handle(requestId: RequestId): GenerationHandle? = handles[requestId]

    fun removeHandle(requestId: RequestId): GenerationHandle? = handles.remove(requestId)

    fun attachConsumerClient(token: HostClientToken, client: ConsumerLocalLlmClient) {
        if (!closed.get()) consumerClients[token] = client
    }

    fun consumerClient(token: HostClientToken): ConsumerLocalLlmClient? = consumerClients[token]

    fun removeConsumerClient(token: HostClientToken): ConsumerLocalLlmClient? = consumerClients.remove(token)

    fun markConsumerSession(token: HostClientToken, sessionId: SessionId) {
        consumerSessions[sessionId] = token
    }

    fun isConsumerSession(token: HostClientToken, sessionId: SessionId): Boolean = consumerSessions[sessionId] == token

    fun removeConsumerSession(token: HostClientToken, sessionId: SessionId): Boolean = consumerSessions.remove(sessionId, token)

    fun attachDeathLink(token: HostClientToken, link: ClientDeathLink) {
        if (closed.get()) {
            link.unlinkSafely()
            return
        }
        deathLinks[token] = link
        if (closed.get() && deathLinks.remove(token, link)) {
            link.unlinkSafely()
        }
    }

    fun removeDeathLink(token: HostClientToken): ClientDeathLink? = deathLinks.remove(token)

    fun attachCallbackDispatcher(token: HostClientToken, dispatcher: HostCallbackDispatcher) {
        if (closed.get()) {
            dispatcher.closeSafely()
            return
        }
        callbackDispatchers[token] = dispatcher
        if (closed.get() && callbackDispatchers.remove(token, dispatcher)) {
            dispatcher.closeSafely()
        }
    }

    fun callbackDispatcher(token: HostClientToken): HostCallbackDispatcher? = callbackDispatchers[token]

    fun removeCallbackDispatcher(token: HostClientToken): HostCallbackDispatcher? = callbackDispatchers.remove(token)

    fun closeAll() {
        if (!closed.compareAndSet(false, true)) return
        handles.values.forEach { handle -> handle.cancelSafely() }
        handles.clear()
        consumerSessions.clear()
        consumerClients.clear()
        deathLinks.values.forEach { link -> link.unlinkSafely() }
        deathLinks.clear()
        callbackDispatchers.values.forEach { dispatcher -> dispatcher.closeSafely() }
        callbackDispatchers.clear()
    }
}

private class ConsumerGenerationHandleBridge(
    private val delegate: ConsumerGenerationHandle,
) : GenerationHandle {
    override val requestId: RequestId = delegate.requestId

    override fun cancel() = delegate.cancel()
}

internal fun GenerationHandle.cancelSafely() {
    runCatching(::cancel)
}

internal fun ClientDeathLink.unlinkSafely() {
    runCatching(::unlink)
}

internal fun HostCallbackDispatcher.closeSafely() {
    runCatching(::close)
}
