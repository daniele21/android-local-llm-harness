package io.github.daniele21.localllm.console.analysis

import io.github.daniele21.localllm.contracts.ConsumerCapabilityErrorCode
import io.github.daniele21.localllm.contracts.ConsumerCapabilityResult
import io.github.daniele21.localllm.contracts.ConsumerErrorCode
import io.github.daniele21.localllm.contracts.ConsumerFailure
import io.github.daniele21.localllm.contracts.ConsumerLimits
import io.github.daniele21.localllm.contracts.ConsumerLocalLlmClient
import io.github.daniele21.localllm.contracts.ConsumerOutputConstraintKind
import io.github.daniele21.localllm.contracts.ConsumerPrepareRequest
import io.github.daniele21.localllm.contracts.ConsumerPrepareResult
import io.github.daniele21.localllm.contracts.ConsumerPreparedSelection
import io.github.daniele21.localllm.contracts.ConsumerReasoningCapability
import io.github.daniele21.localllm.contracts.ConsumerSessionResult
import io.github.daniele21.localllm.contracts.EffectiveConsumerReasoningMode
import io.github.daniele21.localllm.contracts.SessionId
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.UseCaseCapabilities
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.contracts.UseCaseReadiness

internal data class OmbraPreparedConsumerOperation(val sessionId: SessionId, val limits: ConsumerLimits, val capabilityRevision: String)

internal class OmbraConsumerPreparation(
    private val client: ConsumerLocalLlmClient,
    private val useCaseId: UseCaseId,
    private val failureMapper: OmbraConsumerFailureMapper,
) {
    fun prepare(): OmbraPreparedConsumerOperation {
        val capabilities = resolveCapabilities()
        val selection = resolvePreparedSelection()
        validatePreparedSelection(selection, capabilities)
        val sessionId = createSession(selection)
        return OmbraPreparedConsumerOperation(
            sessionId = sessionId,
            limits = capabilities.limits,
            capabilityRevision = capabilities.capabilityRevision,
        )
    }

    private fun resolveCapabilities(): UseCaseCapabilities {
        val capabilities =
            when (val result = client.capabilities(useCaseId)) {
                is ConsumerCapabilityResult.Available -> result.capabilities

                is ConsumerCapabilityResult.Rejected ->
                    throw chunkFailure(failureMapper.mapCapabilityFailure(result.code))
            }
        validateCapabilities(capabilities)
        return capabilities
    }

    private fun resolvePreparedSelection(): ConsumerPreparedSelection =
        when (val result = client.prepare(ConsumerPrepareRequest(useCaseId))) {
            is ConsumerPrepareResult.Prepared -> result.selection

            is ConsumerPrepareResult.Rejected ->
                throw chunkFailure(failureMapper.mapConsumerFailure(result.failure))
        }

    private fun createSession(selection: ConsumerPreparedSelection): SessionId =
        when (val result = client.createSession(selection.preparedId)) {
            is ConsumerSessionResult.Created -> result.sessionId

            is ConsumerSessionResult.Rejected ->
                throw chunkFailure(failureMapper.mapConsumerFailure(result.failure))
        }

    private fun validateCapabilities(capabilities: UseCaseCapabilities) {
        val readinessFailure = readinessFailure(capabilities.readiness)
        if (readinessFailure != null) throw chunkFailure(readinessFailure)
        if (!capabilitiesMatchPolicy(capabilities, useCaseId)) {
            throw chunkFailure(OmbraAnalysisChunkFailureCode.CAPABILITY_INCOMPATIBLE)
        }
    }

    private fun validatePreparedSelection(selection: ConsumerPreparedSelection, capabilities: UseCaseCapabilities) {
        if (!preparedSelectionMatchesPolicy(selection, capabilities, useCaseId)) {
            throw chunkFailure(OmbraAnalysisChunkFailureCode.CAPABILITY_INCOMPATIBLE)
        }
    }
}

internal class OmbraConsumerFailureMapper(private val transportConnected: () -> Boolean) {
    fun mapCapabilityFailure(code: ConsumerCapabilityErrorCode): OmbraAnalysisChunkFailureCode = when (code) {
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

    fun mapConsumerFailure(failure: ConsumerFailure): OmbraAnalysisChunkFailureCode = when (failure.code) {
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

    fun generationFailure(): OmbraAnalysisChunkFailureCode = disconnectedOrGenerationFailure()

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

private fun readinessFailure(readiness: UseCaseReadiness): OmbraAnalysisChunkFailureCode? = when (readiness) {
    UseCaseReadiness.READY,
    UseCaseReadiness.AVAILABLE_REQUIRES_PREPARATION,
    -> null

    UseCaseReadiness.UNAVAILABLE_MODEL -> OmbraAnalysisChunkFailureCode.HOST_UNAVAILABLE

    UseCaseReadiness.UNAVAILABLE_HOST_POLICY,
    UseCaseReadiness.INCOMPATIBLE,
    -> OmbraAnalysisChunkFailureCode.CAPABILITY_INCOMPATIBLE
}

private fun capabilitiesMatchPolicy(capabilities: UseCaseCapabilities, useCaseId: UseCaseId): Boolean = listOf(
    capabilities.useCaseId == useCaseId,
    capabilities.outputConstraints == setOf(ConsumerOutputConstraintKind.JSON_SCHEMA),
    capabilities.defaultOutputConstraint == ConsumerOutputConstraintKind.JSON_SCHEMA,
    capabilities.sessionKinds == setOf(SessionKind.STATELESS),
    capabilities.defaultSessionKind == SessionKind.STATELESS,
    capabilities.reasoning == ConsumerReasoningCapability.NOT_SUPPORTED,
).all { it }

private fun preparedSelectionMatchesPolicy(
    selection: ConsumerPreparedSelection,
    capabilities: UseCaseCapabilities,
    useCaseId: UseCaseId,
): Boolean = listOf(
    selection.useCaseId == useCaseId,
    selection.capabilityRevision == capabilities.capabilityRevision,
    selection.reasoningMode == EffectiveConsumerReasoningMode.DISABLED,
    selection.outputConstraint == ConsumerOutputConstraintKind.JSON_SCHEMA,
    selection.sessionKind == SessionKind.STATELESS,
).all { it }
