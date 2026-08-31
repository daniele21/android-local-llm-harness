package io.github.daniele21.localllm.phonetest

import android.content.Context
import io.github.daniele21.localllm.runtime.ActivationResidencyCoordinator

internal object HarnessRuntimePlatform {
    fun modelStore(context: Context) = HarnessNativeRuntimePlatform.modelStore(context)

    fun backend(context: Context, activationResidency: ActivationResidencyCoordinator) =
        HarnessNativeRuntimePlatform.backend(context, activationResidency)
}
