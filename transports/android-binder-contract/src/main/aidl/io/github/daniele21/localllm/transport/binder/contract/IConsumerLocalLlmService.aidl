package io.github.daniele21.localllm.transport.binder.contract;

import io.github.daniele21.localllm.transport.binder.contract.CancelRequestParcel;
import io.github.daniele21.localllm.transport.binder.contract.CloseSessionRequestParcel;
import io.github.daniele21.localllm.transport.binder.contract.ConsumerControlPlaneRequestParcel;
import io.github.daniele21.localllm.transport.binder.contract.ConsumerGenerationRequestV2Parcel;
import io.github.daniele21.localllm.transport.binder.contract.ConsumerLogicalJobQueryParcel;
import io.github.daniele21.localllm.transport.binder.contract.ConsumerLogicalJobSubmitParcel;
import io.github.daniele21.localllm.transport.binder.contract.ConsumerRequestParcel;
import io.github.daniele21.localllm.transport.binder.contract.IConsumerControlPlaneResultCallback;
import io.github.daniele21.localllm.transport.binder.contract.IConsumerGenerationCallback;
import io.github.daniele21.localllm.transport.binder.contract.IConsumerLogicalJobResultCallback;
import io.github.daniele21.localllm.transport.binder.contract.IConsumerResultCallback;
import io.github.daniele21.localllm.transport.binder.contract.IConsumerRuntimeReadinessResultCallback;

interface IConsumerLocalLlmService {
    void capabilities(in ConsumerRequestParcel request, IConsumerResultCallback callback);
    void prepare(in ConsumerRequestParcel request, IConsumerResultCallback callback);
    void openSession(in ConsumerRequestParcel request, IConsumerResultCallback callback);
    void generate(in ConsumerRequestParcel request, IConsumerGenerationCallback callback);
    oneway void cancel(in CancelRequestParcel request);
    oneway void closeSession(in CloseSessionRequestParcel request);

    void discoverUseCases(in ConsumerControlPlaneRequestParcel request, IConsumerControlPlaneResultCallback callback);
    void discoverPresets(in ConsumerControlPlaneRequestParcel request, IConsumerControlPlaneResultCallback callback);
    void activate(in ConsumerControlPlaneRequestParcel request, IConsumerControlPlaneResultCallback callback);
    void deactivate(in ConsumerControlPlaneRequestParcel request, IConsumerControlPlaneResultCallback callback);

    // Appended in protocol minor 3 so existing transaction IDs remain stable.
    void generateV2(in ConsumerGenerationRequestV2Parcel request, IConsumerGenerationCallback callback);

    // Appended in protocol minor 4; the request reuses control-plane activation identity only.
    void runtimeReadiness(in ConsumerControlPlaneRequestParcel request, IConsumerRuntimeReadinessResultCallback callback);

    // Appended in protocol minor 5. Read-only: no activation, preparation, model load or residency side effect.
    void resolveSetup(in ConsumerControlPlaneRequestParcel request, IConsumerControlPlaneResultCallback callback);

    // Appended in protocol minor 6. Detached jobs are owned by authenticated caller scope, not Binder connection lifetime.
    void submitLogicalGeneration(in ConsumerLogicalJobSubmitParcel request, IConsumerLogicalJobResultCallback callback);
    void getLogicalJob(in ConsumerLogicalJobQueryParcel request, IConsumerLogicalJobResultCallback callback);
    void getLogicalJobResult(in ConsumerLogicalJobQueryParcel request, IConsumerLogicalJobResultCallback callback);
    oneway void cancelLogicalJob(in ConsumerLogicalJobQueryParcel request);
}
