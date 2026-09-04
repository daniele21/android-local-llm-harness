package io.github.daniele21.localllm.consumerfixture

import android.content.Context
import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.transport.binder.client.BinderLocalLlmClient
import io.github.daniele21.localllm.transport.binder.client.SharedRuntimeConnectionObserver
import io.github.daniele21.localllm.transport.binder.client.SharedRuntimeConnectionState
import io.github.daniele21.localllm.transport.binder.client.SharedRuntimeHostConfig

/** Packaged-AAR consumer used by SR-3 packaging checks and SR-6 release-like device evidence. */
class PackagedBinderClientConsumer(context: Context) : AutoCloseable {
    private val client =
        BinderLocalLlmClient.create(
            context = context,
            hostConfig =
            SharedRuntimeHostConfig.create(
                BuildConfig.SHARED_RUNTIME_HOST_PACKAGE,
                BuildConfig.SHARED_RUNTIME_HOST_SERVICE,
            ),
            applicationId = ApplicationId("local-llm-console"),
            clientBuildId = "packaged-consumer-fixture-${BuildConfig.VERSION_NAME}",
            observer = SharedRuntimeConnectionObserver {},
        )

    val connectionState: SharedRuntimeConnectionState
        get() = client.connectionSnapshot.state

    fun connect() {
        client.connect()
    }

    override fun close() {
        client.close()
    }
}
