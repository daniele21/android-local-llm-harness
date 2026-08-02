package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.models.GgufModelProfile
import io.github.daniele21.localllm.store.StoredModel

interface BackendModelHandle {
    val digest: ModelDigest
    val profileId: String
    val loadDurationMs: Long
}

interface BackendContextHandle {
    val model: BackendModelHandle
}

data class BackendGenerationRequest(
    val requestId: String,
    val prompt: String,
    val maxOutputTokens: Int,
    val temperature: Float,
    val topP: Float,
    val topK: Int,
    val seed: Long,
)

data class BackendGenerationMetrics(
    val inputTokens: Int,
    val outputTokens: Int,
    val promptDurationMs: Long,
    val generationDurationMs: Long,
)

sealed interface BackendGenerationOutcome {
    data class Completed(val metrics: BackendGenerationMetrics) : BackendGenerationOutcome
    data class Cancelled(val metrics: BackendGenerationMetrics) : BackendGenerationOutcome
}

class BackendException(val code: String, message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

interface InferenceBackend {
    val id: String

    fun initialize()
    fun shutdown()
    fun loadModel(storedModel: StoredModel, profile: GgufModelProfile): BackendModelHandle
    fun unloadModel(model: BackendModelHandle)
    fun createContext(model: BackendModelHandle, profile: GgufModelProfile): BackendContextHandle
    fun releaseContext(context: BackendContextHandle)

    fun generate(
        context: BackendContextHandle,
        request: BackendGenerationRequest,
        onChunk: (text: String, generatedTokens: Int) -> Boolean,
    ): BackendGenerationOutcome

    fun cancel(requestId: String): Boolean
}
