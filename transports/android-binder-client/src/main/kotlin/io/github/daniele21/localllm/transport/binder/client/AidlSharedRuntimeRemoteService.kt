package io.github.daniele21.localllm.transport.binder.client

import io.github.daniele21.localllm.transport.binder.contract.CancelRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.ClientHelloParcel
import io.github.daniele21.localllm.transport.binder.contract.ClientTokenParcel
import io.github.daniele21.localllm.transport.binder.contract.CloseSessionRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerControlPlaneRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerControlPlaneResultParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerGenerationEventParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerGenerationRequestV2Parcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerResultParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerRuntimeReadinessResultParcel
import io.github.daniele21.localllm.transport.binder.contract.GenerationEventParcel
import io.github.daniele21.localllm.transport.binder.contract.GenerationRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.IClientLifecycle
import io.github.daniele21.localllm.transport.binder.contract.IConsumerControlPlaneResultCallback
import io.github.daniele21.localllm.transport.binder.contract.IConsumerGenerationCallback
import io.github.daniele21.localllm.transport.binder.contract.IConsumerLocalLlmService
import io.github.daniele21.localllm.transport.binder.contract.IConsumerResultCallback
import io.github.daniele21.localllm.transport.binder.contract.IConsumerRuntimeReadinessResultCallback
import io.github.daniele21.localllm.transport.binder.contract.IGenerationCallback
import io.github.daniele21.localllm.transport.binder.contract.ILocalLlmService
import io.github.daniele21.localllm.transport.binder.contract.IPrepareCallback
import io.github.daniele21.localllm.transport.binder.contract.IRegistrationCallback
import io.github.daniele21.localllm.transport.binder.contract.ISessionCallback
import io.github.daniele21.localllm.transport.binder.contract.OpenSessionRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.PrepareRequestParcel
import io.github.daniele21.localllm.transport.binder.contract.PrepareResultParcel
import io.github.daniele21.localllm.transport.binder.contract.ProtocolInfoParcel
import io.github.daniele21.localllm.transport.binder.contract.RegistrationResultParcel
import io.github.daniele21.localllm.transport.binder.contract.SessionResultParcel

internal class AidlSharedRuntimeRemoteService(private val delegate: ILocalLlmService) : SharedRuntimeRemoteService {
    override val consumer: ConsumerSharedRuntimeRemoteService by lazy {
        AidlConsumerSharedRuntimeRemoteService(delegate.consumerApi)
    }

    override fun protocolInfo(): ProtocolInfoParcel = delegate.protocolInfo

    override fun registerClient(
        hello: ClientHelloParcel,
        hostDisconnectingCallback: () -> Unit,
        callback: (RegistrationResultParcel) -> Unit,
    ) {
        delegate.registerClient(
            hello,
            object : IClientLifecycle.Stub() {
                override fun onHostDisconnecting() = hostDisconnectingCallback()
            },
            object : IRegistrationCallback.Stub() {
                override fun onResult(result: RegistrationResultParcel) = callback(result)
            },
        )
    }

    override fun prepare(request: PrepareRequestParcel, callback: (PrepareResultParcel) -> Unit) {
        delegate.prepare(request, object : IPrepareCallback.Stub() {
            override fun onResult(result: PrepareResultParcel) = callback(result)
        })
    }

    override fun openSession(request: OpenSessionRequestParcel, callback: (SessionResultParcel) -> Unit) {
        delegate.openSession(request, object : ISessionCallback.Stub() {
            override fun onResult(result: SessionResultParcel) = callback(result)
        })
    }

    override fun closeSession(request: CloseSessionRequestParcel) = delegate.closeSession(request)

    override fun generate(request: GenerationRequestParcel, callback: (GenerationEventParcel) -> Unit) {
        delegate.generate(request, object : IGenerationCallback.Stub() {
            override fun onEvent(event: GenerationEventParcel) = callback(event)
        })
    }

    override fun cancel(request: CancelRequestParcel) = delegate.cancel(request)

    override fun unregisterClient(clientToken: ClientTokenParcel) = delegate.unregisterClient(clientToken)
}

/** Mirrors the AIDL Consumer service exactly so wire transactions remain easy to audit. */
@Suppress("TooManyFunctions")
private class AidlConsumerSharedRuntimeRemoteService(private val delegate: IConsumerLocalLlmService) : ConsumerSharedRuntimeRemoteService {
    override fun capabilities(request: ConsumerRequestParcel, callback: (ConsumerResultParcel) -> Unit) {
        delegate.capabilities(request, resultCallback(callback))
    }

    override fun prepare(request: ConsumerRequestParcel, callback: (ConsumerResultParcel) -> Unit) {
        delegate.prepare(request, resultCallback(callback))
    }

    override fun openSession(request: ConsumerRequestParcel, callback: (ConsumerResultParcel) -> Unit) {
        delegate.openSession(request, resultCallback(callback))
    }

    override fun generate(request: ConsumerRequestParcel, callback: (ConsumerGenerationEventParcel) -> Unit) {
        delegate.generate(request, generationCallback(callback))
    }

    override fun generateV2(request: ConsumerGenerationRequestV2Parcel, callback: (ConsumerGenerationEventParcel) -> Unit) {
        delegate.generateV2(request, generationCallback(callback))
    }

    override fun cancel(request: CancelRequestParcel) = delegate.cancel(request)

    override fun closeSession(request: CloseSessionRequestParcel) = delegate.closeSession(request)

    override fun discoverUseCases(request: ConsumerControlPlaneRequestParcel, callback: (ConsumerControlPlaneResultParcel) -> Unit) {
        delegate.discoverUseCases(request, controlPlaneResultCallback(callback))
    }

    override fun discoverPresets(request: ConsumerControlPlaneRequestParcel, callback: (ConsumerControlPlaneResultParcel) -> Unit) {
        delegate.discoverPresets(request, controlPlaneResultCallback(callback))
    }

    override fun resolveSetup(request: ConsumerControlPlaneRequestParcel, callback: (ConsumerControlPlaneResultParcel) -> Unit) {
        delegate.resolveSetup(request, controlPlaneResultCallback(callback))
    }

    override fun activate(request: ConsumerControlPlaneRequestParcel, callback: (ConsumerControlPlaneResultParcel) -> Unit) {
        delegate.activate(request, controlPlaneResultCallback(callback))
    }

    override fun deactivate(request: ConsumerControlPlaneRequestParcel, callback: (ConsumerControlPlaneResultParcel) -> Unit) {
        delegate.deactivate(request, controlPlaneResultCallback(callback))
    }

    override fun runtimeReadiness(request: ConsumerControlPlaneRequestParcel, callback: (ConsumerRuntimeReadinessResultParcel) -> Unit) {
        delegate.runtimeReadiness(request, runtimeReadinessResultCallback(callback))
    }
}

private fun resultCallback(callback: (ConsumerResultParcel) -> Unit): IConsumerResultCallback = object : IConsumerResultCallback.Stub() {
    override fun onResult(result: ConsumerResultParcel) = callback(result)
}

private fun generationCallback(callback: (ConsumerGenerationEventParcel) -> Unit): IConsumerGenerationCallback =
    object : IConsumerGenerationCallback.Stub() {
        override fun onEvent(event: ConsumerGenerationEventParcel) = callback(event)
    }

private fun controlPlaneResultCallback(callback: (ConsumerControlPlaneResultParcel) -> Unit): IConsumerControlPlaneResultCallback =
    object : IConsumerControlPlaneResultCallback.Stub() {
        override fun onResult(result: ConsumerControlPlaneResultParcel) = callback(result)
    }

private fun runtimeReadinessResultCallback(
    callback: (ConsumerRuntimeReadinessResultParcel) -> Unit,
): IConsumerRuntimeReadinessResultCallback = object : IConsumerRuntimeReadinessResultCallback.Stub() {
    override fun onResult(result: ConsumerRuntimeReadinessResultParcel) = callback(result)
}