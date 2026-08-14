package io.github.daniele21.localllm.transport.binder.contract;

import io.github.daniele21.localllm.transport.binder.contract.ConsumerResultParcel;

oneway interface IConsumerResultCallback {
    void onResult(in ConsumerResultParcel result);
}
