package io.github.daniele21.localllm.transport.binder.client

import io.github.daniele21.localllm.transport.binder.contract.BinderProtocolV1
import io.github.daniele21.localllm.transport.binder.contract.ClientHelloParcel
import io.github.daniele21.localllm.transport.binder.contract.ClientTokenParcel
import io.github.daniele21.localllm.transport.binder.contract.CloseSessionRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.OpenSessionRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.PrepareRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.PrepareResultParcel
import io.github.daniele21.localllm.transport.binder.contract.ProtocolInfoParcel
import io.github.daniele21.localllm.transport.binder.contract.RegistrationResultParcel
import io.github.daniele21.localllm.transport.binder.contract.SessionResultParcel

internal class FakeSharedRuntimeRemoteService(
    var protocol: ProtocolInfoParcel = compatibleProtocolInfo(),
    var registration: RegistrationResultParcel = successfulRegistration(),
) : SharedRuntimeRemoteService {
    var registerCalls = 0
    var unregisterCalls = 0
    var closeSessionCalls = 0
    var prepareHandler: ((PrepareRequestParcel, (PrepareResultParcel) -> Unit) -> Unit)? = null
    var openSessionHandler: ((OpenSessionRequestParcel, (SessionResultParcel) -> Unit) -> Unit)? = null
    private var hostDisconnecting: (() -> Unit)? = null

    override fun protocolInfo(): ProtocolInfoParcel = protocol

    override fun registerClient(
        hello: ClientHelloParcel,
        onHostDisconnecting: () -> Unit,
        callback: (RegistrationResultParcel) -> Unit,
    ) {
        registerCalls += 1
        hostDisconnecting = onHostDisconnecting
        callback(registration)
    }

    override fun prepare(request: PrepareRequestParcel, callback: (PrepareResultParcel) -> Unit) {
        requireNotNull(prepareHandler) { "Prepare handler not configured" }(request, callback)
    }

    override fun openSession(request: OpenSessionRequestParcel, callback: (SessionResultParcel) -> Unit) {
        requireNotNull(openSessionHandler) { "Open-session handler not configured" }(request, callback)
    }

    override fun closeSession(request: CloseSessionRequestParcel) {
        closeSessionCalls += 1
    }

    override fun unregisterClient(clientToken: ClientTokenParcel) {
        unregisterCalls += 1
    }

    fun disconnectFromHost() {
        hostDisconnecting?.invoke()
    }
}

internal fun compatibleProtocolInfo(protocolMajor: Int = BinderProtocolV1.MAJOR): ProtocolInfoParcel = ProtocolInfoParcel(
    protocolMajor = protocolMajor,
    protocolMinor = BinderProtocolV1.MINOR,
    minSupportedMinor = BinderProtocolV1.MIN_SUPPORTED_MINOR,
    supportedFeatures = BinderProtocolV1.KNOWN_FEATURES.sorted(),
    hostBuildId = "test-host",
)

internal fun successfulRegistration(): RegistrationResultParcel = RegistrationResultParcel(
    clientToken = ClientTokenParcel("test-client-token"),
    negotiatedMinor = BinderProtocolV1.MINOR,
    enabledFeatures = BinderProtocolV1.KNOWN_FEATURES.sorted(),
    error = null,
)
