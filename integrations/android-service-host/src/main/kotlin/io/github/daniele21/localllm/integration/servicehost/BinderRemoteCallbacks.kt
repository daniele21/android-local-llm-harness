package io.github.daniele21.localllm.integration.servicehost

import android.os.RemoteException
import io.github.daniele21.localllm.transport.binder.contract.ClientTokenParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerControlPlaneResultParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerResultParcel
import io.github.daniele21.localllm.transport.binder.contract.IConsumerControlPlaneResultCallback
import io.github.daniele21.localllm.transport.binder.contract.IConsumerGenerationCallback
import io.github.daniele21.localllm.transport.binder.contract.IConsumerResultCallback
import io.github.daniele21.localllm.transport.binder.contract.IGenerationCallback
import io.github.daniele21.localllm.transport.binder.contract.IPrepareCallback
import io.github.daniele21.localllm.transport.binder.contract.IRegistrationCallback
import io.github.daniele21.localllm.transport.binder.contract.ISessionCallback
import io.github.daniele21.localllm.transport.binder.contract.PrepareResultParcel
import io.github.daniele21.localllm.transport.binder.contract.RegistrationResultParcel
import io.github.daniele21.localllm.transport.binder.contract.SessionResultParcel

internal fun remoteRegistrationCallback(
    delegate: SharedRuntimeHostDelegate,
    caller: AuthorizedCaller,
    remote: IRegistrationCallback,
): HostResultCallback<RegistrationResultParcel> = HostResultCallback { result ->
    if (!deliverRemote { remote.onResult(result) }) {
        result.clientToken?.let { token -> delegate.unregisterClient(caller, token.value) }
    }
}

internal fun remotePrepareCallback(
    delegate: SharedRuntimeHostDelegate,
    caller: AuthorizedCaller,
    token: ClientTokenParcel,
    remote: IPrepareCallback,
): HostResultCallback<PrepareResultParcel> = HostResultCallback { result ->
    if (!deliverRemote { remote.onResult(result) }) {
        delegate.unregisterClient(caller, token.value)
    }
}

internal fun remoteSessionCallback(
    delegate: SharedRuntimeHostDelegate,
    caller: AuthorizedCaller,
    token: ClientTokenParcel,
    remote: ISessionCallback,
): HostResultCallback<SessionResultParcel> = HostResultCallback { result ->
    if (!deliverRemote { remote.onResult(result) }) {
        delegate.unregisterClient(caller, token.value)
    }
}

internal fun remoteGenerationCallback(
    delegate: SharedRuntimeHostDelegate,
    caller: AuthorizedCaller,
    token: ClientTokenParcel,
    remote: IGenerationCallback,
): HostEventCallback = HostEventCallback { event ->
    if (!deliverRemote { remote.onEvent(event) }) {
        delegate.unregisterClient(caller, token.value)
        throw RemoteGenerationCallbackFailure()
    }
}

internal fun remoteConsumerResultCallback(
    delegate: SharedRuntimeHostDelegate,
    caller: AuthorizedCaller,
    token: ClientTokenParcel,
    remote: IConsumerResultCallback,
): HostResultCallback<ConsumerResultParcel> = HostResultCallback { result ->
    if (!deliverRemote { remote.onResult(result) }) {
        delegate.unregisterClient(caller, token.value)
    }
}

internal fun remoteConsumerControlPlaneResultCallback(
    delegate: SharedRuntimeHostDelegate,
    caller: AuthorizedCaller,
    token: ClientTokenParcel,
    remote: IConsumerControlPlaneResultCallback,
): HostResultCallback<ConsumerControlPlaneResultParcel> = HostResultCallback { result ->
    if (!deliverRemote { remote.onResult(result) }) {
        delegate.unregisterClient(caller, token.value)
    }
}

internal fun remoteConsumerGenerationCallback(
    delegate: SharedRuntimeHostDelegate,
    caller: AuthorizedCaller,
    token: ClientTokenParcel,
    remote: IConsumerGenerationCallback,
): ConsumerHostEventCallback = ConsumerHostEventCallback { event ->
    if (!deliverRemote { remote.onEvent(event) }) {
        delegate.unregisterClient(caller, token.value)
        throw RemoteGenerationCallbackFailure()
    }
}

internal fun deliverRemote(block: () -> Unit): Boolean = try {
    block()
    true
} catch (_: RemoteException) {
    false
} catch (_: RuntimeException) {
    false
}

private class RemoteGenerationCallbackFailure : RuntimeException()
