package io.github.daniele21.localllm.consumerfixture

import android.content.Context
import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.transport.binder.client.BinderLocalLlmClient
import io.github.daniele21.localllm.transport.binder.client.SharedRuntimeConnectionObserver
import io.github.daniele21.localllm.transport.binder.client.SharedRuntimeConnectionState
import io.github.daniele21.localllm.transport.binder.client.SharedRuntimeHostConfig

/** Compile-only consumer proving the reviewed Binder client API from packaged AARs. */
class PackagedBinderClientConsumer(context: Context) : AutoCloseable {
    private val client = BinderLocalLlmClient.create(
        context = context,
        hostConfig = SharedRuntimeHostConfig.create(
            "io.github.daniele21.localllm.phonetest.debug",
            "io.github.daniele21.localllm.phonetest.HarnessSharedRuntimeService",
        ),
        applicationId = ApplicationId("local-llm-console"),
        clientBuildId = "packaged-consumer-fixture",
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
