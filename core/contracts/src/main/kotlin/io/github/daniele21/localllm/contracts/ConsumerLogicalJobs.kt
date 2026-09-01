package io.github.daniele21.localllm.contracts

@JvmInline
value class ConsumerInferenceJobId(val value: String) {
    init {
        require(SAFE_ID.matches(value)) { "Consumer inference job ID must be a privacy-safe identifier" }
    }

    private companion object {
        val SAFE_ID = Regex("^[A-Za-z0-9._:-]{1,96}$")
    }
}

@JvmInline
value class ConsumerLogicalJobRequestId(val value: String) {
    init {
        require(SAFE_ID.matches(value)) { "Consumer logical job request ID must be a privacy-safe identifier" }
    }

    private companion object {
        val SAFE_ID = Regex("^[A-Za-z0-9._:-]{1,128}$")
    }
}

@JvmInline
value class ConsumerRuntimeSessionId(val value: String) {
    init {
        require(SAFE_ID.matches(value)) { "Consumer runtime session ID must be a privacy-safe identifier" }
    }

    private companion object {
        val SAFE_ID = Regex("^[A-Za-z0-9._:-]{1,96}$")
    }
}

enum class ConsumerInferenceJobState {
    QUEUED,
    PREPARING,
    RUNNING,
    SUCCEEDED,
    CANCEL_REQUESTED,
    CANCELLED,
    FAILED_RETRYABLE,
    RECOVERING,
    INTERRUPTED,
    FAILED_FINAL,
}

data class ConsumerLogicalJobSubmitRequest(
    val clientRequestId: ConsumerLogicalJobRequestId,
    val useCaseId: UseCaseId,
    val preparedId: ConsumerPreparedId,
    val expectedExecution: ConsumerExecutionIdentity,
    val input: ConsumerGenerationInput,
    val outputConstraint: ConsumerOutputConstraint,
    val taskDefinitions: List<TaskDefinition> = emptyList(),
) {
    init {
        require(expectedExecution.useCaseId == useCaseId) { "Logical job execution identity must match the requested use case" }
        TaskDefinitionLimits.validate(taskDefinitions)
    }

    override fun toString(): String =
        "ConsumerLogicalJobSubmitRequest(clientRequestId=$clientRequestId, useCaseId=$useCaseId, preparedId=$preparedId, " +
            "expectedExecution=$expectedExecution, input=<redacted>, outputConstraint=${outputConstraint::class.simpleName}, " +
            "taskDefinitionCount=${taskDefinitions.size})"
}

data class ConsumerInferenceJobSnapshot(
    val jobId: ConsumerInferenceJobId,
    val clientRequestId: ConsumerLogicalJobRequestId,
    val useCaseId: UseCaseId,
    val execution: ConsumerExecutionIdentity,
    val state: ConsumerInferenceJobState,
    val revision: Long,
    val attempt: Int,
    val runtimeSessionId: ConsumerRuntimeSessionId,
    val resultAvailable: Boolean,
    val errorCode: ConsumerErrorCode? = null,
) {
    init {
        require(execution.useCaseId == useCaseId) { "Logical job execution identity must match the job use case" }
        require(revision >= 0) { "Consumer inference job revision must be non-negative" }
        require(attempt >= 1) { "Consumer inference job attempt must be positive" }
    }
}

data class ConsumerInferenceJobOutput(val answer: String, val surfacedReasoning: String?, val metrics: ConsumerInferenceMetrics)

sealed interface ConsumerInferenceJobResponse {
    data class Available(val snapshot: ConsumerInferenceJobSnapshot, val output: ConsumerInferenceJobOutput? = null) :
        ConsumerInferenceJobResponse

    data class Rejected(val failure: ConsumerFailure) : ConsumerInferenceJobResponse
}

interface ConsumerLogicalJobClient {
    fun submitLogicalGeneration(request: ConsumerLogicalJobSubmitRequest): ConsumerInferenceJobResponse

    fun logicalJob(jobId: ConsumerInferenceJobId, useCaseId: UseCaseId): ConsumerInferenceJobResponse

    fun logicalJobResult(jobId: ConsumerInferenceJobId, useCaseId: UseCaseId): ConsumerInferenceJobResponse

    fun cancelLogicalJob(jobId: ConsumerInferenceJobId, useCaseId: UseCaseId)
}

fun ConsumerPreparedSelection.toExecutionIdentity(): ConsumerExecutionIdentity =
    ConsumerExecutionIdentity(
        useCaseId = useCaseId,
        capabilityRevision = capabilityRevision,
        preset = preset,
        reasoningMode = reasoningMode,
        outputConstraint = outputConstraint,
        sessionKind = sessionKind,
    )
