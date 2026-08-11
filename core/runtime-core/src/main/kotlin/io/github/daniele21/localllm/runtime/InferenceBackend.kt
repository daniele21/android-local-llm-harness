package io.github.daniele21.localllm.runtime

import io.github.daniele21.localllm.contracts.ChatTemplateSource
import io.github.daniele21.localllm.contracts.GenerationInput
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.OutputConstraint
import io.github.daniele21.localllm.contracts.StopReason
import io.github.daniele21.localllm.contracts.ThinkingMode
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

data class BackendModelCapabilities(
    val maximumContextTokens: Int,
    val supportsGrammar: Boolean,
    val supportsReasoningTransition: Boolean = false,
)

data class BackendPromptPlanningRequest(
    val input: GenerationInput,
    val systemPrompt: String?,
    val chatTemplatePolicy: ChatTemplatePolicy,
    val thinkingMode: ThinkingMode = ThinkingMode.DISABLED,
)

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

data class BackendReasoningControl(val maxReasoningTokens: Int, val closeMarker: String, val forcedCloseText: String) {
    init {
        require(maxReasoningTokens > 0) { "Reasoning token budget must be positive" }
        require(closeMarker.isNotBlank()) { "Reasoning close marker must not be blank" }
        require(forcedCloseText.isNotBlank()) { "Forced reasoning close text must not be blank" }
        require(closeMarker in forcedCloseText) { "Forced reasoning close text must contain the close marker" }
    }
}

data class BackendGenerationRequest(
    val requestId: String,
    val prompt: String,
    val maxOutputTokens: Int,
    val temperature: Float,
    val topP: Float,
    val topK: Int,
    val minP: Float = 0f,
    val presencePenalty: Float = 0f,
    val repeatPenalty: Float,
    val repeatLastN: Int,
    val seed: Long,
    val outputConstraint: OutputConstraint = OutputConstraint.Text,
    val stopTokenIds: Set<Int> = emptySet(),
    val stopSequences: List<String> = emptyList(),
    val reasoningControl: BackendReasoningControl? = null,
)

data class BackendGenerationMetrics(
    val inputTokens: Int,
    val outputTokens: Int,
    val promptDurationMs: Long,
    val generationDurationMs: Long,
    val stopReason: StopReason = StopReason.UNKNOWN,
    val reasoningTokens: Int? = null,
    val answerTokens: Int? = null,
)

sealed interface BackendGenerationOutcome {
    data class Completed(val metrics: BackendGenerationMetrics) : BackendGenerationOutcome
    data class Cancelled(val metrics: BackendGenerationMetrics) : BackendGenerationOutcome
}

class BackendException(val code: String, message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

interface InferenceBackend {
    val id: String
    val revision: String?
        get() = null

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
