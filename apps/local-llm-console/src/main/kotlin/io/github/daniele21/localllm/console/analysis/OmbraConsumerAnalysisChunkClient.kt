package io.github.daniele21.localllm.console.analysis

import io.github.daniele21.localllm.console.application.OmbraOperationId
import io.github.daniele21.localllm.contracts.ConsumerCapabilityErrorCode
import io.github.daniele21.localllm.contracts.ConsumerCapabilityResult
import io.github.daniele21.localllm.contracts.ConsumerContentType
import io.github.daniele21.localllm.contracts.ConsumerErrorCode
import io.github.daniele21.localllm.contracts.ConsumerExecutionIdentity
import io.github.daniele21.localllm.contracts.ConsumerFailure
import io.github.daniele21.localllm.contracts.ConsumerGenerationEvent
import io.github.daniele21.localllm.contracts.ConsumerGenerationHandle
import io.github.daniele21.localllm.contracts.ConsumerGenerationInput
import io.github.daniele21.localllm.contracts.ConsumerGenerationListener
import io.github.daniele21.localllm.contracts.ConsumerGenerationRequest
import io.github.daniele21.localllm.contracts.ConsumerGenerationStartResult
import io.github.daniele21.localllm.contracts.ConsumerLimits
import io.github.daniele21.localllm.contracts.ConsumerLocalLlmClient
import io.github.daniele21.localllm.contracts.ConsumerOutputConstraint
import io.github.daniele21.localllm.contracts.ConsumerOutputConstraintKind
import io.github.daniele21.localllm.contracts.ConsumerPrepareRequest
import io.github.daniele21.localllm.contracts.ConsumerPrepareResult
import io.github.daniele21.localllm.contracts.ConsumerReasoningCapability
import io.github.daniele21.localllm.contracts.ConsumerSessionResult
import io.github.daniele21.localllm.contracts.EffectiveConsumerReasoningMode
import io.github.daniele21.localllm.contracts.RequestId
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.UseCaseCapabilities
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.contracts.UseCaseReadiness
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * OMB-4B adapter from the application-owned chunk contract to the public Consumer API.
 *
 * Lifecycle discovery/preparation is dispatched through [lifecycleExecutor] so the Binder-backed
 * implementation never performs blocking lifecycle calls on the Android main thread. Model and
 * preset identity remain host-owned: the consumer asks only for the OMBRA use case and accepts the
 * reviewed host defaults after validating the resulting public capability contract.
 */
internal class OmbraConsumerAnalysisChunkClient(
    private val client: ConsumerLocalLlmClient,
    private val lifecycleExecutor: Executor,
    private val transportConnected: () -> Boolean = { true },
    private val useCaseId: UseCaseId = OMBRA_DOCUMENT_PII_USE_CASE,
) : OmbraAnalysisChunkClient {
    private val operations = ConcurrentHashMap<OmbraOperationId, ConsumerOperation>()

    override fun prepare(operationId: OmbraOperationId, onResult: (Result<ConsumerLimits>) -> Unit) {
        val operation = ConsumerOperation(onPrepared = onResult)
        check(operations.putIfAbsent(operationId, operation) == null) { "Duplicate OMBRA Consumer API operation ID" }
        try {
            lifecycleExecutor.execute { prepareOnExecutor(operationId, operation) }
        } catch (_: RejectedExecutionException) {
            operations.remove(operationId, operation)
            onResult(Result.failure(chunkFailure(OmbraAnalysisChunkFailureCode.DISCONNECTED)))
        }
    }

    override fun generate(operationId: OmbraOperationId, request: OmbraStructuredChunkRequest, onResult: (Result<String>) -> Unit) {
        val operation = operations[operationId]
        if (operation == null) {
            onResult(Result.failure(chunkFailure(OmbraAnalysisChunkFailureCode.DISCONNECTED)))
            return
        }
        val generation = synchronized(operation) {
            val sessionId = operation.sessionId
            val limits = operation.limits
            if (operation.cancelled || sessionId == null || limits == null || operation.activeGeneration != null) {
                null
            } else if (!requestFits(request, limits)) {
                null
            } else {
                ActiveConsumerGeneration(requestId(operationId, request.ordinal), onResult).also {
                    operation.activeGeneration = it
                }
            }
        }
        if (generation == null) {
            onResult(Result.failure(chunkFailure(OmbraAnalysisChunkFailureCode.CAPABILITY_INCOMPATIBLE)))
            return
        }

        val sessionId = requireNotNull(operation.sessionId)
        val start =
            runCatching {
                client.generate(
                    ConsumerGenerationRequest(
                        requestId = generation.requestId,
                        sessionId = sessionId,
                        input = ConsumerGenerationInput.Text(composeInput(request)),
                        outputConstraint = ConsumerOutputConstraint.JsonSchema(request.outputJsonSchema),
                    ),
                    ConsumerGenerationListener { event -> handleEvent(operation, generation, event) },
                )
            }.getOrElse {
                finishGeneration(operation, generation, Result.failure(chunkFailure(disconnectedOrGenerationFailure())))
                return
            }

        when (start) {
            is ConsumerGenerationStartResult.Accepted -> {
                val cancelImmediately = synchronized(operation) {
                    generation.handle = start.handle
                    generation.terminal.get() || operation.cancelled
                }
                if (cancelImmediately) start.handle.cancel()
            }

            is ConsumerGenerationStartResult.Rejected ->
                finishGeneration(operation, generation, Result.failure(chunkFailure(mapConsumerFailure(start.failure))))
        }
    }

    override fun cancel(operationId: OmbraOperationId, onCancelled: () -> Unit) {
        val operation = operations[operationId]
        if (operation == null) {
            onCancelled()
            return
        }
        val cancelState = synchronized(operation) {
            operation.cancelled = true
            operation.cancelAcknowledgement = onCancelled
            CancelState(operation.activeGeneration, operation.preparing)
        }
        when {
            cancelState.active != null -> cancelState.active.handle?.cancel()
            cancelState.preparing -> Unit
            else -> closeCancelledOperation(operationId, operation)
        }
    }

    override fun close(operationId: OmbraOperationId) {
        val operation = operations.remove(operationId) ?: return
        val active = synchronized(operation) {
            operation.cancelled = true
            operation.activeGeneration.also { operation.activeGeneration = null }
        }
        active?.handle?.cancel()
        operation.sessionId?.let { sessionId -> runCatching { client.closeSession(sessionId) } }
    }

    private fun prepareOnExecutor(operationId: OmbraOperationId, operation: ConsumerOperation) {
        val outcome = runCatching { prepareConsumerSession() }
        val prepared = outcome.getOrNull()
        val cancelled = synchronized(operation) {
            operation.preparing = false
            if (prepared != null) {
                operation.sessionId = prepared.sessionId
                operation.limits = prepared.limits
                operation.capabilityRevision = prepared.capabilityRevision
            }
            operation.cancelled
        }
        if (cancelled) {
            prepared?.sessionId?.let { runCatching { client.closeSession(it) } }
            operations.remove(operationId, operation)
            takeCancellationAcknowledgement(operation)?.invoke()
            return
        }
        if (prepared == null) {
            operations.remove(operationId, operation)
            val failure = outcome.exceptionOrNull() as? OmbraAnalysisChunkException
                ?: chunkFailure(disconnectedOrGenerationFailure())
            operation.onPrepared(Result.failure(failure))
        } else {
            operation.onPrepared(Result.success(prepared.limits))
        }
    }

    private fun prepareConsumerSession(): PreparedConsumerOperation {
        val capabilities = resolveCapabilities()
        val prepared =
            when (val result = client.prepare(ConsumerPrepareRequest(useCaseId))) {
                is ConsumerPrepareResult.Prepared -> result.selection
                is ConsumerPrepareResult.Rejected -> throw chunkFailure(mapConsumerFailure(result.failure))
            }
        if (
            prepared.useCaseId != useCaseId ||
            prepared.capabilityRevision != capabilities.capabilityRevision ||
            prepared.reasoningMode != EffectiveConsumerReasoningMode.DISABLED ||
            prepared.outputConstraint != ConsumerOutputConstraintKind.JSON_SCHEMA ||
            prepared.sessionKind != SessionKind.STATELESS
        ) {
            throw chunkFailure(OmbraAnalysisChunkFailureCode.CAPABILITY_INCOMPATIBLE)
        }
        val sessionId =
            when (val result = client.createSession(prepared.preparedId)) {
                is ConsumerSessionResult.Created -> result.sessionId
                is ConsumerSessionResult.Rejected -> throw chunkFailure(mapConsumerFailure(result.failure))
            }
        return PreparedConsumerOperation(sessionId, capabilities.limits, capabilities.capabilityRevision)
    }

    private fun resolveCapabilities(): UseCaseCapabilities {
        val capabilities =
            when (val result = client.capabilities(useCaseId)) {
                is ConsumerCapabilityResult.Available -> result.capabilities
                is ConsumerCapabilityResult.Rejected -> throw chunkFailure(mapCapabilityFailure(result.code))
            }
        val readinessFailure =
            when (capabilities.readiness) {
                UseCaseReadiness.READY,
                UseCaseReadiness.AVAILABLE_REQUIRES_PREPARATION,
                -> null

                UseCaseReadiness.UNAVAILABLE_MODEL -> OmbraAnalysisChunkFailureCode.HOST_UNAVAILABLE

                UseCaseReadiness.UNAVAILABLE_HOST_POLICY,
                UseCaseReadiness.INCOMPATIBLE,
                -> OmbraAnalysisChunkFailureCode.CAPABILITY_INCOMPATIBLE
            }
        if (readinessFailure != null) throw chunkFailure(readinessFailure)
        if (
            capabilities.useCaseId != useCaseId ||
            capabilities.outputConstraints != setOf(ConsumerOutputConstraintKind.JSON_SCHEMA) ||
            capabilities.defaultOutputConstraint != ConsumerOutputConstraintKind.JSON_SCHEMA ||
            capabilities.sessionKinds != setOf(SessionKind.STATELESS) ||
            capabilities.defaultSessionKind != SessionKind.STATELESS ||
            capabilities.reasoning != ConsumerReasoningCapability.NOT_SUPPORTED
        ) {
            throw chunkFailure(OmbraAnalysisChunkFailureCode.CAPABILITY_INCOMPATIBLE)
        }
        return capabilities
    }

    private fun handleEvent(operation: ConsumerOperation, generation: ActiveConsumerGeneration, event: ConsumerGenerationEvent) {
        if (event.requestId != generation.requestId) {
            generation.handle?.cancel()
            finishGeneration(
                operation,
                generation,
                Result.failure(chunkFailure(OmbraAnalysisChunkFailureCode.CAPABILITY_INCOMPATIBLE)),
            )
            return
        }
        when (event) {
            is ConsumerGenerationEvent.Queued,
            is ConsumerGenerationEvent.Started,
            -> Unit

            is ConsumerGenerationEvent.Prepared -> {
                if (!executionMatches(event.execution, operation)) {
                    generation.handle?.cancel()
                    finishGeneration(
                        operation,
                        generation,
                        Result.failure(chunkFailure(OmbraAnalysisChunkFailureCode.CAPABILITY_INCOMPATIBLE)),
                    )
                }
            }

            is ConsumerGenerationEvent.ContentDelta -> {
                if (event.contentType == ConsumerContentType.REASONING) {
                    generation.handle?.cancel()
                    finishGeneration(
                        operation,
                        generation,
                        Result.failure(chunkFailure(OmbraAnalysisChunkFailureCode.CAPABILITY_INCOMPATIBLE)),
                    )
                }
            }

            is ConsumerGenerationEvent.Completed -> {
                val valid = event.result.surfacedReasoning.isNullOrEmpty() && executionMatches(event.result.execution, operation)
                val result =
                    if (valid) {
                        Result.success(event.result.answer)
                    } else {
                        Result.failure(chunkFailure(OmbraAnalysisChunkFailureCode.CAPABILITY_INCOMPATIBLE))
                    }
                finishGeneration(operation, generation, result)
            }

            is ConsumerGenerationEvent.Failed ->
                finishGeneration(operation, generation, Result.failure(chunkFailure(mapConsumerFailure(event.failure))))
        }
    }

    private fun finishGeneration(operation: ConsumerOperation, generation: ActiveConsumerGeneration, result: Result<String>) {
        if (!generation.terminal.compareAndSet(false, true)) return
        val cancelled = synchronized(operation) {
            if (operation.activeGeneration === generation) operation.activeGeneration = null
            operation.cancelled
        }
        if (cancelled) {
            takeCancellationAcknowledgement(operation)?.invoke()
        } else {
            generation.onResult(result)
        }
    }

    private fun closeCancelledOperation(operationId: OmbraOperationId, operation: ConsumerOperation) {
        operations.remove(operationId, operation)
        operation.sessionId?.let { runCatching { client.closeSession(it) } }
        takeCancellationAcknowledgement(operation)?.invoke()
    }

    private fun executionMatches(execution: ConsumerExecutionIdentity, operation: ConsumerOperation): Boolean =
        execution.useCaseId == useCaseId &&
            execution.capabilityRevision == operation.capabilityRevision &&
            execution.reasoningMode == EffectiveConsumerReasoningMode.DISABLED &&
            execution.outputConstraint == ConsumerOutputConstraintKind.JSON_SCHEMA &&
            execution.sessionKind == SessionKind.STATELESS

    private fun mapCapabilityFailure(code: ConsumerCapabilityErrorCode): OmbraAnalysisChunkFailureCode = when (code) {
        ConsumerCapabilityErrorCode.MODEL_UNAVAILABLE -> OmbraAnalysisChunkFailureCode.HOST_UNAVAILABLE

        ConsumerCapabilityErrorCode.CAPABILITY_INCOMPATIBLE -> disconnectedOrCapabilityFailure()

        ConsumerCapabilityErrorCode.USE_CASE_NOT_ALLOWED,
        ConsumerCapabilityErrorCode.STALE_CAPABILITY,
        ConsumerCapabilityErrorCode.PRESET_NOT_ALLOWED,
        ConsumerCapabilityErrorCode.REASONING_NOT_ALLOWED,
        ConsumerCapabilityErrorCode.REASONING_REQUIRED,
        ConsumerCapabilityErrorCode.OUTPUT_NOT_ALLOWED,
        ConsumerCapabilityErrorCode.SESSION_KIND_NOT_ALLOWED,
        -> OmbraAnalysisChunkFailureCode.CAPABILITY_INCOMPATIBLE
    }

    private fun mapConsumerFailure(failure: ConsumerFailure): OmbraAnalysisChunkFailureCode = when (failure.code) {
        ConsumerErrorCode.MODEL_UNAVAILABLE -> OmbraAnalysisChunkFailureCode.HOST_UNAVAILABLE

        ConsumerErrorCode.CANCELLED -> OmbraAnalysisChunkFailureCode.CANCELLED

        ConsumerErrorCode.RUNTIME_FAILURE,
        ConsumerErrorCode.PREPARE_FAILED,
        ConsumerErrorCode.SESSION_NOT_FOUND,
        -> disconnectedOrGenerationFailure()

        ConsumerErrorCode.CAPABILITY_INCOMPATIBLE -> disconnectedOrCapabilityFailure()

        ConsumerErrorCode.USE_CASE_NOT_ALLOWED,
        ConsumerErrorCode.STALE_CAPABILITY,
        ConsumerErrorCode.PRESET_NOT_ALLOWED,
        ConsumerErrorCode.REASONING_NOT_ALLOWED,
        ConsumerErrorCode.REASONING_REQUIRED,
        ConsumerErrorCode.OUTPUT_NOT_ALLOWED,
        ConsumerErrorCode.SESSION_KIND_NOT_ALLOWED,
        ConsumerErrorCode.INVALID_INPUT,
        ConsumerErrorCode.PREPARED_SELECTION_STALE,
        ConsumerErrorCode.PREPARED_SELECTION_NOT_FOUND,
        -> OmbraAnalysisChunkFailureCode.CAPABILITY_INCOMPATIBLE
    }

    private fun disconnectedOrCapabilityFailure(): OmbraAnalysisChunkFailureCode = if (transportConnected()) {
        OmbraAnalysisChunkFailureCode.CAPABILITY_INCOMPATIBLE
    } else {
        OmbraAnalysisChunkFailureCode.DISCONNECTED
    }

    private fun disconnectedOrGenerationFailure(): OmbraAnalysisChunkFailureCode = if (transportConnected()) {
        OmbraAnalysisChunkFailureCode.GENERATION_FAILED
    } else {
        OmbraAnalysisChunkFailureCode.DISCONNECTED
    }
}

private data class PreparedConsumerOperation(val sessionId: SessionId, val limits: ConsumerLimits, val capabilityRevision: String)

private data class CancelState(val active: ActiveConsumerGeneration?, val preparing: Boolean)

private class ConsumerOperation(val onPrepared: (Result<ConsumerLimits>) -> Unit) {
    var preparing: Boolean = true
    var cancelled: Boolean = false
    var sessionId: SessionId? = null
    var limits: ConsumerLimits? = null
    var capabilityRevision: String? = null
    var activeGeneration: ActiveConsumerGeneration? = null
    var cancelAcknowledgement: (() -> Unit)? = null
}

private class ActiveConsumerGeneration(val requestId: RequestId, val onResult: (Result<String>) -> Unit) {
    val terminal = AtomicBoolean(false)
    var handle: ConsumerGenerationHandle? = null
}

private fun requestFits(request: OmbraStructuredChunkRequest, limits: ConsumerLimits): Boolean =
    composeInput(request).length <= limits.maxInputCharacters && request.outputJsonSchema.length <= limits.maxJsonSchemaCharacters

private fun composeInput(request: OmbraStructuredChunkRequest): String = request.instruction + OMBRA_DATA_SEPARATOR + request.dataPayload

private fun requestId(operationId: OmbraOperationId, chunkOrdinal: Int): RequestId = RequestId("ombra-${operationId.value}-$chunkOrdinal")

private fun chunkFailure(code: OmbraAnalysisChunkFailureCode): OmbraAnalysisChunkException = OmbraAnalysisChunkException(code)

private fun takeCancellationAcknowledgement(operation: ConsumerOperation): (() -> Unit)? = synchronized(operation) {
    operation.cancelAcknowledgement.also { operation.cancelAcknowledgement = null }
}

private val OMBRA_DOCUMENT_PII_USE_CASE = UseCaseId("document-pii-detection")
private const val OMBRA_DATA_SEPARATOR = "\n\nDATA:\n"
