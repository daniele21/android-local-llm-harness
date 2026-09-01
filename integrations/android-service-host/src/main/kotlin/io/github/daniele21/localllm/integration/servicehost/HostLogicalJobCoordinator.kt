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
import io.github.daniele21.localllm.transport.binder.contract.ConsumerLogicalJobSubmitParcel
import io.github.daniele21.localllm.transport.binder.contract.ConsumerLogicalJobWireTags
import io.github.daniele21.localllm.transport.binder.contract.WireErrorCodes
import io.github.daniele21.localllm.transport.binder.contract.toCoreConsumerInput
import io.github.daniele21.localllm.transport.binder.contract.toCoreConsumerOutput
import io.github.daniele21.localllm.transport.binder.contract.toCoreTaskDefinition
import java.util.LinkedHashMap

/**
 * Tracks semantic durable-job demand independently from Binder connection count and runtime handles.
 * The listener observes only the zero-to-one / one-to-zero boundary.
 */
internal class HostLogicalJobExecutionDemand : AutoCloseable {
    private val lock = Any()
    private val activeJobs = LinkedHashSet<HostLogicalJobId>()
    private var listener: (Boolean) -> Unit = {}

    fun setListener(listener: (Boolean) -> Unit) {
        val active = synchronized(lock) {
            this.listener = listener
            activeJobs.isNotEmpty()
        }
        if (active) listener(true)
    }

    fun acquire(jobId: HostLogicalJobId) {
        val callback = synchronized(lock) {
            val wasEmpty = activeJobs.isEmpty()
            val added = activeJobs.add(jobId)
            if (added && wasEmpty) listener else null
        }
        callback?.invoke(true)
    }

    fun release(jobId: HostLogicalJobId) {
        val callback = synchronized(lock) {
            val removed = activeJobs.remove(jobId)
            if (removed && activeJobs.isEmpty()) listener else null
        }
        callback?.invoke(false)
    }

    override fun close() {
        val callback = synchronized(lock) {
            val hadDemand = activeJobs.isNotEmpty()
            activeJobs.clear()
            if (hadDemand) listener else null
        }
        callback?.invoke(false)
    }
}

/** Owns detached Consumer generation execution independently from Binder connection ownership. */
internal class HostLogicalJobCoordinator(
    private val registry: HostLogicalJobRegistry,
    private val executionDemand: HostLogicalJobExecutionDemand = HostLogicalJobExecutionDemand(),
    private val resultStore: HostLogicalJobResultStore = HostLogicalJobResultStore(),
) : AutoCloseable {
    private data class Execution(
        val client: ConsumerLocalLlmClient,
        val sessionId: SessionId,
        var handle: ConsumerGenerationHandle? = null,
    )

    private val lock = Any()
    private val executions = LinkedHashMap<HostLogicalJobId, Execution>()

    fun submit(
        caller: AuthorizedCaller,
        client: ConsumerLocalLlmClient,
        request: ConsumerLogicalJobSubmitParcel,
    ): ConsumerLogicalJobResultParcel {
        val identity = request.resolveSubmissionIdentity(caller)
        if (identity is SubmissionIdentityResolution.Rejected) {
            return resultStore.failure(request.operationId, identity.code)
        }
        identity as SubmissionIdentityResolution.Valid
        val submission = runCatching { registry.submit(identity.scope, identity.clientRequestId) }.getOrNull()
            ?: return resultStore.failure(request.operationId, WireErrorCodes.CLIENT_BACKPRESSURE)
        if (!submission.created) return resultStore.response(request.operationId, submission.snapshot)

        executionDemand.acquire(submission.snapshot.jobId)
        return startCreatedJob(client, request, identity.preparedId, submission.snapshot)
    }

    fun query(operationId: String, scope: HostLogicalJobScope, jobId: HostLogicalJobId): ConsumerLogicalJobResultParcel {
        val snapshot = registry.snapshot(scope, jobId)
            ?: return resultStore.failure(operationId, WireErrorCodes.INVALID_WIRE_REQUEST)
        return resultStore.response(operationId, snapshot)
    }

    fun result(operationId: String, scope: HostLogicalJobScope, jobId: HostLogicalJobId): ConsumerLogicalJobResultParcel {
        val snapshot = registry.snapshot(scope, jobId)
            ?: return resultStore.failure(operationId, WireErrorCodes.INVALID_WIRE_REQUEST)
        return resultStore.response(operationId, snapshot, includeReplay = true)
    }

    fun cancel(scope: HostLogicalJobScope, jobId: HostLogicalJobId) {
        val current = registry.snapshot(scope, jobId) ?: return
        if (current.isTerminal || current.state == HostLogicalJobState.CANCEL_REQUESTED) return
        val requested = transitionLogicalJob(registry, current, HostLogicalJobState.CANCEL_REQUESTED)
        val handle = synchronized(lock) { executions[jobId]?.handle }
        if (handle != null) {
            runCatching(handle::cancel)
        } else {
            finishCancellation(requested.scope, requested.jobId)
        }
    }

    override fun close() {
        val active = synchronized(lock) {
            val copy = executions.values.toList()
            executions.clear()
            copy
        }
        resultStore.clear()
        executionDemand.close()
        active.forEach { execution ->
            runCatching { execution.handle?.cancel() }
            runCatching { execution.client.closeSession(execution.sessionId) }
        }
    }

    private fun startCreatedJob(
        client: ConsumerLocalLlmClient,
        request: ConsumerLogicalJobSubmitParcel,
        preparedId: String,
        submitted: HostLogicalJobSnapshot,
    ): ConsumerLogicalJobResultParcel {
        val requestParts = runCatching {
            Triple(
                request.input.toCoreConsumerInput(),
                request.outputConstraint.toCoreConsumerOutput(),
                request.taskDefinitions.map { it.toCoreTaskDefinition() },
            )
        }.getOrNull()
        if (requestParts == null) {
            val failed = transitionLogicalJob(registry, submitted, HostLogicalJobState.FAILED_FINAL)
            resultStore.recordError(submitted.jobId, WireErrorCodes.INVALID_WIRE_REQUEST)
            executionDemand.release(submitted.jobId)
            return resultStore.response(request.operationId, failed)
        }

        val preparing = transitionLogicalJob(registry, submitted, HostLogicalJobState.PREPARING)
        val session = runCatching { client.createSession(ConsumerPreparedId(preparedId)) }.getOrNull()
        if (session !is ConsumerSessionResult.Created) {
            val failed = transitionLogicalJob(registry, preparing, HostLogicalJobState.FAILED_FINAL)
            resultStore.recordError(
                preparing.jobId,
                (session as? ConsumerSessionResult.Rejected)?.failure?.code?.toWireCode() ?: WireErrorCodes.RUNTIME_FAILURE,
            )
            executionDemand.release(preparing.jobId)
            return resultStore.response(request.operationId, failed)
        }

        val execution = Execution(client, session.sessionId)
        synchronized(lock) { executions[preparing.jobId] = execution }
        val running = transitionLogicalJob(registry, preparing, HostLogicalJobState.RUNNING)
        val coreRequest = ConsumerGenerationRequest(
            requestId = RequestId("logical:${running.jobId.value}"),
            sessionId = session.sessionId,
            input = requestParts.first,
            outputConstraint = requestParts.second,
            taskDefinitions = requestParts.third,
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
        val current = registry.snapshot(running.scope, running.jobId) ?: running
        return resultStore.response(request.operationId, current)
    }

    private fun onEvent(scope: HostLogicalJobScope, jobId: HostLogicalJobId, event: ConsumerGenerationEvent) {
        when (event) {
            is ConsumerGenerationEvent.Completed -> {
                resultStore.recordSuccess(jobId, event)
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
                if (event.contentType == ConsumerContentType.REASONING || event.contentType == ConsumerContentType.ANSWER) Unit
            }

            else -> Unit
        }
    }

    private fun finishFailure(scope: HostLogicalJobScope, jobId: HostLogicalJobId, code: String) {
        resultStore.recordError(jobId, code)
        finish(scope, jobId, HostLogicalJobState.FAILED_FINAL)
    }

    private fun finishCancellation(scope: HostLogicalJobScope, jobId: HostLogicalJobId) {
        resultStore.recordError(jobId, WireErrorCodes.CANCELLED)
        finish(scope, jobId, HostLogicalJobState.CANCELLED)
    }

    private fun finish(scope: HostLogicalJobScope, jobId: HostLogicalJobId, state: HostLogicalJobState) {
        val current = registry.snapshot(scope, jobId) ?: return
        runCatching { transitionLogicalJob(registry, current, state) }
        val execution = synchronized(lock) { executions.remove(jobId) }
        if (execution != null) runCatching { execution.client.closeSession(execution.sessionId) }
        executionDemand.release(jobId)
    }
}

private sealed interface SubmissionIdentityResolution {
    data class Valid(
        val scope: HostLogicalJobScope,
        val clientRequestId: HostClientRequestId,
        val preparedId: String,
    ) : SubmissionIdentityResolution

    data class Rejected(val code: String) : SubmissionIdentityResolution
}

private fun ConsumerLogicalJobSubmitParcel.resolveSubmissionIdentity(caller: AuthorizedCaller): SubmissionIdentityResolution {
    val scope = authorizedScope(caller)
        ?: return SubmissionIdentityResolution.Rejected(WireErrorCodes.UNAUTHORIZED_USE_CASE)
    val clientRequestId = runCatching { HostClientRequestId(clientRequestId) }.getOrNull()
        ?: return SubmissionIdentityResolution.Rejected(WireErrorCodes.INVALID_WIRE_REQUEST)
    val preparedId = preparedId.takeIf(String::isNotBlank)
        ?: return SubmissionIdentityResolution.Rejected(WireErrorCodes.INVALID_WIRE_REQUEST)
    return SubmissionIdentityResolution.Valid(scope, clientRequestId, preparedId)
}

private fun transitionLogicalJob(
    registry: HostLogicalJobRegistry,
    current: HostLogicalJobSnapshot,
    state: HostLogicalJobState,
): HostLogicalJobSnapshot = checkNotNull(
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

private fun ConsumerLogicalJobSubmitParcel.authorizedScope(caller: AuthorizedCaller): HostLogicalJobScope? {
    val useCase = useCaseId.takeIf(String::isNotBlank)?.let(::UseCaseId)?.takeIf(caller::allows) ?: return null
    return HostLogicalJobScope(caller.applicationId, useCase)
}

internal fun HostLogicalJobState.toWireTag(): String = when (this) {
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

private fun ConsumerErrorCode.toWireCode(): String = when (this) {
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
