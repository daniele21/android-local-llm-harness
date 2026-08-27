package io.github.daniele21.localllm.transport.binder.contract;

import io.github.daniele21.localllm.transport.binder.contract.ConsumerRuntimeReadinessResultParcel;

interface IConsumerRuntimeReadinessResultCallback {
    void onResult(in ConsumerRuntimeReadinessResultParcel result);
}
