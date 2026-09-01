package io.github.daniele21.localllm.transport.binder.contract

import io.github.daniele21.localllm.contracts.ConsumerInferenceJobId
import io.github.daniele21.localllm.contracts.ConsumerInferenceJobOutput
import io.github.daniele21.localllm.contracts.ConsumerInferenceJobResponse
import io.github.daniele21.localllm.contracts.ConsumerInferenceJobSnapshot
import io.github.daniele21.localllm.contracts.ConsumerInferenceJobState
import io.github.daniele21.localllm.contracts.ConsumerLogicalJobRequestId
import io.github.daniele21.localllm.contracts.ConsumerLogicalJobSubmitRequest
import io.github.daniele21.localllm.contracts.ConsumerRuntimeSessionId
import io.github.daniele21.localllm.contracts.UseCaseId

fun ConsumerLogicalJobSubmitRequest.toConsumerLogicalJobWire(
    clientToken: ClientTokenParcel,
    operationId: String,
): ConsumerLogicalJobSubmitParcel =
    ConsumerLogicalJobSubmitParcel(
        clientToken = clientToken,
        operationId = operationId,
        clientRequestId = clientRequestId.value,
        useCaseId = useCaseId.value,
        preparedId = preparedId.value,
        input = input.toConsumerWire(),
        outputConstraint = outputConstraint.toConsumerWire(),
        taskDefinitions = taskDefinitions.map { it.toConsumerWire() },
    )

fun consumerLogicalJobQueryWire(
    clientToken: ClientTokenParcel,
    operationId: String,
    jobId: ConsumerInferenceJobId,
    useCaseId: UseCaseId,
): ConsumerLogicalJobQueryParcel =
    ConsumerLogicalJobQueryParcel(
        clientToken = clientToken,
        operationId = operationId,
        jobId = jobId.value,
        useCaseId = useCaseId.value,
    )

fun ConsumerLogicalJobResultParcel.toCoreLogicalJobResponse(): ConsumerInferenceJobResponse {
    error?.let { return ConsumerInferenceJobResponse.Rejected(it.toConsumerFailure()) }
    val coreSnapshot = requireNotNull(snapshot).toCoreLogicalJobSnapshot()
    val output = when {
        answerText == null && metrics == null -> null
        answerText != null && metrics != null ->
            ConsumerInferenceJobOutput(
                answer = answerText,
                surfacedReasoning = reasoningText,
                metrics = metrics.toCoreConsumerMetrics(),
            )
        else -> error("Logical job result payload is incomplete")
    }
    return ConsumerInferenceJobResponse.Available(coreSnapshot, output)
}

private fun ConsumerLogicalJobSnapshotParcel.toCoreLogicalJobSnapshot() =
    ConsumerInferenceJobSnapshot(
        jobId = ConsumerInferenceJobId(jobId),
        clientRequestId = ConsumerLogicalJobRequestId(clientRequestId),
        useCaseId = UseCaseId(useCaseId),
        state = enumTag(stateTag, "consumer inference job state"),
        revision = revision,
        attempt = attempt,
        runtimeSessionId = ConsumerRuntimeSessionId(runtimeSessionId),
        resultAvailable = resultAvailable,
        errorCode = errorCode?.let {
            WireErrorParcel(it, "Logical job failed", false).toConsumerFailure().code
        },
    )
