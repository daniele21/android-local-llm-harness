package io.github.daniele21.localllm.transport.binder.contract;

import io.github.daniele21.localllm.transport.binder.contract.CancelRequestParcel;
import io.github.daniele21.localllm.transport.binder.contract.CloseSessionRequestParcel;
import io.github.daniele21.localllm.transport.binder.contract.ConsumerRequestParcel;
import io.github.daniele21.localllm.transport.binder.contract.IConsumerGenerationCallback;
import io.github.daniele21.localllm.transport.binder.contract.IConsumerResultCallback;

interface IConsumerLocalLlmService {
    void capabilities(in ConsumerRequestParcel request, IConsumerResultCallback callback);
    void prepare(in ConsumerRequestParcel request, IConsumerResultCallback callback);
    void openSession(in ConsumerRequestParcel request, IConsumerResultCallback callback);
    void generate(in ConsumerRequestParcel request, IConsumerGenerationCallback callback);
    oneway void cancel(in CancelRequestParcel request);
    oneway void closeSession(in CloseSessionRequestParcel request);
}
