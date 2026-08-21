package io.github.daniele21.localllm.llamacpp

import io.github.daniele21.localllm.models.GgufModelProfile

enum class NativeFlashAttentionMode(val nativeValue: Int) {
    AUTO(-1),
    DISABLED(0),
    ENABLED(1),
    ;

    companion object {
        fun fromProfile(enabled: Boolean): NativeFlashAttentionMode = if (enabled) ENABLED else DISABLED
    }
}

interface NativeLlamaGenerationApi {
    fun createContext(
        modelHandle: Long,
        contextSize: Int,
        batchSize: Int,
        microBatchSize: Int,
        threads: Int,
        batchThreads: Int,
        flashAttentionMode: Int,
    ): Array<String>

    fun releaseContext(contextHandle: Long): Array<String>
}

class JniLlamaGenerationApi : NativeLlamaGenerationApi {
    init {
        System.loadLibrary("local_llm_jni")
    }

    external override fun createContext(
        modelHandle: Long,
        contextSize: Int,
        batchSize: Int,
        microBatchSize: Int,
        threads: Int,
        batchThreads: Int,
        flashAttentionMode: Int,
    ): Array<String>

    external override fun releaseContext(contextHandle: Long): Array<String>
}

class LlamaCppGenerationBridge(private val nativeApi: NativeLlamaGenerationApi = JniLlamaGenerationApi()) {
    fun createContext(
        model: LoadedNativeModel,
        profile: GgufModelProfile,
        contextSize: Int = profile.contextSize,
        flashAttentionMode: NativeFlashAttentionMode = NativeFlashAttentionMode.fromProfile(profile.flashAttention),
    ): ContextCreationResult = decodeContextCreation(
        response = nativeApi.createContext(
            modelHandle = model.handle.value,
            contextSize = contextSize,
            batchSize = profile.batchSize,
            microBatchSize = profile.microBatchSize,
            threads = profile.cpuThreads,
            batchThreads = profile.batchThreads,
            flashAttentionMode = flashAttentionMode.nativeValue,
        ),
        model = model,
    )

    fun releaseContext(context: LoadedNativeContext): GenerationNativeOperationResult = decodeOperation(
        nativeApi.releaseContext(context.handle.value),
    )

    private fun decodeContextCreation(response: Array<String>, model: LoadedNativeModel): ContextCreationResult {
        if (response.size == CONTEXT_CREATION_FIELD_COUNT && response[0] == OK) {
            return try {
                ContextCreationResult.Success(
                    LoadedNativeContext(
                        handle = NativeContextHandle(response[1].toLong()),
                        model = model,
                    ),
                )
            } catch (error: NumberFormatException) {
                ContextCreationResult.Failure(protocolError("Native context response is invalid: ${error.message}"))
            } catch (error: IllegalArgumentException) {
                ContextCreationResult.Failure(protocolError("Native context handle is invalid: ${error.message}"))
            }
        }
        return ContextCreationResult.Failure(decodeError(response))
    }

    private fun decodeOperation(response: Array<String>): GenerationNativeOperationResult {
        if (response.size == OPERATION_FIELD_COUNT && response[0] == OK) {
            return GenerationNativeOperationResult.Success
        }
        return GenerationNativeOperationResult.Failure(decodeError(response))
    }

    private fun decodeError(response: Array<String>): GenerationNativeError {
        if (response.size != ERROR_FIELD_COUNT || response[0] != ERROR) {
            return protocolError("Malformed native response: ${response.joinToString(separator = "|")}")
        }
        val code = GenerationNativeErrorCode.entries.firstOrNull { it.name == response[1] }
            ?: GenerationNativeErrorCode.NATIVE_PROTOCOL
        return GenerationNativeError(code = code, message = response[2])
    }

    private fun protocolError(message: String): GenerationNativeError = GenerationNativeError(
        code = GenerationNativeErrorCode.NATIVE_PROTOCOL,
        message = message,
    )

    private companion object {
        const val OK = "ok"
        const val ERROR = "error"
        const val CONTEXT_CREATION_FIELD_COUNT = 2
        const val OPERATION_FIELD_COUNT = 1
        const val ERROR_FIELD_COUNT = 3
    }
}

@JvmInline
value class NativeContextHandle(val value: Long) {
    init {
        require(value > 0) { "Native context handle must be positive" }
    }
}

data class LoadedNativeContext(val handle: NativeContextHandle, val model: LoadedNativeModel)

data class NativeGenerationConfig(
    val maxOutputTokens: Int,
    val temperature: Float,
    val topP: Float,
    val topK: Int,
    val minP: Float = 0f,
    val presencePenalty: Float = 0f,
    val repeatPenalty: Float,
    val repeatLastN: Int,
    val seed: Long,
    val outputConstraintType: String = "TEXT",
    val outputSchema: String? = null,
    val stopTokenIds: IntArray = intArrayOf(),
    val stopSequences: List<String> = emptyList(),
    val reasoningMaxTokens: Int? = null,
    val reasoningCloseMarker: String? = null,
    val reasoningForcedCloseText: String? = null,
) {
    internal fun validationError(prompt: String): GenerationNativeError? =
        baseValidationError(prompt) ?: repeatValidationError() ?: constraintValidationError() ?: reasoningValidationError()

    private fun baseValidationError(prompt: String): GenerationNativeError? = when {
        prompt.isBlank() -> invalid("Prompt must not be blank")
        maxOutputTokens <= 0 -> invalid("Maximum output tokens must be positive")
        temperature < 0F -> invalid("Temperature must not be negative")
        topP <= 0F || topP > 1F -> invalid("Top-p must be in (0, 1]")
        topK < 0 -> invalid("Top-k must not be negative")
        !minP.isFinite() || minP !in 0f..1f -> invalid("Min-p must be in [0, 1]")
        !presencePenalty.isFinite() || presencePenalty !in 0f..2f -> invalid("Presence penalty must be in [0, 2]")
        seed < 0 -> invalid("Seed must not be negative")
        else -> null
    }

    private fun repeatValidationError(): GenerationNativeError? = when {
        !repeatPenalty.isFinite() || repeatPenalty !in 1f..2f -> invalid("Repeat penalty must be in [1, 2]")
        repeatLastN !in 0..4_096 -> invalid("Repeat window must be in [0, 4096]")
        repeatPenalty != 1f && repeatLastN == 0 -> invalid("Repeat window must be positive when repeat penalty is enabled")
        else -> null
    }

    private fun constraintValidationError(): GenerationNativeError? = when {
        outputConstraintType !in OUTPUT_CONSTRAINT_TYPES -> invalid("Unsupported output constraint type")
        outputConstraintType == "JSON_SCHEMA" && outputSchema.isNullOrBlank() -> invalid("JSON Schema must not be blank")
        stopSequences.any { it.isEmpty() } -> invalid("Stop sequences must not be empty")
        else -> null
    }

    private fun reasoningValidationError(): GenerationNativeError? {
        val valuesPresent = listOf(reasoningMaxTokens, reasoningCloseMarker, reasoningForcedCloseText).count { it != null }
        if (valuesPresent == 0) return null
        if (valuesPresent != REASONING_FIELD_COUNT) {
            return invalid("Reasoning transition configuration must be complete")
        }

        val maxReasoning = requireNotNull(reasoningMaxTokens)
        val closeMarker = requireNotNull(reasoningCloseMarker)
        val forcedClose = requireNotNull(reasoningForcedCloseText)
        return when {
            maxReasoning !in 1 until maxOutputTokens ->
                invalid("Reasoning token budget must leave output capacity for the final answer")

            closeMarker.isBlank() -> invalid("Reasoning close marker must not be blank")

            forcedClose.isBlank() -> invalid("Forced reasoning close text must not be blank")

            closeMarker !in forcedClose -> invalid("Forced reasoning close text must contain the close marker")

            else -> null
        }
    }

    private fun invalid(message: String): GenerationNativeError = GenerationNativeError(
        code = GenerationNativeErrorCode.INVALID_ARGUMENT,
        message = message,
    )

    private companion object {
        const val REASONING_FIELD_COUNT = 3
        val OUTPUT_CONSTRAINT_TYPES = setOf("TEXT", "JSON", "JSON_SCHEMA")
    }
}

sealed interface ContextCreationResult {
    data class Success(val context: LoadedNativeContext) : ContextCreationResult
    data class Failure(val error: GenerationNativeError) : ContextCreationResult
}

sealed interface GenerationNativeOperationResult {
    data object Success : GenerationNativeOperationResult
    data class Failure(val error: GenerationNativeError) : GenerationNativeOperationResult
}

data class GenerationNativeError(val code: GenerationNativeErrorCode, val message: String)

enum class GenerationNativeErrorCode {
    INVALID_ARGUMENT,
    UNKNOWN_HANDLE,
    CONTEXT_CREATE_FAILED,
    UNSUPPORTED_MODEL,
    TOKENIZATION_FAILED,
    CONTEXT_OVERFLOW,
    DECODE_FAILED,
    SAMPLER_FAILED,
    TOKEN_DECODE_FAILED,
    INTERNAL,
    NATIVE_PROTOCOL,
}
