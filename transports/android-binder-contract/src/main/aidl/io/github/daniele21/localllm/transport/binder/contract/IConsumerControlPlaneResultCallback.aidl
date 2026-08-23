package io.github.daniele21.localllm.transport.binder.contract;

import io.github.daniele21.localllm.transport.binder.contract.ConsumerControlPlaneResultParcel;

oneway interface IConsumerControlPlaneResultCallback {
    void onResult(in ConsumerControlPlaneResultParcel result);
}
