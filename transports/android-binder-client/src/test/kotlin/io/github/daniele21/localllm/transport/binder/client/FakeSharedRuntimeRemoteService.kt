package io.github.daniele21.localllm.transport.binder.client

import android.os.RemoteException
import io.github.daniele21.localllm.transport.binder.contract.BinderProtocolV1
import io.github.daniele21.localllm.transport.binder.contract.CancelRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.ClientHelloParcel
import io.github.daniele21.localllm.transport.binder.contract.ClientTokenParcel
import io.github.daniele21.localllm.transport.binder.contract.CloseSessionRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerGenerationEventParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerResultParcel
import io.github.daniele21.localllm.transport.binder.contract.GenerationEventParcel
import io.github.daniele21.localllm.transport.binder.contract.GenerationRequestParcel
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
    override val consumer: ConsumerSharedRuntimeRemoteService = FakeConsumerRemoteService(this)

    var registerCalls = 0
    var unregisterCalls = 0
    var closeSessionCalls = 0
    var generateCalls = 0
    var cancelCalls = 0
    var consumerCloseSessionCalls = 0
    var consumerGenerateCalls = 0
    var consumerCancelCalls = 0
    var lastGenerationRequest: GenerationRequestParcel? = null
    var lastCancelRequest: CancelRequestParcel? = null
    var lastConsumerRequest: ConsumerRequestParcel? = null
    var cancelFailure: RemoteException? = null
    var registrationHandler: ((ClientHelloParcel, (RegistrationResultParcel) -> Unit) -> Unit)? = null
    var prepareHandler: ((PrepareRequestParcel, (PrepareResultParcel) -> Unit) -> Unit)? = null
    var openSessionHandler: ((OpenSessionRequestParcel, (SessionResultParcel) -> Unit) -> Unit)? = null
    var generationHandler: ((GenerationRequestParcel, (GenerationEventParcel) -> Unit) -> Unit)? = null
    var consumerCapabilitiesHandler: ((ConsumerRequestParcel, (ConsumerResultParcel) -> Unit) -> Unit)? = null
    var consumerPrepareHandler: ((ConsumerRequestParcel, (ConsumerResultParcel) -> Unit) -> Unit)? = null
    var consumerOpenSessionHandler: ((ConsumerRequestParcel, (ConsumerResultParcel) -> Unit) -> Unit)? = null
    var consumerGenerationHandler: ((ConsumerRequestParcel, (ConsumerGenerationEventParcel) -> Unit) -> Unit)? = null
    private var hostDisconnecting: (() -> Unit)? = null

    override fun protocolInfo(): ProtocolInfoParcel = protocol

    override fun registerClient(
        hello: ClientHelloParcel,
        hostDisconnectingCallback: () -> Unit,
        callback: (RegistrationResultParcel) -> Unit,
    ) {
        registerCalls += 1
        hostDisconnecting = hostDisconnectingCallback
        val handler = registrationHandler
        if (handler == null) callback(registration) else handler(hello, callback)
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

    override fun generate(request: GenerationRequestParcel, callback: (GenerationEventParcel) -> Unit) {
        generateCalls += 1
        lastGenerationRequest = request
        requireNotNull(generationHandler) { "Generation handler not configured" }(request, callback)
    }

    override fun cancel(request: CancelRequestParcel) {
        cancelCalls += 1
        lastCancelRequest = request
        cancelFailure?.let { throw it }
    }

    fun consumerCapabilities(request: ConsumerRequestParcel, callback: (ConsumerResultParcel) -> Unit) {
        lastConsumerRequest = request
        requireNotNull(consumerCapabilitiesHandler) { "Consumer capabilities handler not configured" }(request, callback)
    }

    fun consumerPrepare(request: ConsumerRequestParcel, callback: (ConsumerResultParcel) -> Unit) {
        lastConsumerRequest = request
        requireNotNull(consumerPrepareHandler) { "Consumer prepare handler not configured" }(request, callback)
    }

    fun consumerOpenSession(request: ConsumerRequestParcel, callback: (ConsumerResultParcel) -> Unit) {
        lastConsumerRequest = request
        requireNotNull(consumerOpenSessionHandler) { "Consumer open-session handler not configured" }(request, callback)
    }

    fun consumerGenerate(request: ConsumerRequestParcel, callback: (ConsumerGenerationEventParcel) -> Unit) {
        consumerGenerateCalls += 1
        lastConsumerRequest = request
        requireNotNull(consumerGenerationHandler) { "Consumer generation handler not configured" }(request, callback)
    }

    fun consumerCancel(request: CancelRequestParcel) {
        consumerCancelCalls += 1
        lastCancelRequest = request
        cancelFailure?.let { throw it }
    }

    fun consumerCloseSession(request: CloseSessionRequestParcel) {
        consumerCloseSessionCalls += 1
    }

    override fun unregisterClient(clientToken: ClientTokenParcel) {
        unregisterCalls += 1
    }

    fun disconnectFromHost() {
        hostDisconnecting?.invoke()
    }
}

private class FakeConsumerRemoteService(
    private val parent: FakeSharedRuntimeRemoteService,
) : ConsumerSharedRuntimeRemoteService {
    override fun capabilities(request: ConsumerRequestParcel, callback: (ConsumerResultParcel) -> Unit) = parent.consumerCapabilities(request, callback)
    override fun prepare(request: ConsumerRequestParcel, callback: (ConsumerResultParcel) -> Unit) = parent.consumerPrepare(request, callback)
    override fun openSession(request: ConsumerRequestParcel, callback: (ConsumerResultParcel) -> Unit) = parent.consumerOpenSession(request, callback)
    override fun generate(request: ConsumerRequestParcel, callback: (ConsumerGenerationEventParcel) -> Unit) = parent.consumerGenerate(request, callback)
    override fun cancel(request: CancelRequestParcel) = parent.consumerCancel(request)
    override fun closeSession(request: CloseSessionRequestParcel) = parent.consumerCloseSession(request)
}

internal class FakeEndpointInvalidations : SharedRuntimeEndpointInvalidationSource {
    private val listeners = mutableSetOf<SharedRuntimeEndpointInvalidationListener>()

    override fun addListener(listener: SharedRuntimeEndpointInvalidationListener): AutoCloseable {
        listeners += listener
        return object : AutoCloseable {
            override fun close() {
                listeners -= listener
            }
        }
    }

    fun invalidate(epoch: Long, detail: String) {
        listeners.toList().forEach { it.onEndpointInvalidated(epoch, detail) }
    }
}

internal fun compatibleProtocolInfo(protocolMajor: Int = BinderProtocolV1.MAJOR): ProtocolInfoParcel =
    ProtocolInfoParcel(
        protocolMajor = protocolMajor,
        protocolMinor = BinderProtocolV1.MINOR,
        minSupportedMinor = BinderProtocolV1.MIN_SUPPORTED_MINOR,
        supportedFeatures = BinderProtocolV1.KNOWN_FEATURES.sorted(),
        hostBuildId = "test-host",
    )

internal fun successfulRegistration(): RegistrationResultParcel =
    RegistrationResultParcel(
        clientToken = ClientTokenParcel("test-client-token"),
        negotiatedMinor = BinderProtocolV1.MINOR,
        enabledFeatures = BinderProtocolV1.KNOWN_FEATURES.sorted(),
        error = null,
    )
