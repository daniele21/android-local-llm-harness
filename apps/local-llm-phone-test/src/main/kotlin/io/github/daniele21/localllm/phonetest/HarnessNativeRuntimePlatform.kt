package io.github.daniele21.localllm.phonetest

import android.content.Context
import io.github.daniele21.localllm.runtime.ActivationResidencyCoordinator
import io.github.daniele21.localllm.runtime.ActivationResidencyInferenceBackend
import io.github.daniele21.localllm.runtime.InferenceBackend
import io.github.daniele21.localllm.runtime.LlamaCppInferenceBackend
import io.github.daniele21.localllm.store.FileSystemModelStore
import io.github.daniele21.localllm.store.ModelStore
import java.io.File

/** Production runtime factory shared by the ordinary debug and release phone variants. */
internal object HarnessNativeRuntimePlatform {
    fun modelStore(context: Context): ModelStore = FileSystemModelStore(File(context.noBackupFilesDir, MODEL_STORE_DIRECTORY))

    fun backend(context: Context, activationResidency: ActivationResidencyCoordinator): InferenceBackend {
        val nativeLibraryDirectory = File(context.applicationInfo.nativeLibraryDir)
        require(nativeLibraryDirectory.isDirectory) { "Native library directory is unavailable" }
        return ActivationResidencyInferenceBackend(
            delegate = LlamaCppInferenceBackend(nativeLibraryDirectory),
            activationResidency = activationResidency,
        )
    }

    private const val MODEL_STORE_DIRECTORY = "local-llm-phone-test"
}
