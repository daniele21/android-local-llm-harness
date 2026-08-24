package io.github.daniele21.localllm.transport.binder.client

import android.content.Context
import io.github.daniele21.localllm.contracts.ConsumerCapabilityResult
import io.github.daniele21.localllm.contracts.ConsumerControlPlaneClient
import io.github.daniele21.localllm.contracts.ConsumerGenerationListener
import io.github.daniele21.localllm.contracts.ConsumerGenerationRequest
import io.github.daniele21.localllm.contracts.ConsumerGenerationStartResult
import io.github.daniele21.localllm.contracts.ConsumerLocalLlmClient
import io.github.daniele21.localllm.contracts.ConsumerPrepareRequest
import io.github.daniele21.localllm.contracts.ConsumerPrepareResult
import io.github.daniele21.localllm.contracts.ConsumerPreparedId
import io.github.daniele21.localllm.contracts.ConsumerSessionResult
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.transport.binder.contract.BinderProtocolV1
import java.util.concurrent.atomic.AtomicBoolean

/** Public consumer API backed by an authenticated shared-runtime Binder connection. */
class BinderConsumerLocalLlmClient
private constructor(
    private val connection: SharedRuntimeConnection,
    private val lifecycle: BinderConsumerLifecycleAdapter,
    private val generation: BinderConsumerGenerationAdapter,
    private val controlPlane: BinderConsumerControlPlaneAdapter,
) : ConsumerLocalLlmClient,
    ConsumerControlPlaneClient by controlPlane,
    AutoCloseable {
    private val closed = AtomicBoolean(false)

    val connectionSnapshot: SharedRuntimeConnectionSnapshot
        get() = connection.snapshot

    fun connect() {
        checkOpen()
        connection.connect()
    }

    override fun capabilities(useCaseId: UseCaseId): ConsumerCapabilityResult {
        checkOpen()
        return lifecycle.capabilities(useCaseId)
    }

    override fun prepare(request: ConsumerPrepareRequest): ConsumerPrepareResult {
        checkOpen()
        return lifecycle.prepare(request)
    }

    override fun createSession(preparedId: ConsumerPreparedId): ConsumerSessionResult {
        checkOpen()
        return lifecycle.createSession(preparedId)
    }

    override fun generate(request: ConsumerGenerationRequest, listener: ConsumerGenerationListener): ConsumerGenerationStartResult {
        checkOpen()
        return generation.generate(request, listener)
    }

    override fun closeSession(sessionId: SessionId) {
        if (closed.get()) return
        lifecycle.closeSession(sessionId)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        generation.close()
        lifecycle.close()
        connection.close()
    }

    private fun checkOpen() {
        check(!closed.get()) { "Shared-runtime consumer client is closed" }
    }

    companion object {
        fun create(
            context: Context,
            hostConfig: SharedRuntimeHostConfig,
            clientBuildId: String,
            observer: SharedRuntimeConnectionObserver = SharedRuntimeConnectionObserver {},
        ): BinderConsumerLocalLlmClient {
            val connection =
                SharedRuntimeConnection.create(
                    context = context,
                    hostConfig = hostConfig,
                    clientBuildId = clientBuildId,
                    requiredFeatures = setOf(BinderProtocolV1.FEATURE_CONSUMER_API_V1),
                    observer = observer,
                )
            return BinderConsumerLocalLlmClient(
                connection = connection,
                lifecycle =
                BinderConsumerLifecycleAdapter(
                    endpointProvider = { connection.endpoint },
                    endpointInvalidations = connection.endpointInvalidations,
                ),
                generation =
                BinderConsumerGenerationAdapter(
                    endpointProvider = { connection.endpoint },
                    endpointInvalidations = connection.endpointInvalidations,
                ),
                controlPlane =
                BinderConsumerControlPlaneAdapter(
                    endpointProvider = { connection.endpoint },
                    enabledFeaturesProvider = { connection.snapshot.enabledFeatures },
                    endpointInvalidations = connection.endpointInvalidations,
                ),
            )
        }
    }
}
