package io.github.daniele21.localllm.transport.binder.contract;

import io.github.daniele21.localllm.transport.binder.contract.SessionResultParcel;

oneway interface ISessionCallback {
    void onResult(in SessionResultParcel result);
}
