package io.github.daniele21.localllm.llamacpp

import io.github.daniele21.localllm.models.GgufModelProfile
import java.util.Base64

interface NativeLlamaGenerationApi {
    fun createContext(
        modelHandle: Long,
        contextSize: Int,
        batchSize: Int,
        microBatchSize: Int,
        threads: Int,
        batchThreads: Int,
        flashAttention: Boolean,
    ): Array<String>

    fun releaseContext(contextHandle: Long): Array<String>

    fun generate(
        contextHandle: Long,
        prompt: String,
        maxOutputTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        seed: Long,
    ): Array<String>
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
        flashAttention: Boolean,
    ): Array<String>

    external override fun releaseContext(contextHandle: Long): Array<String>

    external override fun generate(
        contextHandle: Long,
        prompt: String,
        maxOutputTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        seed: Long,
    ): Array<String>
}

class LlamaCppGenerationBridge(private val nativeApi: NativeLlamaGenerationApi = JniLlamaGenerationApi()) {
    fun createContext(model: LoadedNativeModel, profile: GgufModelProfile): ContextCreationResult = decodeContextCreation(
        response = nativeApi.createContext(
            modelHandle = model.handle.value,
            contextSize = profile.contextSize,
            batchSize = profile.batchSize,
            microBatchSize = profile.microBatchSize,
            threads = profile.cpuThreads,
            batchThreads = profile.batchThreads,
            flashAttention = profile.flashAttention,
        ),
        model = model,
    )

    fun releaseContext(context: LoadedNativeContext): GenerationNativeOperationResult = decodeOperation(
        nativeApi.releaseContext(context.handle.value),
    )

    fun generate(context: LoadedNativeContext, prompt: String, config: NativeGenerationConfig): NativeGenerationResult {
        val validationError = config.validationError(prompt)
        if (validationError != null) {
            return NativeGenerationResult.Failure(validationError)
        }

        return decodeGeneration(
            nativeApi.generate(
                contextHandle = context.handle.value,
                prompt = prompt,
                maxOutputTokens = config.maxOutputTokens,
                temperature = config.temperature,
                topP = config.topP,
                topK = config.topK,
                seed = config.seed,
            ),
        )
    }

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

    private fun decodeGeneration(response: Array<String>): NativeGenerationResult {
        if (response.size == GENERATION_FIELD_COUNT && response[0] == OK) {
            return try {
                NativeGenerationResult.Success(
                    output = String(Base64.getDecoder().decode(response[1]), Charsets.UTF_8),
                    metrics = NativeGenerationMetrics(
                        inputTokens = response[2].toInt(),
                        outputTokens = response[3].toInt(),
                        promptDurationMs = response[4].toLong(),
                        generationDurationMs = response[5].toLong(),
                    ),
                )
            } catch (error: IllegalArgumentException) {
                NativeGenerationResult.Failure(
                    protocolError("Native generation response is invalid: ${error.message}"),
                )
            }
        }
        return NativeGenerationResult.Failure(decodeError(response))
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
        const val GENERATION_FIELD_COUNT = 6
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

data class NativeGenerationConfig(val maxOutputTokens: Int, val temperature: Float, val topP: Float, val topK: Int, val seed: Long) {
    internal fun validationError(prompt: String): GenerationNativeError? = when {
        prompt.isBlank() -> invalid("Prompt must not be blank")
        maxOutputTokens <= 0 -> invalid("Maximum output tokens must be positive")
        temperature < 0F -> invalid("Temperature must not be negative")
        topP <= 0F || topP > 1F -> invalid("Top-p must be in (0, 1]")
        topK < 0 -> invalid("Top-k must not be negative")
        seed < 0 -> invalid("Seed must not be negative")
        else -> null
    }

    private fun invalid(message: String): GenerationNativeError = GenerationNativeError(
        code = GenerationNativeErrorCode.INVALID_ARGUMENT,
        message = message,
    )
}

data class NativeGenerationMetrics(val inputTokens: Int, val outputTokens: Int, val promptDurationMs: Long, val generationDurationMs: Long)

sealed interface NativeGenerationResult {
    data class Success(val output: String, val metrics: NativeGenerationMetrics) : NativeGenerationResult

    data class Failure(val error: GenerationNativeError) : NativeGenerationResult
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
