package io.github.daniele21.localllm.transport.binder.contract;

import io.github.daniele21.localllm.transport.binder.contract.PrepareResultParcel;

oneway interface IPrepareCallback {
    void onResult(in PrepareResultParcel result);
}
