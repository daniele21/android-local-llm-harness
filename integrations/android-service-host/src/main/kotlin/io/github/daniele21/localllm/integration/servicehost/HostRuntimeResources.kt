package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.contracts.GenerationHandle
import io.github.daniele21.localllm.contracts.RequestId
import java.util.concurrent.ConcurrentHashMap

internal class HostRuntimeResources {
    private val handles = ConcurrentHashMap<RequestId, GenerationHandle>()
    private val deathLinks = ConcurrentHashMap<HostClientToken, ClientDeathLink>()

    fun attachHandle(requestId: RequestId, handle: GenerationHandle) {
        handles[requestId] = handle
    }

    fun handle(requestId: RequestId): GenerationHandle? = handles[requestId]

    fun removeHandle(requestId: RequestId): GenerationHandle? = handles.remove(requestId)

    fun attachDeathLink(token: HostClientToken, link: ClientDeathLink) {
        deathLinks[token] = link
    }

    fun removeDeathLink(token: HostClientToken): ClientDeathLink? = deathLinks.remove(token)
}

internal fun GenerationHandle.cancelSafely() {
    runCatching(::cancel)
}

internal fun ClientDeathLink.unlinkSafely() {
    runCatching(::unlink)
}
