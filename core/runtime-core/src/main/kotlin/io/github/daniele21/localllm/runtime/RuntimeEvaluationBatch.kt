package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.GenerationMetrics
import io.github.daniele21.localllm.contracts.GenerationRequest
import io.github.daniele21.localllm.contracts.LocalLlmError
import io.github.daniele21.localllm.contracts.RequestId
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Runtime-only control-plane request for bounded evaluation batching.
 *
 * This deliberately does not extend LocalLlmClient: ordinary application generation keeps the
 * single-request public contract and SingleDecodeScheduler behavior.
 */
data class RuntimeEvaluationBatchRequest(val batchId: RequestId, val requests: List<GenerationRequest>) {
    init {
        require(requests.size in MIN_RUNTIME_EVALUATION_BATCH_WIDTH..MAX_RUNTIME_EVALUATION_BATCH_WIDTH) {
            "Runtime evaluation batch size must be in $MIN_RUNTIME_EVALUATION_BATCH_WIDTH..$MAX_RUNTIME_EVALUATION_BATCH_WIDTH"
        }
        require(requests.map(GenerationRequest::requestId).distinct().size == requests.size) {
            "Runtime evaluation batch request IDs must be unique"
        }
        require(requests.map(GenerationRequest::sessionId).distinct().size == requests.size) {
            "Runtime evaluation batch requires one isolated session per case"
        }
        require(requests.none { it.requestId == batchId }) {
            "Runtime evaluation batch ID must differ from every case request ID"
        }
    }

    companion object {
        const val MIN_RUNTIME_EVALUATION_BATCH_WIDTH = 2
        const val MAX_RUNTIME_EVALUATION_BATCH_WIDTH = 4
    }
}

sealed interface RuntimeEvaluationBatchCaseResult {
    val requestId: RequestId
    val metrics: GenerationMetrics

    data class Completed(
        override val requestId: RequestId,
        val output: String,
        override val metrics: GenerationMetrics,
    ) : RuntimeEvaluationBatchCaseResult

    data class Cancelled(
        override val requestId: RequestId,
        override val metrics: GenerationMetrics,
    ) : RuntimeEvaluationBatchCaseResult
}

sealed interface RuntimeEvaluationBatchOutcome {
    data class Completed(val cases: List<RuntimeEvaluationBatchCaseResult>) : RuntimeEvaluationBatchOutcome {
        init {
            require(cases.isNotEmpty()) { "Completed runtime evaluation batch must contain case results" }
            require(cases.map(RuntimeEvaluationBatchCaseResult::requestId).distinct().size == cases.size) {
                "Completed runtime evaluation batch must not contain duplicate request IDs"
            }
        }
    }

    data class Failed(val error: LocalLlmError) : RuntimeEvaluationBatchOutcome
}

fun interface RuntimeEvaluationBatchListener {
    fun onTerminal(outcome: RuntimeEvaluationBatchOutcome)
}

interface RuntimeEvaluationBatchHandle {
    val batchId: RequestId

    /** Cancels the complete queued or running batch. */
    fun cancel(): Boolean

    /**
     * Cooperatively cancels one case only after the batch has started.
     *
     * Queued per-case cancellation intentionally returns false; queued work is cancelled as one
     * scheduler unit so case attribution cannot silently diverge before execution begins.
     */
    fun cancelCase(requestId: RequestId): Boolean
}

interface RuntimeEvaluationBatchClient {
    fun generateEvaluationBatch(
        request: RuntimeEvaluationBatchRequest,
        listener: RuntimeEvaluationBatchListener,
    ): RuntimeEvaluationBatchHandle
}

internal class RuntimeEvaluationBatchLifecycle(
    val requestIds: Set<RequestId>,
    private val onTerminal: () -> Unit,
) {
    private val state = AtomicReference(RuntimeEvaluationBatchState.QUEUED)
    private val cancellationRequested = AtomicBoolean(false)

    fun markRunning(): Boolean = state.compareAndSet(RuntimeEvaluationBatchState.QUEUED, RuntimeEvaluationBatchState.RUNNING)

    fun isRunning(): Boolean = state.get() == RuntimeEvaluationBatchState.RUNNING

    fun requestCancellation(): Boolean = cancellationRequested.compareAndSet(false, true)

    fun isCancellationRequested(): Boolean = cancellationRequested.get()

    fun finish(listener: RuntimeEvaluationBatchListener, outcome: RuntimeEvaluationBatchOutcome) {
        while (true) {
            val current = state.get()
            if (current == RuntimeEvaluationBatchState.TERMINAL) return
            if (state.compareAndSet(current, RuntimeEvaluationBatchState.TERMINAL)) {
                try {
                    runCatching { listener.onTerminal(outcome) }
                } finally {
                    onTerminal()
                }
                return
            }
        }
    }
}

private enum class RuntimeEvaluationBatchState {
    QUEUED,
    RUNNING,
    TERMINAL,
}

internal class ScheduledRuntimeEvaluationBatchHandle(
    override val batchId: RequestId,
    private val schedulerHandle: DecodeTaskHandle,
    private val lifecycle: RuntimeEvaluationBatchLifecycle,
    private val cancelRunningCase: (RequestId) -> Boolean,
) : RuntimeEvaluationBatchHandle {
    override fun cancel(): Boolean {
        lifecycle.requestCancellation()
        return schedulerHandle.cancel()
    }

    override fun cancelCase(requestId: RequestId): Boolean {
        if (requestId !in lifecycle.requestIds || !lifecycle.isRunning()) return false
        return cancelRunningCase(requestId)
    }
}

internal class NoOpRuntimeEvaluationBatchHandle(override val batchId: RequestId) : RuntimeEvaluationBatchHandle {
    override fun cancel(): Boolean = false

    override fun cancelCase(requestId: RequestId): Boolean = false
}
