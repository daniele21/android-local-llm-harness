package io.github.daniele21.localllm.integration.servicehost

import android.content.Context
import android.os.IBinder
import io.github.daniele21.localllm.contracts.LocalLlmClient
import io.github.daniele21.localllm.transport.binder.contract.BinderProtocolV1
import io.github.daniele21.localllm.transport.binder.contract.ProtocolInfoParcel

/**
 * Reusable Android composition root for the shared-runtime host Binder boundary.
 *
 * The caller supplies the host-owned [LocalLlmClient] and authorization policy. This class does
 * not create a runtime, select a model or perform model preparation while the service is bound.
 */
class SharedRuntimeHostComposition(
    context: Context,
    client: LocalLlmClient,
    permissionName: String,
    policies: Collection<AuthorizedClientPolicy>,
    hostBuildId: String,
) {
    private val binderStub = SharedRuntimeBinderStub(
        authorizer = CallerAuthorizer(
            permissionName = permissionName,
            policies = policies,
            environment = AndroidCallerEnvironment(context.applicationContext),
        ),
        delegate = SharedRuntimeHostDelegate(
            client = client,
            protocolInfo = hostProtocolInfo(hostBuildId),
        ),
    )

    val binder: IBinder
        get() = binderStub
}

internal fun hostProtocolInfo(hostBuildId: String): ProtocolInfoParcel {
    require(hostBuildId.isNotBlank()) { "Host build ID must not be blank" }
    require(hostBuildId.length <= BinderProtocolV1.MAX_CLIENT_BUILD_ID_CHARACTERS) {
        "Host build ID exceeds protocol limit"
    }
    return ProtocolInfoParcel(
        protocolMajor = BinderProtocolV1.MAJOR,
        protocolMinor = BinderProtocolV1.MINOR,
        minSupportedMinor = BinderProtocolV1.MIN_SUPPORTED_MINOR,
        supportedFeatures = BinderProtocolV1.KNOWN_FEATURES.sorted(),
        hostBuildId = hostBuildId,
    )
}
