package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.transport.binder.contract.GenerationEventParcel

fun interface HostResultCallback<T> {
    fun onResult(result: T)
}

fun interface HostEventCallback {
    fun onEvent(event: GenerationEventParcel)
}
