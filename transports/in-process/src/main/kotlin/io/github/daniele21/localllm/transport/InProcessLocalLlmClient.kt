package io.github.daniele21.localllm.transport

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.GenerationHandle
import io.github.daniele21.localllm.contracts.GenerationListener
import io.github.daniele21.localllm.contracts.GenerationRequest
import io.github.daniele21.localllm.contracts.LocalLlmClient
import io.github.daniele21.localllm.contracts.PrepareResult
import io.github.daniele21.localllm.contracts.RuntimeSnapshot
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.UseCaseId

class InProcessLocalLlmClient(private val runtime: LocalLlmClient) : LocalLlmClient by runtime {
    override fun runtimeSnapshot(): RuntimeSnapshot = runtime.runtimeSnapshot()

    override fun prepare(applicationId: ApplicationId, useCaseId: UseCaseId): PrepareResult = runtime.prepare(applicationId, useCaseId)

    override fun createSession(applicationId: ApplicationId, useCaseId: UseCaseId): SessionId =
        runtime.createSession(applicationId, useCaseId)

    override fun generate(request: GenerationRequest, listener: GenerationListener): GenerationHandle = runtime.generate(request, listener)
}
