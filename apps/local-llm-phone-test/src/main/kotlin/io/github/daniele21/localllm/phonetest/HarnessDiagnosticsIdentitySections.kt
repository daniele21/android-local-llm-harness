package io.github.daniele21.localllm.phonetest

import android.app.ActivityManager
import android.os.Build
import io.github.daniele21.localllm.models.GgufModelProfile

internal class HarnessDiagnosticsIdentitySections(
    private val packageName: String,
    private val versionName: String?,
    private val versionCode: Long?,
    private val memoryInfo: ActivityManager.MemoryInfo,
    private val model: ImportedPhoneModel?,
    private val executionProfile: GgufModelProfile?,
    private val runtimeState: String?,
    private val loadedModel: String?,
    private val activeSessions: Int?,
    private val queuedRequests: Int?,
) {
    fun appendTo(builder: StringBuilder) {
        builder.appendAppSection()
        builder.appendDeviceSection()
        builder.appendModelSection()
        builder.appendExecutionProfileSection()
        builder.appendRuntimeSection()
    }

    private fun StringBuilder.appendAppSection() {
        appendLine("[app]")
        appendLine("package=$packageName")
        appendLine("versionName=${versionName.asSafeOrUnavailable()}")
        appendLine("versionCode=${versionCode.orUnavailable()}")
        appendLine("buildType=${BuildConfig.BUILD_TYPE}")
        appendLine()
    }

    private fun StringBuilder.appendDeviceSection() {
        appendLine("[device]")
        appendLine("manufacturer=${Build.MANUFACTURER.safeToken()}")
        appendLine("model=${Build.MODEL.safeToken()}")
        appendLine("androidRelease=${Build.VERSION.RELEASE.safeToken()}")
        appendLine("androidApi=${Build.VERSION.SDK_INT}")
        appendLine("abis=${Build.SUPPORTED_ABIS.joinToString().safeToken()}")
        appendLine("availableProcessors=${Runtime.getRuntime().availableProcessors()}")
        appendLine("totalRamBytes=${memoryInfo.totalMem}")
        appendLine("availableRamBytes=${memoryInfo.availMem}")
        appendLine("lowMemory=${memoryInfo.lowMemory}")
        appendLine("memoryThresholdBytes=${memoryInfo.threshold}")
        appendLine()
    }

    private fun StringBuilder.appendModelSection() {
        appendLine("[model]")
        if (model == null) {
            appendLine("selected=false")
        } else {
            appendLine("selected=true")
            appendLine("digest=${model.digest.sha256}")
            appendLine("fileName=${model.fileName.safeToken()}")
            appendLine("sizeBytes=${model.sizeBytes}")
            appendLine("architecture=${model.architecture.safeToken()}")
            appendLine("quantization=${model.quantization.safeToken()}")
        }
        appendLine()
    }

    private fun StringBuilder.appendExecutionProfileSection() {
        appendLine("[execution-profile]")
        if (executionProfile == null) {
            appendLine("available=false")
        } else {
            appendLine("available=true")
            appendLine("profileId=${executionProfile.id.safeToken()}")
            appendLine("contextSize=${executionProfile.contextSize}")
            appendLine("batchSize=${executionProfile.batchSize}")
            appendLine("microBatchSize=${executionProfile.microBatchSize}")
            appendLine("cpuThreads=${executionProfile.cpuThreads}")
            appendLine("batchThreads=${executionProfile.batchThreads}")
            appendLine("gpuLayers=${executionProfile.gpuLayers}")
            appendLine("useMmap=${executionProfile.useMmap}")
            appendLine("useMlock=${executionProfile.useMlock}")
            appendLine("flashAttention=${executionProfile.flashAttention}")
        }
        appendLine()
    }

    private fun StringBuilder.appendRuntimeSection() {
        appendLine("[runtime]")
        appendLine("available=${runtimeState != null}")
        appendLine("state=${runtimeState.asSafeOrUnavailable()}")
        appendLine("loadedModel=${loadedModel.asSafeOrNone()}")
        appendLine("activeSessions=${activeSessions.orUnavailable()}")
        appendLine("queuedRequests=${queuedRequests.orUnavailable()}")
        appendLine()
    }
}
