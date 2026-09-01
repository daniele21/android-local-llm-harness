package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.contracts.ConsumerGenerationEvent
import io.github.daniele21.localllm.transport.binder.contract.ConsumerInferenceMetricsParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerLogicalJobResultParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerLogicalJobSnapshotParcel
import io.github.daniele21.localllm.transport.binder.contract.WireErrorParcel
import io.github.daniele21.localllm.transport.binder.contract.toConsumerWire
import java.util.LinkedHashMap

/** Bounded, process-local replay/error store for durable logical jobs. */
internal class HostLogicalJobResultStore(private val maxReplayResults: Int = DEFAULT_MAX_REPLAY_RESULTS) {
    private data class ReplayResult(val answer: String, val reasoning: String?, val metrics: ConsumerInferenceMetricsParcel?)

    private val lock = Any()
    private val replayResults = LinkedHashMap<HostLogicalJobId, ReplayResult>()
    private val errorCodes = LinkedHashMap<HostLogicalJobId, String>()

    init {
        require(maxReplayResults > 0) { "Logical job replay capacity must be positive" }
    }

    fun recordSuccess(jobId: HostLogicalJobId, event: ConsumerGenerationEvent.Completed) {
        val wireMetrics = event.toConsumerWire(jobId.value, 0L).firstOrNull()?.metrics
        synchronized(lock) {
            replayResults[jobId] = ReplayResult(event.answer, event.surfacedReasoning, wireMetrics)
            trimReplayResults()
        }
    }

    fun recordError(jobId: HostLogicalJobId, code: String) {
        synchronized(lock) { errorCodes[jobId] = code }
    }

    fun response(operationId: String, snapshot: HostLogicalJobSnapshot, includeReplay: Boolean = false): ConsumerLogicalJobResultParcel {
        val replay = if (includeReplay) synchronized(lock) { replayResults[snapshot.jobId] } else null
        return ConsumerLogicalJobResultParcel(
            operationId = operationId,
            snapshot = snapshot.toWire(resultAvailable(snapshot.jobId)),
            answerText = replay?.answer,
            reasoningText = replay?.reasoning,
            metrics = replay?.metrics,
            error = null,
        )
    }

    fun failure(operationId: String, code: String): ConsumerLogicalJobResultParcel = ConsumerLogicalJobResultParcel(
        operationId = operationId,
        error = WireErrorParcel(code = code, safeMessage = "Logical job request failed", retryable = false),
    )

    fun clear() {
        synchronized(lock) {
            replayResults.clear()
            errorCodes.clear()
        }
    }

    private fun resultAvailable(jobId: HostLogicalJobId): Boolean = synchronized(lock) { replayResults.containsKey(jobId) }

    private fun HostLogicalJobSnapshot.toWire(resultAvailable: Boolean): ConsumerLogicalJobSnapshotParcel =
        ConsumerLogicalJobSnapshotParcel(
            jobId = jobId.value,
            clientRequestId = clientRequestId.value,
            useCaseId = scope.useCaseId.value,
            stateTag = state.toWireTag(),
            revision = revision,
            attempt = attempt,
            runtimeSessionId = runtimeSessionId.value,
            resultAvailable = resultAvailable,
            errorCode = synchronized(lock) { errorCodes[jobId] },
        )

    private fun trimReplayResults() {
        while (replayResults.size > maxReplayResults) {
            val oldest = replayResults.entries.firstOrNull() ?: return
            replayResults.remove(oldest.key)
        }
    }

    private companion object {
        const val DEFAULT_MAX_REPLAY_RESULTS = 32
    }
}
