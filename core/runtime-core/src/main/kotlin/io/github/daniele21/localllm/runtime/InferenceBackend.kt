package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.ChatTemplateSource
import io.github.daniele21.localllm.contracts.GenerationInput
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.OutputConstraint
import io.github.daniele21.localllm.contracts.StopReason
import io.github.daniele21.localllm.models.ChatTemplatePolicy
import io.github.daniele21.localllm.models.GgufModelProfile
import io.github.daniele21.localllm.store.StoredModel

interface BackendModelHandle {
    val digest: ModelDigest
    val profileId: String
    val loadDurationMs: Long
}

interface BackendContextHandle {
    val model: BackendModelHandle
    val contextSize: Int
}

data class BackendModelCapabilities(val maximumContextTokens: Int, val supportsGrammar: Boolean)

data class BackendPromptPlanningRequest(val input: GenerationInput, val systemPrompt: String?, val chatTemplatePolicy: ChatTemplatePolicy)

data class BackendPromptPlan(
    val prompt: String,
    val tokenCount: Int,
    val chatTemplateId: String,
    val chatTemplateSource: ChatTemplateSource,
    val stopTokenIds: Set<Int> = emptySet(),
    val stopSequences: List<String> = emptyList(),
)

data class BackendContextConfiguration(val contextSize: Int) {
    init {
        require(contextSize > 0) { "Context size must be positive" }
    }
}

data class BackendGenerationRequest(
    val requestId: String,
    val prompt: String,
    val maxOutputTokens: Int,
    val temperature: Float,
    val topP: Float,
    val topK: Int,
    val repeatPenalty: Float,
    val repeatLastN: Int,
    val seed: Long,
    val outputConstraint: OutputConstraint = OutputConstraint.Text,
    val stopTokenIds: Set<Int> = emptySet(),
    val stopSequences: List<String> = emptyList(),
)

data class BackendGenerationMetrics(
    val inputTokens: Int,
    val outputTokens: Int,
    val promptDurationMs: Long,
    val generationDurationMs: Long,
    val stopReason: StopReason = StopReason.UNKNOWN,
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
    fun modelCapabilities(model: BackendModelHandle): BackendModelCapabilities
    fun planPrompt(model: BackendModelHandle, request: BackendPromptPlanningRequest): BackendPromptPlan

    fun createContext(
        model: BackendModelHandle,
        profile: GgufModelProfile,
        configuration: BackendContextConfiguration,
    ): BackendContextHandle

    fun releaseContext(context: BackendContextHandle)

    fun generate(
        context: BackendContextHandle,
        request: BackendGenerationRequest,
        onChunk: (text: String, generatedTokens: Int) -> Boolean,
    ): BackendGenerationOutcome

    fun cancel(requestId: String): Boolean
}
