package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.contracts.ConsumerContentType
import io.github.daniele21.localllm.contracts.ConsumerErrorCode
import io.github.daniele21.localllm.contracts.ConsumerGenerationEvent
import io.github.daniele21.localllm.contracts.ConsumerGenerationHandle
import io.github.daniele21.localllm.contracts.ConsumerGenerationListener
import io.github.daniele21.localllm.contracts.ConsumerGenerationRequest
import io.github.daniele21.localllm.contracts.ConsumerGenerationStartResult
import io.github.daniele21.localllm.contracts.ConsumerLocalLlmClient
import io.github.daniele21.localllm.contracts.ConsumerPreparedId
import io.github.daniele21.localllm.contracts.ConsumerSessionResult
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.transport.binder.contract.ConsumerLogicalJobResultParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerLogicalJobSnapshotParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerLogicalJobSubmitParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerLogicalJobWireTags
import io.github.daniele21.localllm.transport.binder.contract.WireErrorCodes
import io.github.daniele21.localllm.transport.binder.contract.WireErrorParcel
import io.github.daniele21.localllm.transport.binder.contract.toConsumerWire
import io.github.daniele21.localllm.transport.binder.contract.toCoreConsumerInput
import io.github.daniele21.localllm.transport.binder.contract.toCoreConsumerOutput
import io.github.daniele21.localllm.transport.binder.contract.toCoreTaskDefinition
import java.util.LinkedHashMap

internal fun interface HostLogicalJobExecutionDemandListener {
    fun onExecutionDemandChanged(active: Boolean)
}

/**
 * Tracks semantic durable-job demand independently from Binder connection count and runtime handles.
 * The listener observes only the zero-to-one / one-to-zero boundary, so multiple concurrent queued or
 * running jobs cannot accidentally stop Android host lifetime while work still exists.
 */
internal class HostLogicalJobExecutionDemand(
    private val listener: HostLogicalJobExecutionDemandListener = HostLogicalJobExecutionDemandListener {},
) : AutoCloseable {
    private val lock = Any()
    private val activeJobs = LinkedHashSet<HostLogicalJobId>()

    fun acquire(jobId: HostLogicalJobId) {
        val becameActive =
            synchronized(lock) {
                val wasEmpty = activeJobs.isEmpty()
                val added = activeJobs.add(jobId)
                added && wasEmpty
            }
        if (becameActive) listener.onExecutionDemandChanged(true)
    }

    fun release(jobId: HostLogicalJobId) {
        val becameIdle =
            synchronized(lock) {
                val removed = activeJobs.remove(jobId)
                removed && activeJobs.isEmpty()
            }
        if (becameIdle) listener.onExecutionDemandChanged(false)
    }

    override fun close() {
        val hadDemand =
            synchronized(lock) {
                val active = activeJobs.isNotEmpty()
                activeJobs.clear()
                active
            }
        if (hadDemand) listener.onExecutionDemandChanged(false)
    }
}

/**
 * Owns detached Consumer generation execution independently from Binder connection ownership.
 *
 * The coordinator deliberately stores only bounded process-local replay data. The registry remains
 * the privacy-safe job-state source and contains no prompt or generated content.
 */
internal class HostLogicalJobCoordinator(
    private val registry: HostLogicalJobRegistry,
    executionDemandListener: HostLogicalJobExecutionDemandListener = HostLogicalJobExecutionDemandListener {},
    private val maxReplayResults: Int = DEFAULT_MAX_REPLAY_RESULTS,
) : AutoCloseable {
    private data class Execution(
        val client: ConsumerLocalLlmClient,
        val sessionId: SessionId,
        var handle: ConsumerGenerationHandle? = null,
    )

    private data class ReplayResult(
        val answer: String,
        val reasoning: String?,
        val metrics: io.github.daniele21.localllm.transport.binder.contract.ConsumerInferenceMetricsParcel?,
    )

    private val lock = Any()
    private val executions = LinkedHashMap<HostLogicalJobId, Execution>()
    private val replayResults = LinkedHashMap<HostLogicalJobId, ReplayResult>()
    private val errorCodes = LinkedHashMap<HostLogicalJobId, String>()
    private val executionDemand = HostLogicalJobExecutionDemand(executionDemandListener)

    init {
        require(maxReplayResults > 0) { "Logical job replay capacity must be positive" }
    }

    fun submit(
        caller: AuthorizedCaller,
        client: ConsumerLocalLlmClient,
        request: ConsumerLogicalJobSubmitParcel,
    ): ConsumerLogicalJobResultParcel {
        val scope = request.authorizedScope(caller)
            ?: return failure(request.operationId, WireErrorCodes.UNAUTHORIZED_USE_CASE)
        val clientRequestId = runCatching { HostClientRequestId(request.clientRequestId) }.getOrNull()
            ?: return failure(request.operationId, WireErrorCodes.INVALID_WIRE_REQUEST)
        val preparedId = request.preparedId.takeIf(String::isNotBlank)
            ?: return failure(request.operationId, WireErrorCodes.INVALID_WIRE_REQUEST)
        val submission = runCatching { registry.submit(scope, clientRequestId) }.getOrElse {
            return failure(request.operationId, WireErrorCodes.CLIENT_BACKPRESSURE)
        }
        if (!submission.created) return response(request.operationId, submission.snapshot)

        executionDemand.acquire(submission.snapshot.jobId)
        val coreInput = runCatching { request.input.toCoreConsumerInput() }.getOrNull()
        val coreOutput = runCatching { request.outputConstraint.toCoreConsumerOutput() }.getOrNull()
        val taskDefinitions = runCatching { request.taskDefinitions.map { it.toCoreTaskDefinition() } }.getOrNull()
        if (coreInput == null || coreOutput == null || taskDefinitions == null) {
            val failed = transition(submission.snapshot, HostLogicalJobState.FAILED_FINAL)
            recordError(submission.snapshot.jobId, WireErrorCodes.INVALID_WIRE_REQUEST)
            executionDemand.release(submission.snapshot.jobId)
            return response(request.operationId, failed)
        }

        val preparing = transition(submission.snapshot, HostLogicalJobState.PREPARING)
        val session = runCatching { client.createSession(ConsumerPreparedId(preparedId)) }.getOrNull()
        if (session !is ConsumerSessionResult.Created) {
            val failed = transition(preparing, HostLogicalJobState.FAILED_FINAL)
            recordError(
                preparing.jobId,
                (session as? ConsumerSessionResult.Rejected)?.failure?.code?.toWireCode() ?: WireErrorCodes.RUNTIME_FAILURE,
            )
            executionDemand.release(preparing.jobId)
            return response(request.operationId, failed)
        }

        val execution = Execution(client, session.sessionId)
        synchronized(lock) { executions[preparing.jobId] = execution }
        val running = transition(preparing, HostLogicalJobState.RUNNING)
        val coreRequest = ConsumerGenerationRequest(
            requestId = RequestId("logical:${running.jobId.value}"),
            sessionId = session.sessionId,
            input = coreInput,
            outputConstraint = coreOutput,
            taskDefinitions = taskDefinitions,
        )
        val start = runCatching {
            client.generate(coreRequest, ConsumerGenerationListener { event -> onEvent(running.scope, running.jobId, event) })
        }.getOrNull()
        when (start) {
            is ConsumerGenerationStartResult.Accepted -> synchronized(lock) {
                executions[running.jobId]?.handle = start.handle
            }

            is ConsumerGenerationStartResult.Rejected -> finishFailure(
                running.scope,
                running.jobId,
                start.failure.code.toWireCode(),
            )

            null -> finishFailure(running.scope, running.jobId, WireErrorCodes.RUNTIME_FAILURE)
        }
        return response(request.operationId, current(running.scope, running.jobId) ?: running)
    }

    fun query(operationId: String, scope: HostLogicalJobScope, jobId: HostLogicalJobId): ConsumerLogicalJobResultParcel {
        val snapshot = registry.snapshot(scope, jobId) ?: return failure(operationId, WireErrorCodes.INVALID_WIRE_REQUEST)
        return response(operationId, snapshot)
    }

    fun result(operationId: String, scope: HostLogicalJobScope, jobId: HostLogicalJobId): ConsumerLogicalJobResultParcel {
        val snapshot = registry.snapshot(scope, jobId) ?: return failure(operationId, WireErrorCodes.INVALID_WIRE_REQUEST)
        val replay = synchronized(lock) { replayResults[jobId] }
        return response(operationId, snapshot, replay)
    }

    fun cancel(scope: HostLogicalJobScope, jobId: HostLogicalJobId) {
        val current = registry.snapshot(scope, jobId) ?: return
        if (current.isTerminal || current.state == HostLogicalJobState.CANCEL_REQUESTED) return
        val requested = transition(current, HostLogicalJobState.CANCEL_REQUESTED)
        val handle = synchronized(lock) { executions[jobId]?.handle }
        if (handle != null) {
            runCatching(handle::cancel)
        } else {
            finishCancellation(requested.scope, requested.jobId)
        }
    }

    override fun close() {
        val active =
            synchronized(lock) {
                val copy = executions.values.toList()
                executions.clear()
                replayResults.clear()
                errorCodes.clear()
                copy
            }
        executionDemand.close()
        active.forEach { execution ->
            runCatching { execution.handle?.cancel() }
            runCatching { execution.client.closeSession(execution.sessionId) }
        }
    }

    private fun onEvent(scope: HostLogicalJobScope, jobId: HostLogicalJobId, event: ConsumerGenerationEvent) {
        when (event) {
            is ConsumerGenerationEvent.Completed -> {
                val wireMetrics = event.toConsumerWire(jobId.value, 0L).firstOrNull()?.metrics
                synchronized(lock) {
                    replayResults[jobId] = ReplayResult(event.answer, event.surfacedReasoning, wireMetrics)
                    trimReplayResults()
                }
                finish(scope, jobId, HostLogicalJobState.SUCCEEDED)
            }

            is ConsumerGenerationEvent.Failed -> {
                val current = registry.snapshot(scope, jobId) ?: return
                if (current.state == HostLogicalJobState.CANCEL_REQUESTED || event.failure.code == ConsumerErrorCode.CANCELLED) {
                    finishCancellation(scope, jobId)
                } else {
                    finishFailure(scope, jobId, event.failure.code.toWireCode())
                }
            }

            is ConsumerGenerationEvent.ContentDelta -> {
                // Deltas are intentionally not retained. Completed carries the canonical answer/reasoning.
                if (event.contentType == ConsumerContentType.REASONING || event.contentType == ConsumerContentType.ANSWER) Unit
            }

            else -> Unit
        }
    }

    private fun finishFailure(scope: HostLogicalJobScope, jobId: HostLogicalJobId, code: String) {
        recordError(jobId, code)
        finish(scope, jobId, HostLogicalJobState.FAILED_FINAL)
    }

    private fun finishCancellation(scope: HostLogicalJobScope, jobId: HostLogicalJobId) {
        recordError(jobId, WireErrorCodes.CANCELLED)
        finish(scope, jobId, HostLogicalJobState.CANCELLED)
    }

    private fun finish(scope: HostLogicalJobScope, jobId: HostLogicalJobId, state: HostLogicalJobState) {
        val current = registry.snapshot(scope, jobId) ?: return
        runCatching { transition(current, state) }
        val execution = synchronized(lock) { executions.remove(jobId) }
        if (execution != null) runCatching { execution.client.closeSession(execution.sessionId) }
        executionDemand.release(jobId)
    }

    private fun transition(current: HostLogicalJobSnapshot, state: HostLogicalJobState): HostLogicalJobSnapshot =
        checkNotNull(
            registry.transition(
                current.scope,
                current.jobId,
                HostLogicalJobTransition(
                    state = state,
                    revision = current.revision + 1,
                    attempt = current.attempt,
                    runtimeSessionId = current.runtimeSessionId,
                ),
            ),
        )

    private fun current(scope: HostLogicalJobScope, jobId: HostLogicalJobId): HostLogicalJobSnapshot? = registry.snapshot(scope, jobId)

    private fun response(
        operationId: String,
        snapshot: HostLogicalJobSnapshot,
        replay: ReplayResult? = null,
    ): ConsumerLogicalJobResultParcel =
        ConsumerLogicalJobResultParcel(
            operationId = operationId,
            snapshot = snapshot.toWire(resultAvailable(snapshot.jobId)),
            answerText = replay?.answer,
            reasoningText = replay?.reasoning,
            metrics = replay?.metrics,
            error = null,
        )

    private fun resultAvailable(jobId: HostLogicalJobId): Boolean = synchronized(lock) { replayResults.containsKey(jobId) }

    private fun recordError(jobId: HostLogicalJobId, code: String) {
        synchronized(lock) { errorCodes[jobId] = code }
    }

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

    private fun failure(operationId: String, code: String): ConsumerLogicalJobResultParcel =
        ConsumerLogicalJobResultParcel(
            operationId = operationId,
            error = WireErrorParcel(code = code, safeMessage = "Logical job request failed", retryable = false),
        )

    private companion object {
        const val DEFAULT_MAX_REPLAY_RESULTS = 32
    }
}

private fun ConsumerLogicalJobSubmitParcel.authorizedScope(caller: AuthorizedCaller): HostLogicalJobScope? {
    val useCase = useCaseId.takeIf(String::isNotBlank)?.let(::UseCaseId)?.takeIf(caller::allows) ?: return null
    return HostLogicalJobScope(caller.applicationId, useCase)
}

private fun HostLogicalJobState.toWireTag(): String =
    when (this) {
        HostLogicalJobState.QUEUED -> ConsumerLogicalJobWireTags.STATE_QUEUED
        HostLogicalJobState.PREPARING -> ConsumerLogicalJobWireTags.STATE_PREPARING
        HostLogicalJobState.RUNNING -> ConsumerLogicalJobWireTags.STATE_RUNNING
        HostLogicalJobState.SUCCEEDED -> ConsumerLogicalJobWireTags.STATE_SUCCEEDED
        HostLogicalJobState.CANCEL_REQUESTED -> ConsumerLogicalJobWireTags.STATE_CANCEL_REQUESTED
        HostLogicalJobState.CANCELLED -> ConsumerLogicalJobWireTags.STATE_CANCELLED
        HostLogicalJobState.FAILED_RETRYABLE -> ConsumerLogicalJobWireTags.STATE_FAILED_RETRYABLE
        HostLogicalJobState.RECOVERING -> ConsumerLogicalJobWireTags.STATE_RECOVERING
        HostLogicalJobState.INTERRUPTED -> ConsumerLogicalJobWireTags.STATE_INTERRUPTED
        HostLogicalJobState.FAILED_FINAL -> ConsumerLogicalJobWireTags.STATE_FAILED_FINAL
    }

private fun ConsumerErrorCode.toWireCode(): String =
    when (this) {
        ConsumerErrorCode.USE_CASE_NOT_ALLOWED -> WireErrorCodes.UNAUTHORIZED_USE_CASE

        ConsumerErrorCode.MODEL_UNAVAILABLE -> WireErrorCodes.MODEL_UNAVAILABLE

        ConsumerErrorCode.CANCELLED -> WireErrorCodes.CANCELLED

        ConsumerErrorCode.SESSION_NOT_FOUND,
        ConsumerErrorCode.PREPARED_SELECTION_NOT_FOUND,
        ConsumerErrorCode.PREPARED_SELECTION_STALE,
        -> WireErrorCodes.SESSION_UNAVAILABLE

        ConsumerErrorCode.INVALID_INPUT -> WireErrorCodes.INVALID_WIRE_REQUEST

        ConsumerErrorCode.PREPARE_FAILED -> WireErrorCodes.PREPARATION_FAILED

        else -> WireErrorCodes.RUNTIME_FAILURE
    }
