package io.github.daniele21.localllm.integration.servicehost

import android.content.Context
import android.os.IBinder
import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ConsumerLocalLlmClient
import io.github.daniele21.localllm.contracts.LocalLlmClient
import io.github.daniele21.localllm.transport.binder.contract.BinderProtocolV1
import io.github.daniele21.localllm.transport.binder.contract.ProtocolInfoParcel

class SharedRuntimeHostComposition(
    context: Context,
    client: LocalLlmClient,
    permissionName: String,
    policies: Collection<AuthorizedClientPolicy>,
    hostBuildId: String,
    consumerClientFactory: ((ApplicationId) -> ConsumerLocalLlmClient)? = null,
) : AutoCloseable {
    private val delegate = SharedRuntimeHostDelegate(
        client = client,
        protocolInfo = hostProtocolInfo(hostBuildId, consumerClientFactory != null),
        consumerClientFactory = consumerClientFactory,
    )
    private val binderStub = SharedRuntimeBinderStub(
        authorizer = CallerAuthorizer(
            permissionName = permissionName,
            policies = policies,
            environment = AndroidCallerEnvironment(context.applicationContext),
        ),
        delegate = delegate,
    )

    val binder: IBinder
        get() = binderStub

    override fun close() {
        delegate.close()
    }
}

internal fun hostProtocolInfo(
    hostBuildId: String,
    consumerApiEnabled: Boolean = false,
): ProtocolInfoParcel {
    require(hostBuildId.isNotBlank()) { "Host build ID must not be blank" }
    require(hostBuildId.length <= BinderProtocolV1.MAX_CLIENT_BUILD_ID_CHARACTERS) {
        "Host build ID exceeds protocol limit"
    }
    val features = if (consumerApiEnabled) {
        BinderProtocolV1.KNOWN_FEATURES
    } else {
        BinderProtocolV1.KNOWN_FEATURES - BinderProtocolV1.FEATURE_CONSUMER_API_V1
    }
    return ProtocolInfoParcel(
        protocolMajor = BinderProtocolV1.MAJOR,
        protocolMinor = BinderProtocolV1.MINOR,
        minSupportedMinor = BinderProtocolV1.MIN_SUPPORTED_MINOR,
        supportedFeatures = features.sorted(),
        hostBuildId = hostBuildId,
    )
}
