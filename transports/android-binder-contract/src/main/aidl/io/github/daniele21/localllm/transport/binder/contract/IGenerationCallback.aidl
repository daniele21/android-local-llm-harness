package io.github.daniele21.localllm.transport.binder.contract;

import io.github.daniele21.localllm.transport.binder.contract.GenerationEventParcel;

oneway interface IGenerationCallback {
    void onEvent(in GenerationEventParcel event);
}
