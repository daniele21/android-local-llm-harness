package io.github.daniele21.harnex.hello

import android.content.Context
import io.github.daniele21.localllm.contracts.ConsumerActivationId
import io.github.daniele21.localllm.contracts.ConsumerActivationRequest
import io.github.daniele21.localllm.contracts.ConsumerActivationResult
import io.github.daniele21.localllm.contracts.ConsumerAssignedUseCasesResult
import io.github.daniele21.localllm.contracts.ConsumerDeactivationResult
import io.github.daniele21.localllm.contracts.ConsumerGenerationListener
import io.github.daniele21.localllm.contracts.ConsumerGenerationRequest
import io.github.daniele21.localllm.contracts.ConsumerGenerationStartResult
import io.github.daniele21.localllm.contracts.ConsumerPrepareRequest
import io.github.daniele21.localllm.contracts.ConsumerPrepareResult
import io.github.daniele21.localllm.contracts.ConsumerPreparedId
import io.github.daniele21.localllm.contracts.ConsumerPublishedPresetsResult
import io.github.daniele21.localllm.contracts.ConsumerSessionResult
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.transport.binder.client.BinderConsumerLocalLlmClient
import io.github.daniele21.localllm.transport.binder.client.SharedRuntimeConnectionObserver
import io.github.daniele21.localllm.transport.binder.client.SharedRuntimeHostConfig

/**
 * App-owned seam around the public Consumer SDK.
 *
 * New consumer applications should own a similarly small boundary so product/UI code never depends on
 * Binder implementation details or Harnex runtime/model internals.
 */
internal interface HelloHarnexRuntime : AutoCloseable {
    fun connect()

    fun disconnect()

    fun assignedUseCases(): ConsumerAssignedUseCasesResult

    fun publishedPresets(useCaseId: UseCaseId): ConsumerPublishedPresetsResult

    fun activate(request: ConsumerActivationRequest): ConsumerActivationResult

    fun deactivate(activationId: ConsumerActivationId): ConsumerDeactivationResult

    fun prepare(request: ConsumerPrepareRequest): ConsumerPrepareResult

    fun createSession(preparedId: ConsumerPreparedId): ConsumerSessionResult

    fun generate(request: ConsumerGenerationRequest, listener: ConsumerGenerationListener): ConsumerGenerationStartResult

    fun closeSession(sessionId: SessionId)
}

internal class BinderHelloHarnexRuntime private constructor(
    private val client: BinderConsumerLocalLlmClient,
) : HelloHarnexRuntime {
    override fun connect() = client.connect()

    override fun disconnect() = client.disconnect()

    override fun assignedUseCases(): ConsumerAssignedUseCasesResult = client.assignedUseCases()

    override fun publishedPresets(useCaseId: UseCaseId): ConsumerPublishedPresetsResult = client.publishedPresets(useCaseId)

    override fun activate(request: ConsumerActivationRequest): ConsumerActivationResult = client.activate(request)

    override fun deactivate(activationId: ConsumerActivationId): ConsumerDeactivationResult = client.deactivate(activationId)

    override fun prepare(request: ConsumerPrepareRequest): ConsumerPrepareResult = client.prepare(request)

    override fun createSession(preparedId: ConsumerPreparedId): ConsumerSessionResult = client.createSession(preparedId)

    override fun generate(
        request: ConsumerGenerationRequest,
        listener: ConsumerGenerationListener,
    ): ConsumerGenerationStartResult = client.generate(request, listener)

    override fun closeSession(sessionId: SessionId) = client.closeSession(sessionId)

    override fun close() = client.close()

    companion object {
        fun create(
            context: Context,
            onConnectionChanged: SharedRuntimeConnectionObserver,
        ): BinderHelloHarnexRuntime =
            BinderHelloHarnexRuntime(
                BinderConsumerLocalLlmClient.create(
                    context = context.applicationContext,
                    hostConfig =
                        SharedRuntimeHostConfig.create(
                            BuildConfig.HARNEX_HOST_PACKAGE,
                            HARNEX_HOST_SERVICE,
                        ),
                    clientBuildId = "hello-harnex-${BuildConfig.VERSION_NAME}",
                    observer = onConnectionChanged,
                ),
            )
    }
}

internal const val HARNEX_HOST_SERVICE = "io.github.daniele21.localllm.phonetest.HarnessSharedRuntimeService"
