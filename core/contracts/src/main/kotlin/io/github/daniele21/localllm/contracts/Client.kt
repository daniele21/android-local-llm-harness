package io.github.daniele21.localllm.contracts

interface LocalLlmClient {
    fun runtimeSnapshot(): RuntimeSnapshot
    fun prepare(applicationId: ApplicationId, useCaseId: UseCaseId): PrepareResult
    fun createSession(applicationId: ApplicationId, useCaseId: UseCaseId): SessionId
    fun generate(request: GenerationRequest, listener: GenerationListener): GenerationHandle
    fun closeSession(sessionId: SessionId)
}

data class PrepareResult(val ready: Boolean, val modelDigest: ModelDigest?, val detail: String)

data class RuntimeSnapshot(val state: RuntimeState, val loadedModel: ModelDigest?, val activeSessions: Int, val queuedRequests: Int)

enum class RuntimeState {
    IDLE,
    PREPARING,
    READY,
    GENERATING,
    DEGRADED,
    FAILED,
}
