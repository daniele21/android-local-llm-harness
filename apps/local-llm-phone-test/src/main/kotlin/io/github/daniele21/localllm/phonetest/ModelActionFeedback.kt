package io.github.daniele21.localllm.phonetest

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal enum class ModelActionFeedbackTone {
    INFO,
    SUCCESS,
    ERROR,
}

internal data class ModelActionFeedbackState(
    val latest: String = "No model actions yet",
    val tone: ModelActionFeedbackTone = ModelActionFeedbackTone.INFO,
    val history: List<String> = emptyList(),
)

internal object ModelActionFeedbackStore {
    private const val HISTORY_LIMIT = 5
    private val mutableState = MutableStateFlow(ModelActionFeedbackState())

    val state: StateFlow<ModelActionFeedbackState> = mutableState.asStateFlow()

    fun publish(message: String) {
        val normalized = message.trim().ifBlank { return }
        mutableState.update { current ->
            val history = (listOf(normalized) + current.history)
                .distinct()
                .take(HISTORY_LIMIT)
            current.copy(
                latest = normalized,
                tone = classify(normalized),
                history = history,
            )
        }
    }

    internal fun classify(message: String): ModelActionFeedbackTone = when {
        message.startsWith("Failed:", ignoreCase = true) -> ModelActionFeedbackTone.ERROR
        message.contains("failed", ignoreCase = true) -> ModelActionFeedbackTone.ERROR
        message.contains("selected for Playground", ignoreCase = true) -> ModelActionFeedbackTone.SUCCESS
        message.contains("loaded and ready", ignoreCase = true) -> ModelActionFeedbackTone.SUCCESS
        message.contains("passed", ignoreCase = true) -> ModelActionFeedbackTone.SUCCESS
        message.contains("removed", ignoreCase = true) -> ModelActionFeedbackTone.SUCCESS
        else -> ModelActionFeedbackTone.INFO
    }
}
