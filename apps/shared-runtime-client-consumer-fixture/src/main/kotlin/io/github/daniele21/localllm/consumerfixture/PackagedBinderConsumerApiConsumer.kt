package io.github.daniele21.localllm.consumerfixture

import android.content.Context
import io.github.daniele21.localllm.contracts.ConsumerCapabilityResult
import io.github.daniele21.localllm.contracts.ConsumerPrepareRequest
import io.github.daniele21.localllm.contracts.ConsumerPrepareResult
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.transport.binder.client.BinderConsumerLocalLlmClient
import io.github.daniele21.localllm.transport.binder.client.SharedRuntimeConnectionObserver
import io.github.daniele21.localllm.transport.binder.client.SharedRuntimeConnectionState
import io.github.daniele21.localllm.transport.binder.client.SharedRuntimeHostConfig

/**
 * Packaged-AAR fixture for the CA-4 public consumer Binder surface.
 *
 * This source compiles only against the release client/contract AARs plus public core contracts. It deliberately
 * carries no application ID, model ID, artifact digest/path or raw runtime tuning authority.
 */
class PackagedBinderConsumerApiConsumer(context: Context) : AutoCloseable {
    private val client =
        BinderConsumerLocalLlmClient.create(
            context = context,
            hostConfig =
                SharedRuntimeHostConfig.create(
                    BuildConfig.SHARED_RUNTIME_HOST_PACKAGE,
                    BuildConfig.SHARED_RUNTIME_HOST_SERVICE,
                ),
            clientBuildId = "packaged-consumer-api-fixture-${BuildConfig.VERSION_NAME}",
            observer = SharedRuntimeConnectionObserver {},
        )

    val connectionState: SharedRuntimeConnectionState
        get() = client.connectionSnapshot.state

    fun connect() {
        client.connect()
    }

    fun capabilities(useCaseId: UseCaseId): ConsumerCapabilityResult = client.capabilities(useCaseId)

    fun prepare(request: ConsumerPrepareRequest): ConsumerPrepareResult = client.prepare(request)

    override fun close() {
        client.close()
    }
}
