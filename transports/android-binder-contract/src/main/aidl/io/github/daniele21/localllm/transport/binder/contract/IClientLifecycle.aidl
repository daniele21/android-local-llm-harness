package io.github.daniele21.localllm.transport.binder.contract;

oneway interface IClientLifecycle {
    void onHostDisconnecting();
}
