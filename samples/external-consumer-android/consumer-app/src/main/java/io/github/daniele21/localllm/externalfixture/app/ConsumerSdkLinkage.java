package io.github.daniele21.localllm.externalfixture.app;

import io.github.daniele21.localllm.transport.binder.client.BinderConsumerLocalLlmClient;

/** Keeps the published Consumer SDK on the release-app reachability graph for R8 validation. */
public final class ConsumerSdkLinkage {
    private ConsumerSdkLinkage() {}

    public static Class<?> consumerClientType() {
        return BinderConsumerLocalLlmClient.class;
    }
}
