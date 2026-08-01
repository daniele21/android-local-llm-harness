package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.GenerationEvent
import io.github.daniele21.localllm.contracts.GenerationHandle
import io.github.daniele21.localllm.contracts.GenerationListener
import io.github.daniele21.localllm.contracts.GenerationRequest
import io.github.daniele21.localllm.contracts.LocalLlmClient
import io.github.daniele21.localllm.contracts.LocalLlmError
import io.github.daniele21.localllm.contracts.PrepareResult
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.RuntimeSnapshot
import io.github.daniele21.localllm.contracts.RuntimeState
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.models.ModelProfileRegistry
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

class RuntimeOrchestrator(private val registry: ModelProfileRegistry) : LocalLlmClient {
    private val state = AtomicReference(RuntimeState.IDLE)
    private val sessions = ConcurrentHashMap<SessionId, SessionDescriptor>()

    override fun runtimeSnapshot(): RuntimeSnapshot = RuntimeSnapshot(
        state = state.get(),
        loadedModel = null,
        activeSessions = sessions.size,
        queuedRequests = 0,
    )

    override fun prepare(applicationId: ApplicationId, useCaseId: UseCaseId): PrepareResult {
        state.set(RuntimeState.PREPARING)
        return runCatching { registry.resolve(applicationId, useCaseId) }
            .fold(
                onSuccess = { resolved ->
                    state.set(RuntimeState.READY)
                    PrepareResult(
                        ready = false,
                        modelDigest = resolved.model.artifact.digest,
                        detail = "Profile resolved; native model loading is the next implementation step",
                    )
                },
                onFailure = { error ->
                    state.set(RuntimeState.FAILED)
                    PrepareResult(
                        ready = false,
                        modelDigest = null,
                        detail = error.message ?: "Unable to resolve model profile",
                    )
                },
            )
    }

    override fun createSession(applicationId: ApplicationId, useCaseId: UseCaseId): SessionId {
        registry.resolve(applicationId, useCaseId)
        val id = SessionId(UUID.randomUUID().toString())
        sessions[id] = SessionDescriptor(applicationId, useCaseId)
        return id
    }

    override fun generate(request: GenerationRequest, listener: GenerationListener): GenerationHandle {
        val session = sessions[request.sessionId]
        if (session == null) {
            listener.onEvent(
                GenerationEvent.Failed(
                    requestId = request.requestId,
                    error = LocalLlmError.Configuration("Unknown session ${request.sessionId.value}"),
                ),
            )
        } else {
            listener.onEvent(
                GenerationEvent.Failed(
                    requestId = request.requestId,
                    error = LocalLlmError.NativeRuntime(
                        "Runtime scaffold is active, but llama.cpp inference is not linked yet",
                    ),
                ),
            )
        }
        return NoOpGenerationHandle(request.requestId)
    }

    override fun closeSession(sessionId: SessionId) {
        sessions.remove(sessionId)
    }

    private data class SessionDescriptor(val applicationId: ApplicationId, val useCaseId: UseCaseId)
}

private class NoOpGenerationHandle(override val requestId: RequestId) : GenerationHandle {
    override fun cancel() = Unit
}
