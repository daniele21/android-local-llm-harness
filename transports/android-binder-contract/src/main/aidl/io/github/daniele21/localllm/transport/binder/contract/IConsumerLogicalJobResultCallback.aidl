package io.github.daniele21.localllm.transport.binder.contract;

import io.github.daniele21.localllm.transport.binder.contract.ConsumerLogicalJobResultParcel;

oneway interface IConsumerLogicalJobResultCallback {
    void onResult(in ConsumerLogicalJobResultParcel result);
}
