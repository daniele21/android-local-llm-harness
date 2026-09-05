package io.github.daniele21.localllm.phonetest

internal interface HarnessInferenceActivityActions {
    fun refresh()

    fun openDetail(requestId: String)

    fun selectFilter(selection: InferenceActivityFilterSelection)

    fun clearHistory()

    fun clearFeedback()
}
