package io.github.daniele21.localllm.console.analysis

import io.github.daniele21.localllm.console.application.OmbraAnalysisClient
import io.github.daniele21.localllm.console.application.OmbraAnalysisRequest
import io.github.daniele21.localllm.console.application.OmbraOperationId
import io.github.daniele21.localllm.contracts.ConsumerLimits
import java.util.concurrent.ConcurrentHashMap

/** Content-bearing request passed only to the per-chunk inference boundary. */
internal data class OmbraStructuredChunkRequest(
    val ordinal: Int,
    val instruction: String,
    val dataPayload: String,
    val outputJsonSchema: String,
) {
    init {
        require(ordinal >= 0) { "Chunk ordinal must be non-negative" }
        require(instruction.isNotEmpty()) { "Analysis instruction must not be empty" }
        require(dataPayload.isNotEmpty()) { "Analysis data payload must not be empty" }
        require(outputJsonSchema.isNotEmpty()) { "Analysis output schema must not be empty" }
    }

    override fun toString(): String = "OmbraStructuredChunkRequest(ordinal=$ordinal, instructionLength=${instruction.length}, " +
        "dataPayload=<redacted>, outputJsonSchemaLength=${outputJsonSchema.length})"
}

/**
 * Narrow OMB-3 inference boundary. OMB-4 supplies the real Consumer API implementation while OMB-3
 * drives this contract with fakes to prove sequencing, validation, cancellation and cleanup.
 */
internal interface OmbraAnalysisChunkClient {
    fun prepare(operationId: OmbraOperationId, onResult: (Result<ConsumerLimits>) -> Unit)

    fun generate(operationId: OmbraOperationId, request: OmbraStructuredChunkRequest, onResult: (Result<String>) -> Unit)

    fun cancel(operationId: OmbraOperationId, onCancelled: () -> Unit)

    fun close(operationId: OmbraOperationId)
}

internal enum class OmbraAnalysisChunkFailureCode {
    HOST_UNAVAILABLE,
    CAPABILITY_INCOMPATIBLE,
    GENERATION_FAILED,
    DISCONNECTED,
    CANCELLED,
}

internal class OmbraAnalysisChunkException(val code: OmbraAnalysisChunkFailureCode) :
    RuntimeException("OMBRA analysis chunk failed: $code")

internal enum class OmbraAnalysisFailureCode {
    PLAN_REJECTED,
    INVALID_STRUCTURED_RESULT,
    INVALID_FINDINGS,
    HOST_UNAVAILABLE,
    CAPABILITY_INCOMPATIBLE,
    CHUNK_FAILED,
    DISCONNECTED,
    CANCELLED,
}

internal class OmbraAnalysisException(val code: OmbraAnalysisFailureCode, val invalidFindingCount: Int = 0) :
    RuntimeException("OMBRA analysis failed: $code") {
    init {
        require(invalidFindingCount >= 0) { "Invalid finding count must be non-negative" }
    }
}

/**
 * Application-owned sequential structured analysis implementation.
 *
 * It never returns partial findings: every planned chunk must complete, parse and validate before
 * the merged result is exposed through [OmbraAnalysisClient]. Source text remains confined to the
 * request/chunk boundary and is never added to failure messages or debug representations.
 */
internal class OmbraSequentialAnalysisClient(
    private val chunkClient: OmbraAnalysisChunkClient,
    private val planner: OmbraAnalysisChunkPlanner = OmbraAnalysisChunkPlanner(),
    private val parser: OmbraAnalysisResultParser = OmbraAnalysisResultParser(),
) : OmbraAnalysisClient {
    private val operations = ConcurrentHashMap<OmbraOperationId, ActiveOperation>()

    override fun analyze(operationId: OmbraOperationId, request: OmbraAnalysisRequest, onResult: (Result<List<ValidatedFinding>>) -> Unit) {
        val operation = ActiveOperation(request, onResult)
        check(operations.putIfAbsent(operationId, operation) == null) { "Duplicate OMBRA analysis operation ID" }

        chunkClient.prepare(operationId) { prepared ->
            if (!isActive(operationId, operation)) return@prepare
            prepared.fold(
                onSuccess = { limits -> preparePlan(operationId, operation, limits) },
                onFailure = { failure -> completeFailure(operationId, operation, mapChunkFailure(failure)) },
            )
        }
    }

    override fun cancel(operationId: OmbraOperationId, onCancelled: () -> Unit) {
        val operation = operations[operationId]
        if (operation == null) {
            onCancelled()
            return
        }
        operation.cancelled = true
        chunkClient.cancel(operationId) {
            if (operations.remove(operationId, operation)) {
                runCatching { chunkClient.close(operationId) }
            }
            onCancelled()
        }
    }

    private fun preparePlan(operationId: OmbraOperationId, operation: ActiveOperation, limits: ConsumerLimits) {
        val plan = planner.plan(operation.request.segments, operation.request.definitions, limits)
        if (plan is OmbraChunkPlanResult.Rejected) {
            completeFailure(operationId, operation, OmbraAnalysisException(OmbraAnalysisFailureCode.PLAN_REJECTED))
            return
        }
        val chunks = (plan as OmbraChunkPlanResult.Planned).chunks
        val sourceIndex = runCatching { OmbraAnalysisSourceIndex.build(chunks, operation.request.segments) }.getOrNull()
        if (sourceIndex == null) {
            completeFailure(operationId, operation, OmbraAnalysisException(OmbraAnalysisFailureCode.PLAN_REJECTED))
            return
        }
        operation.chunks = chunks
        operation.sourceIndex = sourceIndex
        generateNext(operationId, operation)
    }

    private fun generateNext(operationId: OmbraOperationId, operation: ActiveOperation) {
        if (!isActive(operationId, operation)) return
        val chunk = operation.chunks.getOrNull(operation.nextChunkIndex)
        if (chunk == null) {
            completeMergedResult(operationId, operation)
            return
        }
        val request =
            OmbraStructuredChunkRequest(
                ordinal = chunk.ordinal,
                instruction = OmbraAnalysisProtocol.instruction,
                dataPayload = chunk.dataPayload,
                outputJsonSchema = OmbraAnalysisProtocol.outputJsonSchema,
            )
        chunkClient.generate(operationId, request) { generated ->
            if (!isActive(operationId, operation)) return@generate
            generated.fold(
                onSuccess = { output -> handleChunkOutput(operationId, operation, chunk, output) },
                onFailure = { failure -> completeFailure(operationId, operation, mapChunkFailure(failure)) },
            )
        }
    }

    private fun handleChunkOutput(operationId: OmbraOperationId, operation: ActiveOperation, chunk: OmbraAnalysisChunk, output: String) {
        when (val parsed = parser.parse(output)) {
            is OmbraAnalysisParseResult.Rejected -> {
                completeFailure(
                    operationId,
                    operation,
                    OmbraAnalysisException(OmbraAnalysisFailureCode.INVALID_STRUCTURED_RESULT),
                )
            }

            is OmbraAnalysisParseResult.Parsed -> {
                val validation =
                    OmbraAnalysisFindingValidator.validate(
                        result = parsed.result,
                        chunk = chunk,
                        definitions = operation.request.definitions,
                        sourceIndex = requireNotNull(operation.sourceIndex),
                    )
                if (!validation.isComplete) {
                    completeFailure(
                        operationId,
                        operation,
                        OmbraAnalysisException(
                            code = OmbraAnalysisFailureCode.INVALID_FINDINGS,
                            invalidFindingCount = validation.invalidFindingCount,
                        ),
                    )
                    return
                }
                operation.validations += validation
                operation.nextChunkIndex += 1
                generateNext(operationId, operation)
            }
        }
    }

    private fun completeMergedResult(operationId: OmbraOperationId, operation: ActiveOperation) {
        val merged = OmbraAnalysisFindingMerger.merge(operation.validations)
        if (!merged.isComplete) {
            completeFailure(
                operationId,
                operation,
                OmbraAnalysisException(
                    code = OmbraAnalysisFailureCode.INVALID_FINDINGS,
                    invalidFindingCount = merged.invalidFindingCount,
                ),
            )
            return
        }
        if (!operations.remove(operationId, operation)) return
        val closeFailure = runCatching { chunkClient.close(operationId) }.exceptionOrNull()
        if (closeFailure != null) {
            operation.onResult(Result.failure(OmbraAnalysisException(OmbraAnalysisFailureCode.CHUNK_FAILED)))
        } else {
            operation.onResult(Result.success(merged.findings))
        }
    }

    private fun completeFailure(operationId: OmbraOperationId, operation: ActiveOperation, failure: Throwable) {
        if (!operations.remove(operationId, operation)) return
        runCatching { chunkClient.close(operationId) }
        operation.onResult(Result.failure(failure))
    }

    private fun mapChunkFailure(failure: Throwable): Throwable {
        val code =
            (failure as? OmbraAnalysisChunkException)?.code
                ?: return OmbraAnalysisException(OmbraAnalysisFailureCode.CHUNK_FAILED)
        val mapped =
            when (code) {
                OmbraAnalysisChunkFailureCode.HOST_UNAVAILABLE -> OmbraAnalysisFailureCode.HOST_UNAVAILABLE
                OmbraAnalysisChunkFailureCode.CAPABILITY_INCOMPATIBLE -> OmbraAnalysisFailureCode.CAPABILITY_INCOMPATIBLE
                OmbraAnalysisChunkFailureCode.DISCONNECTED -> OmbraAnalysisFailureCode.DISCONNECTED
                OmbraAnalysisChunkFailureCode.CANCELLED -> OmbraAnalysisFailureCode.CANCELLED
                OmbraAnalysisChunkFailureCode.GENERATION_FAILED -> OmbraAnalysisFailureCode.CHUNK_FAILED
            }
        return OmbraAnalysisException(mapped)
    }

    private fun isActive(operationId: OmbraOperationId, operation: ActiveOperation): Boolean =
        operations[operationId] === operation && !operation.cancelled

    private class ActiveOperation(val request: OmbraAnalysisRequest, val onResult: (Result<List<ValidatedFinding>>) -> Unit) {
        @Volatile
        var cancelled: Boolean = false
        var chunks: List<OmbraAnalysisChunk> = emptyList()
        var sourceIndex: OmbraAnalysisSourceIndex? = null
        var nextChunkIndex: Int = 0
        val validations = mutableListOf<OmbraChunkFindingValidation>()
    }
}
