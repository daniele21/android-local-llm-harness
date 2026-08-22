package io.github.daniele21.localllm.llamacpp

import io.github.daniele21.localllm.models.GgufModelProfile
import java.util.Base64

private const val OK = "ok"
private const val ERROR = "error"
private const val TRUE = "true"
private const val FALSE = "false"
private const val CONTEXT_ALIGNMENT = 256
private const val CONTEXT_CREATION_FIELD_COUNT = 2
private const val OPERATION_FIELD_COUNT = 1
private const val CANCEL_FIELD_COUNT = 2
private const val ERROR_FIELD_COUNT = 3
private const val BATCH_HEADER_FIELD_COUNT = 2
private const val CASE_FIELD_COUNT = 8

interface NativeLlamaEvaluationBatchApi {
    @Suppress("LongParameterList")
    fun createEvaluationContext(
        modelHandle: Long,
        perSequenceContextSize: Int,
        maxSequences: Int,
        batchSize: Int,
        microBatchSize: Int,
        threads: Int,
        batchThreads: Int,
        flashAttentionMode: Int,
        kvCacheTypeK: String?,
        kvCacheTypeV: String?,
    ): Array<String>

    fun releaseEvaluationContext(contextHandle: Long): Array<String>

    @Suppress("LongParameterList")
    fun generateEvaluationBatch(
        contextHandle: Long,
        requestIds: Array<String>,
        prompts: Array<String>,
        maxOutputTokens: IntArray,
        temperatures: FloatArray,
        topPs: FloatArray,
        topKs: IntArray,
        minPs: FloatArray,
        presencePenalties: FloatArray,
        repeatPenalties: FloatArray,
        repeatLastNs: IntArray,
        seeds: LongArray,
        outputConstraintTypes: Array<String>,
        outputSchemas: Array<String>,
        stopTokenIds: Array<IntArray>,
        stopSequences: Array<Array<String>>,
    ): Array<String>

    fun cancelEvaluationCase(requestId: String): Array<String>
}

class JniLlamaEvaluationBatchApi : NativeLlamaEvaluationBatchApi {
    init {
        System.loadLibrary("local_llm_jni")
    }

    external override fun createEvaluationContext(
        modelHandle: Long,
        perSequenceContextSize: Int,
        maxSequences: Int,
        batchSize: Int,
        microBatchSize: Int,
        threads: Int,
        batchThreads: Int,
        flashAttentionMode: Int,
        kvCacheTypeK: String?,
        kvCacheTypeV: String?,
    ): Array<String>

    external override fun releaseEvaluationContext(contextHandle: Long): Array<String>

    external override fun generateEvaluationBatch(
        contextHandle: Long,
        requestIds: Array<String>,
        prompts: Array<String>,
        maxOutputTokens: IntArray,
        temperatures: FloatArray,
        topPs: FloatArray,
        topKs: IntArray,
        minPs: FloatArray,
        presencePenalties: FloatArray,
        repeatPenalties: FloatArray,
        repeatLastNs: IntArray,
        seeds: LongArray,
        outputConstraintTypes: Array<String>,
        outputSchemas: Array<String>,
        stopTokenIds: Array<IntArray>,
        stopSequences: Array<Array<String>>,
    ): Array<String>

    external override fun cancelEvaluationCase(requestId: String): Array<String>
}

data class NativeEvaluationBatchCase(val requestId: String, val prompt: String, val config: NativeGenerationConfig)

@JvmInline
value class NativeEvaluationContextHandle(val value: Long) {
    init {
        require(value > 0) { "Native evaluation context handle must be positive" }
    }
}

data class LoadedNativeEvaluationContext(
    val handle: NativeEvaluationContextHandle,
    val model: LoadedNativeModel,
    val perSequenceContextSize: Int,
    val maxSequences: Int,
) {
    init {
        require(perSequenceContextSize > 0) { "Evaluation per-sequence context size must be positive" }
        require(maxSequences in MIN_BATCH_WIDTH..MAX_BATCH_WIDTH) { "Evaluation batch width must be in $MIN_BATCH_WIDTH..$MAX_BATCH_WIDTH" }
    }

    companion object {
        const val MIN_BATCH_WIDTH = 2
        const val MAX_BATCH_WIDTH = 4
    }
}

sealed interface EvaluationContextCreationResult {
    data class Success(val context: LoadedNativeEvaluationContext) : EvaluationContextCreationResult
    data class Failure(val error: GenerationNativeError) : EvaluationContextCreationResult
}

sealed interface NativeEvaluationBatchResult {
    data class Completed(val cases: List<NativeEvaluationBatchCaseResult>) : NativeEvaluationBatchResult
    data class Failure(val error: GenerationNativeError) : NativeEvaluationBatchResult
}

data class NativeEvaluationBatchCaseResult(
    val requestId: String,
    val status: NativeEvaluationBatchCaseStatus,
    val output: String,
    val metrics: NativeEvaluationBatchMetrics,
)

enum class NativeEvaluationBatchCaseStatus {
    COMPLETED,
    CANCELLED,
}

data class NativeEvaluationBatchMetrics(
    val inputTokens: Int,
    val outputTokens: Int,
    val promptDurationMs: Long,
    val generationDurationMs: Long,
    val stopReason: String,
) {
    init {
        require(inputTokens >= 0) { "Evaluation input token count must not be negative" }
        require(outputTokens >= 0) { "Evaluation output token count must not be negative" }
        require(promptDurationMs >= 0) { "Evaluation prompt duration must not be negative" }
        require(generationDurationMs >= 0) { "Evaluation generation duration must not be negative" }
        require(stopReason.isNotBlank()) { "Evaluation stop reason must not be blank" }
    }
}

sealed interface EvaluationBatchCancelResult {
    data class Accepted(val wasRunning: Boolean) : EvaluationBatchCancelResult
    data class Failure(val error: GenerationNativeError) : EvaluationBatchCancelResult
}

class LlamaCppEvaluationBatchBridge(private val nativeApi: NativeLlamaEvaluationBatchApi = JniLlamaEvaluationBatchApi()) {
    fun createContext(
        model: LoadedNativeModel,
        profile: GgufModelProfile,
        perSequenceContextSize: Int,
        maxSequences: Int,
        flashAttentionMode: NativeFlashAttentionMode = NativeFlashAttentionMode.fromProfile(profile.flashAttention),
    ): EvaluationContextCreationResult {
        EvaluationBatchValidator.contextError(profile, perSequenceContextSize, maxSequences, flashAttentionMode)?.let { error ->
            return EvaluationContextCreationResult.Failure(error)
        }
        return EvaluationBatchResponseDecoder.decodeContextCreation(
            response = nativeApi.createEvaluationContext(
                modelHandle = model.handle.value,
                perSequenceContextSize = perSequenceContextSize,
                maxSequences = maxSequences,
                batchSize = profile.batchSize,
                microBatchSize = profile.microBatchSize,
                threads = profile.cpuThreads,
                batchThreads = profile.batchThreads,
                flashAttentionMode = flashAttentionMode.nativeValue,
                kvCacheTypeK = profile.kvCacheTypeK,
                kvCacheTypeV = profile.kvCacheTypeV,
            ),
            model = model,
            perSequenceContextSize = perSequenceContextSize,
            maxSequences = maxSequences,
        )
    }

    fun releaseContext(context: LoadedNativeEvaluationContext): GenerationNativeOperationResult =
        EvaluationBatchResponseDecoder.decodeOperation(nativeApi.releaseEvaluationContext(context.handle.value))

    fun generate(context: LoadedNativeEvaluationContext, cases: List<NativeEvaluationBatchCase>): NativeEvaluationBatchResult {
        EvaluationBatchValidator.batchError(context, cases)?.let { error ->
            return NativeEvaluationBatchResult.Failure(error)
        }
        val response = nativeApi.generateEvaluationBatch(
            contextHandle = context.handle.value,
            requestIds = cases.map(NativeEvaluationBatchCase::requestId).toTypedArray(),
            prompts = cases.map(NativeEvaluationBatchCase::prompt).toTypedArray(),
            maxOutputTokens = cases.map { it.config.maxOutputTokens }.toIntArray(),
            temperatures = cases.map { it.config.temperature }.toFloatArray(),
            topPs = cases.map { it.config.topP }.toFloatArray(),
            topKs = cases.map { it.config.topK }.toIntArray(),
            minPs = cases.map { it.config.minP }.toFloatArray(),
            presencePenalties = cases.map { it.config.presencePenalty }.toFloatArray(),
            repeatPenalties = cases.map { it.config.repeatPenalty }.toFloatArray(),
            repeatLastNs = cases.map { it.config.repeatLastN }.toIntArray(),
            seeds = cases.map { it.config.seed }.toLongArray(),
            outputConstraintTypes = cases.map { it.config.outputConstraintType }.toTypedArray(),
            outputSchemas = cases.map { it.config.outputSchema.orEmpty() }.toTypedArray(),
            stopTokenIds = cases.map { it.config.stopTokenIds.copyOf() }.toTypedArray(),
            stopSequences = cases.map { it.config.stopSequences.toTypedArray() }.toTypedArray(),
        )
        return EvaluationBatchResponseDecoder.decodeBatch(response, cases.map(NativeEvaluationBatchCase::requestId))
    }

    fun cancel(requestId: String): EvaluationBatchCancelResult {
        if (requestId.isBlank()) {
            return EvaluationBatchCancelResult.Failure(invalidEvaluationBatchError("Evaluation request ID must not be blank"))
        }
        return EvaluationBatchResponseDecoder.decodeCancel(nativeApi.cancelEvaluationCase(requestId))
    }
}

private object EvaluationBatchValidator {
    fun contextError(
        profile: GgufModelProfile,
        perSequenceContextSize: Int,
        maxSequences: Int,
        flashAttentionMode: NativeFlashAttentionMode,
    ): GenerationNativeError? = when {
        perSequenceContextSize <= 0 || perSequenceContextSize % CONTEXT_ALIGNMENT != 0 ->
            invalidEvaluationBatchError(
                "Evaluation per-sequence context size must be a positive multiple of $CONTEXT_ALIGNMENT",
            )

        maxSequences !in LoadedNativeEvaluationContext.MIN_BATCH_WIDTH..LoadedNativeEvaluationContext.MAX_BATCH_WIDTH ->
            invalidEvaluationBatchError(
                "Evaluation batch width must be in ${LoadedNativeEvaluationContext.MIN_BATCH_WIDTH}..${LoadedNativeEvaluationContext.MAX_BATCH_WIDTH}",
            )

        profile.batchSize < maxSequences -> invalidEvaluationBatchError("Evaluation batch size must be at least the sequence width")

        else -> profile.explicitKvCacheSelectionError() ?: profile.kvCacheCompatibilityError(flashAttentionMode)
    }

    fun batchError(
        context: LoadedNativeEvaluationContext,
        cases: List<NativeEvaluationBatchCase>,
    ): GenerationNativeError? {
        val structuralError = when {
            cases.size !in LoadedNativeEvaluationContext.MIN_BATCH_WIDTH..context.maxSequences ->
                invalidEvaluationBatchError(
                    "Evaluation case count must be in ${LoadedNativeEvaluationContext.MIN_BATCH_WIDTH}..${context.maxSequences}",
                )

            cases.any { it.requestId.isBlank() } || cases.map(NativeEvaluationBatchCase::requestId).distinct().size != cases.size ->
                invalidEvaluationBatchError("Evaluation request IDs must be non-blank and unique")

            else -> null
        }
        return structuralError ?: cases.asSequence().mapNotNull(::caseError).firstOrNull()
    }

    private fun caseError(case: NativeEvaluationBatchCase): GenerationNativeError? =
        case.config.validationError(case.prompt) ?: when {
            case.config.reasoningMaxTokens != null ||
                case.config.reasoningCloseMarker != null ||
                case.config.reasoningForcedCloseText != null ->
                invalidEvaluationBatchError("Evaluation batching does not support reasoning transitions")

            else -> null
        }
}

private object EvaluationBatchResponseDecoder {
    fun decodeContextCreation(
        response: Array<String>,
        model: LoadedNativeModel,
        perSequenceContextSize: Int,
        maxSequences: Int,
    ): EvaluationContextCreationResult {
        if (response.size == CONTEXT_CREATION_FIELD_COUNT && response[0] == OK) {
            return try {
                EvaluationContextCreationResult.Success(
                    LoadedNativeEvaluationContext(
                        handle = NativeEvaluationContextHandle(response[1].toLong()),
                        model = model,
                        perSequenceContextSize = perSequenceContextSize,
                        maxSequences = maxSequences,
                    ),
                )
            } catch (error: IllegalArgumentException) {
                EvaluationContextCreationResult.Failure(
                    evaluationBatchProtocolError("Native evaluation context response is invalid: ${error.message}"),
                )
            }
        }
        return EvaluationContextCreationResult.Failure(decodeError(response))
    }

    fun decodeOperation(response: Array<String>): GenerationNativeOperationResult {
        if (response.size == OPERATION_FIELD_COUNT && response[0] == OK) {
            return GenerationNativeOperationResult.Success
        }
        return GenerationNativeOperationResult.Failure(decodeError(response))
    }

    fun decodeCancel(response: Array<String>): EvaluationBatchCancelResult {
        if (response.size == CANCEL_FIELD_COUNT && response[0] == OK) {
            return when (response[1]) {
                TRUE -> EvaluationBatchCancelResult.Accepted(true)
                FALSE -> EvaluationBatchCancelResult.Accepted(false)
                else -> EvaluationBatchCancelResult.Failure(
                    evaluationBatchProtocolError("Malformed evaluation cancellation response"),
                )
            }
        }
        return EvaluationBatchCancelResult.Failure(decodeError(response))
    }

    fun decodeBatch(response: Array<String>, expectedRequestIds: List<String>): NativeEvaluationBatchResult {
        if (response.size < BATCH_HEADER_FIELD_COUNT || response[0] != OK) {
            return NativeEvaluationBatchResult.Failure(decodeError(response))
        }
        val count = response[1].toIntOrNull()
            ?: return NativeEvaluationBatchResult.Failure(
                evaluationBatchProtocolError("Native evaluation batch count is invalid"),
            )
        if (count != expectedRequestIds.size || response.size != BATCH_HEADER_FIELD_COUNT + count * CASE_FIELD_COUNT) {
            return NativeEvaluationBatchResult.Failure(
                evaluationBatchProtocolError("Native evaluation batch response size is invalid"),
            )
        }
        return try {
            val decoded = List(count) { index -> decodeCase(response, BATCH_HEADER_FIELD_COUNT + index * CASE_FIELD_COUNT) }
            if (decoded.map(NativeEvaluationBatchCaseResult::requestId) != expectedRequestIds) {
                NativeEvaluationBatchResult.Failure(
                    evaluationBatchProtocolError("Native evaluation batch result order does not match the request order"),
                )
            } else {
                NativeEvaluationBatchResult.Completed(decoded)
            }
        } catch (error: IllegalArgumentException) {
            NativeEvaluationBatchResult.Failure(
                evaluationBatchProtocolError("Native evaluation batch response is invalid: ${error.message}"),
            )
        }
    }

    private fun decodeCase(response: Array<String>, offset: Int): NativeEvaluationBatchCaseResult = NativeEvaluationBatchCaseResult(
        requestId = response[offset],
        status = NativeEvaluationBatchCaseStatus.valueOf(response[offset + 1]),
        output = String(Base64.getDecoder().decode(response[offset + 2]), Charsets.UTF_8),
        metrics = NativeEvaluationBatchMetrics(
            inputTokens = response[offset + 3].toInt(),
            outputTokens = response[offset + 4].toInt(),
            promptDurationMs = response[offset + 5].toLong(),
            generationDurationMs = response[offset + 6].toLong(),
            stopReason = response[offset + 7],
        ),
    )

    private fun decodeError(response: Array<String>): GenerationNativeError {
        if (response.size != ERROR_FIELD_COUNT || response[0] != ERROR) {
            return evaluationBatchProtocolError(
                "Malformed native evaluation response: ${response.joinToString(separator = "|")}",
            )
        }
        val code = GenerationNativeErrorCode.entries.firstOrNull { it.name == response[1] }
            ?: GenerationNativeErrorCode.NATIVE_PROTOCOL
        return GenerationNativeError(code, response[2])
    }
}

private fun invalidEvaluationBatchError(message: String): GenerationNativeError = GenerationNativeError(
    GenerationNativeErrorCode.INVALID_ARGUMENT,
    message,
)

private fun evaluationBatchProtocolError(message: String): GenerationNativeError = GenerationNativeError(
    GenerationNativeErrorCode.NATIVE_PROTOCOL,
    message,
)
