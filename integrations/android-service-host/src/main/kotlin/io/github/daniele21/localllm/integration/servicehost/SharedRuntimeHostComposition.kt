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
    consumerControlPlaneHost: ConsumerControlPlaneHost? = null,
    consumerRuntimeReadinessHost: ConsumerRuntimeReadinessHost? = null,
    policySource: (() -> Collection<AuthorizedClientPolicy>)? = null,
) : AutoCloseable {
    private val delegate = SharedRuntimeHostDelegate(
        client = client,
        protocolInfo = hostProtocolInfo(
            hostBuildId = hostBuildId,
            consumerApiEnabled = consumerClientFactory != null,
            consumerControlPlaneEnabled = consumerControlPlaneHost != null,
            consumerRuntimeReadinessEnabled = consumerRuntimeReadinessHost != null,
        ),
        consumerClientFactory = consumerClientFactory,
        consumerControlPlaneHost = consumerControlPlaneHost,
        consumerRuntimeReadinessHost = consumerRuntimeReadinessHost,
    )
    private val binderStub = SharedRuntimeBinderStub(
        authorizer = CallerAuthorizer(
            permissionName = permissionName,
            policies = policies,
            environment = AndroidCallerEnvironment(context.applicationContext),
            policySource = policySource,
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
    consumerControlPlaneEnabled: Boolean = false,
    consumerRuntimeReadinessEnabled: Boolean = false,
): ProtocolInfoParcel {
    require(hostBuildId.isNotBlank()) { "Host build ID must not be blank" }
    require(hostBuildId.length <= BinderProtocolV1.MAX_CLIENT_BUILD_ID_CHARACTERS) {
        "Host build ID exceeds protocol limit"
    }
    require(!consumerControlPlaneEnabled || consumerApiEnabled) {
        "Consumer control plane requires Consumer API v1"
    }
    require(!consumerRuntimeReadinessEnabled || consumerControlPlaneEnabled) {
        "Consumer runtime readiness requires the consumer control plane"
    }
    val features = BinderProtocolV1.KNOWN_FEATURES
        .let { known -> if (consumerApiEnabled) known else known - BinderProtocolV1.FEATURE_CONSUMER_API_V1 }
        .let { consumerFeatures ->
            if (consumerControlPlaneEnabled) {
                consumerFeatures
            } else {
                consumerFeatures -
                    setOf(
                        BinderProtocolV1.FEATURE_CONSUMER_CONTROL_PLANE_V1,
                        BinderProtocolV1.FEATURE_CONSUMER_SETUP_RESOLUTION_V1,
                    )
            }
        }
        .let { controlPlaneFeatures ->
            if (consumerRuntimeReadinessEnabled) {
                controlPlaneFeatures
            } else {
                controlPlaneFeatures - BinderProtocolV1.FEATURE_CONSUMER_RUNTIME_READINESS_V1
            }
        }
    return ProtocolInfoParcel(
        protocolMajor = BinderProtocolV1.MAJOR,
        protocolMinor = BinderProtocolV1.MINOR,
        minSupportedMinor = BinderProtocolV1.MIN_SUPPORTED_MINOR,
        supportedFeatures = features.sorted(),
        hostBuildId = hostBuildId,
    )
}
