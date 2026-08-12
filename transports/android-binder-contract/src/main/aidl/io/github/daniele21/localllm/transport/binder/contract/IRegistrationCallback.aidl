package io.github.daniele21.localllm.transport.binder.contract;

import io.github.daniele21.localllm.transport.binder.contract.RegistrationResultParcel;

oneway interface IRegistrationCallback {
    void onResult(in RegistrationResultParcel result);
}
