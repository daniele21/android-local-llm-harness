package io.github.daniele21.localllm.phonetest

// Activity-scoped Android effects consumed through ViewModel-owned Playground intents.
internal interface PlaygroundEffects : AutoCloseable {
    fun snapshot(): PlaygroundState

    fun start(model: ImportedPhoneModel, prompt: String, options: PlaygroundRequestOptions): Boolean

    fun cancel(): Boolean

    fun releaseRuntime(onComplete: () -> Unit): Boolean
}

internal enum class PlaygroundStartResult {
    STARTED,
    MODEL_REQUIRED,
    BUSY,
    INVALID_SETTINGS,
    CONTROLLER_UNAVAILABLE,
    REJECTED,
}
