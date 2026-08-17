package io.github.daniele21.localllm.externalfixture;

import io.github.daniele21.localllm.contracts.ConsumerLocalLlmClient;
import io.github.daniele21.localllm.transport.binder.client.BinderConsumerLocalLlmClient;

/** Compile-only proof that the published client exposes contracts transitively to an external project. */
public final class ConsumerSdkSurface {
    private ConsumerSdkSurface() {}

    public static Class<?> publicClientType() {
        return ConsumerLocalLlmClient.class;
    }

    public static Class<?> binderClientType() {
        return BinderConsumerLocalLlmClient.class;
    }
}
