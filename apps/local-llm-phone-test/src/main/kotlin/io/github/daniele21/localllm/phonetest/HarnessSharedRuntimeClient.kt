package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.GenerationHandle
import io.github.daniele21.localllm.contracts.GenerationListener
import io.github.daniele21.localllm.contracts.GenerationRequest
import io.github.daniele21.localllm.contracts.LocalLlmClient
import io.github.daniele21.localllm.contracts.PrepareResult
import io.github.daniele21.localllm.contracts.RuntimeSnapshot
import io.github.daniele21.localllm.contracts.RuntimeState
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.SessionOptions
import io.github.daniele21.localllm.contracts.UseCaseId

/**
 * Stable service-facing client over the host graph's process-scoped in-process runtime client.
 *
 * Snapshot observation never creates a runtime. Explicit prepare may lazily create the runtime only
 * after the host has selected a model; bind and handshake remain side-effect free.
 */
internal class HarnessSharedRuntimeClient(
    private val activeClient: () -> LocalLlmClient?,
    private val prepareClient: () -> LocalLlmClient?,
) : LocalLlmClient {
    override fun runtimeSnapshot(): RuntimeSnapshot = activeClient()?.runtimeSnapshot()
        ?: RuntimeSnapshot(
            state = RuntimeState.IDLE,
            loadedModel = null,
            activeSessions = 0,
            queuedRequests = 0,
        )

    override fun prepare(applicationId: ApplicationId, useCaseId: UseCaseId): PrepareResult =
        prepareClient()?.prepare(applicationId, useCaseId)
            ?: PrepareResult(
                ready = false,
                modelDigest = null,
                detail = "Host model is not selected",
            )

    override fun createSession(applicationId: ApplicationId, useCaseId: UseCaseId): SessionId =
        requireActiveClient().createSession(applicationId, useCaseId)

    override fun createSession(applicationId: ApplicationId, useCaseId: UseCaseId, options: SessionOptions): SessionId =
        requireActiveClient().createSession(applicationId, useCaseId, options)

    override fun generate(request: GenerationRequest, listener: GenerationListener): GenerationHandle =
        requireActiveClient().generate(request, listener)

    override fun closeSession(sessionId: SessionId) {
        requireActiveClient().closeSession(sessionId)
    }

    private fun requireActiveClient(): LocalLlmClient = activeClient() ?: error("Host runtime is not prepared")
}
