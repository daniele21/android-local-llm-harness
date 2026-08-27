package io.github.daniele21.localllm.integration.servicehost

import io.github.daniele21.localllm.contracts.ConsumerCapabilityResult
import io.github.daniele21.localllm.contracts.ConsumerErrorCode
import io.github.daniele21.localllm.contracts.ConsumerGenerationEvent
import io.github.daniele21.localllm.contracts.ConsumerGenerationListener
import io.github.daniele21.localllm.contracts.ConsumerGenerationRequest
import io.github.daniele21.localllm.contracts.ConsumerGenerationStartResult
import io.github.daniele21.localllm.contracts.ConsumerLocalLlmClient
import io.github.daniele21.localllm.contracts.ConsumerPrepareRequest
import io.github.daniele21.localllm.contracts.ConsumerPrepareResult
import io.github.daniele21.localllm.contracts.ConsumerPreparedId
import io.github.daniele21.localllm.contracts.ConsumerRuntimeIssue
import io.github.daniele21.localllm.contracts.ConsumerSessionResult
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.UseCaseId
import java.util.concurrent.atomic.AtomicBoolean

/** Adds connection attribution around a consumer client without taking ownership of runtime state. */
internal class RuntimeActivityTrackingConsumerClient(
    private val token: HostClientToken,
    private val delegate: ConsumerLocalLlmClient,
    private val activity: ConsumerRuntimeActivityTracker,
) : ConsumerLocalLlmClient {
    override fun capabilities(useCaseId: UseCaseId): ConsumerCapabilityResult = delegate.capabilities(useCaseId)

    override fun prepare(request: ConsumerPrepareRequest): ConsumerPrepareResult {
        activity.beginPreparation(token)
        return try {
            delegate.prepare(request).also { result ->
                val issue = (result as? ConsumerPrepareResult.Rejected)?.failure?.code?.toRuntimeIssue()
                activity.finishPreparation(token, issue)
            }
        } catch (error: Throwable) {
            activity.finishPreparation(token, ConsumerRuntimeIssue.RUNTIME_FAILED)
            throw error
        }
    }

    override fun createSession(preparedId: ConsumerPreparedId): ConsumerSessionResult = delegate.createSession(preparedId)

    override fun generate(
        request: ConsumerGenerationRequest,
        listener: ConsumerGenerationListener,
    ): ConsumerGenerationStartResult {
        val terminal = AtomicBoolean(false)
        activity.beginGeneration(token)
        val trackingListener = ConsumerGenerationListener { event ->
            try {
                listener.onEvent(event)
            } finally {
                if (event.isTerminal() && terminal.compareAndSet(false, true)) {
                    activity.finishGeneration(token, event.runtimeIssueOrNull())
                }
            }
        }
        return try {
            delegate.generate(request, trackingListener).also { result ->
                if (result is ConsumerGenerationStartResult.Rejected && terminal.compareAndSet(false, true)) {
                    activity.finishGeneration(token, result.failure.code.toRuntimeIssue())
                }
            }
        } catch (error: Throwable) {
            if (terminal.compareAndSet(false, true)) {
                activity.finishGeneration(token, ConsumerRuntimeIssue.RUNTIME_FAILED)
            }
            throw error
        }
    }

    override fun closeSession(sessionId: SessionId) {
        delegate.closeSession(sessionId)
    }
}

private fun ConsumerGenerationEvent.isTerminal(): Boolean =
    this is ConsumerGenerationEvent.Completed || this is ConsumerGenerationEvent.Failed

private fun ConsumerGenerationEvent.runtimeIssueOrNull(): ConsumerRuntimeIssue? = when (this) {
    is ConsumerGenerationEvent.Failed -> failure.code.toRuntimeIssue()
    else -> null
}

private fun ConsumerErrorCode.toRuntimeIssue(): ConsumerRuntimeIssue = when (this) {
    ConsumerErrorCode.MODEL_UNAVAILABLE -> ConsumerRuntimeIssue.MODEL_UNAVAILABLE
    ConsumerErrorCode.STALE_CAPABILITY,
    ConsumerErrorCode.PREPARED_SELECTION_STALE,
    ConsumerErrorCode.PREPARED_SELECTION_NOT_FOUND,
    -> ConsumerRuntimeIssue.CONFIGURATION_STALE

    ConsumerErrorCode.CANCELLED -> ConsumerRuntimeIssue.CANCELLED
    ConsumerErrorCode.RUNTIME_FAILURE -> ConsumerRuntimeIssue.RUNTIME_FAILED
    ConsumerErrorCode.PREPARE_FAILED -> ConsumerRuntimeIssue.PREPARATION_FAILED
    else -> ConsumerRuntimeIssue.PREPARATION_FAILED
}
