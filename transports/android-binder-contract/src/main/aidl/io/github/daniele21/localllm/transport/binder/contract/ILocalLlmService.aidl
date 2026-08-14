package io.github.daniele21.localllm.transport.binder.contract;

import io.github.daniele21.localllm.transport.binder.contract.CancelRequestParcel;
import io.github.daniele21.localllm.transport.binder.contract.ClientHelloParcel;
import io.github.daniele21.localllm.transport.binder.contract.ClientTokenParcel;
import io.github.daniele21.localllm.transport.binder.contract.CloseSessionRequestParcel;
import io.github.daniele21.localllm.transport.binder.contract.ConsumerRequestParcel;
import io.github.daniele21.localllm.transport.binder.contract.GenerationRequestParcel;
import io.github.daniele21.localllm.transport.binder.contract.IClientLifecycle;
import io.github.daniele21.localllm.transport.binder.contract.IConsumerLocalLlmService;
import io.github.daniele21.localllm.transport.binder.contract.IConsumerGenerationCallback;
import io.github.daniele21.localllm.transport.binder.contract.IConsumerResultCallback;
import io.github.daniele21.localllm.transport.binder.contract.IGenerationCallback;
import io.github.daniele21.localllm.transport.binder.contract.IPrepareCallback;
import io.github.daniele21.localllm.transport.binder.contract.IRegistrationCallback;
import io.github.daniele21.localllm.transport.binder.contract.ISessionCallback;
import io.github.daniele21.localllm.transport.binder.contract.OpenSessionRequestParcel;
import io.github.daniele21.localllm.transport.binder.contract.PrepareRequestParcel;
import io.github.daniele21.localllm.transport.binder.contract.ProtocolInfoParcel;

interface ILocalLlmService {
    ProtocolInfoParcel getProtocolInfo();

    void registerClient(
        in ClientHelloParcel hello,
        IClientLifecycle lifecycle,
        IRegistrationCallback callback
    );

    void prepare(in PrepareRequestParcel request, IPrepareCallback callback);

    void openSession(in OpenSessionRequestParcel request, ISessionCallback callback);

    void generate(in GenerationRequestParcel request, IGenerationCallback callback);

    oneway void cancel(in CancelRequestParcel request);

    oneway void closeSession(in CloseSessionRequestParcel request);

    oneway void unregisterClient(in ClientTokenParcel clientToken);

    IConsumerLocalLlmService getConsumerApi();
}
