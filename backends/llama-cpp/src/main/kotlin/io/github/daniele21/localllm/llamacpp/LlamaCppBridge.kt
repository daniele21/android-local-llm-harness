package io.github.daniele21.localllm.llamacpp

import io.github.daniele21.localllm.models.GgufModelProfile

class LlamaCppBridge {
    init {
        System.loadLibrary("local_llm_jni")
    }

    external fun runtimeVersion(): String
    external fun isLlamaCppLinked(): Boolean

    fun inspect(profile: GgufModelProfile): NativeRuntimeStatus = NativeRuntimeStatus(
        linked = isLlamaCppLinked(),
        runtimeVersion = runtimeVersion(),
        modelProfileId = profile.id,
        detail = if (isLlamaCppLinked()) {
            "llama.cpp backend linked"
        } else {
            "JNI stub active; pin and link llama.cpp before inference"
        },
    )
}

data class NativeRuntimeStatus(
    val linked: Boolean,
    val runtimeVersion: String,
    val modelProfileId: String,
    val detail: String,
)
