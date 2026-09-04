package io.github.daniele21.localllm.transport.binder.contract;

import io.github.daniele21.localllm.transport.binder.contract.ConsumerGenerationEventParcel;

oneway interface IConsumerGenerationCallback {
    void onEvent(in ConsumerGenerationEventParcel event);
}
